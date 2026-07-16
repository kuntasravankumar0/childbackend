package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.CallLog;
import com.hmdm.entity.DeviceNotification;
import com.hmdm.repository.CallLogRepository;
import com.hmdm.repository.DeviceNotificationRepository;
import com.hmdm.repository.DeviceRepository;
import com.hmdm.service.GoogleSheetsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/devices/{deviceId}/data")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("isAuthenticated()")
public class AdminDataController {

    private final DeviceRepository deviceRepository;
    private final DeviceNotificationRepository notificationRepository;
    private final CallLogRepository callLogRepository;
    private final GoogleSheetsService googleSheetsService;

    // ─── Contacts (from Google Sheets, paginated) ─────────────────────

    @GetMapping("/contacts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getContacts(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "200") int size) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        
        List<Map<String, Object>> allContacts = googleSheetsService.getContacts(deviceId);
        
        // Paginate in-memory (1000+ contacts is manageable)
        int total = allContacts.size();
        int from = page * size;
        int to = Math.min(from + size, total);
        List<Map<String, Object>> paged = from >= total ? List.of() : allContacts.subList(from, to);
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", paged);
        result.put("total", total);
        result.put("page", page);
        result.put("pages", (int) Math.ceil((double) total / size));
        
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/contacts")
    public ResponseEntity<ApiResponse<Void>> deleteContacts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        googleSheetsService.deleteContacts(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Call Logs (from PostgreSQL with pagination + server-side search) ─

    @GetMapping("/calls")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCallLogs(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Long dateFrom,
            @RequestParam(required = false) Long dateTo) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        
        // Use paginated DB query for efficient 50K+ call log handling
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "callDate"));
        
        Page<CallLog> callLogPage = callLogRepository.searchByDeviceId(
                deviceId, search, type, dateFrom, dateTo, pageRequest);
        
        List<Map<String, Object>> items = callLogPage.getContent().stream().map(cl -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", cl.getId());
            m.put("deviceId", cl.getDeviceId());
            m.put("phoneNumber", cl.getPhoneNumber());
            m.put("callType", cl.getCallType());
            m.put("durationSec", cl.getDurationSec());
            m.put("callDate", cl.getCallDate());
            m.put("contactName", cl.getContactName());
            m.put("syncedAt", cl.getCreatedAt() != null ? cl.getCreatedAt().toString() : "");
            return m;
        }).collect(Collectors.toList());
        
        long totalFiltered = callLogPage.getTotalElements();
        
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", totalFiltered);
        result.put("page", page);
        result.put("pages", callLogPage.getTotalPages());
        result.put("search", search);
        result.put("type", type);
        
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/calls")
    public ResponseEntity<ApiResponse<Void>> deleteCallLogs(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        googleSheetsService.deleteCallLogs(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Notifications (from PostgreSQL with pagination, Sheets fallback) ───

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getNotifications(
            @PathVariable Long deviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();

        // 1. Use paginated PostgreSQL query (fast, memory-efficient)
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Page<DeviceNotification> notifPage = notificationRepository.findByDeviceIdOrderByReceivedAtDesc(deviceId, pageRequest);
        
        if (notifPage.hasContent()) {
            List<Map<String, Object>> items = notifPage.getContent().stream().map(n -> {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", n.getId());
                m.put("deviceId", n.getDeviceId());
                m.put("packageName", n.getPackageName());
                m.put("appName", n.getAppName());
                m.put("title", n.getTitle());
                m.put("text", n.getText());
                m.put("receivedAt", n.getReceivedAt());
                return m;
            }).collect(Collectors.toList());
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", items);
            result.put("total", notifPage.getTotalElements());
            result.put("page", page);
            result.put("pages", notifPage.getTotalPages());
            return ResponseEntity.ok(ApiResponse.ok(result));
        }

        // 2. Fall back to Google Sheets (legacy data only)
        List<Map<String, Object>> fromSheets = googleSheetsService.getNotifications(deviceId);
        if (!fromSheets.isEmpty()) {
            int total = fromSheets.size();
            int from = page * size;
            int to = Math.min(from + size, total);
            List<Map<String, Object>> paged = from >= total ? List.of() : fromSheets.subList(from, to);
            
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("items", paged);
            result.put("total", (long) total);
            result.put("page", page);
            result.put("pages", (int) Math.ceil((double) total / size));
            return ResponseEntity.ok(ApiResponse.ok(result));
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", List.of());
        result.put("total", 0L);
        result.put("page", page);
        result.put("pages", 0);
        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    @DeleteMapping("/notifications")
    public ResponseEntity<ApiResponse<Void>> deleteNotifications(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        notificationRepository.deleteByDeviceId(deviceId);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Dashboard counts (from GoogleSheetsService — includes PostgreSQL fallback) ───

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCounts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        // GoogleSheetsService.getCounts() already handles PostgreSQL fallback
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.putAll(googleSheetsService.getCounts(deviceId));
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }
}
