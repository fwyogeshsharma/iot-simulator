package com.example.iotsimulatorbackend.service;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The value shapes real devices write for Health Connect derived data types.
 *
 * Observed across the Withings mat, a Samsung watch and a smart ring (8,251 rows):
 * every numeric reading is stored as a single-key object, never a bare number.
 * The generic historical generator produces bare numbers, so values are passed
 * through {@link #wrap} to match what the platform's readers expect
 * (see valueExtractor.ts on the web side, which looks these keys up by name).
 *
 *   heart_rate / resting_heart_rate  {bpm}                 bpm
 *   heart_rate_variability           {rmssd_ms}            ms
 *   respiratory_rate                 {breaths_per_minute}  breaths/min
 *   oxygen_saturation                {percentage}          %
 *   steps                            {count}               steps
 *   distance                         {meters}              m
 *   speed                            {meters_per_second}   m/s
 *   total_calories / active_calories {kcal}                kcal
 *   basal_metabolic_rate             {kcal_per_day}        kcal/day
 *   body_fat                         {percentage}          %
 *   height                           {meters}              m
 *   weight                           {kg}                  kg
 *   sleep_stage                      {stage,duration_minutes}          minutes
 *   exercise_session                 {title,exercise_type,duration_minutes}  minutes
 *
 * `sleep` is not handled here - a session is built by {@link SleepSessionGenerator}.
 */
public final class HealthConnectShapes {

    private HealthConnectShapes() {}

    /** data_type -> the single key its numeric value is stored under. */
    private static final Map<String, String> VALUE_KEY = new HashMap<>();

    /** data_type -> canonical unit, used when a config omits one. */
    private static final Map<String, String> UNIT = new HashMap<>();

    /** Types whose numeric value is a whole number in the real feed. */
    private static final List<String> INTEGRAL = Arrays.asList(
            "heart_rate", "resting_heart_rate", "heart_rate_variability",
            "respiratory_rate", "oxygen_saturation", "steps",
            "total_calories", "active_calories", "basal_metabolic_rate");

    static {
        put("heart_rate", "bpm", "bpm");
        put("resting_heart_rate", "bpm", "bpm");
        put("heart_rate_variability", "rmssd_ms", "ms");
        put("respiratory_rate", "breaths_per_minute", "breaths/min");
        put("oxygen_saturation", "percentage", "%");
        put("steps", "count", "steps");
        put("distance", "meters", "m");
        put("speed", "meters_per_second", "m/s");
        put("total_calories", "kcal", "kcal");
        put("active_calories", "kcal", "kcal");
        put("basal_metabolic_rate", "kcal_per_day", "kcal/day");
        put("body_fat", "percentage", "%");
        put("height", "meters", "m");
        put("weight", "kg", "kg");
        // Composite shapes, built in wrap() rather than from a single key.
        UNIT.put("sleep_stage", "minutes");
        UNIT.put("exercise_session", "minutes");
        UNIT.put("sleep", "minutes");
    }

    private static void put(String dataType, String key, String unit) {
        VALUE_KEY.put(dataType, key);
        UNIT.put(dataType, unit);
    }

    /**
     * How often a real device reports each type. Observed row counts over ~8 days:
     * heart_rate 3,631 but weight/height/body_fat/basal_metabolic_rate exactly 1
     * each, all sharing one timestamp - a single sync event, not a daily reading.
     * Emitting those every day would be an obvious tell that data is synthetic.
     */
    private static final int DAILY = 1;
    private static final int WEEKLY = 7;
    private static final int MONTHLY = 30;
    private static final int ONCE = -1;

    private static final Map<String, Integer> CADENCE_DAYS = new HashMap<>();

    static {
        // Height never changes in the real feed - 1.83 m in every stored row.
        CADENCE_DAYS.put("height", ONCE);
        // Body composition arrives when the user steps on a scale.
        CADENCE_DAYS.put("weight", MONTHLY);
        CADENCE_DAYS.put("body_fat", MONTHLY);
        CADENCE_DAYS.put("basal_metabolic_rate", MONTHLY);
        // 1 row on the watch, 4 on the ring across the same window.
        CADENCE_DAYS.put("resting_heart_rate", WEEKLY);
    }

    /**
     * Whether this data type should produce a reading on the given day of a run.
     * Types with no entry default to daily, preserving existing behaviour.
     *
     * @param dayIndex 0-based day number within the generation window
     */
    public static boolean emitsOnDay(String dataType, int dayIndex) {
        Integer every = CADENCE_DAYS.get(dataType);
        if (every == null) return true;
        if (every == ONCE) return dayIndex == 0;
        return dayIndex % every == 0;
    }

    public static boolean isKnown(String dataType) {
        return VALUE_KEY.containsKey(dataType) || UNIT.containsKey(dataType);
    }

    public static String unitFor(String dataType) {
        return UNIT.get(dataType);
    }

    /**
     * Convert a generically generated value into the shape a real device writes.
     * Values already in object form, and types we have no mapping for, are returned
     * untouched so existing device types keep their current behaviour.
     */
    public static Object wrap(String dataType, Object raw, Random random) {
        if (raw instanceof Map) {
            return raw; // already shaped (combined configs, blood_pressure, position…)
        }

        if ("sleep_stage".equals(dataType)) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("stage", raw instanceof String ? raw : "light");
            // Real stage rows carry variable run lengths, not a fixed 1 minute.
            v.put("duration_minutes", 1 + random.nextInt(45));
            return v;
        }

        if ("exercise_session".equals(dataType)) {
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("title", null);
            v.put("exercise_type", 79); // the value every observed session uses
            int minutes = raw instanceof Number ? (int) Math.round(((Number) raw).doubleValue()) : 0;
            v.put("duration_minutes", minutes > 0 ? minutes : 10 + random.nextInt(50));
            return v;
        }

        String key = VALUE_KEY.get(dataType);
        if (key == null || !(raw instanceof Number)) {
            return raw;
        }

        double d = ((Number) raw).doubleValue();
        Map<String, Object> v = new LinkedHashMap<>();
        v.put(key, INTEGRAL.contains(dataType) ? (Object) (int) Math.round(d) : (Object) d);
        return v;
    }
}
