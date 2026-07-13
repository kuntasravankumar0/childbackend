package com.hmdm.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${mdm.files-dir}")
    private String filesDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Only serve /files/** as static — everything else goes to controllers
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + filesDir + "/");
        // Explicitly do NOT add any catch-all resource handler
        // This ensures /{project}/rest/** routes go to DeviceSyncController
    }
}
