package com.horsetrack.equinecare.modules.medical.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "Medical_Records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "record_id")
    private Integer recordId;

    @Column(name = "diagnosis", nullable = false, columnDefinition = "NVARCHAR(500)")
    private String diagnosis;

    @Column(name = "treatment_plan", columnDefinition = "NVARCHAR(MAX)")
    private String treatmentPlan;

    @Column(name = "is_lockout_ordered", nullable = false)
    private Boolean isLockoutOrdered;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "horse_id", nullable = false)
    private Horse horse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "vet_id", nullable = false)
    private User veterinarian;

    @Builder.Default
    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<InjuryMark> injuryMarks = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "medicalRecord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PrescriptionItem> prescriptionItems = new ArrayList<>();

    // Helper method for bidirectional synchronization
    public void addInjuryMark(InjuryMark mark) {
        if (injuryMarks == null) {
            injuryMarks = new ArrayList<>();
        }
        injuryMarks.add(mark);
        mark.setMedicalRecord(this);
    }

    public void removeInjuryMark(InjuryMark mark) {
        if (injuryMarks != null) {
            injuryMarks.remove(mark);
            mark.setMedicalRecord(null);
        }
    }

    // Helper method for bidirectional synchronization
    public void addPrescriptionItem(PrescriptionItem item) {
        if (prescriptionItems == null) {
            prescriptionItems = new ArrayList<>();
        }
        prescriptionItems.add(item);
        item.setMedicalRecord(this);
    }

    public void removePrescriptionItem(PrescriptionItem item) {
        if (prescriptionItems != null) {
            prescriptionItems.remove(item);
            item.setMedicalRecord(null);
        }
    }
}
