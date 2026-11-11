# SmartHen Quail API foundation

This is a new backend; the original external backend and its database are not modified or migrated. No random data or devices are created at startup. `/docs` contains the generated OpenAPI contract. All times are Unix seconds.

## Local development

```sh
python3 -m venv .venv
.venv/bin/pip install -r requirements-dev.txt
SMARTHEN_DB=data/smarthen.sqlite3 .venv/bin/uvicorn app:create_app --factory --host 127.0.0.1 --port 8000
.venv/bin/python -m pytest -q
```

Use HTTPS behind a correctly configured reverse proxy for deployed service; loopback HTTP is for development. The API does not enable cross-origin browser access by default. Back up the SQLite database using SQLite's online backup API, including a consistent WAL state, not by copying only the live `.sqlite3` file.

## Account and house contract

- `POST /api/v1/auth/signup` accepts `{ "email": "owner@example.com", "password": "at-least-12-characters", "display_name": "Owner" }`.
- `POST /api/v1/auth/login` accepts email and password. Both return `{access_token, token_type: "bearer", expires_at, user: {id,email,display_name}}`.
- Send `Authorization: Bearer <access_token>` for account routes. Sessions expire after 24 hours. `POST /api/v1/auth/logout` revokes the current session (204). `GET /api/v1/me` returns the user.
- `POST /api/v1/houses` accepts `{ "name": "Quail house 1" }`; `GET /api/v1/houses` lists only the authenticated owner's houses.
- `GET /api/v1/subscription` returns an inactive subscription. It does not take payment or grant entitlements; there is deliberately no client-accessible plan activation endpoint.

Passwords use salted scrypt (N=32768, r=8, p=1); only SHA-256 digests of cryptographically random bearer tokens are persisted. Passwords and bearer tokens must not be logged. Authentication is limited to 20 attempts per source IP per 15 minutes; configure trusted proxy addresses rather than trusting arbitrary forwarded headers.

## Commissioning an actual controller

Run provisioning on the trusted server, once per controller:

```sh
.venv/bin/python provision.py --database data/smarthen.sqlite3 --name 'Controller 1'
```

This prints `device_id`, a device bearer token and a one-time claim token. Deliver the device token over a secure provisioning channel to the controller and keep the claim token separate (e.g. the physical installation's QR code). The claim expires after 24 hours. Output is secret and must not be committed or retained in terminal logs. Do not put user session tokens on a controller.

The owner calls `POST /api/v1/houses/{house_id}/devices/claim` with `{ "claim_token": "..." }`. Claim consumption and assignment are atomic; already claimed devices cannot be transferred using the old token. `GET /api/v1/houses/{house_id}/devices` returns IDs, names, capabilities and last contact times, never provisioning secrets.

Using its separate device bearer token, the claimed controller submits:

```json
{
  "readings": [
    {"sensor_id":"temperature_1","type":"TEMPERATURE","unit":"degC","value":22.5,"observed_at":1800000000}
  ]
}
```

to `POST /api/v1/device/telemetry`. The timestamp in this example is illustrative: firmware must send its actual synchronized measurement time. Sensor IDs are scoped to the authenticated device. Values must be finite; units must be nonempty; timestamps cannot exceed server time by over 30 seconds. Future-but-tolerated readings must still appear as unknown freshness in the client until their observation time is reached. Historical samples retain their observation time, never masquerading as new measurements. Identical repeated samples are idempotent; conflicting samples at the same sensor/time return 409. One invalid entry rolls back the entire batch.

Owners read latest samples from `GET /api/v1/houses/{house_id}/readings`; history from `GET /api/v1/houses/{house_id}/history?device_id=...&sensor_id=...&since=...`. History returns at most 1000 newest samples, newest first. Both endpoints enforce house ownership. Hardware-specific unit schemas, calibration, retention and pagination remain future work; arbitrary type strings are preserved, not interpreted as validated animal-care limits.

## Commands and acknowledgement

Devices have no command capabilities by default. Add `--capability set_light` to trusted provisioning only when the actual firmware implements that action and validates its payload. The API validates action membership; it does not know hardware-specific safe payload ranges.

1. The device polls `GET /api/v1/device/commands` to establish contact and receive unexpired queued commands.
2. The owner sends `POST /api/v1/houses/{house_id}/devices/{device_id}/commands` with `{ "idempotency_key":"unique-operation-id", "action":"set_light", "payload":{"enabled":true}, "ttl_seconds":60 }`.
3. Response 202 means **queued**, never physical execution. A device absent for over 60 seconds cannot receive a new command.
4. Firmware must persist processed command IDs and deduplicate **before physical execution**. Polling returns queued items repeatedly until acknowledgement; retries must not actuate twice. Firmware must independently validate action, payload and expiry immediately before execution.
5. The device posts `{ "status":"succeeded", "detail":"..." }` or `failed` to `/api/v1/device/commands/{command_id}/ack`. Expired commands cannot start or be acknowledged as fresh. Matching repeated acknowledgements are idempotent; conflicting ones return 409.
6. Owners poll `GET /api/v1/houses/{house_id}/devices/{device_id}/commands` for recorded results. Lost acknowledgements leave execution uncertain; the UI must not infer success.

The fixed 60-second contact policy is a conservative initial connection policy, not a husbandry threshold. It must be validated against actual controller schedules. This foundation does not replace controller-side emergency interlocks or offline autonomous control.

## Explicit simulation

Simulation is never automatic. Provision and claim a separate clearly named simulation controller, set `SMARTHEN_DEVICE_TOKEN` in its environment, then run:

```sh
.venv/bin/python simulator.py --enable-simulator --server http://127.0.0.1:8000
```

It sends one reading with type `SIMULATED_TEMPERATURE` and sensor ID `sim_temperature`. It does not acknowledge commands or claim hardware is running.

## Deployment limits before production

This tested foundation still needs managed schema migrations, operational backup/restore verification, TLS deployment, email verification/recovery, credential rotation/revocation tooling for devices, organization memberships/roles, telemetry retention and quotas, monitoring and load tests. Single-server SQLite is an initial persistence choice; assess PostgreSQL before multi-host deployment. Auth attempt records are retained for the current 15-minute window. Other rows require a deliberate retention policy. Provisioning CLI access is privileged.

No payment provider, camera streaming, firmware, calibrated sensor contracts, audited command payload schemas or safety-certified automation is included. Real-device testing and failure recovery are required before controlling an occupied installation. The old application's endpoints are not compatible; use a separate v1 adapter rather than silently treating the legacy API as authenticated.
