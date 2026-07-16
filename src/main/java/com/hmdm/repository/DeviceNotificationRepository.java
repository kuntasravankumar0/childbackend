package com.hmdm.repository;

import com.hmdm.entity.DeviceNotification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface DeviceNotificationRepository extends JpaRepository<DeviceNotification, Long> {
    List<DeviceNotification> findByDeviceIdOrderByReceivedAtDesc(Long deviceId);
    Page<DeviceNotification> findByDeviceIdOrderByReceivedAtDesc(Long deviceId, Pageable pageable);
    long countByDeviceId(Long deviceId);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
