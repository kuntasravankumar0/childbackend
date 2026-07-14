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

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
