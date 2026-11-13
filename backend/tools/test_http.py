"""Isolated real-HTTP protocol smoke test; no hardware or existing database is touched.

Run with backend dev dependencies installed:
  python test_http.py --backend /path/to/SmartHen/backend
Never prints account credentials, bearer tokens or claim secrets.
"""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import secrets
import socket
import subprocess
import sys
import tempfile
import time
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend', type=Path, required=True)
    args = parser.parse_args()
    backend = args.backend.resolve()
    spec = importlib.util.spec_from_file_location('smarthen_e2e_backend', backend / 'app.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    with tempfile.TemporaryDirectory(prefix='smarthen-http-e2e-') as work:
        database_path = str(Path(work) / 'isolated.sqlite3')
        with socket.socket() as probe:
            probe.bind(('127.0.0.1', 0))
            port = probe.getsockname()[1]
        base = f'http://127.0.0.1:{port}'
        environment = dict(os.environ, SMARTHEN_DB=database_path)
        with open(Path(work) / 'server.log', 'w+') as log:
            server = subprocess.Popen(
                [sys.executable, '-m', 'uvicorn', 'app:create_app', '--factory',
                 '--host', '127.0.0.1', '--port', str(port), '--no-access-log'],
                cwd=backend, env=environment, stdout=log, stderr=subprocess.STDOUT,
            )
            def request(path, *, token=None, body=None, expected=200):
                headers = {'Content-Type': 'application/json'}
                if token:
                    headers['Authorization'] = 'Bearer ' + token
                data = None if body is None else json.dumps(body).encode()
                call = Request(base + path, data=data, headers=headers)
                try:
                    with urlopen(call, timeout=10) as response:
                        status, raw = response.status, response.read()
                except HTTPError as error:
                    status, raw = error.code, error.read()
                assert status == expected, f'Unexpected HTTP status: {status}; expected {expected}'
                return json.loads(raw) if raw else None

            try:
                deadline = time.monotonic() + 15
                while True:
                    if server.poll() is not None:
                        raise RuntimeError('Isolated server exited before readiness; no credentials disclosed')
                    try:
                        request('/health')
                        break
                    except (URLError, TimeoutError):
                        if time.monotonic() >= deadline:
                            raise RuntimeError('Isolated server readiness timed out')
                        time.sleep(0.1)
                print('PASS real HTTP server readiness (isolated temporary SQLite)')

                password = secrets.token_urlsafe(24)
                def signup(email):
                    return request('/api/v1/auth/signup', body={
                        'email': email, 'password': password, 'display_name': 'Protocol Test',
                    }, expected=201)['access_token']
                owner = signup('owner@e2e.example')
                other = signup('other@e2e.example')
                request('/api/v1/me', token=owner)
                house = request('/api/v1/houses', token=owner, body={'name': 'SIMULATED protocol installation'}, expected=201)['id']
                print('PASS signup, authenticated profile and house creation')

                keys = module.provision_device(module.Database(database_path), 'SIMULATED controller', ['set_light'])
                device = keys['device_token']
                claim_path = f'/api/v1/houses/{house}/devices/claim'
                request(claim_path, token=owner, body={'claim_token': keys['claim_token']})
                request(claim_path, token=owner, body={'claim_token': keys['claim_token']}, expected=400)
                simulation = subprocess.run(
                    [sys.executable, str(backend / 'simulator.py'), '--enable-simulator', '--server', base],
                    env=dict(environment, SMARTHEN_DEVICE_TOKEN=device),
                    capture_output=True, timeout=20,
                )
                assert simulation.returncode == 0, 'Explicit telemetry simulator failed'
                latest_path = f'/api/v1/houses/{house}/readings'
                latest = request(latest_path, token=owner)
                assert len(latest) == 1 and latest[0]['type'] == 'SIMULATED_TEMPERATURE'
                assert latest[0]['observed_at'] <= latest[0]['received_at']
                history = request(f'/api/v1/houses/{house}/history?device_id={keys["device_id"]}&sensor_id=sim_temperature', token=owner)
                assert len(history) == 1 and history[0]['observed_at'] == latest[0]['observed_at']
                print('PASS one-time provisioning claim and explicit simulator latest/history over HTTP')

                request(latest_path, token=other, expected=404)
                request('/api/v1/me', token=device, expected=401)
                request('/api/v1/device/commands', token=owner, expected=401)
                print('PASS cross-owner isolation and user/device credential separation')

                request('/api/v1/device/commands', token=device)
                command_path = f'/api/v1/houses/{house}/devices/{keys["device_id"]}/commands'
                payload = {'idempotency_key': 'protocol-only-test-command', 'action': 'set_light', 'payload': {'enabled': True}}
                queued = request(command_path, token=owner, body=payload, expected=202)
                assert queued['status'] == 'queued' and queued['acknowledged_at'] is None
                assert request(command_path, token=owner, body=payload, expected=202)['id'] == queued['id']
                assert request('/api/v1/device/commands', token=device)[0]['id'] == queued['id']
                # Exercise acknowledgement protocol without asserting physical execution.
                ack_path = f'/api/v1/device/commands/{queued["id"]}/ack'
                acknowledgement = {'status': 'failed', 'detail': 'Protocol test only: no hardware connected; action not executed'}
                assert request(ack_path, token=device, body=acknowledgement)['status'] == 'failed'
                assert request(ack_path, token=device, body=acknowledgement)['status'] == 'failed'
                request(ack_path, token=device, body={'status': 'succeeded'}, expected=409)
                assert request('/api/v1/device/commands', token=device) == []
                print('PASS queued command, idempotent replay and simulated failure acknowledgement; no actuation')

                request('/api/v1/auth/logout', token=owner, body={}, expected=204)
                request('/api/v1/me', token=owner, expected=401)
                print('PASS logout revokes session')
                print('RESULT: real HTTP/SQLite protocol passed; no Android UI, firmware or physical hardware tested')
            finally:
                server.terminate()
                try:
                    server.wait(timeout=10)
                except subprocess.TimeoutExpired:
                    server.kill()
                    server.wait(timeout=5)


if __name__ == '__main__':
    main()
