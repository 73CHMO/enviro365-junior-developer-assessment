# Backend implementation status

Author: Alex Motaung

This tracks implementation separately from the unchanged supplied [BACKEND_PLAN.md](BACKEND_PLAN.md).

| Phase | Delivery | Status |
| --- | --- | --- |
| 1 | Java 21 / existing Spring Boot 4.0.8 build; H2 configuration | Complete |
| 2 | Three entities, enums, relationships, repositories, Java seed data | Complete |
| 3 | Portfolio DTOs, service, and GET API | Complete |
| 4 | Withdrawal DTOs, rules, transaction, row locking, POST API | Complete |
| 5 | History and filtered CSV with inclusive boundaries and escaping | Complete |
| 6 | Consistent exceptions, input validation, configured frontend CORS | Complete |
| 7 | Unit and H2 HTTP/database integration tests | Complete: 52 tests passed |
| 8 | Documentation and promotion through alex/develop/main | Complete |

## Verification evidence

Initial `mvnw.cmd test` passed on the existing Java 21 / Spring Boot 4.0.8 starter.

After implementation, `mvnw.cmd test` passed all 52 tests: one application startup test, three portfolio service tests, 24 withdrawal service cases, and 24 HTTP/database cases. No failures, errors, or skipped tests.

The integration suite demonstrates simultaneous withdrawals produce one successful notice and one rejection against the updated balance. It also forces a withdrawal database constraint failure and verifies the balance and history roll back together.

`mvnw.cmd clean verify` also passed all 52 tests and produced the executable Spring Boot jar.

Live HTTP verification used that jar on port 8081 because another application occupied port 8080. Portfolio retrieval returned 200 with two products. Posting a 5000.00 withdrawal returned 201, changed the discretionary balance from 50000.00 to 45000.00, and created one history record. CSV export returned 200 and contained that notice.

## Repository

Public repository: https://github.com/73CHMO/enviro365-junior-developer-assessment

`alex` is the local working branch. Verified changes are promoted to `develop` and then `main`; each tracks its matching origin branch. The merge commits record this promotion path. Check current tracking with `git branch -vv`.

## Remaining assessment work

The backend is the current scope. The frontend, UI screenshots, Alex's personal code review, and portal submission remain separate tasks. The assessment's complete submission requires frontend and backend in this public repository on `main`.
