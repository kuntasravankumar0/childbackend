package com.hmdm.repository;

import com.hmdm.entity.Geofence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GeofenceRepository extends JpaRepository<Geofence, Long> {
    List<Geofence> findByDeviceId(Long deviceId);
    List<Geofence> findByDeviceIdAndActiveTrue(Long deviceId);
    void deleteByDeviceId(Long deviceId);
}
