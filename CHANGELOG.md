# Changelog

## 2026-06-04 - Reliability and security hardening

### Added

- Added Flyway database migration support.
- Added versioned migration scripts under `backend/src/main/resources/db/migration`.
- Added signed API tokens with expiration.
- Added BCrypt password hashing with automatic legacy plain-text password upgrade on successful login.
- Added login failure counting and temporary account lockout.
- Added authentication environment variables for token TTL, token secret, lockout threshold and lockout duration.

### Changed

- User creation and password reset now store hashed passwords.
- Existing databases are baselined at migration version `V12` to avoid replaying historical manual SQL scripts.

## 2026-06-04

### Added

- Added complete local setup instructions for Java, Maven, MySQL, Node.js, npm and Git.
- Added database initialization SQL entry file `000_initial_schema.sql`.
- Added frontend and backend manual startup commands.
- Added database import commands and default connection information.

### Changed

- Clarified that the public repository is not permitted for commercial use without written authorization.
- Simplified public README content and removed internal repository publishing notes.

### Notes

- Production deployment still requires a reviewed Docker image strategy, database backup strategy, log retention, uploaded-file volume mounting and environment-specific configuration.
