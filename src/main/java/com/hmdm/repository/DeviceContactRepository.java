package com.hmdm.repository;

import com.hmdm.entity.DeviceContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceContactRepository extends JpaRepository<DeviceContact, Long> {
    List<DeviceContact> findByDeviceId(Long deviceId);
    Optional<DeviceContact> findByDeviceIdAndRawContactId(Long deviceId, String rawContactId);
    long countByDeviceId(Long deviceId);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
