package com.example.iotsimulatorbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Delete everything the simulator has written for one person, so a demo can start from a
 * known-empty state rather than from whatever the last run left behind.
 *
 * Deliberately limited to the tables the simulator itself populates: device readings, the
 * rehab progress module and the IRQ recovery module. Medication logs, ILQ scores, disease
 * risk flags and the shared alerts table are left alone - they are written by other parts
 * of the platform, and a "reset my demo" button should not quietly reach into them.
 *
 * Order matters only for rehab_enrollments, which is removed last so that anything reading
 * mid-delete sees a person with no programme rather than a programme with no data.
 */
@Service
public class DataCleanupService {

    private static final Logger logger = LoggerFactory.getLogger(DataCleanupService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Value("${supabase.device-data-url}")
    private String deviceDataUrl;

    @Value("${supabase.rehab-enrollments-url}")
    private String rehabEnrollmentsUrl;

    @Value("${supabase.rehab-manual-checkins-url}")
    private String rehabManualCheckinsUrl;

    @Value("${supabase.rehab-domain-scores-url}")
    private String rehabDomainScoresUrl;

    @Value("${supabase.rehab-progress-history-url}")
    private String rehabProgressHistoryUrl;

    @Value("${supabase.rehab-analysis-runs-url}")
    private String rehabAnalysisRunsUrl;

    @Value("${supabase.recovery-checkins-url}")
    private String recoveryCheckinsUrl;

    @Value("${supabase.recovery-sobriety-events-url}")
    private String recoverySobrietyEventsUrl;

    @Value("${supabase.irq-scores-url}")
    private String irqScoresUrl;

    @Value("${supabase.irq-alerts-url}")
    private String irqAlertsUrl;

    public Map<String, Object> cleanup(String elderlyPersonId) {
        if (elderlyPersonId == null || elderlyPersonId.isEmpty()) {
            throw new IllegalArgumentException("elderlyPersonId is required");
        }

        logger.info("Clearing simulator data for person {}", elderlyPersonId);

        Map<String, Object> deleted = new LinkedHashMap<>();
        int total = 0;

        total += record(deleted, "device readings", deviceDataUrl, elderlyPersonId);
        total += record(deleted, "rehab check-ins", rehabManualCheckinsUrl, elderlyPersonId);
        total += record(deleted, "rehab domain scores", rehabDomainScoresUrl, elderlyPersonId);
        total += record(deleted, "rehab progress history", rehabProgressHistoryUrl, elderlyPersonId);
        // Clearing the once-per-day guard as well, so the next generation is scored immediately
        // instead of reporting "already run today" against data that no longer exists.
        total += record(deleted, "rehab analysis runs", rehabAnalysisRunsUrl, elderlyPersonId);
        total += record(deleted, "craving check-ins", recoveryCheckinsUrl, elderlyPersonId);
        total += record(deleted, "sobriety events", recoverySobrietyEventsUrl, elderlyPersonId);
        total += record(deleted, "IRQ alerts", irqAlertsUrl, elderlyPersonId);
        total += record(deleted, "IRQ scores", irqScoresUrl, elderlyPersonId);
        total += record(deleted, "rehab enrollments", rehabEnrollmentsUrl, elderlyPersonId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("elderlyPersonId", elderlyPersonId);
        result.put("deleted", deleted);
        result.put("totalRowsDeleted", total);

        logger.info("Cleared {} row(s) for person {}", total, elderlyPersonId);
        return result;
    }

    private int record(Map<String, Object> into, String label, String url, String elderlyPersonId) {
        int count = deleteFor(url, elderlyPersonId);
        into.put(label, count);
        return count;
    }

    /**
     * Delete every row for this person from one table, returning how many went.
     *
     * A failure on one table is reported as -1 rather than aborting: a partial reset that says
     * which part failed is more useful mid-demo than an exception that leaves the caller guessing
     * how far it got.
     */
    private int deleteFor(String url, String elderlyPersonId) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.set("apikey", supabaseApiKey);
            headers.set("Authorization", "Bearer " + supabaseApiKey);
            headers.set("Prefer", "return=minimal,count=exact");
            headers.setContentType(MediaType.APPLICATION_JSON);

            ResponseEntity<String> response = restTemplate.exchange(
                    url + "?elderly_person_id=eq." + elderlyPersonId,
                    HttpMethod.DELETE, new HttpEntity<>(headers), String.class);

            String range = response.getHeaders().getFirst("Content-Range");
            if (range != null && range.contains("/")) {
                try {
                    return Integer.parseInt(range.substring(range.indexOf('/') + 1).trim());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
            return 0;
        } catch (Exception e) {
            logger.error("Could not clear {} for {}: {}", url, elderlyPersonId, e.getMessage());
            return -1;
        }
    }
}
