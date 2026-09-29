package com.sosid.core;

import com.sosid.common.ApiSupport;
import com.sosid.entity.EmergencySession;
import com.sosid.entity.enums.DomainEnums;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
class ApiController {
    private final OwnerService owners;
    private final OwnerOtpAuthenticationService ownerOtpAuthentication;
    private final EmergencyService emergency;
    private final DocumentService documents;

    ApiController(OwnerService owners, OwnerOtpAuthenticationService ownerOtpAuthentication, EmergencyService emergency, DocumentService documents) {
        this.owners = owners;
        this.ownerOtpAuthentication = ownerOtpAuthentication;
        this.emergency = emergency;
        this.documents = documents;
    }

    private static <T> ResponseEntity<T> secure(ResponseEntity.BodyBuilder b) {
        return b.header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    private static <T> ResponseEntity<T> secure(ResponseEntity<T> b) {
        return ResponseEntity.status(b.getStatusCode()).headers(b.getHeaders()).header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").body(b.getBody());
    }

    @PostMapping("/auth/otp/request")
    public ResponseEntity<Void> requestOwnerOtp(@Valid @RequestBody OwnerOtpRequest body, HttpServletRequest request) {
        ownerOtpAuthentication.request(body.phoneNumber(), ApiSupport.correlationId(request));
        return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @PostMapping("/auth/otp/verify")
    public ResponseEntity<OwnerService.AuthResult> verifyOwnerOtp(@Valid @RequestBody OwnerOtpVerification body, HttpServletRequest request) {
        return secure(ResponseEntity.ok(ownerOtpAuthentication.verify(body.phoneNumber(), body.otp(), ApiSupport.correlationId(request))));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<OwnerService.AuthResult> refresh(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        return secure(ResponseEntity.ok(owners.refresh(body.refreshToken(), ApiSupport.correlationId(request))));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest body, HttpServletRequest request) {
        owners.logout(body.refreshToken(), ApiSupport.correlationId(request));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        var u = owners.current(ApiSupport.currentUserId());
        return Map.of("email", u.getEmail(), "status", u.getStatus());
    }

    @GetMapping("/me/emergency-profile")
    public OwnerService.ProfileView getProfile() {
        return owners.getProfile(ApiSupport.currentUserId());
    }

    @PutMapping("/me/emergency-profile")
    public OwnerService.ProfileView putProfile(@RequestBody ProfileRequest body, HttpServletRequest r) {
        return owners.putProfile(ApiSupport.currentUserId(), body.toInput(), ApiSupport.correlationId(r));
    }

    @PatchMapping("/me/emergency-profile")
    public OwnerService.ProfileView patchProfile(@RequestBody ProfileRequest body, HttpServletRequest r) {
        return owners.putProfile(ApiSupport.currentUserId(), body.toInput(), ApiSupport.correlationId(r));
    }

    @PostMapping("/me/emergency-profile/qr-credentials")
    public OwnerService.QrResult createQr(HttpServletRequest r) {
        return owners.createQr(ApiSupport.currentUserId(), ApiSupport.correlationId(r));
    }

    @GetMapping("/me/emergency-profile/qr-credentials")
    public List<OwnerService.QrView> qrs() {
        return owners.listQrs(ApiSupport.currentUserId());
    }

    @DeleteMapping("/me/emergency-profile/qr-credentials/{id}")
    public ResponseEntity<Void> revokeQr(@PathVariable UUID id, HttpServletRequest r) {
        owners.revokeQr(ApiSupport.currentUserId(), id, ApiSupport.correlationId(r));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/me/documents/uploads")
    public DocumentService.UploadTarget upload(@Valid @RequestBody UploadRequest body) {
        return documents.uploadTarget(ApiSupport.currentUserId(), body.fileName(), body.contentType());
    }

    @PostMapping("/me/documents")
    public DocumentService.DocumentView registerDocument(@Valid @RequestBody DocumentRequest body, HttpServletRequest r) {
        return documents.register(ApiSupport.currentUserId(), new DocumentService.DocumentInput(body.storageReference(), body.documentName(), body.documentType(), body.accessPolicy()), ApiSupport.correlationId(r));
    }

    @GetMapping("/me/documents")
    public List<DocumentService.DocumentView> ownerDocuments() {
        return documents.listOwner(ApiSupport.currentUserId());
    }

    @GetMapping("/me/documents/{id}")
    public DocumentService.DocumentView ownerDocument(@PathVariable UUID id) {
        return documents.getOwner(ApiSupport.currentUserId(), id);
    }

    @PatchMapping("/me/documents/{id}")
    public DocumentService.DocumentView updateDocument(@PathVariable UUID id, @RequestBody DocumentUpdateRequest body, HttpServletRequest r) {
        return documents.update(ApiSupport.currentUserId(), id, new DocumentService.DocumentUpdate(body.documentName(), body.accessPolicy()), ApiSupport.correlationId(r));
    }

    @PostMapping("/me/documents/{id}/archive")
    public ResponseEntity<Void> archive(@PathVariable UUID id, HttpServletRequest r) {
        documents.archive(ApiSupport.currentUserId(), id, ApiSupport.correlationId(r));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/me/documents/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest r) {
        documents.delete(ApiSupport.currentUserId(), id, ApiSupport.correlationId(r));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/emergency/sessions")
    public ResponseEntity<EmergencyService.SessionResult> session(@Valid @RequestBody QrSessionRequest body, HttpServletRequest r) {
        return secure(ResponseEntity.ok(emergency.create(body.qrToken(), ApiSupport.correlationId(r))));
    }

    @GetMapping("/emergency/sessions/{handle}")
    public ResponseEntity<OwnerService.EmergencyProfileView> sessionProfile(@PathVariable String handle, HttpServletRequest r) {
        String c = ApiSupport.correlationId(r);
        EmergencySession s = emergency.require(handle, c);
        emergency.recordDisclosure(s, c);
        return secure(ResponseEntity.ok(owners.getProfileById(s.getProfileId())));
    }

    @PostMapping("/emergency/sessions/{handle}/close")
    public ResponseEntity<Void> close(@PathVariable String handle, HttpServletRequest r) {
        emergency.close(handle, ApiSupport.correlationId(r));
        return ResponseEntity.noContent().header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    @GetMapping("/emergency/sessions/{handle}/documents")
    public ResponseEntity<List<DocumentService.EmergencyDocumentView>> emergencyDocs(@PathVariable String handle, HttpServletRequest r) {
        return secure(ResponseEntity.ok(documents.listEmergency(handle, ApiSupport.correlationId(r))));
    }

    @PostMapping("/emergency/sessions/{handle}/documents/{documentId}/access-requests")
    public ResponseEntity<DocumentService.AccessRequestView> requestPrivate(@PathVariable String handle, @PathVariable UUID documentId, @Valid @RequestBody ContactRequest body, HttpServletRequest r) {
        return secure(ResponseEntity.ok(documents.requestPrivate(handle, documentId, body.contactHandle(), ApiSupport.correlationId(r))));
    }

    @PostMapping("/emergency/sessions/{handle}/document-access-requests/{requestId}/otp-challenges")
    public ResponseEntity<Void> issueOtp(@PathVariable String handle, @PathVariable UUID requestId, HttpServletRequest r) {
        documents.issueOtp(handle, requestId, ApiSupport.correlationId(r));
        return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").header("Referrer-Policy", "no-referrer").build();
    }

    @PostMapping("/emergency/sessions/{handle}/document-access-requests/{requestId}/otp-verifications")
    public ResponseEntity<DocumentService.AuthorizationView> verifyOtp(@PathVariable String handle, @PathVariable UUID requestId, @Valid @RequestBody OtpRequest body, HttpServletRequest r) {
        return secure(ResponseEntity.ok(documents.verifyOtp(handle, requestId, body.otp(), ApiSupport.correlationId(r))));
    }

    @PostMapping("/emergency/sessions/{handle}/documents/{documentId}/access-url")
    public ResponseEntity<Map<String, String>> accessUrl(@PathVariable String handle, @PathVariable UUID documentId, HttpServletRequest r) {
        return secure(ResponseEntity.ok(Map.of("url", documents.accessUrl(handle, documentId, ApiSupport.correlationId(r)))));
    }


    record ProfileRequest(@NotBlank @Size(max = 200) String displayName, LocalDate dateOfBirth,
                          @Size(max = 8) String bloodGroup, @Size(max = 4000) String criticalInstructions,
                          List<OwnerService.ContactInput> contacts, List<String> allergies, List<String> conditions,
                          List<OwnerService.MedicationInput> medications) {
        OwnerService.ProfileInput toInput() {
            return new OwnerService.ProfileInput(displayName, dateOfBirth, bloodGroup, criticalInstructions, contacts, allergies, conditions, medications);
        }
    }

    record UploadRequest(@NotBlank @Size(max = 255) String fileName, @NotBlank @Size(max = 100) String contentType) {
    }

    record DocumentRequest(@NotBlank @Size(max = 512) String storageReference,
                           @NotBlank @Size(max = 255) String documentName,
                           @NotBlank @Size(max = 100) String documentType,
                           @NotNull DomainEnums.DocumentAccessPolicy accessPolicy) {
    }

    record DocumentUpdateRequest(@Size(max = 255) String documentName, DomainEnums.DocumentAccessPolicy accessPolicy) {
    }

    record QrSessionRequest(@NotBlank String qrToken) {
    }

    record ContactRequest(@NotNull UUID contactHandle) {
    }

    record OtpRequest(@Pattern(regexp = "\\d{6}") String otp) {
    }

    record RefreshRequest(@NotBlank String refreshToken) {
    }

    record OwnerOtpRequest(@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phoneNumber) {
    }

    record OwnerOtpVerification(@NotBlank @Pattern(regexp = "^\\+[1-9]\\d{7,14}$") String phoneNumber,
                                @NotBlank @Pattern(regexp = "\\d{6}") String otp) {
    }
}
