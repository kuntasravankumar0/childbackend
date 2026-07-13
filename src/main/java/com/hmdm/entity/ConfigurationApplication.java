package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "configuration_applications")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ConfigurationApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "configuration_id", nullable = false)
    private Configuration configuration;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "show_icon")
    @Builder.Default
    private Boolean showIcon = true;

    @Column
    @Builder.Default
    private Boolean remove = false;

    @Column(name = "run_after_install")
    @Builder.Default
    private Boolean runAfterInstall = false;

    @Column(name = "run_at_boot")
    @Builder.Default
    private Boolean runAtBoot = false;

    @Column(name = "skip_version")
    @Builder.Default
    private Boolean skipVersion = false;

    @Column(name = "use_kiosk")
    @Builder.Default
    private Boolean useKiosk = false;

    @Column(name = "icon_text", length = 100)
    private String iconText;

    @Column(name = "screen_order")
    private Integer screenOrder;

    @Column(name = "key_code")
    private Integer keyCode;

    @Column
    @Builder.Default
    private Boolean bottom = false;

    @Column(name = "long_tap")
    @Builder.Default
    private Boolean longTap = false;

    @Column(length = 500)
    private String intent;
}
