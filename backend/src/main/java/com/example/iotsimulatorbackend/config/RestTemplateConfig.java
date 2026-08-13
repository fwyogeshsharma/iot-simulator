package com.example.iotsimulatorbackend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Every outbound call to Supabase goes through this one client.
     *
     * builder.build() on its own leaves both timeouts at "wait forever", so a single
     * stalled PostgREST connection pinned its Tomcat worker indefinitely. Behind the
     * production proxy that surfaced as an intermittent 504 on /api/devices and
     * /api/disease-profiles, which resolved only when the upstream call finally
     * answered. Bounded waits turn that into a fast, visible failure instead.
     *
     * The read timeout is set below the proxy's own limit on purpose. The production
     * frontend is on Netlify and netlify.toml rewrites /api/* to this backend; that
     * proxy gives up after 26 seconds and returns 504. A timeout above that line would
     * never be reached - the caller would already have been sent a gateway error. A
     * single stalled call (45s has been observed against the live origin) must therefore
     * fail well inside 26s for the request as a whole to have any chance of completing.
     *
     * Bulk paths are unaffected in practice: a 250-row batch inserts in about a second,
     * and historical generation retries three times with backoff over an idempotent
     * insert, so a timeout there costs a retry rather than data.
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(5))
                .setReadTimeout(Duration.ofSeconds(10))
                .build();
    }
}
