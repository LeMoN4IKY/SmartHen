"""Explicit telemetry-only simulator. Does not acknowledge commands or pretend to control hardware."""
import argparse
import json
import os
import time
from urllib.request import Request, urlopen

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--enable-simulator', action='store_true', required=True)
    parser.add_argument('--server', default='http://127.0.0.1:8000')
    args = parser.parse_args()
    token = os.environ['SMARTHEN_DEVICE_TOKEN']
    body = {'readings': [{'sensor_id': 'sim_temperature', 'type': 'SIMULATED_TEMPERATURE',
                          'unit': 'degC', 'value': 22.0, 'observed_at': int(time.time())}]}
    request = Request(args.server.rstrip('/') + '/api/v1/device/telemetry',
                      data=json.dumps(body).encode(), headers={'Authorization': 'Bearer ' + token,
                                                              'Content-Type': 'application/json'})
    with urlopen(request, timeout=15) as response:
        print(response.read().decode())
