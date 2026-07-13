package com.hmdm.repository;

import com.hmdm.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, Long> {
    Optional<Customer> findByPrefix(String prefix);
    Optional<Customer> findByName(String name);
}
