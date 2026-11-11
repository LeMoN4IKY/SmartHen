import pytest
from fastapi.testclient import TestClient
from app import create_app, provision_device, token_hash


@pytest.fixture
def setup(tmp_path):
    clock = [1_800_000_000]
    app = create_app(str(tmp_path / 'test.sqlite3'), clock=lambda: clock[0])
    with TestClient(app) as client:
        yield client, app.state.database, clock


def account(client, email='owner@example.com'):
    response = client.post('/api/v1/auth/signup', json={'email': email, 'password': 'long-secret-password', 'display_name': 'Owner'})
    assert response.status_code == 201, response.text
    return response.json(), {'Authorization': 'Bearer ' + response.json()['access_token']}


def installation(setup):
    client, database, clock = setup
    _, auth = account(client)
    house = client.post('/api/v1/houses', headers=auth, json={'name': 'Quail house'}).json()['id']
    keys = provision_device(database, 'Controller', ['set_light'], now=clock[0])
    response = client.post(f'/api/v1/houses/{house}/devices/claim', headers=auth, json={'claim_token': keys['claim_token']})
    assert response.status_code == 200, response.text
    return auth, house, keys, {'Authorization': 'Bearer ' + keys['device_token']}


def test_password_tokens_login_logout_and_expiry(setup):
    client, database, clock = setup
    result, auth = account(client)
    with database.connect() as db:
        assert db.execute('SELECT password FROM users').fetchone()[0].startswith('scrypt$')
        assert db.execute('SELECT hash FROM sessions').fetchone()[0] == token_hash(result['access_token'])
    assert client.get('/api/v1/me', headers=auth).json()['email'] == 'owner@example.com'
    assert client.post('/api/v1/auth/login', json={'email': 'owner@example.com', 'password': 'wrong-password-long'}).status_code == 401
    login = client.post('/api/v1/auth/login', json={'email': 'OWNER@example.com', 'password': 'long-secret-password'})
    assert login.status_code == 200
    assert client.post('/api/v1/auth/logout', headers=auth).status_code == 204
    assert client.get('/api/v1/me', headers=auth).status_code == 401
    clock[0] += 86400
    assert client.get('/api/v1/me', headers={'Authorization': 'Bearer ' + login.json()['access_token']}).status_code == 401


def test_ownership_device_credentials_and_single_use_claim(setup):
    client, database, clock = setup
    auth, house, keys, device_auth = installation(setup)
    _, other = account(client, 'other@example.com')
    assert client.get(f'/api/v1/houses/{house}/readings', headers=other).status_code == 404
    assert client.get(f'/api/v1/houses/{house}/devices', headers=other).status_code == 404
    assert client.get('/api/v1/me', headers=device_auth).status_code == 401
    assert client.get('/api/v1/device/commands', headers=auth).status_code == 401
    assert client.post(f'/api/v1/houses/{house}/devices/claim', headers=auth, json={'claim_token': keys['claim_token']}).status_code == 400
    expired = provision_device(database, 'Expired', [], lifetime=1, now=clock[0])
    clock[0] += 2
    assert client.post(f'/api/v1/houses/{house}/devices/claim', headers=auth, json={'claim_token': expired['claim_token']}).status_code == 400


def test_telemetry_validates_timestamp_preserves_history_and_rejects_conflicts(setup):
    client, _, clock = setup
    auth, house, keys, device_auth = installation(setup)
    sample = {'sensor_id': 'temp', 'type': 'TEMPERATURE', 'unit': 'degC', 'value': 22.5, 'observed_at': clock[0]}
    assert client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [sample]}).status_code == 200
    assert client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [sample]}).status_code == 200
    assert client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [dict(sample, value=23)]}).status_code == 409
    assert client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [dict(sample, observed_at=clock[0]+31)]}).status_code == 422
    assert client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [dict(sample, unit='')]}).status_code == 422
    rows = client.get(f'/api/v1/houses/{house}/readings', headers=auth).json()
    assert len(rows) == 1 and rows[0]['observed_at'] == clock[0]
    older = dict(sample, observed_at=clock[0]-60, value=20)
    client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [older]})
    assert client.get(f'/api/v1/houses/{house}/readings', headers=auth).json()[0]['value'] == 22.5
    history = client.get(f'/api/v1/houses/{house}/history', headers=auth, params={'device_id': keys['device_id'], 'sensor_id': 'temp'}).json()
    assert len(history) == 2


def test_command_is_queued_idempotent_and_requires_device_ack(setup):
    client, _, clock = setup
    auth, house, keys, device_auth = installation(setup)
    path = f"/api/v1/houses/{house}/devices/{keys['device_id']}/commands"
    command = {'idempotency_key': 'operation-001', 'action': 'set_light', 'payload': {'enabled': True}}
    assert client.post(path, headers=auth, json=command).status_code == 409  # Never seen online.
    assert client.get('/api/v1/device/commands', headers=device_auth).json() == []
    result = client.post(path, headers=auth, json=command)
    assert result.status_code == 202, result.text
    queued = result.json()
    assert queued['status'] == 'queued' and queued['acknowledged_at'] is None
    assert client.post(path, headers=auth, json=command).json()['id'] == queued['id']
    assert client.post(path, headers=auth, json=dict(command, payload={'enabled': False})).status_code == 409
    ack_path = f"/api/v1/device/commands/{queued['id']}/ack"
    assert client.post(ack_path, headers=auth, json={'status': 'succeeded'}).status_code == 401
    assert client.post(ack_path, headers=device_auth, json={'status': 'succeeded'}).json()['status'] == 'succeeded'
    assert client.post(ack_path, headers=device_auth, json={'status': 'failed'}).status_code == 409
    assert client.get('/api/v1/device/commands', headers=device_auth).json() == []
    expiry_command = dict(command, idempotency_key='operation-002', ttl_seconds=5)
    expired = client.post(path, headers=auth, json=expiry_command).json()
    clock[0] += 5
    assert client.post(f"/api/v1/device/commands/{expired['id']}/ack", headers=device_auth, json={'status': 'succeeded'}).status_code == 409
    statuses = {row['id']: row['status'] for row in client.get(path, headers=auth).json()}
    assert statuses[expired['id']] == 'expired'
    assert client.post(path, headers=auth, json=expiry_command).json()['status'] == 'expired'
    clock[0] -= 4
    assert client.post(f"/api/v1/device/commands/{expired['id']}/ack", headers=device_auth, json={'status': 'succeeded'}).status_code == 409
    assert client.get('/api/v1/subscription', headers=auth).json()['status'] == 'inactive'


def test_auth_rate_limit_boundary_and_reset(setup):
    client, _, clock = setup
    for _ in range(20):
        assert client.post('/api/v1/auth/login', json={'email': 'missing@example.com', 'password': 'incorrect-password'}).status_code == 401
    assert client.post('/api/v1/auth/login', json={'email': 'missing@example.com', 'password': 'incorrect-password'}).status_code == 429
    clock[0] += 901
    assert client.post('/api/v1/auth/login', json={'email': 'missing@example.com', 'password': 'incorrect-password'}).status_code == 401


def test_telemetry_batch_failure_rolls_back_all_samples(setup):
    client, _, clock = setup
    auth, house, _, device_auth = installation(setup)
    good = {'sensor_id': 'temp', 'type': 'TEMPERATURE', 'unit': 'degC', 'value': 22.5, 'observed_at': clock[0]}
    future = dict(good, sensor_id='temp2', observed_at=clock[0]+100)
    assert client.post('/api/v1/device/telemetry', headers=device_auth, json={'readings': [good, future]}).status_code == 422
    assert client.get(f'/api/v1/houses/{house}/readings', headers=auth).json() == []


def test_other_device_cannot_acknowledge_command(setup):
    client, database, clock = setup
    auth, house, keys, device_auth = installation(setup)
    second = provision_device(database, 'Second', [], now=clock[0])
    client.post(f'/api/v1/houses/{house}/devices/claim', headers=auth, json={'claim_token': second['claim_token']})
    client.get('/api/v1/device/commands', headers=device_auth)
    command = client.post(f"/api/v1/houses/{house}/devices/{keys['device_id']}/commands", headers=auth,
                          json={'idempotency_key': 'operation-1', 'action': 'set_light'}).json()
    response = client.post(f"/api/v1/device/commands/{command['id']}/ack",
                           headers={'Authorization': 'Bearer '+second['device_token']}, json={'status': 'succeeded'})
    assert response.status_code == 404
