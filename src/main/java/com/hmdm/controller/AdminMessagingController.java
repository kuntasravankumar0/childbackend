package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.Device;
import com.hmdm.entity.PushMessage;
import com.hmdm.repository.DeviceRepository;
import com.hmdm.repository.PushMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Messaging / Pager API — send text messages or commands to Android devices.
 * Corresponds to the hmdm-android-plugin-pager-master push type: "textMessage"
 */
@RestController
@RequestMapping("/api/messaging")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminMessagingController {

    private final DeviceRepository deviceRepository;
    private final PushMessageRepository pushMessageRepository;

    /** Send a text message to a single device (Pager plugin feature) */
    @PostMapping("/device/{deviceId}/message")
    public ResponseEntity<ApiResponse<Void>> sendMessage(
            @PathVariable Long deviceId,
            @RequestBody Map<String, String> body) {

        if (!deviceRepository.existsById(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        String text = body.get("message");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("message is required"));
        }
        PushMessage msg = PushMessage.builder()
                .deviceId(deviceId)
                .messageType("textMessage")  // matches Pager plugin Const.PUSH_MESSAGE_TYPE
                .payload(text)
                .sent(false)
                .build();
        pushMessageRepository.save(msg);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** Broadcast a text message to ALL devices */
    @PostMapping("/broadcast")
    public ResponseEntity<ApiResponse<Map<String, Object>>> broadcast(
            @RequestBody Map<String, String> body) {

        String text = body.get("message");
        if (text == null || text.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("message is required"));
        }
        List<Device> devices = deviceRepository.findAll();
        int count = 0;
        for (Device d : devices) {
            PushMessage msg = PushMessage.builder()
                    .deviceId(d.getId())
                    .messageType("textMessage")
                    .payload(text)
                    .sent(false)
                    .build();
            pushMessageRepository.save(msg);
            count++;
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("queued", count)));
    }

    /** Send a configUpdated push to all devices of a config */
    @PostMapping("/config/{configId}/push")
    public ResponseEntity<ApiResponse<Map<String, Object>>> pushConfigUpdate(
            @PathVariable Long configId) {
        List<Device> devices = deviceRepository.findByConfigId(configId);
        for (Device d : devices) {
            PushMessage msg = PushMessage.builder()
                    .deviceId(d.getId())
                    .messageType("configUpdated")
                    .sent(false)
                    .build();
            pushMessageRepository.save(msg);
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("queued", devices.size())));
    }

    /** Get message history for a device */
    @GetMapping("/device/{deviceId}/history")
    public ResponseEntity<ApiResponse<List<PushMessage>>> getHistory(@PathVariable Long deviceId) {
        List<PushMessage> messages = pushMessageRepository.findByDeviceIdOrderByCreatedAtDesc(deviceId);
        return ResponseEntity.ok(ApiResponse.ok(messages));
    }
}
