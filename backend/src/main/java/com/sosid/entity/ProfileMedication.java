package com.sosid.entity; import jakarta.persistence.*; import lombok.*; import java.util.UUID;
@Entity @Table(name="profile_medications") @Getter @Setter @NoArgsConstructor public class ProfileMedication { @Id private UUID id; private UUID profileId; private String label; private String doseInstruction; private Integer displayOrder; }
