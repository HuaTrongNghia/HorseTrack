package com.horsetrack.equinecare.modules.medical.entity;

import com.horsetrack.equinecare.modules.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Ghi chú tiến trình điều trị của bác sĩ (bảng Protocol_Progress_Notes). */
@Entity
@Table(name = "Protocol_Progress_Notes")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ProtocolProgressNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "note_id")
    private Integer noteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "protocol_id", nullable = false)
    private TreatmentProtocol protocol;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "written_by", nullable = false)
    private User writtenBy;

    @Column(name = "written_at", nullable = false, updatable = false)
    private LocalDateTime writtenAt;

    @Column(name = "content", nullable = false, length = 1000)
    private String content;
}
