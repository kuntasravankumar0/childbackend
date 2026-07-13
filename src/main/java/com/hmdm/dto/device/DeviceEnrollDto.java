package com.hmdm.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceEnrollDto {
    private String deviceId;
    private String customer;
    private String config;
    private String group;
    private String deviceIdUse;
}
