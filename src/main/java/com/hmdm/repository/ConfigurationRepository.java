package com.hmdm.repository;

import com.hmdm.entity.Configuration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationRepository extends JpaRepository<Configuration, Long> {
    List<Configuration> findByCustomerId(Long customerId);
    Optional<Configuration> findByCustomerIdAndName(Long customerId, String name);
}
