# SOS-ID

SOS-ID is an emergency identity platform. A QR code starts a short-lived, responder-facing emergency session that exposes only the profile owner's authorized critical medical information.

The initial backend implementation is in [backend](backend/pom.xml). It covers local owner authentication, profile management, QR credentials, constrained emergency sessions, audit records, document metadata/policies, and registered-contact OTP authorization. AWS-backed upload/download and OTP delivery require runtime configuration.

Design documents are in [docs](docs/architecture.md).
