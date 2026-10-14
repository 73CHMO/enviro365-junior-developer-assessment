# Enviro365 Junior Developer Assessment

Author: Alex Motaung

Public project for the Enviro365 Junior Software Developer assessment (JSD2601).

- [Backend setup, architecture, API documentation, and AI disclosure](backend/README.md)
- [Supplied backend implementation plan](backend/BACKEND_PLAN.md)
- [Implementation and verification status](backend/IMPLEMENTATION_STATUS.md)
- `frontend/`: reserved for the assessment UI; implementation is pending.

The backend uses Java 21, Spring Boot 4.0.8, Maven, Spring Data JPA, and H2.

From the project root on Windows:

```powershell
cd backend
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
```

The API runs at `http://localhost:8080/api/investors/1/portfolio`.

Development occurs on the local `alex` branch. Verified changes are promoted through `develop` to `main`. The public submission must ultimately include both the completed frontend and backend on `main`.
