import base64
import hashlib
import hmac
import unittest
import uuid
from authorization import verify_grant

class AuthorizationTest(unittest.TestCase):
    def test_bound_grant(self):
        secret = b"01234567890123456789012345678901"
        session = str(uuid.uuid4())
        body = f"42:{session}:400".encode()
        encode = lambda value: base64.urlsafe_b64encode(value).rstrip(b"=").decode()
        grant = encode(body) + "." + encode(hmac.new(secret, body, hashlib.sha256).digest())
        self.assertTrue(verify_grant(secret, grant, 42, session, now=100))
        self.assertFalse(verify_grant(secret, grant, 43, session, now=100))
        self.assertFalse(verify_grant(secret, grant, 42, session, now=400))
        self.assertFalse(verify_grant(secret, grant + "x", 42, session, now=100))
