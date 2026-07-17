package com.hmdm.repository;

import com.hmdm.entity.PushMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface PushMessageRepository extends JpaRepository<PushMessage, Long> {
    List<PushMessage> findByDeviceIdAndSentFalseOrderByCreatedAtAsc(Long deviceId);
    List<PushMessage> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);
    long countByDeviceId(Long deviceId);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
