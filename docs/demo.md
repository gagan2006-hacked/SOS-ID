# Phase 1 demo scenario

1. A new user authenticates and creates an emergency profile with their chosen emergency-only medical information and contacts.
2. The user creates a QR credential and stores/prints the generated QR code. The raw credential is not shown again by the API.
3. A responder scans the code. The emergency service validates the credential and creates a brief session, recording an audit event.
4. The responder sees only the emergency projection for the short session lifetime.
5. For a `PUBLIC_TO_QR` document, the responder can request a brief controlled download URL after the session is revalidated.
6. For a `PRIVATE` document, the responder can see limited metadata and request access only through a contact already registered on that profile. The contact's OTP is verified against that exact request; a short temporary authorization permits only that document within the still-valid emergency session.
7. The owner rotates/revokes the QR credential or changes a document policy. Existing emergency sessions and dependent document grants stop working, and the change is audited.

This is a design-only scenario. It does not constitute a claim that the platform has been implemented, medically validated, or approved for production use.
