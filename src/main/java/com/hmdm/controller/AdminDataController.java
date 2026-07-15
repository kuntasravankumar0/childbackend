package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.DeviceNotification;
import com.hmdm.repository.DeviceNotificationRepository;
import com.hmdm.repository.DeviceRepository;
import com.hmdm.service.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices/{deviceId}/data")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class AdminDataController {

    private final DeviceRepository deviceRepository;
    private final DeviceNotificationRepository notificationRepository;
    private final GoogleSheetsService googleSheetsService;

    // ─── Contacts (from Google Sheets) ────────────────────────────────

    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getContacts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        List<Map<String, Object>> contacts = googleSheetsService.getContacts(deviceId);
        return ResponseEntity.ok(ApiResponse.ok(contacts));
    }

    @DeleteMapping("/contacts")
    public ResponseEntity<ApiResponse<Void>> deleteContacts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        googleSheetsService.deleteContacts(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Call Logs (from Google Sheets) ───────────────────────────────

    @GetMapping("/calls")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getCallLogs(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        List<Map<String, Object>> calls = googleSheetsService.getCallLogs(deviceId);
        return ResponseEntity.ok(ApiResponse.ok(calls));
    }

    @DeleteMapping("/calls")
    public ResponseEntity<ApiResponse<Void>> deleteCallLogs(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        googleSheetsService.deleteCallLogs(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Notifications (from Google Sheets, fallback to PostgreSQL) ───

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getNotifications(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();

        // 1. Try Google Sheets first
        List<Map<String, Object>> fromSheets = googleSheetsService.getNotifications(deviceId);
        if (!fromSheets.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.ok(fromSheets));
        }

        // 2. Fall back to PostgreSQL (legacy data)
        List<DeviceNotification> dbNotifs = notificationRepository.findByDeviceIdOrderByReceivedAtDesc(deviceId);
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (DeviceNotification n : dbNotifs) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", n.getId());
            m.put("deviceId", n.getDeviceId());
            m.put("packageName", n.getPackageName());
            m.put("appName", n.getAppName());
            m.put("title", n.getTitle());
            m.put("text", n.getText());
            m.put("receivedAt", n.getReceivedAt());
            result.add(m);
        }
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/notifications")
    public ResponseEntity<ApiResponse<Void>> deleteNotifications(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        notificationRepository.deleteByDeviceId(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Dashboard counts ────────────────────────────────────────────

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCounts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.putAll(googleSheetsService.getCounts(deviceId));
        // If Sheets had no data, fall back to PostgreSQL for notifications count
        if (counts.getOrDefault("notifications", 0L) instanceof Number n && n.longValue() == 0L) {
            long dbCount = notificationRepository.countByDeviceId(deviceId);
            if (dbCount > 0) counts.put("notifications", dbCount);
        }
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }
}
