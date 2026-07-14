package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_logs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "phone_number", length = 100)
    private String phoneNumber;

    @Column(name = "call_type", nullable = false, length = 20)
    private String callType;  // INCOMING, OUTGOING, MISSED

    @Column(name = "duration_sec")
    @Builder.Default
    private Integer durationSec = 0;

    @Column(name = "call_date", nullable = false)
    private Long callDate;  // timestamp in millis

    @Column(name = "contact_name", length = 500)
    private String contactName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
