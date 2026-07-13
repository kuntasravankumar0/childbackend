package com.hmdm.repository;

import com.hmdm.entity.DeviceApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceApplicationRepository extends JpaRepository<DeviceApplication, Long> {
    List<DeviceApplication> findByDeviceId(Long deviceId);
    Optional<DeviceApplication> findByDeviceIdAndPkg(Long deviceId, String pkg);
    void deleteByDeviceId(Long deviceId);
}
