package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.Device;
import com.hmdm.entity.DeviceApplication;
import com.hmdm.entity.DeviceLog;
import com.hmdm.entity.DeviceLocation;
import com.hmdm.entity.PushMessage;
import com.hmdm.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminDeviceController {

    private final DeviceRepository              deviceRepository;
    private final DeviceLogRepository           logRepository;
    private final DeviceLocationRepository      locationRepository;
    private final PushMessageRepository         pushMessageRepository;
    private final ConfigurationRepository       configurationRepository;
    private final DeviceApplicationRepository   deviceApplicationRepository;
    private final DeviceContactRepository       contactRepository;
    private final CallLogRepository             callLogRepository;
    private final GeofenceRepository            geofenceRepository;

    /** List devices with pagination + search */
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> listDevices(
            @RequestParam(defaultValue = "0")    int    page,
            @RequestParam(defaultValue = "20")   int    size,
            @RequestParam(required = false)      String search) {

        PageRequest pr = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Device> result = (search != null && !search.isBlank())
                ? deviceRepository.searchDevices(1L, search, pr)
                : deviceRepository.findByCustomerId(1L, pr);

        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "devices", result.getContent(),
                "total",   result.getTotalElements(),
                "pages",   result.getTotalPages()
        )));
    }

    /** Get single device */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Device>> getDevice(@PathVariable Long id) {
        return deviceRepository.findById(id)
                .map(d -> ResponseEntity.ok(ApiResponse.ok(d)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Update device */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Device>> updateDevice(
            @PathVariable Long id,
            @RequestBody  Map<String, Object> updates) {

        return deviceRepository.findById(id).map(device -> {
            if (updates.containsKey("description"))
                device.setDescription((String) updates.get("description"));
            if (updates.containsKey("configId") && updates.get("configId") != null)
                device.setConfigId(Long.parseLong(updates.get("configId").toString()));
            if (updates.containsKey("groupId") && updates.get("groupId") != null)
                device.setGroupId(Long.parseLong(updates.get("groupId").toString()));
            deviceRepository.save(device);
            return ResponseEntity.ok(ApiResponse.ok(device));
        }).orElse(ResponseEntity.notFound().build());
    }

    /** Delete device with all related data */
    @DeleteMapping("/{id}")
    @Transactional
    public ResponseEntity<ApiResponse<Void>> deleteDevice(@PathVariable Long id) {
        if (!deviceRepository.existsById(id)) return ResponseEntity.notFound().build();
        contactRepository.deleteByDeviceId(id);
        callLogRepository.deleteByDeviceId(id);
        pushMessageRepository.deleteByDeviceId(id);
        logRepository.deleteByDeviceId(id);
        locationRepository.deleteByDeviceId(id);
        geofenceRepository.deleteByDeviceId(id);
        deviceApplicationRepository.deleteByDeviceId(id);
        deviceRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** Get device logs */
    @GetMapping("/{id}/logs")
    public ResponseEntity<ApiResponse<List<DeviceLog>>> getLogs(
            @PathVariable Long id,
            @RequestParam(defaultValue = "0")   int page,
            @RequestParam(defaultValue = "200") int size) {
        Page<DeviceLog> logs = logRepository.findByDeviceIdOrderByLogTimeDesc(
                id, PageRequest.of(page, size));
        return ResponseEntity.ok(ApiResponse.ok(logs.getContent()));
    }

    /** Get device GPS location history */
    @GetMapping("/{id}/locations")
    public ResponseEntity<ApiResponse<List<DeviceLocation>>> getLocations(@PathVariable Long id) {
        List<DeviceLocation> locs = locationRepository.findByDeviceIdOrderByTsDesc(
                id, PageRequest.of(0, 200));
        return ResponseEntity.ok(ApiResponse.ok(locs));
    }

    /** Get installed apps on device (reported by APK) */
    @GetMapping("/{id}/apps")
    public ResponseEntity<ApiResponse<List<DeviceApplication>>> getInstalledApps(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(deviceApplicationRepository.findByDeviceId(id)));
    }

    /** Send push command to device */
    @PostMapping("/{id}/push")
    public ResponseEntity<ApiResponse<Void>> sendPush(
            @PathVariable Long id,
            @RequestBody  Map<String, String> body) {
        if (!deviceRepository.existsById(id)) return ResponseEntity.notFound().build();
        String type    = body.getOrDefault("messageType", "configUpdated");
        String payload = body.get("payload");
        pushMessageRepository.save(PushMessage.builder()
                .deviceId(id)
                .messageType(type)
                .payload(payload)
                .sent(false)
                .build());
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ─── Device Features: Show which features are working per device ────

    /** Get feature status for a single device */
    @GetMapping("/{id}/features")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getFeatures(@PathVariable Long id) {
        return deviceRepository.findById(id)
                .map(device -> ResponseEntity.ok(ApiResponse.ok(computeFeatures(device, id))))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Batch get features for multiple device IDs */
    @PostMapping("/features/batch")
    public ResponseEntity<ApiResponse<Map<String, Object>>> batchFeatures(
            @RequestBody Map<String, List<Long>> body) {
        List<Long> ids = body.getOrDefault("ids", List.of());
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        for (Long id : ids) {
            deviceRepository.findById(id).ifPresent(device ->
                result.put(String.valueOf(id), computeFeatures(device, id)));
        }
        return ResponseEntity.ok(ApiResponse.ok(Map.of("features", result)));
    }

    /** Compute feature status map for a device from existing data */
    private Map<String, Object> computeFeatures(Device device, Long deviceId) {
        Map<String, Object> f = new LinkedHashMap<>();

        // ── Hardware / Network ──
        f.put("gps",          device.getLat() != null && device.getLon() != null);
        f.put("imei",         device.getImei() != null && !device.getImei().isEmpty());
        f.put("networkInfo",  device.getPhone() != null || device.getIccid() != null);
        f.put("battery",      device.getBatteryLevel() != null);
        f.put("android",      device.getAndroidVersion() != null && !device.getAndroidVersion().isEmpty());

        // ── Mode / Status ──
        f.put("mdmMode",      Boolean.TRUE.equals(device.getMdmMode()));
        f.put("kioskMode",    Boolean.TRUE.equals(device.getKioskMode()));
        f.put("defaultLauncher", Boolean.TRUE.equals(device.getDefaultLauncher()));
        f.put("configAssigned",  device.getConfigId() != null);

        // ── Data sync ──
        f.put("contacts",     contactRepository.countByDeviceId(deviceId) > 0);
        f.put("callLogs",     callLogRepository.countByDeviceId(deviceId) > 0);
        f.put("installedApps", !deviceApplicationRepository.findByDeviceId(deviceId).isEmpty());
        f.put("locationHistory", locationRepository.countByDeviceId(deviceId) > 0);

        // ── Online / Last sync ──
        f.put("online",       "ONLINE".equals(device.getStatus()));
        f.put("lastSync",     device.getLastSync() != null);
        f.put("pushCapable",  pushMessageRepository.countByDeviceId(deviceId) > 0);

        return f;
    }

    /** Dashboard stats */
    @GetMapping("/stats")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStats() {
        long total   = deviceRepository.countByCustomerId(1L);
        long online  = deviceRepository.countByCustomerIdAndStatus(1L, "ONLINE");
        long offline = deviceRepository.countByCustomerIdAndStatus(1L, "OFFLINE");
        long pending = deviceRepository.countByCustomerIdAndStatus(1L, "PENDING");
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "total",   total,
                "online",  online,
                "offline", offline,
                "pending", pending
        )));
    }
}
