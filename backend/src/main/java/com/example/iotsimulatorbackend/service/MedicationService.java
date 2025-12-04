package com.example.iotsimulatorbackend.service;

import com.example.iotsimulatorbackend.model.MedicationSimulationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MedicationService {

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${supabase.apikey}")
    private String supabaseApiKey;

    @Value("${supabase.medication-schedules-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_schedules}")
    private String medicationSchedulesUrl;

    @Value("${supabase.medication-adherence-logs-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/medication_adherence_logs}")
    private String medicationAdherenceLogsUrl;

    @Value("${supabase.elderly-persons-url:https://wiyfcvypeifbdaqnfgrr.supabase.co/rest/v1/elderly_persons}")
    private String elderlyPersonsUrl;

    private static final int MAX_DAYS_BACK = 15;
    private static final Random random = new Random();

    // Status distribution: 70% taken, 20% late, 10% missed
    private static final double TAKEN_PROBABILITY = 0.70;
    private static final double LATE_PROBABILITY = 0.20;
    // missed = 1 - taken - late = 0.10

    /**
     * Simulate medication adherence for an elderly person.
     * Creates logs for all active medication schedules for the past 15 days.
     */
    public MedicationSimulationResponse simulateMedicationAdherence(String profileId) {
        try {
            HttpHeaders headers = createHeaders();
            HttpEntity<String> entity = new HttpEntity<>(headers);

            // Step 1: Get elderly_person_id from profile
            String elderlyPersonId = getElderlyPersonId(profileId, entity);
            if (elderlyPersonId == null) {
                return new MedicationSimulationResponse(false, "No elderly person found for this profile");
            }
            System.out.println("Simulating medication for elderly person: " + elderlyPersonId);

            // Step 2: Get all active medication schedules for this elderly person
            List<Map<String, Object>> schedules = getActiveMedicationSchedules(elderlyPersonId, entity);
            if (schedules.isEmpty()) {
                return new MedicationSimulationResponse(false, "No active medication schedules found for this elderly person");
            }
            System.out.println("Found " + schedules.size() + " active medication schedules");

            // Step 3: For each schedule, generate missing logs
            List<Map<String, Object>> allLogsCreated = new ArrayList<>();
            int takenCount = 0, lateCount = 0, missedCount = 0;

            for (Map<String, Object> schedule : schedules) {
                String scheduleId = (String) schedule.get("id");
                String medicationName = (String) schedule.get("medication_name");
                List<String> times = extractTimes(schedule.get("times"));
                LocalDate startDate = parseDate((String) schedule.get("start_date"));
                LocalDate endDate = schedule.get("end_date") != null ? parseDate((String) schedule.get("end_date")) : null;

                System.out.println("Processing schedule: " + medicationName + " (ID: " + scheduleId + ")");
                System.out.println("  Times: " + times + ", Start: " + startDate + ", End: " + endDate);

                // Get existing logs for this schedule to find the last logged timestamp
                LocalDateTime lastLoggedTimestamp = getLastLoggedTimestamp(scheduleId, elderlyPersonId, entity);
                System.out.println("  Last logged timestamp: " + lastLoggedTimestamp);

                // Calculate the date range to simulate
                LocalDate today = LocalDate.now();
                LocalDateTime now = LocalDateTime.now();
                LocalDate simulationStartDate = today.minusDays(MAX_DAYS_BACK);

                // Adjust start date based on schedule start date and last logged timestamp
                if (startDate.isAfter(simulationStartDate)) {
                    simulationStartDate = startDate;
                }
                if (lastLoggedTimestamp != null && lastLoggedTimestamp.toLocalDate().isAfter(simulationStartDate)) {
                    // Start from the day after the last log
                    simulationStartDate = lastLoggedTimestamp.toLocalDate();
                }

                // Respect end date if set
                LocalDate simulationEndDate = today;
                if (endDate != null && endDate.isBefore(today)) {
                    simulationEndDate = endDate;
                }

                System.out.println("  Simulation range: " + simulationStartDate + " to " + simulationEndDate);

                // Generate logs for each day and each scheduled time
                for (LocalDate date = simulationStartDate; !date.isAfter(simulationEndDate); date = date.plusDays(1)) {
                    for (String scheduledTime : times) {
                        LocalDateTime scheduledDateTime = parseScheduledDateTime(date, scheduledTime);

                        // Skip if this is in the future
                        if (scheduledDateTime.isAfter(now)) {
                            continue;
                        }

                        // Always check if we already have a log for this exact date and scheduled_time
                        // This prevents duplicate entries on subsequent clicks
                        if (hasExistingLog(scheduleId, elderlyPersonId, date, scheduledTime, entity)) {
                            System.out.println("    Skipping existing log for " + date + " " + scheduledTime);
                            continue;
                        }

                        // Generate the log
                        Map<String, Object> log = generateAdherenceLog(scheduleId, elderlyPersonId, scheduledTime, scheduledDateTime);

                        // Insert the log
                        boolean inserted = insertAdherenceLog(log, entity);
                        if (inserted) {
                            allLogsCreated.add(log);
                            String status = (String) log.get("status");
                            switch (status) {
                                case "taken": takenCount++; break;
                                case "late": lateCount++; break;
                                case "missed": missedCount++; break;
                            }
                        }
                    }
                }
            }

            String message;
            if (allLogsCreated.isEmpty()) {
                message = "No new logs needed - all medication schedules for this person already have adherence logs up to the current time";
            } else {
                message = "Successfully created " + allLogsCreated.size() + " medication adherence logs";
            }

            MedicationSimulationResponse response = new MedicationSimulationResponse(true, message);
            response.setTotalLogsCreated(allLogsCreated.size());
            response.setTakenCount(takenCount);
            response.setLateCount(lateCount);
            response.setMissedCount(missedCount);
            response.setLogsCreated(allLogsCreated);

            System.out.println("Simulation complete: " + allLogsCreated.size() + " logs created " +
                "(taken: " + takenCount + ", late: " + lateCount + ", missed: " + missedCount + ")");

            return response;

        } catch (Exception e) {
            System.err.println("Error simulating medication adherence: " + e.getMessage());
            e.printStackTrace();
            return new MedicationSimulationResponse(false, "Error: " + e.getMessage());
        }
    }

    private HttpHeaders createHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("apikey", supabaseApiKey);
        headers.set("Authorization", "Bearer " + supabaseApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Prefer", "return=minimal");
        return headers;
    }

    private String getElderlyPersonId(String profileId, HttpEntity<String> entity) throws Exception {
        String url = elderlyPersonsUrl + "?user_id=eq." + profileId + "&select=id";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode jsonArray = objectMapper.readTree(response.getBody());

        if (jsonArray.size() > 0) {
            return jsonArray.get(0).get("id").asText();
        }
        return null;
    }

    private List<Map<String, Object>> getActiveMedicationSchedules(String elderlyPersonId, HttpEntity<String> entity) throws Exception {
        String url = medicationSchedulesUrl + "?elderly_person_id=eq." + elderlyPersonId + "&is_active=eq.true";
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode jsonArray = objectMapper.readTree(response.getBody());

        List<Map<String, Object>> schedules = new ArrayList<>();
        for (JsonNode node : jsonArray) {
            Map<String, Object> schedule = objectMapper.convertValue(node, Map.class);
            schedules.add(schedule);
        }
        return schedules;
    }

    @SuppressWarnings("unchecked")
    private List<String> extractTimes(Object timesObj) {
        if (timesObj == null) return Collections.emptyList();

        if (timesObj instanceof List) {
            return (List<String>) timesObj;
        }
        if (timesObj instanceof String) {
            // Parse JSON array string like "[\"08:00\",\"20:00\"]"
            try {
                return objectMapper.readValue((String) timesObj, List.class);
            } catch (Exception e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null) return null;
        // Handle various date formats
        if (dateStr.contains("T")) {
            return LocalDate.parse(dateStr.substring(0, 10));
        }
        return LocalDate.parse(dateStr);
    }

    private LocalDateTime parseScheduledDateTime(LocalDate date, String time) {
        // Time format is "HH:mm"
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = Integer.parseInt(parts[1]);
        return date.atTime(hour, minute);
    }

    private LocalDateTime getLastLoggedTimestamp(String scheduleId, String elderlyPersonId, HttpEntity<String> entity) throws Exception {
        String url = medicationAdherenceLogsUrl +
            "?schedule_id=eq." + scheduleId +
            "&elderly_person_id=eq." + elderlyPersonId +
            "&order=timestamp.desc&limit=1";

        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        JsonNode jsonArray = objectMapper.readTree(response.getBody());

        if (jsonArray.size() > 0 && jsonArray.get(0).has("timestamp")) {
            String timestamp = jsonArray.get(0).get("timestamp").asText();
            return parseTimestamp(timestamp);
        }
        return null;
    }

    private LocalDateTime parseTimestamp(String timestamp) {
        // Handle format like "2025-12-03 08:09:00+00"
        if (timestamp.contains("+")) {
            timestamp = timestamp.substring(0, timestamp.indexOf("+"));
        }
        timestamp = timestamp.replace(" ", "T");
        return LocalDateTime.parse(timestamp);
    }

    private boolean hasExistingLog(String scheduleId, String elderlyPersonId, LocalDate date, String scheduledTime, HttpEntity<String> entity) {
        try {
            // Query all logs for this schedule, elderly person, and scheduled_time
            // Then filter by date in code to avoid timestamp format issues
            String url = medicationAdherenceLogsUrl +
                "?schedule_id=eq." + scheduleId +
                "&elderly_person_id=eq." + elderlyPersonId +
                "&scheduled_time=eq." + scheduledTime +
                "&select=id,timestamp";

            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            JsonNode jsonArray = objectMapper.readTree(response.getBody());

            // Check if any log exists for this specific date
            for (JsonNode logNode : jsonArray) {
                if (logNode.has("timestamp") && !logNode.get("timestamp").isNull()) {
                    String timestampStr = logNode.get("timestamp").asText();
                    // Extract just the date part (first 10 characters: YYYY-MM-DD)
                    String logDateStr = timestampStr.substring(0, 10);
                    if (logDateStr.equals(date.toString())) {
                        System.out.println("    Found existing log for " + date + " " + scheduledTime);
                        return true;
                    }
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error checking existing log: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private Map<String, Object> generateAdherenceLog(String scheduleId, String elderlyPersonId,
                                                      String scheduledTime, LocalDateTime scheduledDateTime) {
        Map<String, Object> log = new HashMap<>();
        log.put("id", UUID.randomUUID().toString());
        log.put("schedule_id", scheduleId);
        log.put("elderly_person_id", elderlyPersonId);
        log.put("scheduled_time", scheduledTime);

        // Determine status based on probability distribution
        double rand = random.nextDouble();
        String status;
        LocalDateTime actualTimestamp;
        boolean dispenserConfirmed;
        boolean caregiverAlerted;
        String notes = null;

        if (rand < TAKEN_PROBABILITY) {
            // Taken: within 0-10 minutes of scheduled time
            status = "taken";
            int minutesVariance = random.nextInt(11); // 0-10 minutes
            actualTimestamp = scheduledDateTime.plusMinutes(minutesVariance);
            dispenserConfirmed = random.nextDouble() < 0.85; // 85% chance dispenser confirmed
            caregiverAlerted = false;
        } else if (rand < TAKEN_PROBABILITY + LATE_PROBABILITY) {
            // Late: 30-90 minutes after scheduled time
            status = "late";
            int minutesLate = 30 + random.nextInt(61); // 30-90 minutes
            actualTimestamp = scheduledDateTime.plusMinutes(minutesLate);
            dispenserConfirmed = false;
            caregiverAlerted = false;
        } else {
            // Missed: 2+ hours after scheduled time
            status = "missed";
            int minutesMissed = 120 + random.nextInt(61); // 2-3 hours after
            actualTimestamp = scheduledDateTime.plusMinutes(minutesMissed);
            dispenserConfirmed = false;
            caregiverAlerted = true;
            // Add note based on time of day
            int hour = scheduledDateTime.getHour();
            if (hour < 12) {
                notes = "Missed morning dose";
            } else if (hour < 18) {
                notes = "Missed afternoon dose";
            } else {
                notes = "Missed evening dose";
            }
        }

        log.put("status", status);
        log.put("timestamp", formatTimestamp(actualTimestamp));
        log.put("dispenser_confirmed", dispenserConfirmed);
        log.put("caregiver_alerted", caregiverAlerted);
        if (notes != null) {
            log.put("notes", notes);
        }

        return log;
    }

    private String formatTimestamp(LocalDateTime dateTime) {
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "+00";
    }

    private boolean insertAdherenceLog(Map<String, Object> log, HttpEntity<String> getEntity) {
        try {
            HttpHeaders headers = createHeaders();
            String jsonBody = objectMapper.writeValueAsString(log);
            HttpEntity<String> postEntity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                medicationAdherenceLogsUrl,
                HttpMethod.POST,
                postEntity,
                String.class
            );

            return response.getStatusCode().is2xxSuccessful();
        } catch (Exception e) {
            System.err.println("Error inserting adherence log: " + e.getMessage());
            return false;
        }
    }

    /**
     * Check if a device is a medication dispenser device
     */
    public boolean isMedicationDevice(String deviceType) {
        return "medication".equalsIgnoreCase(deviceType);
    }
}
