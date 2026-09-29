# Phase 1 domain model

## Aggregates

### User

Represents the authenticated profile owner. It holds identity and account state, not medical data. A disabled user cannot manage a profile or have emergency access served from their QR credentials.

Fields: `id`, `email`, `status`, `createdAt`, `updatedAt`.

### EmergencyProfile

An owner-controlled collection of emergency-disclosable information. Phase 1 keeps only information required by the stated purpose: identity details suitable for responder identification, emergency contacts, allergies, conditions, medications, blood group if supplied, and free-text critical instructions. Every field is optional and only fields included in the emergency projection are eligible for disclosure.

Fields: `id`, `ownerUserId`, `displayName`, `dateOfBirth` (optional), `bloodGroup` (optional), `allergies`, `conditions`, `medications`, `criticalInstructions`, `status`, timestamps.

Structured list entries are preferred for allergies, conditions, medications, and contacts so their status and future provenance can be managed independently. The exact clinical coding standard is intentionally undecided; Phase 1 must not claim medical-code normalization it does not perform.

### EmergencyContact

A contact associated with an emergency profile. It has a name, relationship, phone number, and order. Whether SOS-ID sends notifications to contacts is out of scope.

### QrCredential

A rotatable, revocable emergency-access credential associated with one profile. Only a keyed/hashed verifier is stored; the raw scanner token is displayed only at creation/replacement time and encoded in the QR asset.

Fields: `id`, `profileId`, `tokenVerifier`, `status`, `issuedAt`, `revokedAt`, `expiresAt` (optional), `lastUsedAt` (optional).

### EmergencySession

An ephemeral authorization decision created after a valid QR scan. It retains no richer medical snapshot than needed to enforce access. Its durable record is a metadata-only audit event. A server-side opaque session handle is returned to the emergency web view.

Fields: `id`, `qrCredentialId`, `profileId`, `issuedAt`, `expiresAt`, `revokedAt` (optional), `requestFingerprint` (minimized/pseudonymous).

### MedicalDocument *(new; future capability)*

Document metadata owned by one emergency profile. The binary exists only in private object storage; PostgreSQL stores no document contents. A document has a storage reference, display/type metadata, processing status, an access policy, and timestamps. Owner-controlled lifecycle state supports archival/deletion under an approved retention policy.

Fields: `id`, `profileId`, `storageReference`, `documentName`, `documentType`, `accessPolicy`, `processingStatus`, `lifecycleStatus`, `createdAt`, `updatedAt`.

`accessPolicy` is a closed enum in this revision:

- `PUBLIC_TO_QR`: a valid emergency session can request controlled access directly.
- `PRIVATE`: a valid emergency session can see limited metadata and create an access request, but cannot obtain content until the registered-contact OTP flow grants a temporary authorization.

### DocumentAccessRequest *(new; future capability)*

Represents an emergency session's request for one private document. It binds the document, the session, and the authorization workflow. It is not itself authorization and cannot be reused for another document or session.

Fields: `id`, `documentId`, `emergencySessionId`, `status`, `createdAt`, `expiresAt`, `completedAt` (optional).

### EmergencyContactOtpChallenge *(new; future capability)*

An OTP challenge bound to one access request and one already-registered emergency contact belonging to the same profile as the document. It holds only a keyed/hash verifier and challenge metadata—not the plaintext OTP. It has a short expiry, attempt counter/limit, consumed timestamp, and delivery/reference metadata that does not expose the raw OTP.

Fields: `id`, `accessRequestId`, `emergencyContactId`, `otpVerifier`, `expiresAt`, `attemptCount`, `maxAttempts`, `consumedAt` (optional), `createdAt`.

The contact selection must be validated server-side from the document profile's active registered contacts. An arbitrary client-supplied phone number is never an authorization target.

### TemporaryDocumentAuthorization *(new; future capability)*

A successful OTP-verification result that permits a single private document to be accessed by a single emergency session for a bounded period. Its expiry can be no later than the emergency session expiry and it becomes unusable immediately if the session, QR credential, owner account, or profile is revoked/disabled. It does not change the document policy.

Fields: `id`, `documentId`, `emergencySessionId`, `accessRequestId`, `authorizedContactId`, `issuedAt`, `expiresAt`, `revokedAt` (optional).

### AuditEvent

An append-only record of a security-relevant event. It has an actor classification, target references, outcome, correlation ID, timestamp, source metadata, and a carefully allow-listed metadata object. It must never contain a QR token, access token, password, full IP address when a reduced representation suffices, or medical content.

## Ownership and lifecycle

`User` owns one `EmergencyProfile`; the profile owns its contacts, medical entries, QR credentials, and documents. QR credentials may be replaced without altering the profile. Scanning an active credential may create many temporary sessions. A session may create private-document access requests; a request may have challenges and at most one successful temporary authorization. Sessions and all profile, credential, document, and authorization mutations produce audit events.

## Emergency disclosure projection

The responder receives only:

- profile display name and optional date of birth;
- optional blood group;
- allergies, conditions, medications, and critical instructions explicitly stored for emergency disclosure;
- emergency contacts.

It does not receive owner email, account state, credential metadata, audit history, authentication data, documents, or internal identifiers.
