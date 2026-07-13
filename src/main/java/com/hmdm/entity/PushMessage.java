package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "push_messages")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PushMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "message_type", nullable = false, length = 100)
    private String messageType;

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column
    @Builder.Default
    private Boolean sent = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
