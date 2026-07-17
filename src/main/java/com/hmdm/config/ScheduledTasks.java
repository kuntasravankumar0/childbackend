package com.hmdm.config;

import com.hmdm.entity.Device;
import com.hmdm.entity.DeviceLocation;
import com.hmdm.entity.Geofence;
import com.hmdm.repository.DeviceLocationRepository;
import com.hmdm.repository.DeviceRepository;
import com.hmdm.repository.GeofenceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled background tasks for the MDM backend.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasks {

    private final DeviceRepository           deviceRepository;
    private final DeviceLocationRepository   locationRepository;
    private final GeofenceRepository         geofenceRepository;

    /**
     * Every 5 minutes — mark devices OFFLINE if last sync > 10 minutes ago.
     */
    @Scheduled(fixedDelay = 300_000)
    public void markOfflineDevices() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(10);
        List<Device> devices = deviceRepository.findAll();
        int marked = 0;
        for (Device d : devices) {
            if ("ONLINE".equals(d.getStatus())
                    && d.getLastSync() != null
                    && d.getLastSync().isBefore(cutoff)) {
                d.setStatus("OFFLINE");
                deviceRepository.save(d);
                marked++;
            }
        }
        if (marked > 0) {
            log.info("ScheduledTasks: marked {} device(s) as offline", marked);
        }
    }

    /**
     * Every 2 minutes — check all active geofences for entry/exit events.
     * Logs a warning when a device enters or exits a geofence.
     */
    @Scheduled(fixedDelay = 120_000)
    public void checkGeofences() {
        List<Geofence> activeFences = geofenceRepository.findAll().stream()
                .filter(Geofence::getActive)
                .toList();

        for (Geofence fence : activeFences) {
            boolean wasInside = fence.getIsInside() != null && fence.getIsInside();
            boolean nowInside = locationRepository.findTopByDeviceIdOrderByTsDesc(fence.getDeviceId())
                    .map(loc -> haversine(loc.getLat(), loc.getLon(),
                            fence.getLatitude(), fence.getLongitude()) <= fence.getRadius())
                    .orElse(false);

            if (wasInside != nowInside) {
                fence.setIsInside(nowInside);
                geofenceRepository.save(fence);

                String event = nowInside ? "ENTERED" : "EXITED";
                log.warn("GEOFENCE ALERT: device {} {} geofence '{}' (ID: {})",
                        fence.getDeviceId(), event, fence.getName(), fence.getId());
            }
        }
    }

    /**
     * Haversine distance in meters between two lat/lon points.
     */
    private double haversine(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371000;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
