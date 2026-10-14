package com.enviro.assessment.junior.alexmotaung.config;

import java.time.Clock;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] origins;

    public CorsConfig(@Value("${app.frontend-origins}") String[] origins) {
        this.origins = origins;
    }

    @Bean
    public Clock applicationClock(@Value("${app.time-zone}") String zone) {
        return Clock.system(ZoneId.of(zone));
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(origins)
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Content-Type", "Authorization", "X-XSRF-TOKEN")
                .allowCredentials(true)
                .exposedHeaders("Content-Disposition")
                .maxAge(3600);
    }
}
