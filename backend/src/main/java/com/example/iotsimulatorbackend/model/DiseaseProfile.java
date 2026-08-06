package com.example.iotsimulatorbackend.model;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A row from public.disease_profiles: how to shape synthetic sleep/vital data
 * for one condition.
 *
 * The `profile` map mirrors the JSONB column. Nested range values are two-element
 * [min, max] lists; the generator samples uniformly within them per night.
 *
 * These are simulation parameters for exercising detection algorithms, not
 * clinical thresholds.
 */
public class DiseaseProfile {

    private String code;
    private String name;
    private String category;
    private int minDays;
    private int recommendedDays;
    private int confidence;
    private List<String> requiredSignals;
    private Map<String, Object> profile;
    private String description;

    public DiseaseProfile() {
    }

    // ---- range helpers -----------------------------------------------------

    /**
     * Look up a [min, max] range at profile.section.key.
     * Returns the supplied fallback when the profile does not define it, so a
     * sparse profile still generates plausible data.
     */
    @SuppressWarnings("unchecked")
    public double[] range(String section, String key, double fallbackMin, double fallbackMax) {
        Object sec = profile == null ? null : profile.get(section);
        if (sec instanceof Map) {
            Object raw = ((Map<String, Object>) sec).get(key);
            if (raw instanceof List) {
                List<?> pair = (List<?>) raw;
                if (pair.size() >= 2 && pair.get(0) instanceof Number && pair.get(1) instanceof Number) {
                    return new double[]{
                            ((Number) pair.get(0)).doubleValue(),
                            ((Number) pair.get(1)).doubleValue()
                    };
                }
            }
        }
        return new double[]{fallbackMin, fallbackMax};
    }

    /** Scalar lookup at profile.section.key (used for schedule strings/numbers). */
    @SuppressWarnings("unchecked")
    public Object scalar(String section, String key) {
        Object sec = profile == null ? null : profile.get(section);
        if (sec instanceof Map) {
            return ((Map<String, Object>) sec).get(key);
        }
        return null;
    }

    public double scalarDouble(String section, String key, double fallback) {
        Object v = scalar(section, key);
        return (v instanceof Number) ? ((Number) v).doubleValue() : fallback;
    }

    /** Long-horizon drift: which field moves, and by how much per 30 days. */
    @SuppressWarnings("unchecked")
    public String trendField() {
        Object t = profile == null ? null : profile.get("trend");
        if (t instanceof Map) {
            Object f = ((Map<String, Object>) t).get("field");
            return f == null ? null : f.toString();
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public double trendPer30Days() {
        Object t = profile == null ? null : profile.get("trend");
        if (t instanceof Map) {
            Object v = ((Map<String, Object>) t).get("per_30d");
            if (v instanceof Number) return ((Number) v).doubleValue();
        }
        return 0.0;
    }

    // ---- getters / setters -------------------------------------------------

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public int getMinDays() { return minDays; }
    public void setMinDays(int minDays) { this.minDays = minDays; }

    public int getRecommendedDays() { return recommendedDays; }
    public void setRecommendedDays(int recommendedDays) { this.recommendedDays = recommendedDays; }

    public int getConfidence() { return confidence; }
    public void setConfidence(int confidence) { this.confidence = confidence; }

    public List<String> getRequiredSignals() {
        return requiredSignals == null ? Collections.emptyList() : requiredSignals;
    }
    public void setRequiredSignals(List<String> requiredSignals) { this.requiredSignals = requiredSignals; }

    public Map<String, Object> getProfile() { return profile; }
    public void setProfile(Map<String, Object> profile) { this.profile = profile; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    @Override
    public String toString() {
        return "DiseaseProfile{" + code + " '" + name + "' " + minDays + "-" + recommendedDays + "d}";
    }
}
