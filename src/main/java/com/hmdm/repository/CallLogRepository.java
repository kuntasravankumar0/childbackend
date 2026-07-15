package com.hmdm.repository;

import com.hmdm.entity.CallLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CallLogRepository extends JpaRepository<CallLog, Long> {
    List<CallLog> findByDeviceIdOrderByCallDateDesc(Long deviceId);
    long countByDeviceId(Long deviceId);

    // Dedup check: find by exact phoneNumber, callDate, callType, and contactName for a device
    boolean existsByDeviceIdAndPhoneNumberAndCallDateAndCallTypeAndContactName(
            Long deviceId, String phoneNumber, Long callDate, String callType, String contactName);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
