package com.sosid.entity;
import jakarta.persistence.*; import lombok.*; import java.util.UUID;
@Entity @Table(name="emergency_contacts") @Getter @Setter @NoArgsConstructor public class EmergencyContact { @Id private UUID id; private UUID profileId; private String name; private String relationship; private String phoneNumber; private Integer displayOrder; private boolean active; }
