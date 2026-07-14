package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "device_contacts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DeviceContact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false)
    private Long deviceId;

    @Column(length = 500)
    private String name;

    @Column(length = 100)
    private String phone;

    @Column(name = "phone_type", length = 50)
    private String phoneType;

    @Column(length = 500)
    private String email;

    @Column(name = "raw_contact_id", length = 100)
    private String rawContactId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
