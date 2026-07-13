package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.Group;
import com.hmdm.repository.GroupRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminGroupController {

    private final GroupRepository groupRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Group>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(groupRepository.findByCustomerId(1L)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Group>> create(@RequestBody Group group) {
        group.setId(null);
        group.setCustomerId(1L);
        return ResponseEntity.ok(ApiResponse.ok(groupRepository.save(group)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Group>> update(@PathVariable Long id, @RequestBody Group group) {
        if (!groupRepository.existsById(id)) return ResponseEntity.notFound().build();
        group.setId(id);
        group.setCustomerId(1L);
        return ResponseEntity.ok(ApiResponse.ok(groupRepository.save(group)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!groupRepository.existsById(id)) return ResponseEntity.notFound().build();
        groupRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }
}
