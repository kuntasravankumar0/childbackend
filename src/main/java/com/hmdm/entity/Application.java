package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Application {

    public static final String TYPE_APP    = "app";
    public static final String TYPE_WEB    = "web";
    public static final String TYPE_INTENT = "intent";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(nullable = false, length = 200)
    private String pkg;

    @Column(length = 100)
    private String version;

    @Column(name = "version_code")
    private Integer versionCode;

    @Column(length = 1000)
    private String url;

    @Column(nullable = false, length = 20)
    @Builder.Default
    private String type = TYPE_APP;

    @Column
    @Builder.Default
    private Boolean system = false;

    @Column(name = "file_path", length = 500)
    private String filePath;

    @Column(columnDefinition = "TEXT")
    private String icon;

    @Column(columnDefinition = "TEXT")
    private String description;

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
