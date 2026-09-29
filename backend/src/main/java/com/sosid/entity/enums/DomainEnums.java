package com.sosid.entity.enums;

// Kept together temporarily to preserve a stable database enum vocabulary during refactoring.
public final class DomainEnums {
    private DomainEnums() { }
    public enum AccountStatus { ACTIVE, DISABLED }
    public enum ProfileStatus { ACTIVE, DISABLED }
    public enum CredentialStatus { ACTIVE, REVOKED }
    public enum DocumentAccessPolicy { PUBLIC_TO_QR, PRIVATE }
    public enum ProcessingStatus { PENDING, READY, FAILED }
    public enum DocumentLifecycleStatus { ACTIVE, ARCHIVED, DELETED }
    public enum AccessRequestStatus { PENDING, AUTHORIZED, EXPIRED, REVOKED }
}
