package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Geofence — a virtual geographic boundary for a device.
 * When a device enters or exits this boundary, an alert is generated.
 */
@Entity
@Table(name = "geofences")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Geofence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Center latitude of the geofence circle */
    @Column(nullable = false)
    private Double latitude;

    /** Center longitude of the geofence circle */
    @Column(nullable = false)
    private Double longitude;

    /** Radius in meters (default 100m) */
    @Column(nullable = false)
    @Builder.Default
    private Double radius = 100.0;

    /** Type: "ENTER" = alert on entry, "EXIT" = alert on exit, "BOTH" = alert on both */
    @Column(name = "alert_type", nullable = false, length = 10)
    @Builder.Default
    private String alertType = "BOTH";

    /** Whether the device is currently inside this geofence */
    @Column(name = "is_inside")
    @Builder.Default
    private Boolean isInside = false;

    /** Whether this geofence is active */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
