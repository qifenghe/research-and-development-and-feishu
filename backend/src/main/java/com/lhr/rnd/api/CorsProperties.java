package com.lhr.rnd.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "rnd.cors")
public class CorsProperties {
    private List<String> allowedOriginPatterns = new ArrayList<>(List.of(
            "http://127.0.0.1:5173",
            "http://127.0.0.1:5174",
            "http://127.0.0.1:8787",
            "http://localhost:5173",
            "http://localhost:5174",
            "http://localhost:8787",
            "http://192.168.*:*",
            "http://10.*:*",
            "http://172.16.*:*"
    ));
    private boolean allowCredentials;

    public List<String> getAllowedOriginPatterns() {
        return allowedOriginPatterns;
    }

    public void setAllowedOriginPatterns(List<String> allowedOriginPatterns) {
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    public boolean isAllowCredentials() {
        return allowCredentials;
    }

    public void setAllowCredentials(boolean allowCredentials) {
        this.allowCredentials = allowCredentials;
    }
}
