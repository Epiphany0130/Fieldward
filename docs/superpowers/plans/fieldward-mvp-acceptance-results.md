# Fieldward MVP acceptance results

Date: 2026-11-28. This is a partial technical verification, not a release approval. The end-to-end acceptance items in Task 15 and OpenSpec section 7 remain open: no complete synthetic-user browser journey was run and no `MvpFlowTest` is claimed.

## Automated checks

| Check | Result | Scope and limitations |
| --- | --- | --- |
| `mvn -s /private/tmp/fieldward-maven-settings.xml -Dmaven.repo.local=/private/tmp/fieldward-m2 -f api/pom.xml test` | Passed: 93 tests, 0 failures, 0 errors, 0 skipped | Java 21; embedded isolated PostgreSQL. The test base baselines migration V1 (pgvector) and runs later migrations, so this does not validate pgvector installation or a production database deployment. Local socket access outside the sandbox was required. |
| `npm --prefix web run build` | Passed | Vue type-check and Vite production build, Node 22.15.0. |
| `node --experimental-strip-types --test web/tests/*.test.mjs` | Passed: 3 tests | Node 22.15.0; type stripping is experimental. Without the flag this Node version cannot import the TypeScript test dependency. These are focused tests, not browser journeys. |
| `openspec validate redesign-mvp --strict` | Passed | Validates change artifacts, not feature completion. |

The API suite contains focused tests for exploration, direction changes, diagnosis, learning, job matching and applications, resume versions, credits, authorization, realtime events, and legacy migration. These results do not establish that the features work together in either complete user journey. The first sandboxed API attempt could not bind the embedded database socket; the successful run above used local socket access outside the sandbox. The default Maven cache was also not writable in the sandbox, so the successful command used a temporary cache and settings.

## Outstanding acceptance and dependencies

- **15.1 / 7.1:** The complete beginner journey from profile and exploration through general learning, diagnosis, route and all-job matching has not been exercised end to end.
- **15.2 / 7.2:** The project-experience journey from uploaded resume through diagnosis, job-specific interview, review and automatic resume update has not been exercised end to end.
- **15.3 / 7.3:** Focused automated checks do not replace integrated acceptance of direction confirmation, reconnect, exhausted credits, route changes, historical users, account isolation and media cleanup. Browser and provider behavior still need verification.
- **15.4 / 7.4:** Actual provider costs, browser/device audio and video compatibility, job refresh latency, and end-of-session or abnormal-exit media cleanup have not been measured. No cost or latency target can be declared met. Test providers can help verify functional flows, but cannot establish actual provider costs or real media behavior. Cost and media acceptance require integration with the real providers and measurements using representative usage and a suitable browser/device matrix. Job refresh latency also requires an authorized job source and representative measurements.

OpenSpec sections 1–6 retain their existing unchecked items. Complete those dependencies and run the two synthetic-user journeys and operational measurements before checking off section 7 or approving release.
