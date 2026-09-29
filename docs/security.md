# Security design and threat model — Phase 1

## Authentication and authorization model

Owner authentication establishes a subject with the `OWNER` authority. Access tokens are short-lived and audience-bound to SOS-ID APIs; refresh credentials are rotated, securely stored, and individually revocable. The application derives the target profile from the authenticated subject (`/me`), rather than accepting an owner identifier from the client.

Emergency access is a separate, capability-style authorization path. Possession of an active opaque QR credential may initiate one short-lived emergency session. It grants only the responder projection, never owner endpoints or broader API access. Credential state and profile/account state are checked at session creation and access; any revocation invalidates active sessions. The session handle is high entropy, non-guessable, short lived, and neither reusable as an owner token nor included in logs.

> **New decision — document authorization hierarchy.** The application enforces this sequence: owner authentication → QR credential → unexpired emergency session → document policy → direct public access *or* private request → registered-contact OTP challenge/verification → temporary document authorization → controlled document access. The frontend may render state but makes none of these authorization decisions.

Privileged support/admin roles are not introduced in Phase 1 because their operational need and approval workflow are unspecified. If later required, they need separate identities, least-privilege scopes, MFA, reason capture, and audit controls.

## Emergency-session flow

```mermaid
sequenceDiagram
  participant R as Responder / QR scanner
  participant E as Emergency API
  participant D as PostgreSQL
  participant C as Redis
  participant A as Audit
  R->>E: POST /emergency/sessions (opaque QR token)
  E->>E: validate format, TLS, rate limit
  E->>D: locate hashed verifier; check credential/profile/account state
  alt active and eligible
    E->>C: store opaque session handle with strict TTL
    E->>D: record emergency-session metadata
    E->>A: record SESSION_CREATED
    E-->>R: handle + expiry (no medical data)
    R->>E: GET /emergency/sessions/{handle}
    E->>C: validate TTL/revocation state
    E->>D: re-check authoritative eligibility and read projection
    E->>A: record PROFILE_DISCLOSED
    E-->>R: minimal emergency projection; Cache-Control: no-store
  else invalid, expired, revoked, disabled, or throttled
    E->>A: record rejected access without token
    E-->>R: generic access-unavailable response
  end
```

The expiry duration is intentionally left configurable pending product approval; it must be short and cannot be extended by the responder. QR scan, display, close, expiry, and denied-access actions are auditable.

## Private-document authorization flow *(new; future capability)*

```mermaid
sequenceDiagram
  participant R as Responder session
  participant D as Document authorization API
  participant DB as PostgreSQL
  participant EC as Registered emergency contact
  participant S as Private S3
  R->>D: Create request for one private document + registered contact handle
  D->>DB: Validate session, document policy, and contact belongs to profile
  D->>DB: Create request and one short-lived OTP challenge
  D-->>EC: Deliver OTP through approved channel
  R->>D: Verify OTP for request
  D->>DB: Atomically check verifier, expiry, attempts, session, document
  D->>DB: Consume challenge; grant temporary authorization for same tuple
  R->>D: Request controlled document URL
  D->>DB: Revalidate all revocation and expiry boundaries
  D-->>R: Brief, object-specific URL
  R->>S: Download authorized object
```

The OTP verifier is keyed/hashed and is never stored or logged in plaintext. A challenge is single use, short lived, attempt-limited, request-bound, contact-bound, and document/session-bound. New challenges and verification must stop if the emergency session expires, its QR credential is revoked, the account/profile is disabled, the document is archived, or its policy changes.

## Audit model

Audit events are append-only, timestamped by the server, correlated to a request/trace ID, and written transactionally with the relevant state change when feasible. Events include: account lifecycle, authentication success/failure, profile create/update/disable, QR issue/revoke/use, emergency session create/access/close/expire, document create/policy change/archive/delete, document list through an emergency session, public-document access, private access request, OTP issue, OTP verification success/failure, temporary authorization grant/expiry/revocation, private-document access, authorization denial, and privileged configuration changes.

Record actor class (owner, emergency capability, system), target references, outcome, event type, source category, and minimal integrity metadata. Do not store medical values, raw tokens, full authorization headers, passwords, or raw QR contents. Restrict audit-read access operationally; Phase 1 does not expose audit history to clients. Ship protected audit copies to centralized monitoring/retention storage and alert on suspicious patterns.

## Threat model and mitigations

| Threat | Risk | Mitigations |
|---|---|---|
| Stolen or photographed QR code | unauthorized profile/public-document disclosure | minimal profile projection, high-entropy opaque credential, short session TTL, rotate/revoke credential, rate limits, no broad account access; private documents still need registered-contact OTP |
| QR token in URL, logs, referrers, analytics | capability theft | QR token in URL fragment, then POST body after landing; redaction at proxy/app/APM; no third-party scripts on emergency pages; strict referrer policy |
| Credential brute force | profile enumeration/disclosure | at least 128-bit random secrets, keyed verifier/hash, generic failures, throttling by source and credential fingerprint, anomaly detection |
| Session-handle theft | temporary disclosure | HTTPS/HSTS, short TTL, `no-store`, referrer policy, no session handles in URLs where avoidable, revocation checks |
| Broken object-level authorization | owner sees/modifies another profile | `/me` resource model, server-derived ownership, authorization tests for every mutation/read, no client-supplied owner IDs |
| Injection/XSS | data theft or account takeover | parameterized persistence, schema validation, output encoding, CSP, secure cookies where used, dependency scanning |
| Account takeover | profile tampering/credential creation | strong authentication, rate limits, reset protections, refresh rotation/revocation, MFA considered before high-risk operations |
| Sensitive data in logs/backups | broad privacy breach | data classification, structured allow-list logging, encryption, least-privilege backup access, retention/deletion policy |
| Redis outage or eviction | accidental authorization bypass | Redis is not the sole authority; revalidate authoritative credential/profile state; fail closed when validity cannot be established |
| Insider/cloud credential compromise | mass data exposure | least-privilege IAM, separate environments/accounts, secrets manager, KMS, private networking, audit trails, break-glass controls only when justified |
| Abuse/availability attack | emergency service unavailable | WAF, rate limits, autoscaling, health checks, DDoS protections, tested database backup/restore and incident runbooks |
| Stolen emergency-session handle | temporary profile/public-document access | high-entropy handle, TLS/HSTS, short TTL, `no-store`, referrer controls, QR/session revocation; private documents still require their scoped authorization |
| Private-document enumeration | sensitive metadata disclosure | list only minimal approved metadata, opaque document handles, generic errors, rate limits, do not expose storage keys or distinguish inaccessible states |
| Document-ID guessing | unauthorized document retrieval | opaque handles, session/profile-scoped lookup, authorization before metadata/download URL, generic denial |
| OTP brute force | unauthorized private access | high-entropy OTP, keyed verifier, short challenge TTL, per-challenge and source attempt limits, lock/consume on limit, generic failures and alerting |
| OTP replay | reuse of valid OTP | atomic single-use challenge consumption, request/document/session/contact binding, expiry checks; no challenge can authorize a second grant |
| Unauthorized emergency-contact use | arbitrary number authorizes access | choose only an active contact registered on the same profile; server resolves/validates contact membership; never accept arbitrary phone numbers |
| Access-request replay or confused deputy | request/grant used for another document/session | bind and revalidate exact request, document, session, policy, and contact at every transition; unique authorization per request |
| Authorization for one document used for another | cross-document private disclosure | authorization lookup requires matching document ID and session ID; database/application invariants repeat and compare both references |
| Expired session with still-valid document authorization | access beyond QR boundary | authorization expiry is capped at session expiry and controlled-URL issuance revalidates session; expiry makes all dependent grants unusable |
| Revoked QR with active document authorization | access after credential revocation | every download decision checks current QR/session/profile/account eligibility; revoke propagation invalidates dependent grants |
| Owner changes policy during active request | stale or unintended disclosure | policy change/archival revokes pending requests/challenges/grants; access URL issuance reads current policy transactionally |

Security review must validate legal/privacy obligations, retention periods, encryption standards, the chosen identity provider, session TTL, and incident-response ownership before production use.
