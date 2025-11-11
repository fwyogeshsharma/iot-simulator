package com.example.iotsimulatorbackend.controller;

import com.example.iotsimulatorbackend.model.DataTypeInfo;
import com.example.iotsimulatorbackend.model.DataTypeConfig;
import com.example.iotsimulatorbackend.model.Device;
import com.example.iotsimulatorbackend.model.SimulationRequest;
import com.example.iotsimulatorbackend.model.SimulationResponse;
import com.example.iotsimulatorbackend.model.SimulationStatistics;
import com.example.iotsimulatorbackend.model.GeofencePlace;
import com.example.iotsimulatorbackend.service.SimulatorService;
import com.example.iotsimulatorbackend.service.SimulationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class SimulatorController {

    @Autowired
    private SimulatorService service;

    @Autowired
    private SimulationManager simulationManager;

    @GetMapping("/devices/{elderlyPersonId}")
    public ResponseEntity<List<Device>> getDevices(@PathVariable String elderlyPersonId) {
        return ResponseEntity.ok(service.getDevicesByElderlyPersonId(elderlyPersonId));
    }

    @GetMapping("/data-types/{deviceId}")
    public ResponseEntity<List<DataTypeConfig>> getDataTypes(@PathVariable String deviceId) {
        return ResponseEntity.ok(service.getDataTypesByDeviceId(deviceId));
    }

    @GetMapping("/ranges/{dataType}")
    public ResponseEntity<DataTypeInfo> getRange(@PathVariable String dataType) {
        DataTypeInfo info = service.getRangeForDataType(dataType);
        return info != null ? ResponseEntity.ok(info) : ResponseEntity.notFound().build();
    }

    @GetMapping("/units/{dataType}")
    public ResponseEntity<String> getUnit(@PathVariable String dataType) {
        return ResponseEntity.ok(service.getUnitForDataType(dataType));
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

    @GetMapping("/simulation/statistics/{simulationId}")
    public ResponseEntity<SimulationStatistics> getSimulationStatistics(@PathVariable String simulationId) {
        SimulationStatistics statistics = simulationManager.getSimulationStatistics(simulationId);

        if (statistics == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(statistics);
    }

    @GetMapping("/geofence-places/{elderlyPersonId}")
    public ResponseEntity<List<GeofencePlace>> getGeofencePlaces(@PathVariable String elderlyPersonId) {
        return ResponseEntity.ok(service.getGeofencePlacesByElderlyPersonId(elderlyPersonId));
    }
}
