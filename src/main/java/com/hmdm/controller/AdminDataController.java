package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.CallLog;
import com.hmdm.entity.DeviceContact;
import com.hmdm.entity.DeviceNotification;
import com.hmdm.repository.CallLogRepository;
import com.hmdm.repository.DeviceContactRepository;
import com.hmdm.repository.DeviceNotificationRepository;
import com.hmdm.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/devices/{deviceId}/data")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminDataController {

    private final DeviceRepository deviceRepository;
    private final DeviceContactRepository contactRepository;
    private final CallLogRepository callLogRepository;
    private final DeviceNotificationRepository notificationRepository;

    // ─── Contacts ─────────────────────────────────────────────────────

    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<List<DeviceContact>>> getContacts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.ok(contactRepository.findByDeviceId(deviceId)));
    }

    @DeleteMapping("/contacts")
    public ResponseEntity<ApiResponse<Void>> deleteContacts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        contactRepository.deleteByDeviceId(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    @DeleteMapping("/contacts/{contactId}")
    public ResponseEntity<ApiResponse<Void>> deleteContact(@PathVariable Long deviceId,
                                                            @PathVariable Long contactId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        contactRepository.deleteById(contactId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Call Logs ───────────────────────────────────────────────────

    @GetMapping("/calls")
    public ResponseEntity<ApiResponse<List<CallLog>>> getCallLogs(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(ApiResponse.ok(callLogRepository.findByDeviceIdOrderByCallDateDesc(deviceId)));
    }

    @DeleteMapping("/calls")
    public ResponseEntity<ApiResponse<Void>> deleteCallLogs(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        callLogRepository.deleteByDeviceId(deviceId);
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
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "contacts",      contactRepository.countByDeviceId(deviceId),
                "callLogs",      callLogRepository.countByDeviceId(deviceId),
                "notifications", notificationRepository.countByDeviceId(deviceId)
        )));
    }
}
