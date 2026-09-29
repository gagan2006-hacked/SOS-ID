# API specification — Phase 1

Base path: `/api/v1`. JSON is UTF-8. All authenticated owner endpoints use `Authorization: Bearer <access-token>`. Error bodies use a stable code, human-readable message, correlation ID, and field-level validation errors where relevant. No endpoint returns internal database IDs unless they are required to perform a permitted owner action.

## Owner authentication

The concrete sign-in mechanism (password, external OIDC provider, or both) remains a product decision. The API boundary is:

| Method | Path | Purpose | Authorization |
|---|---|---|---|
| `POST` | `/auth/register` | create an owner account when local registration is enabled | public, rate limited |
| `POST` | `/auth/login` | establish owner authentication | public, rate limited |
| `POST` | `/auth/refresh` | rotate a refresh credential | valid refresh credential |
| `POST` | `/auth/logout` | revoke current refresh credential/session | authenticated owner |
| `POST` | `/auth/password/forgot` | begin reset if password sign-in is enabled | public, abuse protected |
| `POST` | `/auth/password/reset` | complete password reset if enabled | valid reset proof |

Account-enumeration-resistant responses are required for public recovery/authentication requests.

## Owner and profile

| Method | Path | Purpose | Authorization |
|---|---|---|---|
| `GET` | `/me` | get current owner account summary | owner |
| `GET` | `/me/emergency-profile` | get full editable profile | owner |
| `PUT` | `/me/emergency-profile` | replace/update owner profile and emergency data | owner |
| `PATCH` | `/me/emergency-profile` | update selected fields | owner |
| `POST` | `/me/emergency-profile/qr-credentials` | issue replacement/additional QR credential per product policy | owner |
| `GET` | `/me/emergency-profile/qr-credentials` | list credential metadata, never raw values | owner |
| `DELETE` | `/me/emergency-profile/qr-credentials/{credentialId}` | revoke a credential | owner of profile |

Profile update requests accept only allow-listed emergency fields. Responses from owner endpoints may include data the owner supplied; emergency endpoints use the smaller responder projection below.

## Owner documents *(new; future capability)*

| Method | Path | Purpose | Authorization |
|---|---|---|---|
| `POST` | `/me/documents/uploads` | request a constrained, short-lived private-object upload target | owner |
| `POST` | `/me/documents` | register uploaded document metadata and initial policy | owner |
| `GET` | `/me/documents` | list owner document metadata | owner |
| `GET` | `/me/documents/{documentId}` | get owner document metadata/status | owner of profile |
| `PATCH` | `/me/documents/{documentId}` | update owner-editable metadata or `accessPolicy` | owner of profile |
| `POST` | `/me/documents/{documentId}/archive` | archive a document and revoke active document grants | owner of profile |
| `DELETE` | `/me/documents/{documentId}` | delete only when the approved retention policy permits it | owner of profile |

The upload target accepts an allow-listed object key, content type, size, checksum, and brief expiry. Registering a document is a separate application call after upload confirmation. Owner document identifiers are opaque API identifiers, not profile/storage references. Changing a document from `PUBLIC_TO_QR` to `PRIVATE`, archiving it, or deleting it revokes/invalidates all pending requests and temporary authorizations for that document. Changing from `PRIVATE` to `PUBLIC_TO_QR` does not make old grants broader; they are closed as no longer needed.

## Emergency access

| Method | Path | Purpose | Authorization |
|---|---|---|---|
| `POST` | `/emergency/sessions` | exchange a QR credential for an ephemeral session handle | QR token; abuse controls |
| `GET` | `/emergency/sessions/{sessionHandle}` | return emergency disclosure projection | valid, unexpired session handle |
| `POST` | `/emergency/sessions/{sessionHandle}/close` | optionally end access early | valid session handle |

`POST /emergency/sessions` accepts the opaque QR token in the request body. A QR asset should encode a fixed emergency landing URL with the credential in its URL fragment, so browser navigation does not send it to the server, logs, or referrers; the landing client posts the fragment over TLS and immediately clears it. The response contains only a short-lived session handle and expiry. `GET` returns the emergency disclosure projection described in the domain model. Both endpoints must return indistinguishable failures for malformed, revoked, unknown, expired, disabled, or rate-limited credentials.

## Emergency document access *(new; future capability)*

| Method | Path | Purpose | Authorization |
|---|---|---|---|
| `GET` | `/emergency/sessions/{sessionHandle}/documents` | list eligible document metadata; never content | valid emergency session |
| `POST` | `/emergency/sessions/{sessionHandle}/documents/{documentHandle}/access-requests` | create a request for one `PRIVATE` document and select a registered contact | valid session; document must be private |
| `POST` | `/emergency/sessions/{sessionHandle}/document-access-requests/{requestHandle}/otp-challenges` | issue OTP to the request's registered contact | valid session; active matching request |
| `POST` | `/emergency/sessions/{sessionHandle}/document-access-requests/{requestHandle}/otp-verifications` | verify OTP and grant scoped temporary authorization | valid session; active matching request/challenge |
| `POST` | `/emergency/sessions/{sessionHandle}/documents/{documentHandle}/access-url` | obtain a one-time, short-lived controlled download URL | valid session; public policy or matching active private authorization |

The document list may show limited metadata for `PRIVATE` documents and indicates that contact authorization is required; it never leaks storage references or contents. The access-request endpoint takes a registered emergency-contact opaque handle, not a telephone number. The backend confirms the contact belongs to the document profile before it creates the request/challenge.

The OTP endpoint is rate-limited and returns generic responses. Verification consumes a single challenge and creates authorization only for the exact `(emergencySession, document, accessRequest)` tuple. `access-url` re-evaluates the live session, QR credential, owner/profile state, document lifecycle, document policy, and—when private—the exact temporary authorization before issuing a tightly constrained URL. It returns a generic unavailable response on any mismatch. The controlled URL must itself be short lived and limited to the requested object; it cannot outlive the authorization decision.

## API-wide controls

- Enforce request-size limits, strict JSON schema validation, pagination for lists, and per-route rate limits.
- Use idempotency keys for credential issuance and profile-mutating operations if clients may retry.
- Include `Cache-Control: no-store` on auth and emergency responses.
- Do not expose an endpoint that retrieves a profile directly by user/profile/credential identifier.
