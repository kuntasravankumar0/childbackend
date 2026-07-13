package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(nullable = false, length = 200)
    private String pkg;

    @Column(length = 200)
    private String name;

    @Column(length = 100)
    private String version;

    @Column(name = "version_code")
    private Integer versionCode;

    @Column
    @Builder.Default
    private Boolean installed = true;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
