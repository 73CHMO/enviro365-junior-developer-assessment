# Authentication, masking, and persistent storage

Author: Alex Motaung

This security extension was requested after the original assessment architecture was implemented. The original BACKEND_PLAN.md is retained as the initial plan; this document describes the added authentication scope.

## Accounts and sign-in

- Registration creates a password-protected account and its own investor record.
- A new account starts with an empty portfolio; registration does not fabricate investment balances.
- Passwords use BCrypt with cost 12. Registration requires at least 12 characters and limits passwords to 72 UTF-8 bytes to prevent BCrypt truncation.
- Sign-in errors are generic. Login and registration are limited to 20 requests per minute per actual backend peer, ignoring spoofed forwarded IP headers.
- Each portfolio, withdrawal, history, and CSV route checks the authenticated account's investor ownership.

The local frontend proxy shares one backend peer address. A public deployment should enforce client-aware rate limits at its trusted edge; the local limiter is not a distributed abuse-prevention service.

## JWT and refresh protection

- Access JWTs use a random private HS256 key and expire after five minutes.
- The decoder restricts the algorithm and validates issuer, audience, timestamps, and current account token version.
- Access JWTs stay in module memory. Neither JWTs nor refresh credentials are stored in localStorage or sessionStorage.
- Refresh credentials are random 256-bit opaque tokens in HttpOnly, SameSite=Strict cookies. Development scopes them to /api/auth; production uses a Secure, host-only __Host- cookie at / to prevent parent-domain cookie replacement.
- Only SHA-256 hashes of refresh tokens are stored in the database.
- Refresh tokens rotate on use and expire after seven days; active refresh extends that period.
- Replaying a consumed, still-active token revokes all account credentials. Already-revoked/expired tokens cannot repeatedly revoke later legitimate sign-ins.
- Logout revokes all active account credentials, including previously issued JWTs, and deletes the refresh cookie.
- Browser Web Locks coordinate refresh across tabs; a BroadcastChannel communicates logout/account changes.
- Spring Security uses STATELESS session management and a null request cache. No JSESSIONID authentication session is created.

JWT does not make credential theft impossible. A currently valid stolen bearer token can be used until expiry or revocation. HTTPS, protected secrets, safe script handling, and device/browser security remain necessary.

## CSRF and browser controls

Cookie-backed login, registration, refresh, and logout requests require a CSRF cookie/token header pair. The frontend obtains the token from GET /api/auth/csrf and sends its declared header.

Investment APIs authenticate only explicit Authorization: Bearer headers; a refresh cookie alone grants no portfolio access. Spring's resource-server configuration treats explicit bearer requests separately from ambient cookie authentication.

Cookies use Secure and hardened cookie-name prefixes under the prod profile. Plain HTTP localhost development uses non-Secure cookies so the local demo works. Production requires HTTPS.

The frontend restricts scripts to local files with Content Security Policy, escapes dynamic product/account text, denies framing, and avoids exposing sensitive values in notices. Login and registration DTO string representations are redacted.

## ID and card handling

Registration requires:
- First/last name and email.
- Password.
- South African 13-digit ID number.
- Explicit date of birth to avoid ambiguity from the ID's two-digit year.
- An optional card number.

South African IDs must have a valid checksum, valid citizenship/administrative digits, and a matching real date of birth that is not future-dated or more than 120 years old. Registered age is derived from date of birth when read. These checks validate structure, not government identity ownership.

Card numbers are checked with Luhn after removing allowed spaces/hyphens. Card input accepts 12–19 digits. A valid checksum does not verify that a card exists, is owned by the applicant, or has funds. No payment is processed and no CVV or expiry date is collected.

Stored references:
- ID: keyed HMAC fingerprint for uniqueness, plus last four digits.
- Card: last four digits only.
- Password: BCrypt hash only.
- Date of birth, names, and email: account/profile data in the local database.

Full IDs and full card numbers are never stored or returned. Sensitive form inputs are password-style masked; responses display masked email, ID, and card references. Inputs are cleared after registration completes. Masks do not encrypt network requests: use HTTPS for public access.

## Persistence and secrets

H2 now uses file storage at backend/data/enviro365.mv.db and JPA update mode. Accounts, balances, and withdrawal history survive restart. This replaces the initial in-memory assessment configuration.

Local development signing and ID-hash keys are generated once into backend/data/.security-keys.properties and reused. The complete data directory, environment files, logs, browser traces, and test downloads are ignored by Git. The frontend server's explicit static-file allowlist cannot serve database or key files.

Do not delete/change the identity key independently of stored account fingerprints. Preserve the database and its keys together when backing up development data. Control filesystem access to the data directory.

For the prod profile:
- Supply separate JWT_SECRET and IDENTITY_HASH_KEY environment values, each Base64-encoded and containing at least 32 cryptographically random bytes.
- Demo seeding must be disabled; the profile disables it and startup rejects an enabled production seed setting.
- Cookies are Secure and use __Host- prefixes, with no Domain attribute.
- Use HTTPS at the browser edge and configure trusted origins; do not expose the local HTTP demo as a public service.
- Use protected persistent storage/backups and deployment-managed secrets.
- The prod database defaults to a separate enviro365-prod file; DATABASE_URL can override it.

This is an assessment application, not a claim of a completed production financial security audit, identity-verification service, or payment-processing integration.

## Demo access

Local seeded accounts:
- alice@example.com — eligible sample retirement/discretionary investments.
- thabo@example.com — age-65 retirement restriction and eligible savings.

Local demo password: Enviro365!Demo2026

The login screen includes Explore the demo portfolio for Alice. These are explicitly public sample credentials and must never be used for real accounts. DEMO_PASSWORD can override them when seeding a fresh development database. Changing it does not rewrite an existing account's password.

## Authentication API

| Method | Endpoint | Result |
| --- | --- | --- |
| GET | /api/auth/csrf | CSRF token and header name, plus HttpOnly CSRF cookie |
| POST | /api/auth/register | 201; access JWT and masked user; refresh cookie |
| POST | /api/auth/login | 200; access JWT and masked user; refresh cookie |
| POST | /api/auth/refresh | 200; rotated refresh cookie and new access JWT |
| POST | /api/auth/logout | 204; all account credentials revoked; cookie deleted |
| GET | /api/auth/me | Authenticated masked account details |

A 401 means missing/invalid/expired authentication; 403 means failed CSRF or forbidden portfolio ownership; 429 means sign-in rate limiting. Authentication responses use Cache-Control: no-store.

The frontend proxy forwards Authorization, CSRF headers, cookies, and Set-Cookie response headers to support these endpoints. Existing investment API contracts remain otherwise intact; portfolio email fields are masked.

## Verification

Backend: 77 tests cover the original financial rules and added authentication, masking, ID/card checks, CSRF, ownership, expiry/issuer/audience validation, tampered JWT rejection, logout, refresh rotation/replay, and sign-in throttling.

An additional production cookie-policy test verifies the Secure __Host- prefix, root path, HttpOnly/SameSite flags, and absence of Domain. Persistent storage was independently checked after application shutdown: five accounts and three masked card references remained on disk. All eight authenticated browser scenarios passed.

Frontend unit tests: 10 checks cover exact money/date handling and ID/Luhn validation. Browser verification additionally covers signed-in withdrawals/CSV, registration masking, refresh restoration, account isolation, logout, and multiple tabs.

## References

- [Spring Security JWT resource servers](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html)
- [Spring Security CSRF guidance](https://docs.spring.io/spring-security/reference/7.0/servlet/exploits/csrf.html)
- [OWASP session and token storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Session_Management_Cheat_Sheet.html)
- [OWASP password storage guidance](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)
- [SARS identity-number structure and checksum specification](https://www.sars.gov.za/wp-content/uploads/Docs/3rdParty/Archive/Archived-Business-Requirement-Specification-IT3s-Data-Submission-WATERMARK.pdf)
