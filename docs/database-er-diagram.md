# Database ER diagram — Phase 1

```mermaid
erDiagram
  USERS ||--o| EMERGENCY_PROFILES : owns
  EMERGENCY_PROFILES ||--o{ EMERGENCY_CONTACTS : contains
  EMERGENCY_PROFILES ||--o{ PROFILE_ALLERGIES : lists
  EMERGENCY_PROFILES ||--o{ PROFILE_CONDITIONS : lists
  EMERGENCY_PROFILES ||--o{ PROFILE_MEDICATIONS : lists
  EMERGENCY_PROFILES ||--o{ QR_CREDENTIALS : has
  EMERGENCY_PROFILES ||--o{ MEDICAL_DOCUMENTS : owns
  QR_CREDENTIALS ||--o{ EMERGENCY_SESSIONS : initiates
  EMERGENCY_SESSIONS ||--o{ DOCUMENT_ACCESS_REQUESTS : creates
  MEDICAL_DOCUMENTS ||--o{ DOCUMENT_ACCESS_REQUESTS : targets
  DOCUMENT_ACCESS_REQUESTS ||--o{ EMERGENCY_CONTACT_OTP_CHALLENGES : has
  EMERGENCY_CONTACTS ||--o{ EMERGENCY_CONTACT_OTP_CHALLENGES : authorizes
  DOCUMENT_ACCESS_REQUESTS ||--o| TEMPORARY_DOCUMENT_AUTHORIZATIONS : yields
  EMERGENCY_SESSIONS ||--o{ TEMPORARY_DOCUMENT_AUTHORIZATIONS : scopes
  MEDICAL_DOCUMENTS ||--o{ TEMPORARY_DOCUMENT_AUTHORIZATIONS : permits
  EMERGENCY_CONTACTS ||--o{ TEMPORARY_DOCUMENT_AUTHORIZATIONS : verifies
  MEDICAL_DOCUMENTS ||--o{ AUDIT_EVENTS : concerns
  DOCUMENT_ACCESS_REQUESTS ||--o{ AUDIT_EVENTS : concerns
  TEMPORARY_DOCUMENT_AUTHORIZATIONS ||--o{ AUDIT_EVENTS : concerns
  USERS ||--o{ AUDIT_EVENTS : acts_as_owner
  EMERGENCY_PROFILES ||--o{ AUDIT_EVENTS : concerns
  QR_CREDENTIALS ||--o{ AUDIT_EVENTS : concerns
  EMERGENCY_SESSIONS ||--o{ AUDIT_EVENTS : concerns

  USERS {
    uuid id PK
    string email UK
    string status
    timestamptz created_at
    timestamptz updated_at
  }
  EMERGENCY_PROFILES {
    uuid id PK
    uuid owner_user_id FK
    string display_name
    date date_of_birth
    string blood_group
    string critical_instructions
    string status
    timestamptz created_at
    timestamptz updated_at
  }
  EMERGENCY_CONTACTS {
    uuid id PK
    uuid profile_id FK
    string name
    string relationship
    string phone_number
    int display_order
  }
  PROFILE_ALLERGIES {
    uuid id PK
    uuid profile_id FK
    string label
    int display_order
  }
  PROFILE_CONDITIONS {
    uuid id PK
    uuid profile_id FK
    string label
    int display_order
  }
  PROFILE_MEDICATIONS {
    uuid id PK
    uuid profile_id FK
    string label
    string dose_instruction
    int display_order
  }
  QR_CREDENTIALS {
    uuid id PK
    uuid profile_id FK
    bytes token_verifier
    string status
    timestamptz issued_at
    timestamptz revoked_at
    timestamptz expires_at
    timestamptz last_used_at
  }
  EMERGENCY_SESSIONS {
    uuid id PK
    uuid qr_credential_id FK
    uuid profile_id FK
    timestamptz issued_at
    timestamptz expires_at
    timestamptz revoked_at
    bytes request_fingerprint
  }
  MEDICAL_DOCUMENTS {
    uuid id PK
    uuid profile_id FK
    string storage_reference UK
    string document_name
    string document_type
    string access_policy
    string processing_status
    string lifecycle_status
    timestamptz created_at
    timestamptz updated_at
  }
  DOCUMENT_ACCESS_REQUESTS {
    uuid id PK
    uuid document_id FK
    uuid emergency_session_id FK
    uuid medical_document_id FK
    uuid document_access_request_id FK
    uuid temporary_document_authorization_id FK
    string status
    timestamptz created_at
    timestamptz expires_at
    timestamptz completed_at
  }
  EMERGENCY_CONTACT_OTP_CHALLENGES {
    uuid id PK
    uuid access_request_id FK
    uuid emergency_contact_id FK
    bytes otp_verifier
    timestamptz expires_at
    int attempt_count
    int max_attempts
    timestamptz consumed_at
    timestamptz created_at
  }
  TEMPORARY_DOCUMENT_AUTHORIZATIONS {
    uuid id PK
    uuid document_id FK
    uuid emergency_session_id FK
    uuid access_request_id FK
    uuid authorized_contact_id FK
    timestamptz issued_at
    timestamptz expires_at
    timestamptz revoked_at
  }
  AUDIT_EVENTS {
    uuid id PK
    string event_type
    string outcome
    uuid actor_user_id FK
    uuid profile_id FK
    uuid qr_credential_id FK
    uuid emergency_session_id FK
    string correlation_id
    jsonb metadata
    timestamptz occurred_at
  }
```

## Relational rules

- `users.email` is unique after an explicitly chosen normalization policy.
- `emergency_profiles.owner_user_id` is unique, enforcing one profile per user for Phase 1.
- `qr_credentials.token_verifier` is unique; raw token material is never persisted.
- `display_order` is unique within its parent collection.
- Foreign keys prevent orphaned profile data. Audit foreign keys may be nullable when an event is rejected before a target is resolved.
- `medical_documents.access_policy` is constrained to `PUBLIC_TO_QR` or `PRIVATE`; document content is not a database column.
- A `document_access_request` must reference a private document and a currently eligible emergency session from that document's profile; this cross-table invariant is enforced in the application/service transaction.
- An OTP challenge's emergency contact must belong to the same profile as the requested document. Its verifier is one-way/keyed, its attempts are bounded, and it is single-use.
- `temporary_document_authorizations` has a unique `access_request_id` and must repeat the request's document/session references. Service validation enforces equality, expires the authorization no later than its session, and prevents cross-document/session confused-deputy grants.
- Deleting medically relevant data should be a deliberate policy decision. Phase 1 should use account/profile disabling and preserve audit evidence rather than hard-delete by default.
