package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_notifications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(name = "package_name", length = 500)
    private String packageName;

    @Column(name = "app_name", length = 500)
    private String appName;

    @Column(columnDefinition = "TEXT")
    private String title;

    @Column(columnDefinition = "TEXT")
    private String text;

    @Column(name = "received_at", nullable = false)
    private Long receivedAt;  // timestamp in millis

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
