package com.sosid.entity; import jakarta.persistence.*; import lombok.*; import java.util.UUID;
@Entity @Table(name="profile_conditions") @Getter @Setter @NoArgsConstructor public class ProfileCondition { @Id private UUID id; private UUID profileId; private String label; private Integer displayOrder; }
