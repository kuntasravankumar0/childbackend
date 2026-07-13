package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.Application;
import com.hmdm.repository.ApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/applications")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminApplicationController {

    private final ApplicationRepository applicationRepository;

    @Value("${mdm.files-dir}")
    private String filesDir;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Application>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(applicationRepository.findByCustomerId(1L)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Application>> get(@PathVariable Long id) {
        return applicationRepository.findById(id)
                .map(a -> ResponseEntity.ok(ApiResponse.ok(a)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Application>> create(@RequestBody Application app) {
        app.setId(null);
        app.setCustomerId(1L);
        return ResponseEntity.ok(ApiResponse.ok(applicationRepository.save(app)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Application>> update(@PathVariable Long id,
                                                            @RequestBody Application app) {
        if (!applicationRepository.existsById(id)) return ResponseEntity.notFound().build();
        app.setId(id);
        app.setCustomerId(1L);
        return ResponseEntity.ok(ApiResponse.ok(applicationRepository.save(app)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!applicationRepository.existsById(id)) return ResponseEntity.notFound().build();
        applicationRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** Upload APK file — returns relative URL */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<String>> uploadApk(
            @RequestParam("file") MultipartFile file) {
        try {
            Path dir = Paths.get(filesDir, "apk");
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + "_" + sanitize(file.getOriginalFilename());
            Files.copy(file.getInputStream(), dir.resolve(filename));
            String url = "/files/apk/" + filename;
            return ResponseEntity.ok(ApiResponse.ok(url));
        } catch (IOException e) {
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.error("Upload failed: " + e.getMessage()));
        }
    }

    private String sanitize(String name) {
        if (name == null) return "file.apk";
        return name.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }
}
