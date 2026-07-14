package com.hmdm.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceDataSyncDto {

    private List<ContactDto> contacts;
    private List<CallLogDto> callLogs;
    private List<NotificationDto> notifications;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ContactDto {
        private String name;
        private String phone;
        private String phoneType;
        private String email;
        private String rawContactId;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CallLogDto {
        private String phoneNumber;
        private String callType;    // INCOMING, OUTGOING, MISSED
        private Integer durationSec;
        private Long callDate;
        private String contactName;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NotificationDto {
        private String packageName;
        private String appName;
        private String title;
        private String text;
        private Long receivedAt;
    }
}
