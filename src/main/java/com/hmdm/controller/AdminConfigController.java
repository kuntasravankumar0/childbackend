package com.hmdm.controller;

import com.hmdm.dto.ApiResponse;
import com.hmdm.entity.Application;
import com.hmdm.entity.ApplicationSetting;
import com.hmdm.entity.Configuration;
import com.hmdm.entity.ConfigurationApplication;
import com.hmdm.repository.ApplicationRepository;
import com.hmdm.repository.ConfigurationRepository;
import com.hmdm.repository.DeviceRepository;
import com.hmdm.repository.PushMessageRepository;
import com.hmdm.entity.PushMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/configurations")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class AdminConfigController {

    private final ConfigurationRepository   configRepository;
    private final ApplicationRepository     appRepository;
    private final DeviceRepository          deviceRepository;
    private final PushMessageRepository     pushMessageRepository;

    /** List all configurations */
    @GetMapping
    public ResponseEntity<ApiResponse<List<Configuration>>> list() {
        return ResponseEntity.ok(ApiResponse.ok(configRepository.findByCustomerId(1L)));
    }

    /** Get configuration detail */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<Configuration>> get(@PathVariable Long id) {
        return configRepository.findById(id)
                .map(c -> ResponseEntity.ok(ApiResponse.ok(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    /** Create new configuration */
    @PostMapping
    public ResponseEntity<ApiResponse<Configuration>> create(@RequestBody Configuration config) {
        config.setId(null);
        config.setCustomerId(1L);
        return ResponseEntity.ok(ApiResponse.ok(configRepository.save(config)));
    }

    /** Update configuration */
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Configuration>> update(
            @PathVariable Long id, @RequestBody Configuration config) {
        if (!configRepository.existsById(id)) return ResponseEntity.notFound().build();
        config.setId(id);
        config.setCustomerId(1L);
        Configuration saved = configRepository.save(config);
        // Push configUpdated to all devices using this config
        pushConfigUpdate(id);
        return ResponseEntity.ok(ApiResponse.ok(saved));
    }

    /** Delete configuration */
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        if (!configRepository.existsById(id)) return ResponseEntity.notFound().build();
        configRepository.deleteById(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** Add application to configuration */
    @PostMapping("/{id}/applications")
    public ResponseEntity<ApiResponse<Void>> addApplication(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Configuration> cfgOpt = configRepository.findById(id);
        if (cfgOpt.isEmpty()) return ResponseEntity.notFound().build();
        Configuration cfg = cfgOpt.get();

        Long appId = Long.parseLong(body.get("applicationId").toString());
        Optional<Application> appOpt = appRepository.findById(appId);
        if (appOpt.isEmpty()) return ResponseEntity.badRequest()
                .body(ApiResponse.error("Application not found"));

        ConfigurationApplication ca = ConfigurationApplication.builder()
                .configuration(cfg)
                .application(appOpt.get())
                .showIcon(getBool(body, "showIcon", true))
                .remove(getBool(body, "remove", false))
                .runAfterInstall(getBool(body, "runAfterInstall", false))
                .runAtBoot(getBool(body, "runAtBoot", false))
                .skipVersion(getBool(body, "skipVersion", false))
                .useKiosk(getBool(body, "useKiosk", false))
                .bottom(getBool(body, "bottom", false))
                .longTap(getBool(body, "longTap", false))
                .build();
        cfg.getApplications().add(ca);
        configRepository.save(cfg);
        pushConfigUpdate(id);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    /** Add application setting to configuration */
    @PostMapping("/{id}/settings")
    public ResponseEntity<ApiResponse<Void>> addSetting(
            @PathVariable Long id, @RequestBody Map<String, Object> body) {
        Optional<Configuration> cfgOpt = configRepository.findById(id);
        if (cfgOpt.isEmpty()) return ResponseEntity.notFound().build();
        Configuration cfg = cfgOpt.get();

        ApplicationSetting setting = ApplicationSetting.builder()
                .configuration(cfg)
                .packageId((String) body.get("packageId"))
                .name((String) body.get("name"))
                .value(body.get("value") != null ? body.get("value").toString() : null)
                .type(1)
                .readOnly(getBool(body, "readOnly", false))
                .isVariable(getBool(body, "isVariable", false))
                .build();
        cfg.getApplicationSettings().add(setting);
        configRepository.save(cfg);
        return ResponseEntity.ok(ApiResponse.ok());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private void pushConfigUpdate(Long configId) {
        deviceRepository.findByConfigId(configId).forEach(device ->
                pushMessageRepository.save(PushMessage.builder()
                        .deviceId(device.getId())
                        .messageType("configUpdated")
                        .sent(false)
                        .build()));
    }

    private Boolean getBool(Map<String, Object> m, String key, boolean defaultVal) {
        Object v = m.get(key);
        if (v == null) return defaultVal;
        return Boolean.parseBoolean(v.toString());
    }
}
