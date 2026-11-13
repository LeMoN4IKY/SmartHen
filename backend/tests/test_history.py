import base64
from test_api import setup, installation, account


def seed(database, device_id, timestamps):
    with database.connect() as db:
        db.executemany('INSERT INTO readings(device_id,sensor_id,type,unit,value,observed_at,received_at) VALUES(?,?,?,?,?,?,?)',
                       [(device_id, 'temp', 'TEMPERATURE', 'degC', 22.0, timestamp, timestamp) for timestamp in timestamps])


def test_page_reads_all_points_and_freezes_snapshot(setup):
    client, database, _ = setup
    auth, house, keys, _ = installation(setup)
    seed(database, keys['device_id'], range(100, 1110))
    path = f'/api/v1/houses/{house}/history-page'
    params = {'device_id': keys['device_id'], 'sensor_id': 'temp', 'limit': 200}
    first = client.get(path, headers=auth, params=params).json()
    assert len(first['points']) == 200 and first['next_cursor']
    # New live and historical backfilled rows do not change an in-progress page sequence.
    seed(database, keys['device_id'], [99, 1200])
    points = first['points']
    cursor = first['next_cursor']
    while cursor:
        response = client.get(path, headers=auth, params=dict(params, cursor=cursor))
        assert response.status_code == 200, response.text
        page = response.json()
        points.extend(page['points'])
        cursor = page['next_cursor']
    assert [point['observed_at'] for point in points] == list(range(1109, 99, -1))
    assert client.get(path, headers=auth, params=params).json()['points'][0]['observed_at'] == 1200


def test_range_inclusive_empty_and_validation(setup):
    client, database, _ = setup
    auth, house, keys, _ = installation(setup)
    seed(database, keys['device_id'], [100, 101, 102])
    path = f'/api/v1/houses/{house}/history-page'
    params = {'device_id': keys['device_id'], 'sensor_id': 'temp'}
    page = client.get(path, headers=auth, params=dict(params, since=101, until=102)).json()
    assert [point['observed_at'] for point in page['points']] == [102, 101]
    assert page['next_cursor'] is None
    assert client.get(path, headers=auth, params=dict(params, since=999)).json() == {'points': [], 'next_cursor': None}
    for invalid in [{'since': -1}, {'since': 5, 'until': 4}, {'until': -1}, {'limit': 0}, {'limit': 1001}, {'since': 2**64}]:
        assert client.get(path, headers=auth, params={**params, **invalid}).status_code == 422


def test_cursor_bound_to_query_and_ownership(setup):
    client, database, _ = setup
    auth, house, keys, _ = installation(setup)
    seed(database, keys['device_id'], [100, 101])
    path = f'/api/v1/houses/{house}/history-page'
    params = {'device_id': keys['device_id'], 'sensor_id': 'temp', 'limit': 1}
    cursor = client.get(path, headers=auth, params=params).json()['next_cursor']
    assert client.get(path, headers=auth, params=dict(params, cursor=cursor, since=1)).status_code == 422
    assert client.get(path, headers=auth, params=dict(params, cursor=cursor, sensor_id='another')).status_code == 422
    _, other = account(client, 'other@example.com')
    assert client.get(path, headers=other, params=dict(params, cursor=cursor)).status_code == 404
    assert client.get(path, params=params).status_code == 401
    for invalid in ['garbage!', base64.urlsafe_b64encode(b'null').decode(), base64.urlsafe_b64encode(b'{}').decode()]:
        assert client.get(path, headers=auth, params=dict(params, cursor=invalid)).status_code == 422


def test_same_timestamp_different_sensor_does_not_leak_into_page(setup):
    client, database, _ = setup
    auth, house, keys, _ = installation(setup)
    seed(database, keys['device_id'], [100, 101])
    with database.connect() as db:
        db.execute('INSERT INTO readings(device_id,sensor_id,type,unit,value,observed_at,received_at) VALUES(?,?,?,?,?,?,?)',
                   (keys['device_id'], 'humidity', 'HUMIDITY', '%', 50, 101, 101))
    path = f'/api/v1/houses/{house}/history-page'
    params = {'device_id': keys['device_id'], 'sensor_id': 'temp', 'limit': 1}
    first = client.get(path, headers=auth, params=params).json()
    second = client.get(path, headers=auth, params=dict(params, cursor=first['next_cursor'])).json()
    assert [first['points'][0]['observed_at'], second['points'][0]['observed_at']] == [101, 100]
    assert second['next_cursor'] is None
