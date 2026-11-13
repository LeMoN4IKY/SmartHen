"""SmartHen API foundation. Device commissioning is an explicit operator CLI action."""
from __future__ import annotations

from contextlib import contextmanager
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import re
import secrets
import sqlite3
import time
from typing import Callable, Literal

from fastapi import Depends, FastAPI, HTTPException, Query, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pydantic import BaseModel, ConfigDict, Field, field_validator


def token_hash(value: str) -> str:
    return hashlib.sha256(value.encode()).hexdigest()


def password_hash(password: str, salt: str | None = None) -> str:
    salt = salt or secrets.token_hex(16)
    derived = hashlib.scrypt(password.encode(), salt=bytes.fromhex(salt), n=32768,
                             r=8, p=1, maxmem=64 * 1024 * 1024).hex()
    return f"scrypt$32768$8$1${salt}${derived}"


def password_matches(password: str, stored: str) -> bool:
    return hmac.compare_digest(password_hash(password, stored.split('$')[4]), stored)


class Database:
    def __init__(self, path: str):
        self.path = path
        Path(path).parent.mkdir(parents=True, exist_ok=True)
        with self.connect() as db:
            db.executescript('''
            PRAGMA journal_mode=WAL;
            CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY, email TEXT UNIQUE NOT NULL, name TEXT NOT NULL, password TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS sessions(hash TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), expires_at INTEGER NOT NULL);
            CREATE TABLE IF NOT EXISTS houses(id TEXT PRIMARY KEY, user_id TEXT NOT NULL REFERENCES users(id), name TEXT NOT NULL);
            CREATE TABLE IF NOT EXISTS devices(id TEXT PRIMARY KEY, house_id TEXT REFERENCES houses(id), name TEXT NOT NULL,
                credential_hash TEXT UNIQUE NOT NULL, claim_hash TEXT UNIQUE, claim_expires INTEGER NOT NULL,
                capabilities TEXT NOT NULL, last_seen INTEGER);
            CREATE TABLE IF NOT EXISTS readings(id INTEGER PRIMARY KEY AUTOINCREMENT, device_id TEXT NOT NULL REFERENCES devices(id),
                sensor_id TEXT NOT NULL, type TEXT NOT NULL, unit TEXT NOT NULL, value REAL NOT NULL, observed_at INTEGER NOT NULL,
                received_at INTEGER NOT NULL, UNIQUE(device_id,sensor_id,observed_at));
            CREATE INDEX IF NOT EXISTS readings_lookup ON readings(device_id,sensor_id,observed_at);
            CREATE TABLE IF NOT EXISTS commands(id TEXT PRIMARY KEY, device_id TEXT NOT NULL REFERENCES devices(id),
                user_id TEXT NOT NULL REFERENCES users(id), idempotency_key TEXT NOT NULL, action TEXT NOT NULL, payload TEXT NOT NULL,
                status TEXT NOT NULL, created_at INTEGER NOT NULL, expires_at INTEGER NOT NULL, acknowledged_at INTEGER, detail TEXT,
                UNIQUE(user_id,device_id,idempotency_key));
            CREATE TABLE IF NOT EXISTS auth_attempts(ip TEXT NOT NULL, timestamp INTEGER NOT NULL);
            ''')

    @contextmanager
    def connect(self):
        db = sqlite3.connect(self.path, timeout=10)
        db.row_factory = sqlite3.Row
        db.execute('PRAGMA foreign_keys=ON')
        try:
            with db:
                yield db
        finally:
            db.close()


class Model(BaseModel):
    model_config = ConfigDict(extra='forbid', allow_inf_nan=False)


class Login(Model):
    email: str = Field(max_length=254)
    password: str = Field(min_length=12, max_length=128)

    @field_validator('email')
    @classmethod
    def email_valid(cls, value):
        value = value.strip().lower()
        if not re.fullmatch(r'[^\s@]+@[^\s@]+\.[^\s@]+', value):
            raise ValueError('Invalid email')
        return value


class Signup(Login):
    display_name: str = Field(min_length=1, max_length=100)

    @field_validator('display_name')
    @classmethod
    def name_valid(cls, value):
        if not value.strip():
            raise ValueError('Name required')
        return value.strip()


class HouseInput(Model):
    name: str = Field(min_length=1, max_length=100)

    @field_validator('name')
    @classmethod
    def name_valid(cls, value):
        if not value.strip():
            raise ValueError('Name required')
        return value.strip()


class Claim(Model):
    claim_token: str = Field(min_length=20, max_length=200)


class Reading(Model):
    sensor_id: str = Field(pattern=r'^[A-Za-z0-9_-]{1,64}$')
    type: str = Field(pattern=r'^[A-Z_]{1,40}$')
    unit: str = Field(max_length=20)
    value: float
    observed_at: int = Field(gt=0)

    @field_validator('unit')
    @classmethod
    def unit_valid(cls, value):
        if not value.strip() or any(ord(c) < 32 for c in value):
            raise ValueError('A physical measurement unit is required')
        return value


class Telemetry(Model):
    readings: list[Reading] = Field(min_length=1, max_length=100)


class CommandInput(Model):
    idempotency_key: str = Field(min_length=8, max_length=100)
    action: str = Field(pattern=r'^[a-z][a-z0-9_]{0,63}$')
    # Payload is deliberately a typed map; opaque nested executable/config documents are not accepted.
    payload: dict[str, bool | float | str] = Field(default_factory=dict, max_length=20)
    ttl_seconds: int = Field(default=60, ge=5, le=300)


class Acknowledgement(Model):
    status: Literal['succeeded', 'failed']
    detail: str = Field(default='', max_length=500)


def provision_device(database: Database, name: str, capabilities: list[str], lifetime=86400, now=None):
    """Run only from trusted provisioning tooling, never expose as a public HTTP endpoint."""
    if not name.strip() or any(not re.fullmatch(r'[a-z][a-z0-9_]{0,63}', c) for c in capabilities):
        raise ValueError('Invalid device metadata')
    now = int(time.time()) if now is None else now
    device_id, credential, claim = secrets.token_hex(16), secrets.token_urlsafe(32), secrets.token_urlsafe(32)
    with database.connect() as db:
        db.execute('INSERT INTO devices(id,house_id,name,credential_hash,claim_hash,claim_expires,capabilities,last_seen) VALUES(?,?,?,?,?,?,?,?)',
                   (device_id, None, name.strip(), token_hash(credential), token_hash(claim), now + lifetime,
                    json.dumps(sorted(set(capabilities))), None))
    return dict(device_id=device_id, device_token=credential, claim_token=claim, claim_expires=now + lifetime)


def create_app(database_path: str | None = None, clock: Callable[[], float] = time.time) -> FastAPI:
    database = Database(database_path or os.environ.get('SMARTHEN_DB', 'data/smarthen.sqlite3'))
    api = FastAPI(title='SmartHen Quail', version='2.0.0')
    api.state.database = database
    bearer = HTTPBearer(auto_error=False)
    dummy_password = password_hash('dummy-password-not-a-user')

    def now():
        return int(clock())

    def bearer_value(credentials):
        if credentials is None or len(credentials.credentials) > 200:
            raise HTTPException(401, 'Bearer authentication required', headers={'WWW-Authenticate': 'Bearer'})
        return credentials.credentials

    def user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
        hashed = token_hash(bearer_value(credentials))
        with database.connect() as db:
            row = db.execute('SELECT u.* FROM users u JOIN sessions s ON u.id=s.user_id WHERE s.hash=? AND s.expires_at>?', (hashed, now())).fetchone()
        if row is None:
            raise HTTPException(401, 'Invalid or expired session')
        return dict(row)

    def device(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
        hashed = token_hash(bearer_value(credentials))
        with database.connect() as db:
            row = db.execute('SELECT * FROM devices WHERE credential_hash=? AND house_id IS NOT NULL', (hashed,)).fetchone()
        if row is None:
            raise HTTPException(401, 'Invalid or unclaimed device')
        return dict(row)

    def house_access(db, house_id, owner):
        row = db.execute('SELECT * FROM houses WHERE id=? AND user_id=?', (house_id, owner['id'])).fetchone()
        if row is None:
            raise HTTPException(404, 'House not found')
        return row

    def device_access(db, house_id, device_id, owner):
        house_access(db, house_id, owner)
        row = db.execute('SELECT * FROM devices WHERE id=? AND house_id=?', (device_id, house_id)).fetchone()
        if row is None:
            raise HTTPException(404, 'Device not found')
        return row

    def public_user(row):
        return dict(id=row['id'], email=row['email'], display_name=row['name'])

    def session(db, row):
        token = secrets.token_urlsafe(32)
        expiry = now() + 86400
        db.execute('DELETE FROM sessions WHERE expires_at<=?', (now(),))
        db.execute('INSERT INTO sessions VALUES(?,?,?)', (token_hash(token), row['id'], expiry))
        return dict(access_token=token, token_type='bearer', expires_at=expiry, user=public_user(row))

    def limit_auth(request):
        # Database-backed rate limit works across workers. Proxy trust must be configured by deployment.
        ip = request.client.host if request.client else 'unknown'
        with database.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            db.execute('DELETE FROM auth_attempts WHERE timestamp<?', (now()-900,))
            if db.execute('SELECT COUNT(*) FROM auth_attempts WHERE ip=?', (ip,)).fetchone()[0] >= 20:
                raise HTTPException(429, 'Too many authentication attempts', headers={'Retry-After': '900'})
            db.execute('INSERT INTO auth_attempts VALUES(?,?)', (ip, now()))

    @api.get('/health')
    def health():
        return {'status': 'ok'}

    @api.post('/api/v1/auth/signup', status_code=201)
    def signup(body: Signup, request: Request):
        limit_auth(request)
        hashed = password_hash(body.password)
        row = dict(id=secrets.token_hex(16), email=body.email, name=body.display_name, password=hashed)
        try:
            with database.connect() as db:
                db.execute('INSERT INTO users VALUES(:id,:email,:name,:password)', row)
                return session(db, row)
        except sqlite3.IntegrityError:
            raise HTTPException(409, 'Account already exists')

    @api.post('/api/v1/auth/login')
    def login(body: Login, request: Request):
        limit_auth(request)
        with database.connect() as db:
            row = db.execute('SELECT * FROM users WHERE email=?', (body.email,)).fetchone()
            valid = password_matches(body.password, row['password'] if row else dummy_password)
            if not valid or row is None:
                raise HTTPException(401, 'Invalid email or password')
            return session(db, row)

    @api.post('/api/v1/auth/logout', status_code=204)
    def logout(owner=Depends(user), credentials=Depends(bearer)):
        with database.connect() as db:
            db.execute('DELETE FROM sessions WHERE hash=?', (token_hash(credentials.credentials),))

    @api.get('/api/v1/me')
    def me(owner=Depends(user)):
        return public_user(owner)

    @api.get('/api/v1/houses')
    def houses(owner=Depends(user)):
        with database.connect() as db:
            return [dict(r) for r in db.execute('SELECT id,name FROM houses WHERE user_id=? ORDER BY name,id', (owner['id'],))]

    @api.post('/api/v1/houses', status_code=201)
    def add_house(body: HouseInput, owner=Depends(user)):
        result = dict(id=secrets.token_hex(16), name=body.name)
        with database.connect() as db:
            db.execute('INSERT INTO houses VALUES(?,?,?)', (result['id'], owner['id'], result['name']))
        return result

    @api.post('/api/v1/houses/{house_id}/devices/claim')
    def claim_device(house_id: str, body: Claim, owner=Depends(user)):
        with database.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            house_access(db, house_id, owner)
            row = db.execute('SELECT * FROM devices WHERE claim_hash=? AND house_id IS NULL AND claim_expires>?', (token_hash(body.claim_token), now())).fetchone()
            if row is None:
                raise HTTPException(400, 'Invalid, expired or consumed claim token')
            db.execute('UPDATE devices SET house_id=?,claim_hash=NULL WHERE id=?', (house_id, row['id']))
            return dict(id=row['id'], name=row['name'], capabilities=json.loads(row['capabilities']), last_seen=None)

    @api.get('/api/v1/houses/{house_id}/devices')
    def devices(house_id: str, owner=Depends(user)):
        with database.connect() as db:
            house_access(db, house_id, owner)
            return [dict(id=r['id'], name=r['name'], capabilities=json.loads(r['capabilities']), last_seen=r['last_seen'])
                    for r in db.execute('SELECT * FROM devices WHERE house_id=? ORDER BY id', (house_id,))]

    @api.post('/api/v1/device/telemetry')
    def telemetry(body: Telemetry, sender=Depends(device)):
        # Accept historical backfill but reject future samples and duplicates with conflicting data.
        timestamp = now()
        with database.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            for reading in body.readings:
                if reading.observed_at > timestamp + 30:
                    raise HTTPException(422, 'Device timestamp is in the future')
                old = db.execute('SELECT * FROM readings WHERE device_id=? AND sensor_id=? AND observed_at=?',
                                 (sender['id'], reading.sensor_id, reading.observed_at)).fetchone()
                if old:
                    if (old['type'], old['unit'], old['value']) != (reading.type, reading.unit, reading.value):
                        raise HTTPException(409, 'Conflicting sample at the same timestamp')
                    continue
                db.execute('INSERT INTO readings(device_id,sensor_id,type,unit,value,observed_at,received_at) VALUES(?,?,?,?,?,?,?)',
                           (sender['id'], reading.sensor_id, reading.type, reading.unit, reading.value, reading.observed_at, timestamp))
            db.execute('UPDATE devices SET last_seen=? WHERE id=?', (timestamp, sender['id']))
        return {'status': 'accepted', 'received_at': timestamp}

    @api.get('/api/v1/houses/{house_id}/readings')
    def readings(house_id: str, owner=Depends(user)):
        with database.connect() as db:
            house_access(db, house_id, owner)
            return [dict(r) for r in db.execute('''SELECT r.device_id,r.sensor_id,r.type,r.unit,r.value,r.observed_at,r.received_at FROM readings r
                JOIN devices d ON r.device_id=d.id WHERE d.house_id=? AND r.observed_at=(SELECT MAX(x.observed_at) FROM readings x
                WHERE x.device_id=r.device_id AND x.sensor_id=r.sensor_id) ORDER BY r.device_id,r.sensor_id''', (house_id,))]

    @api.get('/api/v1/houses/{house_id}/history')
    def history(house_id: str, device_id: str, sensor_id: str, since: int = 0, owner=Depends(user)):
        if since < 0:
            raise HTTPException(422, 'Invalid history timestamp')
        with database.connect() as db:
            device_access(db, house_id, device_id, owner)
            return [dict(r) for r in db.execute('''SELECT device_id,sensor_id,type,unit,value,observed_at,received_at FROM readings
                WHERE device_id=? AND sensor_id=? AND observed_at>=? ORDER BY observed_at DESC LIMIT 1000''', (device_id, sensor_id, since))]

    @api.get('/api/v1/houses/{house_id}/history-page')
    def history_page(
        house_id: str,
        device_id: str,
        sensor_id: str,
        since: int = Query(default=0, ge=0, le=9223372036854775807),
        until: int | None = Query(default=None, ge=0, le=9223372036854775807),
        limit: int = Query(default=200, ge=1, le=1000),
        cursor: str | None = Query(default=None, max_length=2048),
        owner=Depends(user),
    ):
        if until is not None and until < since:
            raise HTTPException(422, 'until must be greater than or equal to since')
        scope = token_hash(json.dumps([house_id, device_id, sensor_id, since, until], separators=(',', ':')))
        with database.connect() as db:
            # Authorization always precedes cursor processing; a cursor never conveys access rights.
            device_access(db, house_id, device_id, owner)
            conditions = 'device_id=? AND sensor_id=? AND observed_at>=?'
            parameters = [device_id, sensor_id, since]
            if until is not None:
                conditions += ' AND observed_at<=?'
                parameters.append(until)
            boundary = None
            if cursor is not None:
                try:
                    decoded = json.loads(base64.b64decode(cursor, altchars=b'-_', validate=True))
                    if not isinstance(decoded, dict) or set(decoded) != {'v', 'scope', 'before', 'snapshot'}:
                        raise ValueError()
                    boundary = decoded['before']
                    snapshot = decoded['snapshot']
                    if decoded['v'] != 1 or decoded['scope'] != scope or not isinstance(boundary, list) or len(boundary) != 2:
                        raise ValueError()
                    if any(type(number) is not int or not 0 < number <= 9223372036854775807 for number in [*boundary, snapshot]):
                        raise ValueError()
                    if boundary[1] > snapshot or boundary[0] < since or (until is not None and boundary[0] > until):
                        raise ValueError()
                except (ValueError, TypeError, KeyError, UnicodeError, RecursionError):
                    raise HTTPException(422, 'Invalid cursor for this history query')
            else:
                # Freeze the inserted-row high-water mark: new live/backfilled samples appear on refresh,
                # not halfway through pagination. No database connection is held between page requests.
                snapshot = db.execute(f'SELECT COALESCE(MAX(id),0) FROM readings WHERE {conditions}', parameters).fetchone()[0]
            conditions += ' AND id<=?'
            parameters.append(snapshot)
            if boundary is not None:
                conditions += ' AND (observed_at<? OR (observed_at=? AND id<?))'
                parameters.extend([boundary[0], boundary[0], boundary[1]])
            rows = db.execute(f'''SELECT id,device_id,sensor_id,type,unit,value,observed_at,received_at FROM readings
                WHERE {conditions} ORDER BY observed_at DESC,id DESC LIMIT ?''', [*parameters, limit+1]).fetchall()
            page = rows[:limit]
            next_cursor = None
            if len(rows) > limit:
                last = page[-1]
                encoded = json.dumps(dict(v=1, scope=scope, before=[last['observed_at'], last['id']], snapshot=snapshot), separators=(',', ':')).encode()
                next_cursor = base64.urlsafe_b64encode(encoded).decode()
            return dict(points=[{key: row[key] for key in row.keys() if key != 'id'} for row in page], next_cursor=next_cursor)

    def command_public(row):
        result = dict(row)
        result['payload'] = json.loads(result['payload'])
        return result

    @api.post('/api/v1/houses/{house_id}/devices/{device_id}/commands', status_code=202)
    def command(house_id: str, device_id: str, body: CommandInput, owner=Depends(user)):
        payload = json.dumps(body.payload, sort_keys=True, separators=(',', ':'), allow_nan=False)
        if len(payload) > 2048:
            raise HTTPException(422, 'Payload too large')
        with database.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            target = device_access(db, house_id, device_id, owner)
            db.execute("UPDATE commands SET status='expired' WHERE device_id=? AND status='queued' AND expires_at<=?", (device_id, now()))
            old = db.execute('SELECT * FROM commands WHERE user_id=? AND device_id=? AND idempotency_key=?', (owner['id'], device_id, body.idempotency_key)).fetchone()
            if old:
                if old['action'] != body.action or old['payload'] != payload:
                    raise HTTPException(409, 'Idempotency key already used for another command')
                return command_public(old)
            if body.action not in json.loads(target['capabilities']):
                raise HTTPException(422, 'Unsupported device action')
            if target['last_seen'] is None or now() - target['last_seen'] > 60:
                raise HTTPException(409, 'Device connection is stale')
            identifier = secrets.token_hex(16)
            db.execute('INSERT INTO commands(id,device_id,user_id,idempotency_key,action,payload,status,created_at,expires_at,acknowledged_at,detail) VALUES(?,?,?,?,?,?,?,?,?,?,?)',
                       (identifier, device_id, owner['id'], body.idempotency_key, body.action, payload, 'queued', now(), now()+body.ttl_seconds, None, None))
            return command_public(db.execute('SELECT * FROM commands WHERE id=?', (identifier,)).fetchone())

    @api.get('/api/v1/houses/{house_id}/devices/{device_id}/commands')
    def commands(house_id: str, device_id: str, owner=Depends(user)):
        with database.connect() as db:
            device_access(db, house_id, device_id, owner)
            db.execute("UPDATE commands SET status='expired' WHERE device_id=? AND status='queued' AND expires_at<=?", (device_id, now()))
            return [command_public(r) for r in db.execute('SELECT * FROM commands WHERE device_id=? ORDER BY created_at DESC LIMIT 100', (device_id,))]

    @api.get('/api/v1/device/commands')
    def pending(sender=Depends(device)):
        with database.connect() as db:
            db.execute("UPDATE commands SET status='expired' WHERE device_id=? AND status='queued' AND expires_at<=?", (sender['id'], now()))
            db.execute('UPDATE devices SET last_seen=? WHERE id=?', (now(), sender['id']))
            return [command_public(r) for r in db.execute("SELECT * FROM commands WHERE device_id=? AND status='queued' ORDER BY created_at LIMIT 100", (sender['id'],))]

    @api.post('/api/v1/device/commands/{command_id}/ack')
    def acknowledge(command_id: str, body: Acknowledgement, sender=Depends(device)):
        with database.connect() as db:
            db.execute('BEGIN IMMEDIATE')
            row = db.execute('SELECT * FROM commands WHERE id=? AND device_id=?', (command_id, sender['id'])).fetchone()
            if row is None:
                raise HTTPException(404, 'Command not found')
            if row['status'] in ('succeeded', 'failed'):
                if row['status'] != body.status or row['detail'] != body.detail:
                    raise HTTPException(409, 'Acknowledgement conflicts with stored result')
                return command_public(row)
            if row['status'] != 'queued' or row['expires_at'] <= now():
                raise HTTPException(409, 'Command expired; execution must not start')
            db.execute('UPDATE commands SET status=?,acknowledged_at=?,detail=? WHERE id=?', (body.status, now(), body.detail, command_id))
            return command_public(db.execute('SELECT * FROM commands WHERE id=?', (command_id,)).fetchone())

    @api.get('/api/v1/subscription')
    def subscription(owner=Depends(user)):
        return dict(status='inactive', tariff=None, expires_at=None)

    return api
