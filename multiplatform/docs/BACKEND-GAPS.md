# Backend integration observations

Read-only review of `SmartHen-backend/main.py`, `models.py`, and routers on 2026-11-10. No backend files changed.

- `GET /sensors/{coop_id}` returns `last_seen` in Unix **seconds**, and defaults it to zero. Preserve zero as unknown, never replace it with the current time. A successful fetch does not prove fresh telemetry.
- Sensor primary keys are `(id, coop_id)`. Never identify a device by sensor ID alone; sample IDs repeat between houses.
- Background health checks run every 60 seconds and mark offline after 300 seconds. The client must compute age itself because `is_online=true` can lag. Product freshness thresholds remain to be agreed against actual device sampling frequency.
- Sensor `min_value/max_value/warning_threshold` are not validated husbandry guidance. Seed egg count exceeds its own maximum, and the single warning threshold cannot express both high and low limits. Do not use these values to configure quail welfare controls without an equipment/age-specific contract.
- No registration, password hashing, session issuance, authentication dependency, or per-house authorization was found in these routers. Caller-supplied user IDs are not proof of identity.
- `POST /coops/add` can assign a serial to an arbitrary user ID without proving possession or current-owner approval. Do not expose this as secure production enrollment.
- Sensor ingestion accepts a query parameter value without device authentication, finite-value validation, sequence numbers, or device timestamps. The timestamp represents server reception.
- No actuator command endpoint, device capabilities, command identifiers, expiry, idempotency, acknowledgement, or audit history exists. Local command eligibility is only a preflight; the server and controller must enforce the same constraints.
- Camera APIs mutate one process-global dictionary. They are not house-scoped, do not operate hardware, and do not provide a video stream. Do not show their response as proof of physical camera state.
- Subscription update directly grants a plan and uses a fixed historical expiry. No payment-provider verification, purchase restoration, receipt handling, or webhook contract exists. It must not be presented as an actual payment integration.
- Startup seeds random historical values. This data needs explicit demo provenance before displaying it in a real installation.

Domain sources are intentionally independent of these legacy transport shapes. API adapters should preserve identity, source, timestamp and invalid/unknown states. LIVE denotes an actual backend response; CACHE and DEMO can be displayed but must not authorize equipment commands.
