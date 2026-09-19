package com.draft.footy.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS policy for the API, in one place.
 *
 * <p>The controllers previously carried a bare {@code @CrossOrigin}, which allows <em>any</em> origin.
 * That is harmless while the API is anonymous and read-mostly, but it stops being harmless the moment
 * anything resembling auth or a user-owned run is added — at that point any page on the internet could
 * drive the API from a visitor's browser. Easier to scope it now than to remember later.
 *
 * <p>In development the Vite dev server proxies {@code /api} through itself, so the browser sees a
 * same-origin request and CORS never applies. This exists for direct calls to :8080 and for whatever
 * origin the app is eventually served from — set {@code footy.cors.allowed-origins} to that.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final String[] allowedOrigins;

    public WebConfig(@Value("${footy.cors.allowed-origins:http://localhost:5173}") String origins) {
        this.allowedOrigins = origins.split("\\s*,\\s*");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "POST")
            .allowCredentials(false);
    }
}
