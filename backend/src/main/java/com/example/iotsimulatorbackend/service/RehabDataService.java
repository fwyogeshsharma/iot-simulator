package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.RehabDataRequest;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
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

    @Value("${supabase.devices-url}")
    private String devicesUrl;

    @Value("${supabase.device-data-url}")
    private String deviceDataUrl;

    @Value("${supabase.irq-scores-url}")
    private String irqScoresUrl;

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

        // PHYSIOTHERAPY REHAB - INACTIVE. This guard existed only because
        // compute_rehab_progress_for_person refuses to score while the baseline and recent
        // windows overlap. Nothing scores those domains now, and the IRQ does not read the
        // baseline window at all, so a short run is no longer a problem worth rejecting.
        // if (days < baselineDays * 2) {
        //     throw new IllegalArgumentException(String.format(
        //             "days must be at least twice baselineWindowDays: %d days with a %d-day baseline "
        //                     + "needs at least %d. Below that every domain is skipped as "
        //                     + "'Baseline period in progress'.",
        //             days, baselineDays, baselineDays * 2));
        // }

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

        // PHYSIOTHERAPY REHAB - INACTIVE (steps 1 and 2). The Rehab Progress card was removed
        // from the platform's health dashboard (Health.tsx, commit 5ca3133), so the enrollment
        // and the pain/adherence check-ins feed a score nobody can see. The IRQ reads neither:
        // it needs recovery_checkins, recovery_sobriety_events and device_data only.
        //
        // 1. Enrollment. The partial unique index allows only one active row per person, so an
        //    existing one has to be retired before the new one goes in.
        // int removed = deleteExistingEnrollments(request.getElderlyPersonId());
        // createEnrollment(request, programStart, baselineDays);
        // result.put("enrollmentsReplaced", removed);

        // 2. Manual check-ins - the only rehab domain that is not device-derived.
        // int checkins = writeManualCheckins(request.getElderlyPersonId(), programStart, days, degrading);
        // result.put("manualCheckinsWritten", checkins);

        // 3. Device metrics. Still required: physiological stress and activity & routine are
        //    two of the four IRQ components, and backfillIrqScores reads the same per-day
        //    figures. Do not comment this out with the rehab steps above.
        Map<LocalDate, Map<String, Double>> metricsByDay = new LinkedHashMap<>();
        if (request.isIncludeDeviceMetrics()) {
            result.put("deviceRowsWritten", writeDeviceTrajectory(
                    request.getElderlyPersonId(), programStart, days, degrading, result, metricsByDay));
        }

        // 4. IRQ recovery signals.
        Map<LocalDate, Integer> cravingByDay = new LinkedHashMap<>();
        if (request.isIncludeRecovery()) {
            int cravings = writeRecoveryCheckins(request.getElderlyPersonId(), programStart, days,
                    degrading, cravingByDay);
            int events = writeSobrietyEvents(request.getElderlyPersonId(), programStart, today, degrading);
            result.put("recoveryCheckinsWritten", cravings);
            result.put("sobrietyEventsWritten", events);

            // One irq_scores row per day of the programme. Without this the chart plots one point
            // per generation run - the table is a log of computations, not of days.
            result.put("irqHistoryWritten", backfillIrqScores(request.getElderlyPersonId(),
                    programStart, today, degrading, cravingByDay, metricsByDay));
        }

        // PHYSIOTHERAPY REHAB - INACTIVE (step 5). The once-per-day guard and the analysis run
        // both belong to rehab progress scoring, which nothing displays now.
        //
        // 5. run_rehab_progress_analysis is guarded to once per calendar day per person. Without
        //    clearing that row, data generated after today's run would not be scored until
        //    tomorrow - which looks exactly like the generator having done nothing.
        // clearAnalysisGuard(request.getElderlyPersonId());
        // result.put("analysisGuardCleared", true);

        if (request.isScoreAfterGenerate()) {
            // result.put("rehab", runRehabAnalysis(request.getElderlyPersonId()));
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
    private int writeRecoveryCheckins(String elderlyPersonId, LocalDate programStart, int days,
                                      boolean degrading, Map<LocalDate, Integer> cravingByDay) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i <= days; i++) {
            double progress = (double) i / days;

            double craving = degrading ? 1.5 + 6.5 * progress : 7.5 - 6.0 * progress;
            craving += random.nextGaussian() * 0.7;

            int intensity = (int) Math.round(clamp(craving, 0, 10));
            cravingByDay.put(programStart.plusDays(i), intensity);

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("elderly_person_id", elderlyPersonId);
            row.put("checkin_date", programStart.plusDays(i).toString());
            row.put("craving_intensity", intensity);
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

    // ------------------------------------------------------------------ device metrics

    /**
     * One metric moving linearly across the programme.
     *
     * `delta` is the fractional change from first day to last, sized against the clamp its scoring
     * function uses rather than picked for realism alone. The domain scorers compare the
     * baseline-window average with the recent-window average and feed the percentage change through
     * rehab_normalize_score with a metric-specific max_expected_pct - 30 for steps, 20 for gait
     * speed, 40 for HRV, 15 for resting heart rate. Because those two windows sit at roughly 11%
     * and 88% through the programme, a delta equal to the clamp lands the domain near 88 instead of
     * saturating at 100, which reads as a real recovery rather than a synthetic one.
     */
    private static final class Metric {
        final String dataType, unit, valueKey;
        final double start, delta, noise;
        final int precision;

        Metric(String dataType, String unit, String valueKey, double start, double delta,
               double noise, int precision) {
            this.dataType = dataType; this.unit = unit; this.valueKey = valueKey;
            this.start = start; this.delta = delta; this.noise = noise; this.precision = precision;
        }
    }

    /** Types this method owns, cleared before writing so a re-run replaces rather than blends. */
    private static final List<String> DEVICE_TYPES = Arrays.asList(
            "steps", "distance", "speed", "exercise_session", "floors_climbed",
            "resting_heart_rate", "heart_rate_variability", "oxygen_saturation",
            "weight", "body_fat", "lean_body_mass", "sleep");

    /**
     * Write the device-derived half of the trajectory: mobility, cardiovascular, body composition
     * and sleep. Deltas are negated for a degrading run, so one table drives both directions.
     *
     * Body composition is included because weight and body_fat are otherwise emitted monthly -
     * about two readings in sixty days, where the domain needs at least half of each window
     * covered - and lean_body_mass is not generated at all, so that domain could only ever report
     * insufficient_data.
     */
    private int writeDeviceTrajectory(String elderlyPersonId, LocalDate programStart, int days,
                                      boolean degrading, Map<String, Object> result,
                                      Map<LocalDate, Map<String, Double>> metricsByDay) {
        String deviceId = findDevice(elderlyPersonId);
        if (deviceId == null) {
            logger.warn("No active device for person {} - skipping device metrics", elderlyPersonId);
            result.put("deviceMetricsSkipped", "no active device found for this person");
            return 0;
        }

        // Improving direction; negated below when degrading.
        List<Metric> metrics = Arrays.asList(
                new Metric("steps", "steps", "count", 4000, 0.30, 0.10, 0),
                new Metric("distance", "m", "meters", 2900, 0.30, 0.10, 1),
                new Metric("speed", "m/s", "meters_per_second", 0.85, 0.20, 0.06, 2),
                new Metric("exercise_session", "minutes", "duration_minutes", 12, 0.40, 0.15, 0),
                new Metric("floors_climbed", "floors", "floors", 3, 0.40, 0.20, 0),
                new Metric("resting_heart_rate", "bpm", "bpm", 74, -0.15, 0.03, 0),
                new Metric("heart_rate_variability", "ms", "rmssd_ms", 32, 0.40, 0.10, 0),
                new Metric("oxygen_saturation", "%", "percentage", 95, 0.05, 0.008, 0),
                new Metric("body_fat", "%", "percentage", 33, -0.15, 0.03, 1),
                new Metric("lean_body_mass", "kg", "kg", 48, 0.10, 0.02, 1),
                // Weight is scored on stability against its own baseline rather than direction, so
                // an improving run holds steady and a degrading one drifts enough to be noticed.
                new Metric("weight", "kg", "kg", 78, degrading ? 0.08 : 0.01, 0.006, 1));

        clearDeviceWindow(elderlyPersonId, programStart);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Metric m : metrics) {
            double delta = "weight".equals(m.dataType) ? m.delta : (degrading ? -m.delta : m.delta);
            for (int i = 0; i <= days; i++) {
                double progress = (double) i / days;
                double value = m.start * (1 + delta * progress);
                value *= 1 + random.nextGaussian() * m.noise;
                value = Math.max(0, value);

                LocalDate day = programStart.plusDays(i);
                metricsByDay.computeIfAbsent(day, k -> new LinkedHashMap<>()).put(m.dataType, value);

                Map<String, Object> v = new LinkedHashMap<>();
                v.put(m.valueKey, round(value, m.precision));
                if ("exercise_session".equals(m.dataType)) {
                    v.put("exercise_type", 79);
                }
                rows.add(deviceRow(deviceId, elderlyPersonId, m.dataType, m.unit, v,
                        programStart.plusDays(i).atTime(8, 0)));
            }
        }

        rows.addAll(sleepRows(deviceId, elderlyPersonId, programStart, days, degrading));

        upsert(deviceDataUrl, rows, "device_id,data_type,recorded_at", "device metric");
        return rows.size();
    }

    /**
     * Nightly sleep summaries carrying the three fields _rehab_score_sleep reads. Duration moves
     * away from the eight-hour target on a degrading run, because that domain scores duration on
     * closeness to a healthy night rather than on more being better.
     */
    private List<Map<String, Object>> sleepRows(String deviceId, String elderlyPersonId,
                                                LocalDate programStart, int days, boolean degrading) {
        List<Map<String, Object>> rows = new ArrayList<>();

        for (int i = 0; i <= days; i++) {
            double progress = (double) i / days;

            double efficiency = degrading ? 88 - 14 * progress : 78 + 12 * progress;
            double awakenings = degrading ? 1.8 + 3.6 * progress : 4.5 - 2.7 * progress;
            double duration = degrading ? 470 + 120 * progress : 470 - 5 * progress;

            efficiency += random.nextGaussian() * 2.0;
            awakenings += random.nextGaussian() * 0.5;
            duration += random.nextGaussian() * 15;

            Map<String, Object> v = new LinkedHashMap<>();
            v.put("duration_minutes", (int) Math.round(clamp(duration, 180, 720)));
            v.put("sleep_efficiency_percentage", (int) Math.round(clamp(efficiency, 40, 99)));
            v.put("awakenings_count", (int) Math.round(clamp(awakenings, 0, 20)));

            rows.add(deviceRow(deviceId, elderlyPersonId, "sleep", "minutes", v,
                    programStart.plusDays(i).atTime(6, 30)));
        }
        return rows;
    }

    /** First active device for this person - the trajectory is written against a single device. */
    private String findDevice(String elderlyPersonId) {
        try {
            String url = devicesUrl + "?select=id,device_name&elderly_person_id=eq." + elderlyPersonId
                    + "&status=eq.active&limit=1";
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET,
                    new HttpEntity<>(jsonHeaders(null)), String.class);
            JsonNode array = objectMapper.readTree(response.getBody());
            if (array.isArray() && array.size() > 0) {
                logger.info("Writing device trajectory against {}",
                        array.get(0).path("device_name").asText());
                return array.get(0).path("id").asText();
            }
        } catch (Exception e) {
            logger.error("Could not look up a device for {}: {}", elderlyPersonId, e.getMessage());
        }
        return null;
    }

    /**
     * Remove existing readings of the types this trajectory owns, so a re-run replaces them rather
     * than averaging the new trend against whatever the generic generator left behind.
     */
    private void clearDeviceWindow(String elderlyPersonId, LocalDate programStart) {
        Instant from = programStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        String types = String.join(",", DEVICE_TYPES);
        // Scoped to the person, not to the device being written. The domain scoring functions
        // aggregate every device belonging to the person, so readings left on a second device
        // would average against the new trend and flatten it - mobility scored 67 instead of 87
        // that way, because a mat and a ring still held their old flat step counts.
        String url = deviceDataUrl + "?elderly_person_id=eq." + elderlyPersonId
                + "&data_type=in.(" + types + ")&recorded_at=gte." + from;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(jsonHeaders("return=minimal")), String.class);
        } catch (Exception e) {
            logger.warn("Could not clear the device window: {}", e.getMessage());
        }
    }

    private Map<String, Object> deviceRow(String deviceId, String elderlyPersonId, String dataType,
                                          String unit, Object value, LocalDateTime recordedAt) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("device_id", deviceId);
        row.put("elderly_person_id", elderlyPersonId);
        row.put("data_type", dataType);
        row.put("value", value);
        row.put("unit", unit);
        row.put("recorded_at", recordedAt.atZone(ZoneId.systemDefault()).toInstant().toString());
        return row;
    }

    private Object round(double value, int precision) {
        if (precision == 0) {
            return (int) Math.round(value);
        }
        double factor = Math.pow(10, precision);
        return Math.round(value * factor) / factor;
    }

    // ------------------------------------------------------------------ IRQ history

    /**
     * Write one irq_scores row per programme day, so the score history charts a trend instead of a
     * point per generation run.
     *
     * irq-compute can only ever score the present: a 24-hour device window, craving over the
     * trailing seven days, and a clean-day streak measured to today. Calling it repeatedly would
     * produce identical rows, not a curve. So the same four component formulas are replayed here
     * against each historical day - normalizeValue and the weightings are copied from
     * supabase/functions/irq-compute/index.ts, and the values come from the series this run has
     * just generated rather than from a second read of the database.
     *
     * Today is deliberately left out: the live Compute IRQ call writes that row, which keeps the
     * demo honest - the last point on the chart is produced by the real edge function, and it lands
     * on the curve because both read the same underlying data.
     */
    private int backfillIrqScores(String elderlyPersonId, LocalDate programStart, LocalDate today,
                                  boolean degrading, Map<LocalDate, Integer> cravingByDay,
                                  Map<LocalDate, Map<String, Double>> metricsByDay) {
        clearIrqHistory(elderlyPersonId, programStart);

        LocalDate relapse = degrading ? today.minusDays(RELAPSE_DAYS_AGO) : null;
        List<Map<String, Object>> rows = new ArrayList<>();

        for (LocalDate day = programStart; day.isBefore(today); day = day.plusDays(1)) {
            Map<String, Double> metrics = metricsByDay.get(day);

            double physiological = physiologicalStress(metrics);
            double activity = activityRoutine(metrics);
            double sobriety = sobrietyScore(day, programStart, relapse);
            double craving = cravingScore(day, cravingByDay);

            // Weights from the default row in irq_configurations.
            double score = physiological * 0.25 + activity * 0.20 + sobriety * 0.35 + craving * 0.20;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("elderly_person_id", elderlyPersonId);
            row.put("score", round2(score));
            row.put("physiological_stress_score", round2(physiological));
            row.put("activity_routine_score", round2(activity));
            row.put("sobriety_score", round2(sobriety));
            row.put("craving_control_score", round2(craving));
            row.put("computation_timestamp",
                    day.atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toString());
            row.put("data_points_analyzed", metrics == null ? 0 : metrics.size());
            row.put("time_window_hours", 24);
            row.put("confidence_level", metrics == null ? 0.0
                    : round2(Math.min(1.0, metrics.size() / 48.0)));
            row.put("detailed_metrics", Collections.singletonMap("source", "simulator backfill"));
            rows.add(row);
        }

        if (rows.isEmpty()) {
            return 0;
        }
        post(irqScoresUrl, rows, "return=minimal", "IRQ history");
        return rows.size();
    }

    /** Withdrawal/stress proxy over the vitals this generator produces. */
    private double physiologicalStress(Map<String, Double> metrics) {
        if (metrics == null) {
            return 50;
        }
        double total = 0;
        int count = 0;
        if (metrics.containsKey("resting_heart_rate")) {
            total += normalize(metrics.get("resting_heart_rate"), 40, 100, 62);
            count++;
        }
        if (metrics.containsKey("heart_rate_variability")) {
            total += normalize(metrics.get("heart_rate_variability"), 10, 100, 60);
            count++;
        }
        return count > 0 ? total / count : 50;
    }

    private double activityRoutine(Map<String, Double> metrics) {
        if (metrics == null) {
            return 50;
        }
        double total = 0;
        int count = 0;
        if (metrics.containsKey("steps")) { total += normalize(metrics.get("steps"), 2000, 10000, 5000); count++; }
        if (metrics.containsKey("distance")) { total += normalize(metrics.get("distance"), 500, 5000, 3000); count++; }
        if (metrics.containsKey("speed")) { total += normalize(metrics.get("speed"), 0.3, 2.0, 1.2); count++; }
        if (metrics.containsKey("floors_climbed")) { total += normalize(metrics.get("floors_climbed"), 0, 20, 8); count++; }
        if (metrics.containsKey("exercise_session")) { total += normalize(metrics.get("exercise_session"), 0, 60, 30); count++; }
        // irq-compute credits a full 100 when no fall was detected in the window.
        total += 100;
        count++;
        return count > 0 ? total / count : 50;
    }

    /** Ramps from 20 on day zero to 100 at 90 clean days, reset by a relapse. */
    private double sobrietyScore(LocalDate day, LocalDate programStart, LocalDate relapse) {
        LocalDate anchor = (relapse != null && !relapse.isAfter(day)) ? relapse : programStart;
        long cleanDays = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(anchor, day));
        return Math.min(100, 20 + (cleanDays / 90.0) * 80);
    }

    /** Inverse of the trailing seven-day craving average, as irq-compute scores it. */
    private double cravingScore(LocalDate day, Map<LocalDate, Integer> cravingByDay) {
        double total = 0;
        int count = 0;
        for (int back = 0; back < 7; back++) {
            Integer v = cravingByDay.get(day.minusDays(back));
            if (v != null) { total += v; count++; }
        }
        if (count == 0) {
            return 60; // irq-compute's neutral default when nothing has been logged
        }
        return clamp(100 - (total / count) * 10, 0, 100);
    }

    /** normalizeValue() from irq-compute, reproduced exactly. */
    private double normalize(double value, double min, double max, double optimal) {
        if (value <= min) return 0;
        if (value >= max) return value > optimal ? 50 : 100;
        double distance = Math.abs(value - optimal);
        double maxDistance = Math.max(optimal - min, max - optimal);
        return Math.max(0, 100 - (distance / maxDistance) * 100);
    }

    private void clearIrqHistory(String elderlyPersonId, LocalDate programStart) {
        Instant from = programStart.atStartOfDay(ZoneId.systemDefault()).toInstant();
        String url = irqScoresUrl + "?elderly_person_id=eq." + elderlyPersonId
                + "&computation_timestamp=gte." + from;
        try {
            restTemplate.exchange(url, HttpMethod.DELETE,
                    new HttpEntity<>(jsonHeaders("return=minimal")), String.class);
        } catch (Exception e) {
            logger.warn("Could not clear IRQ history: {}", e.getMessage());
        }
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
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
