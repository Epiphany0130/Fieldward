"""Verify short-lived Java session grants before accepting a session."""
import base64
import hashlib
import hmac
import time
import uuid


def verify_grant(secret, grant, account_id, session_id, now=None):
    if len(secret) < 32:
        return False
    try:
        encoded, signature = grant.split(".")
        decode = lambda part: base64.b64decode(part + "=" * (-len(part) % 4), altchars=b"-_", validate=True)
        body = decode(encoded)
        if not hmac.compare_digest(hmac.new(secret, body, hashlib.sha256).digest(), decode(signature)):
            return False
        account, session, expires = body.decode("ascii").split(":")
        return int(account) == account_id and uuid.UUID(session) == uuid.UUID(session_id) and int(expires) > (time.time() if now is None else now)
    except (ValueError, TypeError, UnicodeError):
        return False
