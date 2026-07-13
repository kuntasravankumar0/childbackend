package com.hmdm.repository;

import com.hmdm.entity.DeviceLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DeviceLogRepository extends JpaRepository<DeviceLog, Long> {
    Page<DeviceLog> findByDeviceIdOrderByLogTimeDesc(Long deviceId, Pageable pageable);
    List<DeviceLog> findByDeviceIdOrderByLogTimeDesc(Long deviceId);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DeviceLog l WHERE l.createdAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
