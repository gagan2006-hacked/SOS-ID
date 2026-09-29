# SOS-ID backend

Spring Boot modular-monolith backend targeting Java 21.

## Run locally

```powershell
cd backend
mvn spring-boot:run
```

The default profile uses an in-memory H2 database for development. Set `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` for PostgreSQL. Set a long, random `JWT_SECRET` outside development.

Document upload/download requires `S3_BUCKET` and AWS task/instance credentials. OTP sending is intentionally disabled unless `OTP_DELIVERY_ENABLED=true` and AWS SNS permissions are configured; no OTP is returned by the API or written to logs.

## Verification

```powershell
cd backend
mvn test
```
