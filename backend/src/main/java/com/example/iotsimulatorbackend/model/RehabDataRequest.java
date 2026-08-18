package com.example.iotsimulatorbackend.model;

/**
 * Request to generate a rehabilitation / recovery trajectory for one person.
 *
 * Covers only the signals a wearable cannot produce - the rehab manual check-ins and the
 * IRQ recovery log. The device-derived half of both scores (mobility, cardio, body
 * composition, sleep) already comes from ordinary historical device generation.
 */
public class RehabDataRequest {

    /** "improvement" or "degradation". */
    public static final String IMPROVEMENT = "improvement";
    public static final String DEGRADATION = "degradation";

    private String elderlyPersonId;
    private String trajectory = IMPROVEMENT;

    /**
     * Length of the program, in days back from today. Must be at least twice
     * baselineWindowDays or compute_rehab_progress_for_person refuses to score - the
     * baseline and recent windows would overlap.
     */
    private int days = 60;

    private int baselineWindowDays = 14;

    private String protocolKey = "general_mobility";

    /** Also write the IRQ tables (craving check-ins + sobriety events). */
    private boolean includeRecovery = true;

    /** Run the rehab scoring RPC and irq-compute once the data is in place. */
    private boolean scoreAfterGenerate = true;

    public String getElderlyPersonId() {
        return elderlyPersonId;
    }

    public void setElderlyPersonId(String elderlyPersonId) {
        this.elderlyPersonId = elderlyPersonId;
    }

    public String getTrajectory() {
        return trajectory;
    }

    public void setTrajectory(String trajectory) {
        this.trajectory = trajectory;
    }

    public boolean isDegradation() {
        return DEGRADATION.equalsIgnoreCase(trajectory);
    }

    public int getDays() {
        return days;
    }

    public void setDays(int days) {
        this.days = days;
    }

    public int getBaselineWindowDays() {
        return baselineWindowDays;
    }

    public void setBaselineWindowDays(int baselineWindowDays) {
        this.baselineWindowDays = baselineWindowDays;
    }

    public String getProtocolKey() {
        return protocolKey;
    }

    public void setProtocolKey(String protocolKey) {
        this.protocolKey = protocolKey;
    }

    public boolean isIncludeRecovery() {
        return includeRecovery;
    }

    public void setIncludeRecovery(boolean includeRecovery) {
        this.includeRecovery = includeRecovery;
    }

    public boolean isScoreAfterGenerate() {
        return scoreAfterGenerate;
    }

    public void setScoreAfterGenerate(boolean scoreAfterGenerate) {
        this.scoreAfterGenerate = scoreAfterGenerate;
    }

    @Override
    public String toString() {
        return "RehabDataRequest{elderlyPersonId='" + elderlyPersonId + "', trajectory='" + trajectory
                + "', days=" + days + ", baselineWindowDays=" + baselineWindowDays
                + ", includeRecovery=" + includeRecovery + "}";
    }
}
