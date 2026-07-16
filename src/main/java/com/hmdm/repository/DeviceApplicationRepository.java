package com.hmdm.repository;

import com.hmdm.entity.DeviceApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceApplicationRepository extends JpaRepository<DeviceApplication, Long> {
    List<DeviceApplication> findByDeviceId(Long deviceId);
    Optional<DeviceApplication> findByDeviceIdAndPkg(Long deviceId, String pkg);
    void deleteByDeviceId(Long deviceId);

    /**
     * Upsert: insert a device application or update if (device_id, pkg) already exists.
     * Prevents duplicate key violations from race conditions during parallel syncs.
     */
    @Modifying
    @Transactional
    @Query(value = "INSERT INTO device_applications (device_id, pkg, name, version, version_code, installed, updated_at) " +
           "VALUES (:deviceId, :pkg, :name, :version, :versionCode, :installed, NOW()) " +
           "ON CONFLICT (device_id, pkg) DO UPDATE SET " +
           "name = COALESCE(NULLIF(:name, ''), EXCLUDED.name, device_applications.name), " +
           "version = COALESCE(NULLIF(:version, ''), EXCLUDED.version, device_applications.version), " +
           "version_code = COALESCE(:versionCode, EXCLUDED.version_code, device_applications.version_code), " +
           "installed = :installed, " +
           "updated_at = NOW()", nativeQuery = true)
    void upsert(@Param("deviceId") Long deviceId,
                @Param("pkg") String pkg,
                @Param("name") String name,
                @Param("version") String version,
                @Param("versionCode") Integer versionCode,
                @Param("installed") Boolean installed);
}
