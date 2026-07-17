package com.hmdm.repository;

import com.hmdm.entity.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, Long> {
    List<Geofence> findByDeviceId(Long deviceId);
    List<Geofence> findByDeviceIdAndActiveTrue(Long deviceId);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
