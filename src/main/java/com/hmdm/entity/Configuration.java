package com.hmdm.entity;

import jakarta.persistence.*;
import lombok.*;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "configurations")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Configuration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(nullable = false, length = 200)
    private String name;

    @Column
    private String description;

    @Column(name = "background_color", length = 20)
    private String backgroundColor;

    @Column(name = "text_color", length = 20)
    private String textColor;

    @Column(name = "background_image_url", length = 500)
    private String backgroundImageUrl;

    @Column(name = "icon_size")
    @Builder.Default
    private Integer iconSize = 100;

    @Column(length = 50)
    private String title;

    @Column(name = "display_status")
    @Builder.Default
    private Boolean displayStatus = false;

    @Column
    private Boolean gps;

    @Column
    private Boolean bluetooth;

    @Column
    private Boolean wifi;

    @Column(name = "mobile_data")
    private Boolean mobileData;

    @Column(name = "kiosk_mode")
    @Builder.Default
    private Boolean kioskMode = false;

    @Column(name = "main_app", length = 200)
    private String mainApp;

    @Column(name = "lock_status_bar")
    private Boolean lockStatusBar;

    @Column(name = "system_update_type")
    @Builder.Default
    private Integer systemUpdateType = 0;

    @Column(name = "system_update_from", length = 10)
    private String systemUpdateFrom;

    @Column(name = "system_update_to", length = 10)
    private String systemUpdateTo;

    @Column(name = "push_options", length = 50)
    @Builder.Default
    private String pushOptions = "mqttWorker";

    @Column(name = "keepalive_time")
    @Builder.Default
    private Integer keepaliveTime = 300;

    @Column(name = "request_updates", length = 50)
    private String requestUpdates;

    @Column(name = "disable_location")
    @Builder.Default
    private Boolean disableLocation = false;

    @Column(name = "app_permissions", length = 50)
    private String appPermissions;

    @Column(name = "usb_storage")
    private Boolean usbStorage;

    @Column(name = "auto_brightness")
    private Boolean autoBrightness;

    @Column
    private Integer brightness;

    @Column(name = "manage_timeout")
    private Boolean manageTimeout;

    @Column(name = "timeout_val")
    private Integer timeoutVal;

    @Column(name = "lock_volume")
    private Boolean lockVolume;

    @Column(name = "manage_volume")
    private Boolean manageVolume;

    @Column
    private Integer volume;

    @Column(name = "password_mode", length = 50)
    private String passwordMode;

    @Column(name = "time_zone", length = 100)
    private String timeZone;

    @Column
    private Integer orientation;

    @Column(name = "kiosk_home")
    private Boolean kioskHome;

    @Column(name = "kiosk_recents")
    private Boolean kioskRecents;

    @Column(name = "kiosk_notifications")
    private Boolean kioskNotifications;

    @Column(name = "kiosk_system_info")
    private Boolean kioskSystemInfo;

    @Column(name = "kiosk_keyguard")
    private Boolean kioskKeyguard;

    @Column(name = "kiosk_lock_buttons")
    private Boolean kioskLockButtons;

    @Column(name = "kiosk_screen_on")
    private Boolean kioskScreenOn;

    @Column(name = "lock_safe_settings")
    @Builder.Default
    private Boolean lockSafeSettings = false;

    @Column
    @Builder.Default
    private Boolean permissive = false;

    @Column(name = "kiosk_exit")
    @Builder.Default
    private Boolean kioskExit = false;

    @Column(name = "disable_screenshots")
    @Builder.Default
    private Boolean disableScreenshots = false;

    @Column(name = "autostart_foreground")
    @Builder.Default
    private Boolean autostartForeground = false;

    @Column(name = "show_wifi")
    @Builder.Default
    private Boolean showWifi = false;

    @Column(columnDefinition = "TEXT")
    private String restrictions;

    @Column(length = 500)
    private String custom1;

    @Column(length = 500)
    private String custom2;

    @Column(length = 500)
    private String custom3;

    @Column(length = 200)
    private String password;

    @Column(name = "new_server_url", length = 500)
    private String newServerUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<ConfigurationApplication> applications = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<ApplicationSetting> applicationSettings = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "configuration", cascade = CascadeType.ALL, fetch = FetchType.LAZY, orphanRemoval = true)
    @Builder.Default
    private List<RemoteFile> files = new ArrayList<>();

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
