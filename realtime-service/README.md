# Realtime prototype

This directory contains a fake provider and grant verifier for tests. It does not provide a running media service or connect to the Java API. It never stores media or transcripts.

The Java mutation and internal endpoints are deliberately hard-disabled; the device page is a permission preview only. They must not be enabled until actual provider transport, retention and cleanup, grant renewal/revocation, usage-based billing, and end-to-end security and browser testing are implemented. The current bounded free-text checkpoint cannot prove that submitted text excludes a transcript. The segment charge is provisional and is not a measured provider cost.

Run the fake tests from this directory with `python3 -m unittest discover -s tests`.
