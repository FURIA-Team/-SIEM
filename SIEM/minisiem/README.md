# MiniSIEM

MiniSIEM is a lightweight security information and event management application for collecting security events, reviewing alerts, and monitoring system activity through a web dashboard.

## Features

- Security dashboard with event and threat activity.
- Security event ingestion, search, severity and time filtering, pagination, and CSV export.
- Alert details and status tracking (`OPEN`, `INVESTIGATING`, `RESOLVED`).
- User and role administration.
- System settings and audit/system logs.
- Session-based authentication with Spring Security and CSRF protection.
- SQLite persistence through Spring Data JPA and Hibernate.

## Technology stack

- Java 25
- Spring Boot
- Spring Security
- SQLite
- Hibernate and Spring Data JPA
- Maven Wrapper
- JUnit 5 and Spring test support

## Requirements

- Java Development Kit (JDK) 25.
- PowerShell on Windows to run the commands below.
- No separately installed Maven is required; the Maven Wrapper downloads the configured Maven distribution.

## Run the application

In PowerShell, from the project root:

```powershell
.\mvnw.cmd spring-boot:run
```

The application is available at [http://localhost:8080/](http://localhost:8080/). The default SQLite database is `minisiem.db` in the project directory. Keep this local database out of version control.

### Initial administrator setup

Set `MINISIEM_ADMIN_BOOTSTRAP_TOKEN` to a private, one-time token of at least 32 characters before starting the application. Do not commit the token or put it in a tracked configuration file.

```powershell
$env:MINISIEM_ADMIN_BOOTSTRAP_TOKEN = "<your-private-token-of-at-least-32-characters>"
.\mvnw.cmd spring-boot:run
```

Use the configured token with `POST /api/auth/bootstrap-admin` to create the initial administrator. Bootstrap is disabled when the environment variable is empty and is only intended for the initial administrator setup.

## Roles

- **ADMIN** — can use the user-facing security features and administer users and system settings.
- **USER** — can access user-facing dashboard, event, alert, and log operations. User administration and settings are restricted to ADMIN.

## Create a security event

`POST /api/user/events` accepts an authenticated session and requires the CSRF token used by the application. Required JSON fields are `title`, `severity`, `source`, and `destination`; `severity` must be `CRITICAL`, `HIGH`, `MEDIUM`, or `LOW` (case-insensitive). `details` and `occurredAt` are optional.

Example request body:

```json
{
  "title": "Failed login attempt",
  "severity": "MEDIUM",
  "source": "192.0.2.10",
  "destination": "auth-service",
  "details": "Authentication failed for a test account"
}
```

`occurredAt`, when provided, must be a date-time value accepted by Java `LocalDateTime`, for example `2026-10-31T12:30:00`.

## Run tests

Tests use a separate SQLite database under `target/`, not the default application database.

```powershell
.\mvnw.cmd clean test
```

## License

This project is licensed under the MIT License. See [LICENSE](./LICENSE) for details.
