package com.hmdm.dto.device;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DeviceDataSyncDto {

    private List<ContactDto> contacts;
    private List<CallLogDto> callLogs;
    private List<MediaRecordDto> media;

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
    public static class MediaRecordDto {
        private String mediaType;   // "camera" or "audio"
        private String fileName;
        private Long fileSize;
        private Long capturedAt;
    }
}
