package com.hmdm.repository;

import com.hmdm.entity.CallLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
public interface CallLogRepository extends JpaRepository<CallLog, Long> {

    // Full list (legacy, won't be used for large datasets)
    List<CallLog> findByDeviceIdOrderByCallDateDesc(Long deviceId);

    // PAGINATED queries — efficient for 50K+ call logs
    Page<CallLog> findByDeviceId(Long deviceId, Pageable pageable);

    // Search by phone number + filter by call type
    @Query("SELECT c FROM CallLog c WHERE c.deviceId = :deviceId " +
           "AND (:search IS NULL OR :search = '' OR c.phoneNumber LIKE %:search% OR c.contactName LIKE %:search%) " +
           "AND (:type IS NULL OR :type = 'ALL' OR c.callType = :type) " +
           "AND (:dateFrom IS NULL OR :dateFrom = 0 OR c.callDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR :dateTo = 0 OR c.callDate <= :dateTo) " +
           "ORDER BY c.callDate DESC")
    Page<CallLog> searchByDeviceId(
            @Param("deviceId") Long deviceId,
            @Param("search") String search,
            @Param("type") String type,
            @Param("dateFrom") Long dateFrom,
            @Param("dateTo") Long dateTo,
            Pageable pageable);

    // Count with filters (for pagination metadata)
    @Query("SELECT COUNT(c) FROM CallLog c WHERE c.deviceId = :deviceId " +
           "AND (:search IS NULL OR :search = '' OR c.phoneNumber LIKE %:search% OR c.contactName LIKE %:search%) " +
           "AND (:type IS NULL OR :type = 'ALL' OR c.callType = :type) " +
           "AND (:dateFrom IS NULL OR :dateFrom = 0 OR c.callDate >= :dateFrom) " +
           "AND (:dateTo IS NULL OR :dateTo = 0 OR c.callDate <= :dateTo)")
    long countByDeviceIdWithFilters(
            @Param("deviceId") Long deviceId,
            @Param("search") String search,
            @Param("type") String type,
            @Param("dateFrom") Long dateFrom,
            @Param("dateTo") Long dateTo);

    long countByDeviceId(Long deviceId);

    // Dedup check
    boolean existsByDeviceIdAndPhoneNumberAndCallDateAndCallTypeAndContactName(
            Long deviceId, String phoneNumber, Long callDate, String callType, String contactName);

    @Modifying
    @Transactional
    void deleteByDeviceId(Long deviceId);
}
