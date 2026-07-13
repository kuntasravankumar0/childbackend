package com.hmdm.repository;

import com.hmdm.entity.PushMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PushMessageRepository extends JpaRepository<PushMessage, Long> {
    List<PushMessage> findByDeviceIdAndSentFalseOrderByCreatedAtAsc(Long deviceId);
    List<PushMessage> findByDeviceIdOrderByCreatedAtDesc(Long deviceId);
}
