package com.hmdm.repository;

import com.hmdm.entity.DeviceLocation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceLocationRepository extends JpaRepository<DeviceLocation, Long> {
    List<DeviceLocation> findByDeviceIdOrderByTsDesc(Long deviceId);
    List<DeviceLocation> findByDeviceIdOrderByTsDesc(Long deviceId, Pageable pageable);
    Optional<DeviceLocation> findTopByDeviceIdOrderByTsDesc(Long deviceId);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
