# AWS architecture — target deployment

## Phase 1 runtime

```mermaid
flowchart TB
  Internet --> WAF[AWS WAF]
  WAF --> ALB[Application Load Balancer / TLS]
  ALB --> ECS[ECS/Fargate Spring Boot containers]
  ECS --> RDS[(RDS PostgreSQL, Multi-AZ)]
  ECS --> ElastiCache[(ElastiCache Redis)]
  ECS --> Secrets[Secrets Manager]
  ECS --> KMS[AWS KMS]
  ECS --> CW[CloudWatch logs/metrics/alarms]
  CloudTrail[CloudTrail] --> AuditStore[Protected audit retention]
  ECS -. future .-> S3[S3]
  ECS -. future .-> Textract[Textract]
  ECS -. future .-> Bedrock[Bedrock]
  ECS -. future .-> SES[SES / SNS]
```

Place ECS tasks, RDS, and Redis in private subnets across at least two availability zones. The load balancer is public; no database or cache endpoint is public. Use security groups to allow only application-to-database/cache traffic. TLS terminates at the ALB and is enforced between service components where supported. Keep application secrets in Secrets Manager and use task IAM roles rather than static AWS keys. Encrypt RDS, Redis backups, S3 (when added), secrets, and audit stores with KMS-managed keys.

## Future document-processing flow

Document processing is intentionally deferred from Phase 1. When approved, the application must make the document-policy decision before issuing any controlled object URL; S3 never evaluates `PUBLIC_TO_QR`/`PRIVATE` policy and is never public. The bounded pipeline is:

```mermaid
flowchart LR
  Owner --> Upload[Authenticated upload request]
  Upload --> S3[S3 private encrypted object]
  S3 --> Queue[Event queue / job]
  Queue --> Worker[Document worker]
  Worker --> Textract[AWS Textract]
  Worker --> Review[Validation / owner review]
  Review --> Profile[Explicit profile update]
  Worker --> Audit[Audit event]
  Worker -. only approved use .-> Bedrock[Amazon Bedrock]
```

Upload should use short-lived, constrained presigned URLs; scan/type/size-validate files; isolate untrusted documents; retain originals only under an approved policy; and require owner review before extracted text affects the emergency profile. Download URLs are generated only after the Spring Boot document-authorization service revalidates the emergency session, document policy, and private-document authorization where required. They are object-specific, brief, and cannot outlive the authorization/session boundary. Textract or Bedrock output is untrusted input, not medical truth. Do not send data to Bedrock until the applicable data-use, retention, regional, and consent decisions are approved.

SES/SNS are reserved for opt-in account and security notifications. Notifications must never include emergency medical content or a live emergency-session link.
