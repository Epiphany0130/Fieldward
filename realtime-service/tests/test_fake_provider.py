import unittest
from fake_provider import FakeProvider


class FakeProviderTest(unittest.TestCase):
    def test_modes_and_cleanup(self):
        provider = FakeProvider()
        self.assertEqual(provider.open("phone", "PHONE"), {"audio": True, "video": False})
        self.assertEqual(provider.open("video", "VIDEO"), {"audio": True, "video": True})
        provider.cache("phone", b"audio", "full transcript")
        provider.close("phone")
        self.assertNotIn("phone", provider.sessions)
        provider.close("phone")

    def test_expiry_erases_cached_content(self):
        now = [100]
        provider = FakeProvider(clock=lambda: now[0], ttl=5)
        provider.open("one", "PHONE")
        provider.cache("one", b"audio", "transcript")
        now[0] = 105
        provider.expire()
        self.assertEqual(provider.sessions, {})

if __name__ == "__main__":
    unittest.main()
