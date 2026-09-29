package com.sosid.entity; import jakarta.persistence.*; import lombok.*; import java.util.UUID;
@Entity @Table(name="profile_allergies") @Getter @Setter @NoArgsConstructor public class ProfileAllergy { @Id private UUID id; private UUID profileId; private String label; private Integer displayOrder; }
