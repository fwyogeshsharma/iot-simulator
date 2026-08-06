package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.Device;
import com.example.iotsimulatorbackend.model.DiseaseProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Generates one night of sleep data shaped by a {@link DiseaseProfile}.
 *
 * Output shapes mirror the real Withings Sleep Tracking Mat exactly, as observed
 * on device fc9176e5 (2,066 rows):
 *
 *   sleep                  {start,end,title,notes,stages[],stage_minutes{8},
 *                           awakenings_count,duration_minutes,time_asleep_minutes,
 *                           time_in_bed_minutes,sleep_efficiency_percentage}   minutes
 *   heart_rate             {bpm}                                              bpm
 *   respiratory_rate       {breaths_per_minute}                               breaths/min
 *   heart_rate_variability {rmssd_ms}                                         ms
 *   oxygen_saturation      {percentage}                                       %
 *
 * Volume: 5 rows per night by default. The per-minute hypnogram lives inside the
 * single `sleep` row's stages[] array rather than as ~446 separate sleep_stage
 * rows, which keeps a 365-night generation at ~1,825 rows instead of ~160,000.
 * No information is lost - stage_minutes is derived from that same array.
 *
 * Two defects present in the real device feed are deliberately NOT reproduced:
 *   - awakenings_count there equals awake_minutes (counts minutes, not episodes).
 *     Here it counts contiguous awake runs after sleep onset.
 *   - stage_minutes there sums to 446 against a stated duration of 460.
 *     Here stage_minutes is computed from the emitted array, so it always ties out.
 */
@Service
public class SleepSessionGenerator {

    private static final Logger logger = LoggerFactory.getLogger(SleepSessionGenerator.class);

    /** Emit separate per-minute sleep_stage rows. Off by default - see class note. */
    private static final int SLEEP_STAGE_ROWS_PER_NIGHT = 0;

    private static final int CYCLE_MINUTES = 90;

    private final Random random = new Random();

    /** Data types this generator owns. Callers should skip their generic path for these. */
    public static final List<String> GENERATED_TYPES = List.of(
            "sleep", "heart_rate", "respiratory_rate", "heart_rate_variability", "oxygen_saturation");

    /**
     * Build every payload for one night.
     *
     * @param dayIndex 0-based night number in the run, used to apply the profile's
     *                 long-horizon trend (e.g. deep sleep declining over 180 days).
     */
    public List<Map<String, Object>> generateNight(Device device, DiseaseProfile profile,
                                                   LocalDate night, int dayIndex) {
        List<Map<String, Object>> rows = new ArrayList<>();

        // ---- session timing -------------------------------------------------
        LocalTime bedtime = parseTime(profile.scalar("schedule", "bedtime_hhmm"), LocalTime.of(22, 45));
        double bedtimeSd = profile.scalarDouble("schedule", "bedtime_sd_min", 20);
        int bedtimeJitter = (int) Math.round(random.nextGaussian() * bedtimeSd);

        // duration_min is time ASLEEP. Time in bed is built up from it so that
        // efficiency, latency and WASO stay mutually consistent:
        //     in_bed = asleep + latency + WASO
        //     efficiency = asleep / in_bed
        // Sampling efficiency independently would let it contradict the other three,
        // so profile.sleep.efficiency_pct is documentation of the expected result
        // rather than an input.
        int targetAsleep = (int) Math.round(sample(profile.range("sleep", "duration_min", 420, 480)));
        int latency = (int) Math.round(sample(profile.range("sleep", "latency_min", 8, 18)));

        // Wake After Sleep Onset. Falls back to the awake share of the night when a
        // profile omits waso_min.
        double[] wasoRange = profile.range("sleep", "waso_min", -1, -1);
        int waso;
        if (wasoRange[0] < 0) {
            double awakeShare = sample(profile.range("stages_pct", "awake", 4, 8)) / 100.0;
            waso = (int) Math.round(Math.max(0, targetAsleep * awakeShare / Math.max(0.01, 1 - awakeShare)) - latency);
            waso = Math.max(0, waso);
        } else {
            waso = (int) Math.round(sample(wasoRange));
        }

        int timeInBed = targetAsleep + latency + waso;

        // Session starts the evening before the "night" date, matching the real
        // device where a session titled 2026-08-05 begins 2026-08-04T17:17Z.
        LocalDateTime start = LocalDateTime.of(night.minusDays(1), bedtime).plusMinutes(bedtimeJitter);
        LocalDateTime end = start.plusMinutes(timeInBed);

        // ---- stage percentages, with trend drift ----------------------------
        // Stage percentages apply to the asleep portion of the night, not to time in bed.
        double deepPct = sample(profile.range("stages_pct", "deep", 20, 25));
        double remPct = sample(profile.range("stages_pct", "rem", 20, 25));

        if ("deep_pct".equals(profile.trendField())) {
            deepPct = Math.max(2.0, deepPct + profile.trendPer30Days() * (dayIndex / 30.0));
        }
        if ("rem_pct".equals(profile.trendField())) {
            remPct = Math.max(2.0, remPct + profile.trendPer30Days() * (dayIndex / 30.0));
        }

        int awakenings = (int) Math.round(sample(profile.range("sleep", "awakenings", 1, 3)));

        String[] hypnogram = buildHypnogram(timeInBed, latency, waso, awakenings, deepPct, remPct);

        // ---- derive everything from the emitted array so totals tie out ------
        Map<String, Integer> stageMinutes = tally(hypnogram);
        int awakeMinutes = stageMinutes.getOrDefault("awake", 0);
        int timeAsleep = timeInBed - awakeMinutes;
        int efficiency = (int) Math.round(100.0 * timeAsleep / Math.max(1, timeInBed));
        int awakeningEpisodes = countAwakeEpisodes(hypnogram, latency);

        rows.add(payload(device, "sleep", "minutes",
                sleepValue(start, end, night, hypnogram, stageMinutes,
                        awakeningEpisodes, timeInBed, timeAsleep, efficiency),
                end));

        // ---- nightly vitals, correlated with the night just built -----------
        // Fragmented, shallow sleep pushes HR up and HRV down beyond the profile
        // range alone, so the two signals move together the way the detection
        // algorithms expect.
        double fragmentation = Math.min(1.0, awakeningEpisodes / 12.0);

        double hr = sample(profile.range("vitals", "night_hr_bpm", 52, 62));
        if ("night_hr_bpm".equals(profile.trendField())) {
            hr += profile.trendPer30Days() * (dayIndex / 30.0);
        }
        hr += fragmentation * 4.0;

        double hrv = sample(profile.range("vitals", "hrv_rmssd_ms", 55, 85));
        if ("hrv_rmssd_ms".equals(profile.trendField())) {
            hrv += profile.trendPer30Days() * (dayIndex / 30.0);
        }
        hrv -= fragmentation * 6.0;

        double resp = sample(profile.range("vitals", "resp_bpm", 13, 16));
        double spo2 = sample(profile.range("vitals", "spo2_pct", 96, 99));

        rows.add(payload(device, "heart_rate", "bpm",
                mapOf("bpm", (int) Math.round(clamp(hr, 35, 120))), end));
        rows.add(payload(device, "heart_rate_variability", "ms",
                mapOf("rmssd_ms", (int) Math.round(clamp(hrv, 5, 150))), end));
        rows.add(payload(device, "respiratory_rate", "breaths/min",
                mapOf("breaths_per_minute", (int) Math.round(clamp(resp, 6, 30))), end));
        rows.add(payload(device, "oxygen_saturation", "%",
                mapOf("percentage", (int) Math.round(clamp(spo2, 70, 100))), end));

        // ---- optional per-minute stage rows ---------------------------------
        if (SLEEP_STAGE_ROWS_PER_NIGHT > 0) {
            int step = Math.max(1, hypnogram.length / SLEEP_STAGE_ROWS_PER_NIGHT);
            for (int i = 0; i < hypnogram.length; i += step) {
                Map<String, Object> v = new LinkedHashMap<>();
                v.put("stage", hypnogram[i]);
                v.put("duration_minutes", 1);
                rows.add(payload(device, "sleep_stage", "minutes", v, start.plusMinutes(i + 1)));
            }
        }

        return rows;
    }

    // ------------------------------------------------------------------------

    /**
     * Lay out a minute-by-minute hypnogram: onset latency awake, then ~90-minute
     * cycles with deep front-loaded and REM back-loaded, then awake interruptions
     * distributed across the back half.
     */
    private String[] buildHypnogram(int timeInBed, int latency, int waso,
                                    int awakenings, double deepPct, double remPct) {
        String[] out = new String[timeInBed];

        for (int i = 0; i < Math.min(latency, timeInBed); i++) {
            out[i] = "awake";
        }

        int asleepMinutes = timeInBed - latency;
        if (asleepMinutes <= 0) {
            for (int i = 0; i < timeInBed; i++) if (out[i] == null) out[i] = "awake";
            return out;
        }

        int targetDeep = (int) Math.round(asleepMinutes * deepPct / 100.0);
        int targetRem = (int) Math.round(asleepMinutes * remPct / 100.0);
        targetDeep = Math.max(0, Math.min(targetDeep, asleepMinutes));
        targetRem = Math.max(0, Math.min(targetRem, asleepMinutes - targetDeep));

        int cycles = Math.max(1, asleepMinutes / CYCLE_MINUTES);
        int deepLeft = targetDeep;
        int remLeft = targetRem;

        for (int i = latency; i < timeInBed; i++) {
            int pos = i - latency;
            int cycle = Math.min(cycles - 1, pos / CYCLE_MINUTES);
            double through = cycles == 1 ? 0.5 : (double) cycle / (cycles - 1);
            int inCycle = pos % CYCLE_MINUTES;

            // Deep dominates the first third of early cycles; REM the last third
            // of late cycles. Everything else is light.
            boolean deepWindow = inCycle < CYCLE_MINUTES * 0.35 && through < 0.6;
            boolean remWindow = inCycle > CYCLE_MINUTES * 0.65 && through > 0.3;

            if (deepWindow && deepLeft > 0) {
                out[i] = "deep";
                deepLeft--;
            } else if (remWindow && remLeft > 0) {
                out[i] = "rem";
                remLeft--;
            } else {
                out[i] = "light";
            }
        }

        // Spend any unplaced deep/REM budget on light minutes so the totals land.
        for (int i = timeInBed - 1; i >= latency && (deepLeft > 0 || remLeft > 0); i--) {
            if (!"light".equals(out[i])) continue;
            if (remLeft > 0) { out[i] = "rem"; remLeft--; }
            else { out[i] = "deep"; deepLeft--; }
        }

        // WASO episodes, weighted to the back half of the night. The budget is WASO
        // alone - onset latency is already awake and must not eat into it, which is
        // what previously left long-latency profiles with zero awakenings.
        int awakeToPlace = Math.max(0, waso);
        int episodes = Math.max(awakeToPlace > 0 ? 1 : 0, awakenings);
        int perEpisode = episodes > 0 ? Math.max(1, awakeToPlace / episodes) : 0;
        for (int e = 0; e < episodes && awakeToPlace > 0; e++) {
            int runLength = Math.min(awakeToPlace, Math.max(1, perEpisode + random.nextInt(3) - 1));
            int earliest = latency + 1;
            int span = Math.max(1, timeInBed - runLength - earliest);
            int at = earliest + (int) (span * (0.4 + 0.6 * random.nextDouble()));
            for (int i = at; i < Math.min(timeInBed, at + runLength); i++) {
                if (!"awake".equals(out[i])) {
                    out[i] = "awake";
                    awakeToPlace--;
                }
            }
        }

        for (int i = 0; i < timeInBed; i++) if (out[i] == null) out[i] = "light";
        return out;
    }

    private Map<String, Integer> tally(String[] hypnogram) {
        Map<String, Integer> m = new LinkedHashMap<>();
        for (String s : hypnogram) m.merge(s, 1, Integer::sum);
        return m;
    }

    /** Contiguous awake runs after sleep onset - episodes, not minutes. */
    private int countAwakeEpisodes(String[] hypnogram, int latency) {
        int episodes = 0;
        boolean inRun = false;
        for (int i = latency; i < hypnogram.length; i++) {
            boolean awake = "awake".equals(hypnogram[i]);
            if (awake && !inRun) episodes++;
            inRun = awake;
        }
        return episodes;
    }

    /** Key order matches the real device row. */
    private Map<String, Object> sleepValue(LocalDateTime start, LocalDateTime end, LocalDate night,
                                           String[] hypnogram, Map<String, Integer> tally,
                                           int awakenings, int timeInBed, int timeAsleep, int efficiency) {
        // Real devices emit one entry per contiguous stage RUN, with a variable
        // duration_minutes - e.g. 9-17 entries of 3-45 minutes for a night. Emitting
        // one entry per minute (which an outlier Withings night does, 446 of them)
        // is both unrepresentative and ~40x larger.
        List<Map<String, Object>> stages = new ArrayList<>();
        int i = 0;
        while (i < hypnogram.length) {
            int runStart = i;
            while (i < hypnogram.length && hypnogram[i].equals(hypnogram[runStart])) {
                i++;
            }
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("end", iso(start.plusMinutes(i)));
            s.put("stage", hypnogram[runStart]);
            s.put("start", iso(start.plusMinutes(runStart)));
            s.put("duration_minutes", i - runStart);
            stages.add(s);
        }

        Map<String, Object> stageMinutes = new LinkedHashMap<>();
        stageMinutes.put("rem_minutes", tally.getOrDefault("rem", 0));
        stageMinutes.put("deep_minutes", tally.getOrDefault("deep", 0));
        stageMinutes.put("awake_minutes", tally.getOrDefault("awake", 0));
        stageMinutes.put("light_minutes", tally.getOrDefault("light", 0));
        stageMinutes.put("unknown_minutes", 0);
        stageMinutes.put("sleeping_minutes", 0);
        stageMinutes.put("out_of_bed_minutes", 0);
        stageMinutes.put("awake_in_bed_minutes", 0);

        Map<String, Object> v = new LinkedHashMap<>();
        v.put("end", iso(end));
        v.put("notes", null);
        v.put("start", iso(start));
        v.put("title", night.toString());
        v.put("stages", stages);
        v.put("stage_minutes", stageMinutes);
        v.put("awakenings_count", awakenings);
        v.put("duration_minutes", timeInBed);
        v.put("time_asleep_minutes", timeAsleep);
        v.put("time_in_bed_minutes", timeInBed);
        v.put("sleep_efficiency_percentage", efficiency);
        return v;
    }

    private Map<String, Object> payload(Device device, String dataType, String unit,
                                        Object value, LocalDateTime recordedAt) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("device_id", device.getId());
        p.put("elderly_person_id", device.getElderlyPersonId());
        p.put("data_type", dataType);
        p.put("value", value);
        p.put("unit", unit);
        p.put("recorded_at", recordedAt.atZone(ZoneId.systemDefault()).toInstant().toString());
        return p;
    }

    private Map<String, Object> mapOf(String k, Object v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put(k, v);
        return m;
    }

    private double sample(double[] range) {
        return range[0] + random.nextDouble() * (range[1] - range[0]);
    }

    private double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private String iso(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toInstant().toString();
    }

    private LocalTime parseTime(Object hhmm, LocalTime fallback) {
        if (hhmm == null) return fallback;
        try {
            String[] parts = hhmm.toString().split(":");
            return LocalTime.of(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        } catch (Exception e) {
            logger.warn("Bad bedtime '{}' in disease profile, using {}", hhmm, fallback);
            return fallback;
        }
    }
}
