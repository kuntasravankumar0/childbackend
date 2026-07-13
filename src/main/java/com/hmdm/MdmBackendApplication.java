package com.hmdm;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Headwind MDM — Spring Boot Backend
 *
 * Default admin credentials: Sravan / Sravan@123
 * Database: Aiven PostgreSQL (pg-1a5b5a-kunta-a987.d.aivencloud.com:25430)
 *
 * Android APK endpoints (no auth):
 *   POST /{project}/rest/public/sync/configuration/{deviceId}
 *   POST /{project}/rest/public/sync/info
 *
 * Admin web app endpoints (JWT required):
 *   POST /api/auth/login
 *   GET  /api/devices
 *   GET  /api/dashboard/summary
 */
@SpringBootApplication
@EnableScheduling
public class MdmBackendApplication {

    public static void main(String[] args) {
        // Fix JVM DNS caching — use OS DNS (0 = no caching, -1 = forever)
        java.security.Security.setProperty("networkaddress.cache.ttl", "0");
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0");

        SpringApplication.run(MdmBackendApplication.class, args);
    }
}
