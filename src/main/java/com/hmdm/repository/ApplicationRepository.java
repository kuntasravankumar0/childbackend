package com.hmdm.repository;

import com.hmdm.entity.Application;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApplicationRepository extends JpaRepository<Application, Long> {
    List<Application> findByCustomerId(Long customerId);
    Optional<Application> findByCustomerIdAndPkg(Long customerId, String pkg);
    List<Application> findByCustomerIdAndType(Long customerId, String type);
}
