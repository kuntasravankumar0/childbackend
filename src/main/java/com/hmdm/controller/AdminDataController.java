package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.CallLog;
import com.hmdm.repository.CallLogRepository;
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

    // ─── Dashboard counts ───

    @GetMapping("/counts")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getCounts(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) return ResponseEntity.notFound().build();
        Map<String, Object> counts = new LinkedHashMap<>();
        counts.putAll(googleSheetsService.getCounts(deviceId));
        return ResponseEntity.ok(ApiResponse.ok(counts));
    }
}
