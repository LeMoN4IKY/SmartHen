# Paginated history contract

Use `GET /api/v1/houses/{house_id}/history-page` for analytics. It requires the owner's bearer session and these query parameters:

| Parameter | Meaning |
| --- | --- |
| `device_id`, `sensor_id` | Exact measurement series |
| `since` | Inclusive observation-time lower bound, Unix seconds, default 0 |
| `until` | Optional inclusive upper bound, must be at least `since` |
| `limit` | Page size from 1 to 1000, default 200 |
| `cursor` | Opaque `next_cursor` from the previous page |

Response: `{ "points": [{ "device_id": "...", "sensor_id": "...", "type": "TEMPERATURE", "unit": "degC", "value": 22.5, "observed_at": 1800000000, "received_at": 1800000001 }], "next_cursor": null }`.

Points are ordered newest first. `next_cursor: null` means the entire requested snapshot was read; otherwise the UI must offer another page or clearly indicate that the displayed series is partial. Preserve the same house/device/sensor and range on subsequent requests. Page size may change. Cursor validation errors return 422, ownership failures 404, missing sessions 401.

The first page captures a database insertion high-water mark. New live samples and late historical backfill are excluded from subsequent pages until the client refreshes without a cursor. This provides consistent pagination without long-running database transactions. Ordering uses observation time and row ID; sensor observations are unique by device, sensor and timestamp. Cursors are navigation state, not access credentials; every page independently authorizes the house and device.

The existing `/history` endpoint remains backward compatible and capped at 1000 newest rows. New analytics clients should use `/history-page` so truncation cannot silently appear to be a complete history. Timestamp and page-size inputs are bounded before reaching SQLite.
