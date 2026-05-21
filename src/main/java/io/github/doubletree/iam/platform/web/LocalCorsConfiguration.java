package io.github.doubletree.iam.platform.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class LocalCorsConfiguration implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .exposedHeaders("WWW-Authenticate")
                .allowCredentials(false);
        registry.addMapping("/oauth2/**")
                .allowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
                .allowedMethods("GET", "POST", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(false);
    }
}
