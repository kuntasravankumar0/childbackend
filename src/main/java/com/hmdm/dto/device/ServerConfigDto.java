package com.hmdm.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * JSON config structure returned to Android device
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ServerConfigDto {

    private String backgroundColor;
    private String textColor;
    private String backgroundImageUrl;
    private Integer iconSize;
    private String title;
    private Boolean displayStatus;
    private Boolean gps;
    private Boolean bluetooth;
    private Boolean wifi;
    private Boolean mobileData;
    private Boolean kioskMode;
    private String mainApp;
    private Boolean lockStatusBar;
    private Integer systemUpdateType;
    private String systemUpdateFrom;
    private String systemUpdateTo;
    private String pushOptions;
    private Integer keepaliveTime;
    private String requestUpdates;
    private Boolean disableLocation;
    private String appPermissions;
    private Boolean usbStorage;
    private Boolean autoBrightness;
    private Integer brightness;
    private Boolean manageTimeout;
    private Integer timeout;
    private Boolean lockVolume;
    private Boolean manageVolume;
    private Integer volume;
    private String passwordMode;
    private String timeZone;
    private String allowedClasses;
    private Integer orientation;
    private Boolean kioskHome;
    private Boolean kioskRecents;
    private Boolean kioskNotifications;
    private Boolean kioskSystemInfo;
    private Boolean kioskKeyguard;
    private Boolean kioskLockButtons;
    private Boolean kioskScreenOn;
    private Boolean lockSafeSettings;
    private Boolean permissive;
    private Boolean kioskExit;
    private Boolean disableScreenshots;
    private Boolean autostartForeground;
    private Boolean showWifi;
    private String restrictions;
    private String description;
    private String custom1;
    private String custom2;
    private String custom3;
    private String newServerUrl;
    private String password;

    private List<AppDto> applications;
    private List<AppSettingDto> applicationSettings;
    private List<RemoteFileDto> files;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppDto {
        private String type;
        private String name;
        private String pkg;
        private String version;
        private Integer code;
        private String url;
        private Boolean useKiosk;
        private Boolean showIcon;
        private Boolean remove;
        private Boolean runAfterInstall;
        private Boolean runAtBoot;
        private Boolean skipVersion;
        private String iconText;
        private String icon;
        private Integer screenOrder;
        private Integer keyCode;
        private Boolean bottom;
        private Boolean longTap;
        private String intent;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AppSettingDto {
        private String packageId;
        private String name;
        private String value;
        private Integer type;
        private Long lastUpdate;
        private Boolean readOnly;
        private Boolean isVariable;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class RemoteFileDto {
        private String path;
        private String url;
        private String description;
        private String checksum;
        private Boolean remove;
    }
}
