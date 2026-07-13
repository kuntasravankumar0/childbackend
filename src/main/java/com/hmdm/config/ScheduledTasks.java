package com.hmdm.config;

import com.hmdm.entity.Device;
import com.hmdm.repository.DeviceRepository;
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

    private final DeviceRepository deviceRepository;

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
}
