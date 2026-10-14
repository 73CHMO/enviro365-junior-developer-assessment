# Enviro365 Junior Developer Assessment

Author: Alex Motaung

Public project for the Enviro365 Junior Software Developer assessment (JSD2601).

- [Backend setup, architecture, API documentation, and AI disclosure](backend/README.md)
- [Supplied backend implementation plan](backend/BACKEND_PLAN.md)
- [Implementation and verification status](backend/IMPLEMENTATION_STATUS.md)
- [Frontend setup, design, browser tests, AI disclosure, and UI screenshots](frontend/README.md)

The backend uses Java 21, Spring Boot 4.0.8, Maven, Spring Data JPA, and H2.

From the project root on Windows:

```powershell
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

The API runs at `http://localhost:8080/api/investors/1/portfolio`.

Development occurs on the local `alex` branch. Verified changes are promoted through `develop` to `main`. The public submission must ultimately include both the completed frontend and backend on `main`.

## Remaining assessment checklist

- [x] Build the frontend portfolio dashboard and product display.
- [x] Add a withdrawal form with validation, API submission, and clear success/error messages.
- [x] Show withdrawal history and download CSV statements with date filtering.
- [x] Verify the complete UI-to-backend flow, including rejected requests and balance/history refresh.
- [x] Document frontend setup and add UI screenshots to the frontend README.
- [ ] Alex reviews and understands the AI-assisted implementation; keep the AI disclosure accurate.
- [x] Promote the completed frontend and final documentation through `develop` to `main`.
- [ ] Confirm the public repository works without sign-in and submit its URL in the portal before 14 October 2026 at 18:48.

The backend APIs, business rules, H2 integration, frontend, and public repository are complete. All five advanced features are implemented: DTOs, input validation, global exception handling, tests, and UI validation. Verification passed 52 backend tests, seven frontend unit tests, and five live browser tests.

To launch the frontend in a second terminal, run `npm.cmd start` from `frontend` and open `http://localhost:4200`. Detailed setup and screenshots are in the frontend README.

## License

This project is licensed under the [MIT License](LICENSE). Copyright (c) 2026 Alex Motaung.
