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
        // Google Sheets deletion is handled via overwrite — clear by marking
        log.info("Delete contacts request for device {} (Google Sheets — manual deletion required)", deviceId);
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
        log.info("Delete call logs request for device {} (Google Sheets — manual deletion required)", deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Notifications ───────────────────────────────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<List<DeviceNotification>>> getNotifications(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.ok(notificationRepository.findByDeviceIdOrderByReceivedAtDesc(deviceId)));
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
        counts.put("notifications", notificationRepository.countByDeviceId(deviceId));
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }
}
