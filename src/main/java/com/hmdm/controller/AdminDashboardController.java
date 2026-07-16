package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminDashboardController {

    private final DeviceRepository deviceRepository;
    private final ConfigurationRepository configurationRepository;
    private final DeviceLogRepository logRepository;
    private final PushMessageRepository pushMessageRepository;

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<Map<String, Object>>> summary() {
        long totalDevices  = deviceRepository.countByCustomerId(1L);
        long onlineDevices = deviceRepository.countByCustomerIdAndStatus(1L, "ONLINE");
        long offlineDevices= deviceRepository.countByCustomerIdAndStatus(1L, "OFFLINE");
        long pendingDevices= deviceRepository.countByCustomerIdAndStatus(1L, "PENDING");
        long resetDevices  = deviceRepository.countByCustomerIdAndStatus(1L, "RESET");
        long totalConfigs  = configurationRepository.countByCustomerId(1L);
        long pendingMsgs   = pushMessageRepository.count();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("devices", Map.of(
                "total",   totalDevices,
                "online",  onlineDevices,
                "offline", offlineDevices,
                "pending", pendingDevices,
                "reset",   resetDevices
        ));
        result.put("configurations", totalConfigs);
        result.put("pendingMessages",pendingMsgs);
        result.put("serverTime",     LocalDateTime.now().toString());

        return ResponseEntity.ok(ApiResponse.ok(result));
    }
}
