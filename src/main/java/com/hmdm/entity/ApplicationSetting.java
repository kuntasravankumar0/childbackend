package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "application_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ApplicationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", nullable = false)
    private Configuration configuration;

    @Column(name = "package_id", nullable = false, length = 200)
    private String packageId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String value;

    @Column
    @Builder.Default
    private Integer type = 1;

    @Column(name = "last_update")
    private Long lastUpdate;

    @Column(name = "read_only")
    @Builder.Default
    private Boolean readOnly = false;

    @Column(name = "is_variable")
    @Builder.Default
    private Boolean isVariable = false;
}
