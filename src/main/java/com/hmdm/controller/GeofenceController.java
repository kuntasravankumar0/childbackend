package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.DeviceLocation;
import com.hmdm.entity.Geofence;
import com.hmdm.repository.DeviceLocationRepository;
import com.hmdm.repository.DeviceRepository;
import com.hmdm.repository.GeofenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geofence management — admin can define geographic boundaries for devices
 * and receive alerts when devices enter or exit.
 */
@RestController
@RequestMapping("/api/devices/{deviceId}/geofences")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
@Slf4j
public class GeofenceController {

    private final GeofenceRepository         geofenceRepository;
    private final DeviceRepository           deviceRepository;
    private final DeviceLocationRepository   locationRepository;

    /** List all geofences for a device */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Geofence>>> listGeofences(@PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            return ResponseEntity.notFound().build();
        }
        List<Geofence> fences = geofenceRepository.findByDeviceId(deviceId);
        return ResponseEntity.ok(ApiResponse.ok(fences));
    }

    /** Get a single geofence */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Geofence>> getGeofence(
            @PathVariable Long deviceId,
            @PathVariable Long id) {
        return geofenceRepository.findById(id)
                .filter(f -> f.getDeviceId().equals(deviceId))
                .map(f -> ResponseEntity.ok(ApiResponse.ok(f)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create a new geofence */
    @PostMapping
    public ResponseEntity<ApiResponse<Geofence>> createGeofence(
            @PathVariable Long deviceId,
            @RequestBody Geofence fence) {
        if (!deviceRepository.existsById(deviceId)) {
            return ResponseEntity.notFound().build();
        }

        fence.setDeviceId(deviceId);
        fence.setCreatedAt(LocalDateTime.now());
        fence.setUpdatedAt(LocalDateTime.now());

        // Check current device location to determine if inside
        fence.setIsInside(checkIfInside(deviceId, fence));

        Geofence saved = geofenceRepository.save(fence);
        log.info("Geofence created: {} for device {}", saved.getName(), deviceId);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /** Update a geofence */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Geofence>> updateGeofence(
            @PathVariable Long deviceId,
            @PathVariable Long id,
            @RequestBody Geofence updates) {
        return geofenceRepository.findById(id)
                .filter(f -> f.getDeviceId().equals(deviceId))
                .map(fence -> {
                    if (updates.getName() != null) fence.setName(updates.getName());
                    if (updates.getDescription() != null) fence.setDescription(updates.getDescription());
                    if (updates.getLatitude() != null) fence.setLatitude(updates.getLatitude());
                    if (updates.getLongitude() != null) fence.setLongitude(updates.getLongitude());
                    if (updates.getRadius() != null) fence.setRadius(updates.getRadius());
                    if (updates.getAlertType() != null) fence.setAlertType(updates.getAlertType());
                    if (updates.getActive() != null) fence.setActive(updates.getActive());

                    // Re-check device position
                    fence.setIsInside(checkIfInside(deviceId, fence));
                    fence.setUpdatedAt(LocalDateTime.now());

                    Geofence saved = geofenceRepository.save(fence);
                    log.info("Geofence updated: {} for device {}", saved.getName(), deviceId);
                    return ResponseEntity.ok(ApiResponse.ok(saved));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Delete a geofence */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteGeofence(
            @PathVariable Long deviceId,
            @PathVariable Long id) {
        return geofenceRepository.findById(id)
                .filter(f -> f.getDeviceId().equals(deviceId))
                .map(fence -> {
                    geofenceRepository.delete(fence);
                    return ResponseEntity.ok(ApiResponse.<Void>ok());
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Check current geofence status for all active geofences */
    @PostMapping("/check")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> checkGeofences(
            @PathVariable Long deviceId) {
        if (!deviceRepository.existsById(deviceId)) {
            return ResponseEntity.notFound().build();
        }

        List<Geofence> fences = geofenceRepository.findByDeviceIdAndActiveTrue(deviceId);
        List<Map<String, Object>> results = new ArrayList<>();

        for (Geofence fence : fences) {
            boolean wasInside = fence.getIsInside() != null && fence.getIsInside();
            boolean nowInside = checkIfInside(deviceId, fence);

            if (wasInside != nowInside) {
                fence.setIsInside(nowInside);
                geofenceRepository.save(fence);

                String event = nowInside ? "ENTERED" : "EXITED";
                log.info("Geofence alert: device {} {} geofence '{}'",
                        deviceId, event, fence.getName());

                Map<String, Object> alert = new LinkedHashMap<>();
                alert.put("geofenceId", fence.getId());
                alert.put("geofenceName", fence.getName());
                alert.put("event", event);
                alert.put("timestamp", System.currentTimeMillis());
                results.add(alert);
            }
        }

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    /**
     * Check if device's latest location is inside the geofence circle.
     * Uses Haversine formula.
     */
    private boolean checkIfInside(Long deviceId, Geofence fence) {
        return locationRepository.findTopByDeviceIdOrderByTsDesc(deviceId)
                .map(loc -> {
                    double distance = haversine(
                            loc.getLat(), loc.getLon(),
                            fence.getLatitude(), fence.getLongitude()
                    );
                    return distance <= fence.getRadius();
                })
                .orElse(false);
    }

    /**
     * Haversine distance in meters between two lat/lon points.
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
