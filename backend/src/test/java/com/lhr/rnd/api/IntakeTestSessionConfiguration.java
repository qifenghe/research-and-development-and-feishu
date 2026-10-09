package com.lhr.rnd.api;

import com.lhr.rnd.service.SessionPrincipal;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;
import java.time.Instant;

/** Explicit sessions for legacy business API fixtures only; never part of production authentication. */
@TestConfiguration
public class IntakeTestSessionConfiguration {
    @Bean
    OncePerRequestFilter intakeFixtureSession() {
        return new OncePerRequestFilter() {
            @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                                     jakarta.servlet.http.HttpServletResponse response,
                                                     jakarta.servlet.FilterChain chain) throws java.io.IOException,jakarta.servlet.ServletException {
                var uri=request.getRequestURI();
                if(request.getAttribute("sessionPrincipal")==null && request.getMethod().equals("POST") &&
                        (uri.equals("/api/v1/sample-requests") || uri.matches("/api/v1/sample-requests/[^/]+/approve"))) {
                    boolean review=uri.endsWith("/approve");
                    request.setAttribute("sessionPrincipal",new SessionPrincipal("FIXTURE-INTAKE", "fixture", review?"研发总监":"研发内勤",null,
                            review?"RND_DIRECTOR":"RND_ASSISTANT",Instant.now().plusSeconds(600)));
                }
                chain.doFilter(request,response);
            }
        };
    }
}
