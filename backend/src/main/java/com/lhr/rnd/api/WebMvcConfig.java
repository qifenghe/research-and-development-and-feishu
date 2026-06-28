package com.lhr.rnd.api;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
    private final SessionAuthenticationInterceptor sessionAuthenticationInterceptor;
    private final CorsProperties corsProperties;

    public WebMvcConfig(
            SessionAuthenticationInterceptor sessionAuthenticationInterceptor,
            CorsProperties corsProperties
    ) {
        this.sessionAuthenticationInterceptor = sessionAuthenticationInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(sessionAuthenticationInterceptor)
                .addPathPatterns("/api/v1/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns(corsProperties.getAllowedOriginPatterns().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(corsProperties.isAllowCredentials())
                .maxAge(3600);
    }
}
