package com.hmdm.repository;

import com.hmdm.entity.Device;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByNumber(String number);
    Optional<Device> findByCustomerIdAndNumber(Long customerId, String number);
    Page<Device>     findByCustomerId(Long customerId, Pageable pageable);
    List<Device>     findByConfigId(Long configId);
    List<Device>     findByGroupId(Long groupId);
    long countByCustomerId(Long customerId);

    @Query("SELECT d FROM Device d WHERE d.customerId = :customerId AND " +
           "(:search IS NULL OR LOWER(d.number) LIKE LOWER(CONCAT('%',:search,'%')) OR " +
           "LOWER(COALESCE(d.model,'')) LIKE LOWER(CONCAT('%',:search,'%')) OR " +
           "LOWER(COALESCE(d.imei,''))  LIKE LOWER(CONCAT('%',:search,'%')) OR " +
           "LOWER(COALESCE(d.description,'')) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Device> searchDevices(@Param("customerId") Long customerId,
                               @Param("search")     String search,
                               Pageable pageable);

    @Query("SELECT COUNT(d) FROM Device d WHERE d.customerId = :customerId AND d.status = :status")
    long countByCustomerIdAndStatus(@Param("customerId") Long customerId,
                                    @Param("status")     String status);
}
