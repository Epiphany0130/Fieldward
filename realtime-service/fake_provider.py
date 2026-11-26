"""In-memory test double. It does not connect to an audio/video provider."""
import time


class FakeProvider:
    def __init__(self, clock=time.monotonic, ttl=300):
        self.clock = clock
        self.ttl = ttl
        self.sessions = {}

    def open(self, session_id, mode):
        if mode not in ("PHONE", "VIDEO"):
            raise ValueError("unsupported mode")
        if session_id in self.sessions:
            raise ValueError("session already open")
        self.sessions[session_id] = self.clock() + self.ttl
        return {"audio": True, "video": mode == "VIDEO"}

    def cache(self, session_id, media, transcript):
        # The fake intentionally never retains media or a transcript.
        self.expire()
        if session_id not in self.sessions:
            raise ValueError("session is closed")

    def close(self, session_id):
        self.sessions.pop(session_id, None)

    def expire(self):
        now = self.clock()
        for session_id, deadline in list(self.sessions.items()):
            if deadline <= now:
                self.close(session_id)
