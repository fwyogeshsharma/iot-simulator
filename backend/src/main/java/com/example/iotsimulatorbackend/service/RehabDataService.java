package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.RehabDataRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates a rehabilitation / recovery trajectory - improving or degrading - for one person.
 *
 * Only writes what a wearable cannot measure, because the device-derived half of both scores
 * already comes from ordinary historical device generation:
 *
 *   rehab_enrollments          anchors the baseline window the rehab score is measured against
 *   rehab_manual_checkins      pain_score + exercise_adherence_pct -> _rehab_score_manual
 *   recovery_checkins          craving_intensity                   -> irq-compute computeCravingControl
 *   recovery_sobriety_events   program_start / relapse             -> irq-compute computeSobrietyStatus
 *
 * The numbers are chosen against the scoring functions rather than by eye. _rehab_score_manual
 * feeds pain through rehab_delta_score(baseline, recent, higher_is_better = false, max_delta = 5),
 * so a five-point move across the program is exactly a full-scale change; adherence is used
 * directly as its own 0-100 component. irq-compute scores craving as 100 - avg * 10 over a
 * trailing 7 days, and sobriety as 20 + (clean_days / 90) * 80.
 */
@Service
public class RehabDataService {

    private static final Logger logger = LoggerFactory.getLogger(RehabDataService.class);

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Value("${supabase.rehab-enrollments-url}")
    private String rehabEnrollmentsUrl;

    @Value("${supabase.rehab-manual-checkins-url}")
    private String rehabManualCheckinsUrl;

    @Value("${supabase.rehab-analysis-runs-url}")
    private String rehabAnalysisRunsUrl;

    @Value("${supabase.recovery-checkins-url}")
    private String recoveryCheckinsUrl;

    @Value("${supabase.recovery-sobriety-events-url}")
    private String recoverySobrietyEventsUrl;

    @Value("${supabase.rpc-url}")
    private String rpcUrl;

    @Value("${simulator.irq-compute-url}")
    private String irqComputeUrl;

    private final Random random = new Random();

    /** Days before today that a degradation run logs its relapse, keeping the clean streak short. */
    private static final int RELAPSE_DAYS_AGO = 5;

    public Map<String, Object> generate(RehabDataRequest request) {
        if (request.getElderlyPersonId() == null || request.getElderlyPersonId().isEmpty()) {
            throw new IllegalArgumentException("elderlyPersonId is required");
        }

        int baselineDays = Math.max(3, Math.min(30, request.getBaselineWindowDays()));
        int days = request.getDays();

        // compute_rehab_progress_for_person refuses to score while the baseline and recent
        // windows would overlap, reporting every domain as insufficient_data instead. Fail here
        // with the actual numbers rather than let the caller wonder why nothing scored.
        if (days < baselineDays * 2) {
            throw new IllegalArgumentException(String.format(
                    "days must be at least twice baselineWindowDays: %d days with a %d-day baseline "
                            + "needs at least %d. Below that every domain is skipped as "
                            + "'Baseline period in progress'.",
                    days, baselineDays, baselineDays * 2));
        }

        boolean degrading = request.isDegradation();
        LocalDate today = LocalDate.now();
        LocalDate programStart = today.minusDays(days);

        logger.info("Generating {} rehab trajectory for person {} over {} days (baseline {}), from {}",
                degrading ? "DEGRADATION" : "IMPROVEMENT", request.getElderlyPersonId(),
                days, baselineDays, programStart);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("trajectory", degrading ? RehabDataRequest.DEGRADATION : RehabDataRequest.IMPROVEMENT);
        result.put("programStartDate", programStart.toString());
        result.put("baselineWindowDays", baselineDays);
        result.put("days", days);

        // 1. Enrollment. The partial unique index allows only one active row per person, so an
        //    existing one has to be retired before the new one goes in.
        int removed = deleteExistingEnrollments(request.getElderlyPersonId());
        createEnrollment(request, programStart, baselineDays);
        result.put("enrollmentsReplaced", removed);

        // 2. Manual check-ins - the only rehab domain that is not device-derived.
        int checkins = writeManualCheckins(request.getElderlyPersonId(), programStart, days, degrading);
        result.put("manualCheckinsWritten", checkins);

        // 3. IRQ recovery signals.
        if (request.isIncludeRecovery()) {
            int cravings = writeRecoveryCheckins(request.getElderlyPersonId(), programStart, days, degrading);
            int events = writeSobrietyEvents(request.getElderlyPersonId(), programStart, today, degrading);
            result.put("recoveryCheckinsWritten", cravings);
            result.put("sobrietyEventsWritten", events);
        }

        // 4. run_rehab_progress_analysis is guarded to once per calendar day per person. Without
        //    clearing that row, data generated after today's run would not be scored until
        //    tomorrow - which looks exactly like the generator having done nothing.
        clearAnalysisGuard(request.getElderlyPersonId());
        result.put("analysisGuardCleared", true);

        if (request.isScoreAfterGenerate()) {
            result.put("rehab", runRehabAnalysis(request.getElderlyPersonId()));
            if (request.isIncludeRecovery()) {
                result.put("irq", runIrqCompute(request.getElderlyPersonId()));
            }
        }

        return result;
    }

    // ------------------------------------------------------------------ enrollment

    /**
     * Clear any existing enrollment for this person so the new one can be the single active row
     * the partial unique index allows.
     *
     * Deletes rather than flipping is_active to false, for a mundane reason: this app talks to
     * PostgREST through the JDK HttpURLConnection, which rejects PATCH outright. Deleting is also
     * the honest thing for a generator to do - a retired enrollment left lying around still shows
     * up in the app as a program the person once had.
     *
     * @return how many enrollments were removed
     */
    private int deleteExistingEnrollments(String elderlyPersonId) {
        String url = rehabEnrollmentsUrl + "?elderly_person_id=eq." + elderlyPersonId;
        try {
            HttpHeaders headers = jsonHeaders("return=minimal,count=exact");
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(headers), String.class);

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
            throw new RuntimeException("Could not clear the existing rehab enrollment: " + e.getMessage(), e);
        }
    }

    private void createEnrollment(RehabDataRequest request, LocalDate programStart, int baselineDays) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("elderly_person_id", request.getElderlyPersonId());
        row.put("protocol_key", request.getProtocolKey());
        row.put("program_start_date", programStart.toString());
        row.put("baseline_window_days", baselineDays);
        row.put("is_active", true);
        row.put("notes", "Generated by the IoT simulator ("
                + (request.isDegradation() ? "degradation" : "improvement") + " trajectory)");

        post(rehabEnrollmentsUrl, Collections.singletonList(row), "return=minimal", "rehab enrollment");
    }

    // ------------------------------------------------------------------ check-ins

    /**
     * Pain falls and adherence climbs across an improving program, and the reverse for a
     * degrading one. Pain moves a full five points over the program because that is exactly the
     * max_expected_delta _rehab_score_manual clamps against, so the ends of the trajectory land
     * on the ends of the score.
     */
    private int writeManualCheckins(String elderlyPersonId, LocalDate programStart, int days, boolean degrading) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i <= days; i++) {
            double progress = (double) i / days;

            double pain = degrading ? 2.5 + 5.5 * progress : 7.5 - 5.5 * progress;
            double adherence = degrading ? 92 - 60 * progress : 35 + 57 * progress;

            // Day-to-day noise, so the series reads as something a person logged rather than a line.
            pain += random.nextGaussian() * 0.6;
            adherence += random.nextGaussian() * 6;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("elderly_person_id", elderlyPersonId);
            row.put("checkin_date", programStart.plusDays(i).toString());
            row.put("pain_score", (int) Math.round(clamp(pain, 0, 10)));
            row.put("exercise_adherence_pct", (int) Math.round(clamp(adherence, 0, 100)));
            rows.add(row);
        }

        upsert(rehabManualCheckinsUrl, rows, "elderly_person_id,checkin_date", "rehab manual check-ins");
        return rows.size();
    }

    /**
     * Craving intensity, which irq-compute averages over a trailing 7 days and inverts
     * (score = 100 - avg * 10). An improving run therefore ends near 1, a degrading one near 8.
     */
    private int writeRecoveryCheckins(String elderlyPersonId, LocalDate programStart, int days, boolean degrading) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i <= days; i++) {
            double progress = (double) i / days;

            double craving = degrading ? 1.5 + 6.5 * progress : 7.5 - 6.0 * progress;
            craving += random.nextGaussian() * 0.7;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("elderly_person_id", elderlyPersonId);
            row.put("checkin_date", programStart.plusDays(i).toString());
            row.put("craving_intensity", (int) Math.round(clamp(craving, 0, 10)));
            rows.add(row);
        }

        upsert(recoveryCheckinsUrl, rows, "elderly_person_id,checkin_date", "recovery check-ins");
        return rows.size();
    }

    /**
     * The sobriety event log. computeSobrietyStatus takes the most recent relapse, or the
     * program_start when there is none, and ramps the score from 20 at day 0 to 100 at 90 clean
     * days - so an improving run leaves the streak running, while a degrading one logs a recent
     * relapse that resets it.
     *
     * Existing events are cleared first: with no unique constraint to upsert against, repeated
     * runs would otherwise stack program_start rows and leave the streak anchored to whichever
     * one happened to sort first.
     */
    private int writeSobrietyEvents(String elderlyPersonId, LocalDate programStart, LocalDate today,
                                    boolean degrading) {
        try {
            HttpHeaders headers = jsonHeaders("return=minimal");
            restTemplate.exchange(recoverySobrietyEventsUrl + "?elderly_person_id=eq." + elderlyPersonId,
                    HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            logger.warn("Could not clear existing sobriety events: {}", e.getMessage());
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(sobrietyEvent(elderlyPersonId, "program_start", programStart, "Program start (simulated)"));

        if (degrading) {
            rows.add(sobrietyEvent(elderlyPersonId, "relapse", today.minusDays(RELAPSE_DAYS_AGO),
                    "Relapse logged (simulated degradation)"));
        } else {
            // Scoring-neutral: it exists so an improving run still shows something in the
            // recovery log beyond the enrollment itself.
            rows.add(sobrietyEvent(elderlyPersonId, "milestone_note", today.minusDays(RELAPSE_DAYS_AGO),
                    "Milestone: steady progress (simulated)"));
        }

        post(recoverySobrietyEventsUrl, rows, "return=minimal", "sobriety events");
        return rows.size();
    }

    private Map<String, Object> sobrietyEvent(String elderlyPersonId, String type, LocalDate date, String notes) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("elderly_person_id", elderlyPersonId);
        row.put("event_type", type);
        row.put("event_date", date.toString());
        row.put("notes", notes);
        return row;
    }

    // ------------------------------------------------------------------ scoring

    private void clearAnalysisGuard(String elderlyPersonId) {
        try {
            HttpHeaders headers = jsonHeaders("return=minimal");
            restTemplate.exchange(rehabAnalysisRunsUrl + "?elderly_person_id=eq." + elderlyPersonId,
                    HttpMethod.DELETE, new HttpEntity<>(headers), String.class);
        } catch (Exception e) {
            logger.warn("Could not clear the rehab analysis guard - scores may not refresh until tomorrow: {}",
                    e.getMessage());
        }
    }

    private Object runRehabAnalysis(String elderlyPersonId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("p_elderly_person_id", elderlyPersonId);
            body.put("p_source", "simulator");

            ResponseEntity<String> response = restTemplate.postForEntity(
                    rpcUrl + "/run_rehab_progress_analysis",
                    new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders(null)), String.class);

            return objectMapper.readValue(response.getBody(), Object.class);
        } catch (Exception e) {
            logger.error("run_rehab_progress_analysis failed: {}", e.getMessage());
            return Collections.singletonMap("error", String.valueOf(e.getMessage()));
        }
    }

    private Object runIrqCompute(String elderlyPersonId) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("elderly_person_id", elderlyPersonId);
            body.put("time_window_hours", 24);

            ResponseEntity<String> response = restTemplate.postForEntity(irqComputeUrl,
                    new HttpEntity<>(objectMapper.writeValueAsString(body), jsonHeaders(null)), String.class);

            return objectMapper.readValue(response.getBody(), Object.class);
        } catch (Exception e) {
            // irq-compute answers 400 when the person has no device data in the window at all,
            // which is a legitimate state here - the rehab half of the run still succeeded.
            logger.warn("irq-compute did not return a score: {}", e.getMessage());
            return Collections.singletonMap("error", String.valueOf(e.getMessage()));
        }
    }

    // ------------------------------------------------------------------ plumbing

    private HttpHeaders jsonHeaders(String prefer) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (prefer != null) {
            headers.set("Prefer", prefer);
        }
        return headers;
    }

    private void post(String url, List<Map<String, Object>> rows, String prefer, String what) {
        try {
            restTemplate.postForEntity(url,
                    new HttpEntity<>(objectMapper.writeValueAsString(rows), jsonHeaders(prefer)), String.class);
            logger.info("Wrote {} {} row(s)", rows.size(), what);
        } catch (Exception e) {
            throw new RuntimeException("Could not write " + what + ": " + e.getMessage(), e);
        }
    }

    /**
     * Insert that overwrites on conflict, so re-running a person with the opposite trajectory
     * replaces their check-ins rather than colliding with the one-per-day unique constraint.
     */
    private void upsert(String url, List<Map<String, Object>> rows, String conflictColumns, String what) {
        String target = url + (url.contains("?") ? "&" : "?") + "on_conflict=" + conflictColumns;
        post(target, rows, "return=minimal,resolution=merge-duplicates", what);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
