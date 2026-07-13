package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String number;

    @Column(length = 500)
    private String description;

    @Column(name = "group_id")
    private Long groupId;

    @Column(name = "config_id")
    private Long configId;

    @Column(length = 200)
    private String model;

    @Column(length = 50)
    private String imei;

    @Column(length = 50)
    private String imei2;

    @Column(length = 50)
    private String phone;

    @Column(length = 50)
    private String phone2;

    @Column(length = 50)
    private String iccid;

    @Column(length = 50)
    private String iccid2;

    @Column(length = 50)
    private String imsi;

    @Column(length = 50)
    private String imsi2;

    @Column(length = 100)
    private String serial;

    @Column(length = 200)
    private String cpu;

    @Column(name = "android_version", length = 20)
    private String androidVersion;

    @Column(name = "battery_level")
    private Integer batteryLevel;

    @Column(name = "battery_charging", length = 20)
    private String batteryCharging;

    @Column(name = "mdm_mode")
    @Builder.Default
    private Boolean mdmMode = false;

    @Column(name = "kiosk_mode")
    @Builder.Default
    private Boolean kioskMode = false;

    @Column(name = "default_launcher")
    @Builder.Default
    private Boolean defaultLauncher = false;

    @Column(name = "launcher_type", length = 50)
    private String launcherType;

    @Column(name = "launcher_package", length = 200)
    private String launcherPackage;

    @Column(name = "ip_address", length = 50)
    private String ipAddress;

    @Column(name = "external_ip", length = 50)
    private String externalIp;

    @Column
    private Double lat;

    @Column
    private Double lon;

    @Column(name = "location_ts")
    private Long locationTs;

    @Column(length = 500)
    private String custom1;

    @Column(length = 500)
    private String custom2;

    @Column(length = 500)
    private String custom3;

    @Column(length = 20)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "last_sync")
    private LocalDateTime lastSync;

    @Column(name = "enrolled_at")
    private LocalDateTime enrolledAt;

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
