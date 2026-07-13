package com.hmdm.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.List;

/**
 * Matches the JSON structure sent by the Android APK to /rest/public/sync/info
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeviceInfoDto {

    private String deviceId;
    private String model;
    private String phone;
    private String imei;
    private String imei2;
    private String phone2;
    private String iccid;
    private String iccid2;
    private String imsi;
    private String imsi2;
    private String cpu;
    private String serial;
    private String androidVersion;
    private Integer batteryLevel;
    private String batteryCharging;
    private Boolean mdmMode;
    private Boolean kioskMode;
    private Boolean defaultLauncher;
    private String launcherType;
    private String launcherPackage;
    private Boolean factoryReset;
    private String custom1;
    private String custom2;
    private String custom3;
    private List<Integer> permissions;
    private List<InstalledAppDto> applications;
    private Location location;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Location {
        private long ts;
        private double lat;
        private double lon;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InstalledAppDto {
        private String  pkg;
        private String  name;
        private String  version;
        private Integer code;
    }
}
