package com.blockstore.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;

/**
 * Robust CORS Filter configuration.
 * Using a Filter bean is generally more reliable than WebMvcConfigurer
 * for handling pre-flight (OPTIONS) requests and cross-origin security.
 */
@Configuration
public class WebConfig {

    @Bean
    public CorsFilter corsFilter() {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        CorsConfiguration config = new CorsConfiguration();

        // Allow the React frontend (use patterns when credentials are enabled)
        config.setAllowedOriginPatterns(Arrays.asList("http://localhost:3000", "http://127.0.0.1:3000"));

        // Standard methods needed
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS", "HEAD"));

        // Allow all headers
        config.setAllowedHeaders(Arrays.asList("*"));

        // Expose custom headers so the React app can read filename and blockchain tx
        // info
        config.setExposedHeaders(Arrays.asList(
                "X-Served-From",
                "X-Tx-Hash",
                "X-Block-Number",
                "Content-Disposition"));

        config.setAllowCredentials(true); // Required for session cookies
        config.setMaxAge(3600L); // Cache pre-flight response for 1 hour

        source.registerCorsConfiguration("/**", config);

        System.out.println("Global CORS Filter initialized (credentials enabled)");
        return new CorsFilter(source);
    }
}
