package com.example.iotsimulatorbackend.controller;

import com.example.iotsimulatorbackend.model.DataTypeConfig;
import com.example.iotsimulatorbackend.model.Device;
import com.example.iotsimulatorbackend.model.SimulationRequest;
import com.example.iotsimulatorbackend.model.SimulationResponse;
import com.example.iotsimulatorbackend.model.SimulationStatistics;
import com.example.iotsimulatorbackend.model.GeofencePlace;
import com.example.iotsimulatorbackend.model.SensorGenerateRequest;
import com.example.iotsimulatorbackend.model.DeviceCompany;
import com.example.iotsimulatorbackend.model.DeviceModel;
import com.example.iotsimulatorbackend.model.MedicationSimulationRequest;
import com.example.iotsimulatorbackend.model.MedicationSimulationResponse;
import com.example.iotsimulatorbackend.model.HistoricalDataRequest;
import com.example.iotsimulatorbackend.model.HistoricalDataResponse;
import com.example.iotsimulatorbackend.model.HistoricalJobStatus;
import com.example.iotsimulatorbackend.model.RehabDataRequest;
import com.example.iotsimulatorbackend.service.SimulatorService;
import com.example.iotsimulatorbackend.service.SimulationManager;
import com.example.iotsimulatorbackend.service.MedicationService;
import com.example.iotsimulatorbackend.service.HistoricalDataService;
import com.example.iotsimulatorbackend.service.RehabDataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class SimulatorController {

    @Autowired
    private SimulatorService service;

    @Autowired
    private SimulationManager simulationManager;

    @Autowired
    private MedicationService medicationService;

    @Autowired
    private HistoricalDataService historicalDataService;

    @Autowired
    private RehabDataService rehabDataService;

    @GetMapping("/devices/{elderlyPersonId}")
    public ResponseEntity<List<Device>> getDevices(@PathVariable String elderlyPersonId) {
        return ResponseEntity.ok(service.getDevicesByElderlyPersonId(elderlyPersonId));
    }

    @GetMapping("/data-types/{deviceId}")
    public ResponseEntity<List<DataTypeConfig>> getDataTypes(@PathVariable String deviceId) {
        return ResponseEntity.ok(service.getDataTypesByDeviceId(deviceId));
    }

    @PostMapping("/simulation/start")
    public ResponseEntity<SimulationResponse> startSimulation(@RequestBody SimulationRequest request) {
        String simulationId = simulationManager.startSimulation(
            request.getElderlyPersonId(),
            request.getDeviceIds()
        );

        if (simulationId == null) {
            return ResponseEntity.badRequest()
                .body(new SimulationResponse(null, "error", request.getElderlyPersonId(),
                    0, 0, "No devices found for simulation"));
        }

        return ResponseEntity.ok(new SimulationResponse(
            simulationId,
            "running",
            request.getElderlyPersonId(),
            request.getDeviceIds() != null ? request.getDeviceIds().size() : 0,
            0,
            "Simulation started successfully"
        ));
    }

    @PostMapping("/simulation/stop")
    public ResponseEntity<SimulationResponse> stopSimulation(@RequestParam String simulationId) {
        boolean stopped = simulationManager.stopSimulation(simulationId);

        return ResponseEntity.ok(new SimulationResponse(
            simulationId,
            stopped ? "stopped" : "not_found",
            null,
            0,
            0,
            stopped ? "Simulation stopped successfully" : "Simulation not found"
        ));
    }

    @GetMapping("/simulation/status/{simulationId}")
    public ResponseEntity<SimulationResponse> getSimulationStatus(@PathVariable String simulationId) {
        boolean isRunning = simulationManager.isSimulationRunning(simulationId);

        return ResponseEntity.ok(new SimulationResponse(
            simulationId,
            isRunning ? "running" : "stopped",
            null,
            0,
            0,
            isRunning ? "Simulation is running" : "Simulation is not running"
        ));
    }

    @GetMapping("/simulation/active/{elderlyPersonId}")
    public ResponseEntity<SimulationResponse> getActiveSimulation(@PathVariable String elderlyPersonId) {
        String activeSimulationId = simulationManager.getActiveSimulationForElderlyPerson(elderlyPersonId);

        if (activeSimulationId == null) {
            return ResponseEntity.ok(new SimulationResponse(
                null,
                "none",
                elderlyPersonId,
                0,
                0,
                "No active simulation for this elderly person"
            ));
        }

        // Get the device IDs being simulated
        java.util.List<String> deviceIds = simulationManager.getSimulationDeviceIds(activeSimulationId);
        int deviceCount = deviceIds != null ? deviceIds.size() : 0;

        return ResponseEntity.ok(new SimulationResponse(
            activeSimulationId,
            "running",
            elderlyPersonId,
            deviceCount,
            0,
            "Active simulation found",
            deviceIds
        ));
    }

    @GetMapping("/simulation/statistics/{simulationId}")
    public ResponseEntity<SimulationStatistics> getSimulationStatistics(@PathVariable String simulationId) {
        SimulationStatistics statistics = simulationManager.getSimulationStatistics(simulationId);

        if (statistics == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statistics);
    }

    @PostMapping("/sensor/generate")
    public ResponseEntity<?> generateSensorData(@RequestBody SensorGenerateRequest request) {
        try {
            Map<String, Object> result = simulationManager.generateAndSendSensorData(
                request.getDeviceId(),
                request.getDataType(),
                request.getLocation()
            );
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );
        }
    }

    @PostMapping("/sensor/generate-model-data/{deviceId}")
    public ResponseEntity<?> generateModelBasedData(@PathVariable String deviceId) {
        try {
            Map<String, Object> result = simulationManager.generateAndSendModelBasedData(deviceId);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                Map.of("error", e.getMessage())
            );
        }
    }

    @GetMapping("/geofence-places/{elderlyPersonId}")
    public ResponseEntity<List<GeofencePlace>> getGeofencePlaces(@PathVariable String elderlyPersonId) {
        return ResponseEntity.ok(service.getGeofencePlacesByElderlyPersonId(elderlyPersonId));
    }

    @GetMapping("/companies")
    public ResponseEntity<List<DeviceCompany>> getAllCompanies() {
        return ResponseEntity.ok(service.getAllCompanies());
    }

    @GetMapping("/models/{companyId}")
    public ResponseEntity<List<DeviceModel>> getModelsByCompanyId(@PathVariable String companyId) {
        return ResponseEntity.ok(service.getModelsByCompanyId(companyId));
    }

    @GetMapping("/model/{modelId}")
    public ResponseEntity<DeviceModel> getModelById(@PathVariable String modelId) {
        DeviceModel model = service.getModelById(modelId);
        if (model == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(model);
    }

    @PostMapping("/medication/simulate/{profileId}")
    public ResponseEntity<MedicationSimulationResponse> simulateMedicationAdherence(
            @PathVariable String profileId) {
        MedicationSimulationResponse response = medicationService.simulateMedicationAdherence(profileId);
        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        } else {
            return ResponseEntity.badRequest().body(response);
        }
    }

    @PostMapping("/simulation/generate-historical")
    public ResponseEntity<HistoricalDataResponse> generateHistoricalData(
            @RequestBody HistoricalDataRequest request) {
        try {
            String jobId = historicalDataService.generateHistoricalData(request);
            HistoricalDataResponse response = new HistoricalDataResponse(
                jobId,
                "processing",
                "Generating historical data, please wait..."
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(
                new HistoricalDataResponse(null, "failed", "Error: " + e.getMessage())
            );
        }
    }

    /**
     * Conditions the simulator can generate data for, for the UI's picker.
     * Each entry carries min_days / recommended_days so the UI can default the
     * range and warn when the chosen window is too short to be analysable.
     */
    /**
     * Generate a rehab / recovery trajectory for one person: the enrollment that anchors the
     * baseline window, the daily pain and adherence check-ins, and the IRQ craving and sobriety
     * log. Device-derived metrics are not touched - those come from historical device generation.
     *
     * Synchronous on purpose. It writes a few hundred rows at most, unlike historical device
     * generation which needs the async job pattern.
     */
    @PostMapping("/rehab/generate")
    public ResponseEntity<Map<String, Object>> generateRehabData(@RequestBody RehabDataRequest request) {
        try {
            return ResponseEntity.ok(rehabDataService.generate(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    @GetMapping("/disease-profiles")
    public ResponseEntity<List<com.example.iotsimulatorbackend.model.DiseaseProfile>> getDiseaseProfiles() {
        return ResponseEntity.ok(service.getDiseaseProfiles());
    }

    @GetMapping("/simulation/historical-status/{jobId}")
    public ResponseEntity<HistoricalJobStatus> getHistoricalJobStatus(@PathVariable String jobId) {
        HistoricalJobStatus status = historicalDataService.getJobStatus(jobId);
        if (status == null) {
            HistoricalJobStatus notFound = new HistoricalJobStatus(jobId, "not_found");
            notFound.setErrorMessage("Job not found");
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(status);
    }
}
