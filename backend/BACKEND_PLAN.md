# Enviro365 Junior Developer Assessment

Backend Architecture & Implementation Plan — Version 1.0

Project: Investment Portfolio & Withdrawal Management System Developer: Alex Motaung Base package: `com.enviro.assessment.junior.alexmotaung`

Our objective is to build a complete, reliable, testable Spring Boot backend that satisfies the Enviro365 assessment requirements, demonstrates good Java development practices, and can be confidently explained during a technical interview.

We will use your existing Spring Boot project rather than create another application.

## 1. Scope and requirements

The assessment describes an investment platform where investors can view their portfolios, submit withdrawal notices, and download statements.

It explicitly requires three backend capabilities:

1. Retrieve investor portfolio information, including investment products.
2. Create withdrawal notices and calculate balances.
3. Export filtered CSV statements.

It also specifies three business rules: retirement withdrawals require an investor older than 65, withdrawals cannot exceed the available balance, and withdrawals cannot exceed 90% of that balance.

The assessment additionally requires H2, REST best practices, a specific Java package structure, and at least three advanced features.

### Assessment coverage

| Requirement                   | Our backend implementation             |
| ----------------------------- | -------------------------------------- |
| Spring Boot                   | Existing Spring Boot 4 project         |
| H2 database                   | Spring Data JPA + H2                   |
| Portfolio retrieval           | Investor and products API              |
| Withdrawal notices            | Validated transaction processing       |
| Balance calculations          | `BigDecimal` and transactional updates |
| Retirement age restriction    | Strictly older than 65                 |
| Available balance restriction | Insufficient balance validation        |
| 90% withdrawal restriction    | Maximum withdrawal calculation         |
| CSV statements                | Filtered CSV export API                |
| Error handling                | Global exception handler               |
| DTO layer                     | Separate request/response models       |
| Input validation              | Jakarta Bean Validation                |
| Unit tests                    | JUnit + Mockito                        |
| Documentation                 | README and API examples                |

Important scope boundary: The assessment describes withdrawal notices, but does not specify bank transfers, external payment gateways, authentication, approval workflows, or regulatory integrations. We should not invent these as mandatory features.

For our implementation, we'll document the assumption that an accepted withdrawal immediately reduces the product's available balance. That is consistent with the required balance calculations, but it is still an implementation choice.

## 2. Technology decisions

| Component       | Technology                         |
| --------------- | ---------------------------------- |
| Language        | Java 21                            |
| Framework       | Spring Boot 4.0.8                  |
| Build           | Maven Wrapper                      |
| API             | REST / JSON                        |
| Persistence     | Spring Data JPA                    |
| ORM             | Hibernate                          |
| Database        | H2                                 |
| Validation      | Jakarta Validation                 |
| Testing         | JUnit 5, Mockito, Spring Boot Test |
| API testing     | Postman                            |
| Version control | Git / GitHub                       |

We'll retain Spring Boot 4.0.8 and Java 21 from your current repository, assuming the existing project builds successfully.

No Redis, MySQL, JWT, Docker, Kafka, or microservices are needed for this assessment.

## 3. Proposed backend architecture

A layered monolithic architecture is ideal for this task.

Frontend / Postman

HTTP requests and responses

REST / JSON

Controller Layer

Endpoints · Request validation · HTTP responses

Service Layer

Business rules · Balance calculations · Transactions

Repository Layer

Spring Data JPA · Database queries

H2 Database

Investors · Products · Withdrawals

Cross-cutting: DTOs · Validation · Exception Handling · Testing

The responsibilities will stay clear:

- Controller: Receives an HTTP request and returns a response.
- Service: Makes business decisions and coordinates transactions.
- Repository: Retrieves and persists data.
- Entity: Represents persisted database records.
- DTO: Defines what API clients send and receive.
- Exception handler: Converts errors into consistent responses.

We won't place business rules inside controllers or database entities.

## 4. Database design

The assessment can be satisfied with three principal entities. We do not need a separate Portfolio table because an investor's portfolio can be represented by their associated investment products.

INVESTOR

PK · id

firstName · lastName · email · age

1 investor owns many products

PRODUCT

PK · id | FK · investor_id

name · type · balance

1 product has many withdrawals

WITHDRAWAL

PK · id | FK · investor_id, product_id

amount · createdAt · status

### Entity specifications

Investor

| Field     | Java type | Constraint       |
| --------- | --------- | ---------------- |
| id        | Long      | Primary key      |
| firstName | String    | Required         |
| lastName  | String    | Required         |
| email     | String    | Required, unique |
| age       | Integer   | Nonnegative      |

The assessment uses age, so we'll use an integer. In a real financial application, date of birth would be more appropriate because age changes over time.

Product

| Field    | Java type   | Constraint           |
| -------- | ----------- | -------------------- |
| id       | Long        | Primary key          |
| name     | String      | Required             |
| type     | ProductType | Required             |
| balance  | BigDecimal  | Nonnegative, scale 2 |
| investor | Investor    | Required foreign key |

Product types from the proposed implementation are `RETIREMENT`, `DISCRETIONARY`, `SAVINGS`, and `TAX_FREE`. These are illustrative classifications rather than types mandated by the assessment.

Withdrawal

| Field     | Java type        | Constraint  |
| --------- | ---------------- | ----------- |
| id        | Long             | Primary key |
| investor  | Investor         | Required    |
| product   | Product          | Required    |
| amount    | BigDecimal       | Positive    |
| createdAt | LocalDateTime    | Required    |
| status    | WithdrawalStatus | Required    |

For the assessment, an accepted withdrawal will have status `COMPLETED`. Invalid requests will return an error and leave the balance unchanged. We will not persist rejected requests unless there is extra time to define their lifecycle properly.

### Financial precision

We'll use `BigDecimal` for financial calculations, never `double` or `float`.

The product balance and withdrawal amount should use two decimal places. Reject requests containing fractional cents rather than silently rounding withdrawal amounts.

## 5. REST API contract

Four API routes are sufficient to cover the mandatory backend functionality.

| Method | Endpoint                                         | Purpose            |
| ------ | ------------------------------------------------ | ------------------ |
| GET    | `/api/investors/{investorId}/portfolio`          | Retrieve portfolio |
| POST   | `/api/investors/{investorId}/withdrawals`        | Submit withdrawal  |
| GET    | `/api/investors/{investorId}/withdrawals`        | Retrieve history   |
| GET    | `/api/investors/{investorId}/withdrawals/export` | Download CSV       |

### API 1 — Retrieve investor portfolio

`GET /api/investors/1/portfolio`

```

{
  "investor": {
    "id": 1,
    "firstName": "Alice",
    "lastName": "Mokoena",
    "email": "alice@example.com",
    "age": 70
  },
  "products": [
    {
      "id": 1,
      "name": "Retirement Annuity",
      "type": "RETIREMENT",
      "balance": 100000.00
    },
    {
      "id": 2,
      "name": "Discretionary Portfolio",
      "type": "DISCRETIONARY",
      "balance": 50000.00
    }
  ]
}

```

Expected outcomes: `200 OK` when the investor exists and `404 Not Found` otherwise.

### API 2 — Create withdrawal

`POST /api/investors/1/withdrawals`

Request:

```

{
  "productId": 2,
  "amount": 5000.00
}

```

Successful response with `201 Created`:

```

{
  "id": 1,
  "productId": 2,
  "productName": "Discretionary Portfolio",
  "amount": 5000.00,
  "createdAt": "2026-10-14T15:00:00",
  "status": "COMPLETED"
}

```

The timestamp above is illustrative. The application must generate the real transaction time.

After a successful withdrawal, the product's balance decreases from R50,000 to R45,000.

### API 3 — Withdrawal history

`GET /api/investors/1/withdrawals`

Return the investor's withdrawal records in descending creation-date order. The frontend can display these in a history table.

An investor without withdrawals should receive an empty array, not an error.

### API 4 — Export statement

`GET /api/investors/1/withdrawals/export?from=2026-10-01T00:00:00&to=2026-10-14T23:59:59`

Returns a downloadable `text/csv` response.

```
id,investorId,productId,productName,amount,createdAt,status
1,1,2,Discretionary Portfolio,5000.00,2026-10-14T15:00:00,COMPLETED
```

We'll support optional `from` and `to` parameters, inclusive date boundaries, and clear errors when `from` is after `to`. Proper CSV escaping is necessary, including commas, quotation marks, and line breaks in values.

## 6. Withdrawal business rules

This is the most important backend component because it directly determines whether a financial transaction is allowed.

### Withdrawal decision flow

1

Receive withdrawal

Validate required fields and positive amount

2

Find investor

Reject if investor does not exist

3

Find product

Reject if product does not exist or belongs to someone else

4

Check retirement age

RETIREMENT requires age > 65

5

Check available balance

Requested amount must not exceed balance

6

Check 90% limit

Requested amount must be ≤ 90% of current balance

7

Complete transaction

Subtract balance and persist withdrawal

### Interactive business-rule examples

Withdrawal rule simulator

Design example

Investor age

70 years

Product type

Retirement

Discretionary

Available balance (R)

Withdrawal (R)

Maximum (90%)

## R 90 000,00

Balance after withdrawal

## R 50 000,00

Accepted: Withdrawal meets the business rules.

This simulator illustrates the intended validation logic; it is not connected to the Spring Boot backend.

### Transaction safety

The withdrawal service will use `@Transactional` so that the balance update and creation of the withdrawal record succeed or fail together.

We'll also use pessimistic row locking when retrieving the product for an update, preventing two simultaneous withdrawal requests from both validating against an outdated balance. This is a small but valuable correctness measure for financial data.

The database remains the source of truth. We should never trust balances or maximum withdrawal amounts supplied by a frontend.

## 7. Complete package structure

I recommend using package-by-layer organization, consistent with your existing proposed backend. It is simple enough for a junior assessment while still following clean architecture principles.

```

src/
├── main/
│   ├── java/com/enviro/assessment/junior/alexmotaung/
│   │   ├── JuniorDeveloperAssessmentApplication.java
│   │   │
│   │   ├── config/
│   │   │   ├── DataLoader.java
│   │   │   └── CorsConfig.java
│   │   │
│   │   ├── controller/
│   │   │   ├── InvestorController.java
│   │   │   └── WithdrawalController.java
│   │   │
│   │   ├── dto/
│   │   │   ├── InvestorDTO.java
│   │   │   ├── ProductDTO.java
│   │   │   ├── PortfolioDTO.java
│   │   │   ├── WithdrawalRequest.java
│   │   │   └── WithdrawalResponse.java
│   │   │
│   │   ├── model/
│   │   │   ├── Investor.java
│   │   │   ├── Product.java
│   │   │   ├── ProductType.java
│   │   │   ├── Withdrawal.java
│   │   │   └── WithdrawalStatus.java
│   │   │
│   │   ├── repository/
│   │   │   ├── InvestorRepository.java
│   │   │   ├── ProductRepository.java
│   │   │   └── WithdrawalRepository.java
│   │   │
│   │   ├── service/
│   │   │   ├── PortfolioService.java
│   │   │   └── WithdrawalService.java
│   │   │
│   │   └── exception/
│   │       ├── BusinessException.java
│   │       ├── ResourceNotFoundException.java
│   │       ├── GlobalExceptionHandler.java
│   │       └── ErrorResponse.java
│   │
│   └── resources/
│       └── application.properties
│
└── test/
    └── java/com/enviro/assessment/junior/alexmotaung/
        ├── PortfolioServiceTest.java
        ├── WithdrawalServiceTest.java
        └── WithdrawalControllerTest.java

pom.xml
mvnw
mvnw.cmd
README.md

```

We retain your existing application class, `JuniorDeveloperAssessmentApplication`, avoiding unnecessary renaming.

## 8. Exception handling design

All API errors should have a consistent format.

Example:

```
{
  "timestamp": "2026-10-14T15:00:00",
  "status": 400,
  "error": "Withdrawal exceeds 90% of balance",
  "details": {}
}
```

| Situation                  | HTTP response               |
| -------------------------- | --------------------------- |
| Valid portfolio retrieval  | `200 OK`                    |
| Successful withdrawal      | `201 Created`               |
| Missing investor/product   | `404 Not Found`             |
| Invalid withdrawal amount  | `400 Bad Request`           |
| Retirement age restriction | `400 Bad Request`           |
| Withdrawal over 90%        | `400 Bad Request`           |
| Invalid date filtering     | `400 Bad Request`           |
| Unexpected server failure  | `500 Internal Server Error` |

Unexpected errors must be logged internally without exposing stack traces or sensitive implementation details to API clients.

## 9. Testing strategy

Our goal is to demonstrate that the business rules work, not merely that the application starts.

### Required test scenarios

| Test                                | Expected result                  |
| ----------------------------------- | -------------------------------- |
| Retrieve existing investor          | Portfolio returned               |
| Retrieve unknown investor           | 404                              |
| Retirement withdrawal at age 64     | Rejected                         |
| Retirement withdrawal at age 65     | Rejected                         |
| Retirement withdrawal at age 66     | Accepted, subject to other rules |
| Withdrawal exactly 90% of balance   | Accepted                         |
| Withdrawal greater than 90%         | Rejected                         |
| Withdrawal exceeds balance          | Rejected                         |
| Withdrawal of zero                  | Rejected                         |
| Negative withdrawal                 | Rejected                         |
| Product belongs to another investor | Rejected                         |
| Successful withdrawal               | Balance and history updated      |
| Failed withdrawal                   | Balance unchanged                |
| CSV with valid date range           | Correct records                  |
| CSV with invalid date range         | 400                              |
| Simultaneous withdrawals            | Balance remains consistent       |

The final concurrent-withdrawal test is an engineering improvement rather than an explicit assessment requirement.

We will use JUnit and Mockito for service tests, and Spring Boot integration testing for database persistence and HTTP behaviour. In Spring Boot 4, we'll use the compatible test starters already available in your Maven configuration.

## 10. Implementation roadmap

The order matters. Every phase should leave the project in a buildable state.

Backend delivery checklist

8 phases

0 of 8 phases completed

0%

Phase 1: Foundation

15–20 min

Verify Java 21 and Spring Boot 4 Maven build

Configure H2, JPA and validation dependencies

Configure application.properties

Phase 2: Persistence layer

25–35 min

Create three JPA entities

Create product and withdrawal types

Create repositories and entity relationships

Phase 3: Portfolio API

20–30 min

Create portfolio DTOs

Implement PortfolioService

Implement GET portfolio endpoint

Phase 4: Withdrawal processing

35–50 min

Create request and response DTOs

Implement age, balance and 90% rules

Add transactional persistence and locking

Expose POST withdrawal endpoint

Phase 5: History and CSV

25–35 min

Implement history retrieval

Implement filtered CSV export

Validate date parameters and CSV escaping

Phase 6: Errors and validation

15–25 min

Implement global exception handling

Add field validation and status codes

Restrict CORS to the intended frontend origin

Phase 7: Testing

35–50 min

Create service tests and boundary cases

Test REST endpoints and database updates

Run Maven tests and fix failures

Phase 8: Documentation and Git

20–30 min

Document APIs and local setup

Include AI usage disclosure

Commit backend and verify main branch

These are planning estimates, not guaranteed completion times. The complete backend may take roughly 3–5 hours, depending on dependency availability and testing issues.

## 11. Definition of done

We should only consider the backend complete after all of the following have been demonstrated:

- Application starts using the project's Maven Wrapper.
- H2 initializes and loads example investors and products.
- Portfolio API returns the correct investor and products.
- Valid withdrawals update balances and create history.
- Every mandatory business rule rejects invalid requests.
- CSV export works with and without date filtering.
- Errors return useful, consistent JSON.
- Unit and integration tests pass.
- The README includes accurate setup, API examples, and AI usage information.
- All backend code is committed to the repository's `main` branch.

## 12. Risks and decisions

There are three decisions worth documenting before implementation.

First, the distinction between a withdrawal notice and completed withdrawal. The assessment does not provide an approval or settlement lifecycle. We will use immediate balance deduction for this assessment and explain the assumption in the README.

Second, the use of H2 in-memory storage. Data will reset whenever the application restarts. This is appropriate for a demonstrable assessment using seeded sample data; it is not production-grade investment storage.

Third, identifying investors by URL path. This is adequate for a local assessment with test data, but without authentication it is not secure for actual financial information. The application must clearly be presented as an assessment prototype, not a production financial platform.

## 13. Final architectural decision

We should freeze this architecture before coding to avoid spending valuable time repeatedly redesigning it.

My recommendation is:

- One Spring Boot application, not microservices.
- Three database entities, with clear relationships.
- Four REST endpoints, covering the complete required backend.
- Two services, keeping business logic in the service layer.
- Five DTOs, separating database models from HTTP contracts.
- One global exception handler, providing consistent errors.
- JUnit and Mockito tests, particularly for financial rules.
- H2 and Maven, matching the assessment and existing repository.

Our first implementation milestone is Phase 1: Foundation. We verify and complete the existing Maven project, configure H2, JPA and validation, and run a clean build before introducing domain classes.

Once that milestone succeeds, we can implement the backend phase by phase without introducing unnecessary complexity.