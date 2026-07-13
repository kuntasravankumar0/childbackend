package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.Customer;
import com.hmdm.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminCustomerController {

    private final CustomerRepository customerRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Customer>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(customerRepository.findAll()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Customer>> create(@RequestBody Customer customer) {
        customer.setId(null);
        return ResponseEntity.ok(ApiResponse.ok(customerRepository.save(customer)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Customer>> update(@PathVariable Long id,
                                                        @RequestBody Customer customer) {
        if (!customerRepository.existsById(id)) return ResponseEntity.notFound().build();
        customer.setId(id);
        return ResponseEntity.ok(ApiResponse.ok(customerRepository.save(customer)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!customerRepository.existsById(id)) return ResponseEntity.notFound().build();
        customerRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
