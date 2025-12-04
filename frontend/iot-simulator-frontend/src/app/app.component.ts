import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';
import { interval, Subscription } from 'rxjs';

interface Profile {
  id: string;
  email: string;
  full_name: string;
}

interface Device {
  id: string;
  deviceName: string;
  deviceId: string;
  apiKey: string;
  deviceType?: string;
  description?: string;
  elderlyPersonId?: string;
  location?: string;
  companyId?: string;
  modelId?: string;
  companyName?: string;
  modelName?: string;
  modelSpecifications?: any;
}

interface SimulationResponse {
  simulationId: string;
  status: string;
  message: string;
  deviceCount?: number;
  dataTypeCount?: number;
}

interface SimulationStatistics {
  simulationId: string;
  totalDataPointsGenerated: number;
  totalDataPointsSuccessful: number;
  totalDataPointsFailed: number;
  elapsedTimeSeconds: number;
  successRate: number;
  dataPointsPerMinute: number;
  deviceStats?: any;
  dataTypeStats?: any;
}

interface DataTypeConfig {
  dataType: string;
  displayName: string;
  unit: string;
  minValue?: number;
  maxValue?: number;
}

interface GenerateSensorResponse {
  success: boolean;
  message: string;
  deviceId?: string;
  dataType?: string;
  displayName?: string;
  value?: number | any;
  unit?: string;
  error?: string;
  generatedLocations?: Array<{
    geofenceName: string;
    latitude: number;
    longitude: number;
    radius: number;
  }>;
  geofencesProcessed?: number;
  totalGeofences?: number;
}

interface ModelDataResponse {
  success: boolean;
  message: string;
  deviceId?: string;
  modelName?: string;
  companyName?: string;
  totalDataPoints?: number;
  successCount?: number;
  failCount?: number;
  dataPoints?: Array<{
    data_type: string;
    value: any;
    unit?: string;
  }>;
  elapsedMs?: number;
  error?: string;
}

interface MedicationSimulationResponse {
  success: boolean;
  message: string;
  totalLogsCreated?: number;
  takenCount?: number;
  lateCount?: number;
  missedCount?: number;
  logsCreated?: Array<any>;
}

interface Settings {
  email: string;
  selectedDeviceIds: string[];
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  profiles: Profile[] = [];
  devices: Device[] = [];

  selectedProfile: Profile | null = null;
  selectedDeviceIds: Set<string> = new Set();

  isSimulating = false;
  simulationId: string | null = null;
  simulationStatus = '';
  message = '';

  // Statistics
  statistics: SimulationStatistics | null = null;
  statisticsSubscription: Subscription | null = null;

  // Individual sensor data generation
  dataTypes: DataTypeConfig[] = [];
  selectedSingleDevice: Device | null = null;
  generatingDataTypeId: string | null = null;
  lastGeneratedData: GenerateSensorResponse | null = null;
  generationMessage = '';

  // Model-based data generation
  generatingModelData = false;
  lastModelDataResponse: ModelDataResponse | null = null;
  modelDataMessage = '';

  // Medication adherence simulation
  simulatingMedication = false;
  lastMedicationResponse: MedicationSimulationResponse | null = null;
  medicationSimulationMessage = '';

  settings: Settings | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadProfiles();
    this.loadSettings();
  }

  ngOnDestroy() {
    // Stop statistics polling
    if (this.statisticsSubscription) {
      this.statisticsSubscription.unsubscribe();
    }
    // Stop any active simulation when component is destroyed
    if (this.isSimulating && this.simulationId) {
      this.stopSimulation();
    }
  }

  loadProfiles() {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${environment.profilesApiKey}`,
      'Content-Type': 'application/json',
      'apikey': environment.profilesApiKey
    });
    this.http.get<Profile[]>(environment.profilesUrl, { headers }).subscribe({
      next: (data) => {
        console.log('Profiles loaded:', data);
        this.profiles = data || [];
        if (this.settings) {
          this.selectedProfile = this.profiles.find(p => p.email === this.settings!.email) || null;
          if (this.selectedProfile) this.onProfileChange();
        }
      },
      error: (err) => {
        console.error('Failed to load profiles:', err);
      }
    });
  }

  onProfileChange() {
    // Clear previous state when changing elderly person
    this.selectedDeviceIds.clear();
    this.devices = [];
    this.dataTypes = [];
    this.selectedSingleDevice = null;
    this.lastGeneratedData = null;
    if (!this.selectedProfile) return;

    // Call Supabase directly - query elderly_persons by user_id
    const elderlyPersonsUrl = `${environment.elderlyPersonsUrl}?select=id,full_name&user_id=eq.${this.selectedProfile.id}`;
    console.log('Loading elderly persons from:', elderlyPersonsUrl);

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${environment.profilesApiKey}`,
      'Content-Type': 'application/json',
      'apikey': environment.profilesApiKey
    });

    this.http.get<any[]>(elderlyPersonsUrl, { headers }).subscribe({
      next: (elderlyPersons) => {
        console.log('Elderly persons loaded:', elderlyPersons);

        // For each elderly person, load their devices
        if (elderlyPersons && elderlyPersons.length > 0) {
          // Use the first elderly person's ID to load devices
          const elderlyPersonId = elderlyPersons[0].id;
          this.loadDevicesForElderlyPerson(elderlyPersonId);
        } else {
          this.devices = [];
        }
      },
      error: (err) => {
        console.error('Failed to load elderly persons:', err);
        this.devices = [];
      }
    });
    this.saveSettings();
  }

  loadDevicesForElderlyPerson(elderlyPersonId: string) {
    // Use backend API to get devices with company/model info
    const backendDevicesUrl = `${environment.backendUrl}/devices/${this.selectedProfile?.id}`;
    console.log('Loading devices from backend:', backendDevicesUrl);

    this.http.get<Device[]>(backendDevicesUrl).subscribe({
      next: (data) => {
        console.log('Devices loaded from backend:', data);
        this.devices = data || [];

        // Select all devices by default
        this.selectedDeviceIds = new Set(this.devices.map(d => d.id));

        // Clear data types for new device selection
        this.dataTypes = [];
        this.selectedSingleDevice = null;
        this.lastModelDataResponse = null;
        this.modelDataMessage = '';

        // Restore previously selected devices from settings if available and same elderly person
        if (this.settings && this.settings.selectedDeviceIds && this.settings.selectedDeviceIds.length > 0) {
          // Only restore if we're loading the same elderly person (check by comparing email in settings)
          if (this.settings.email === this.selectedProfile?.email) {
            // Validate that all saved device IDs still exist in current devices
            const validDeviceIds = this.settings.selectedDeviceIds.filter(id =>
              this.devices.some(d => d.id === id)
            );
            if (validDeviceIds.length > 0) {
              this.selectedDeviceIds = new Set(validDeviceIds);
            }
          }
        }

        // Check if only one device is selected and load sensors automatically
        this.checkForSingleDeviceSelection();
      },
      error: (err) => {
        console.error('Failed to load devices from backend:', err);
        this.devices = [];
      }
    });
  }

  toggleDeviceSelection(deviceId: string) {
    if (this.selectedDeviceIds.has(deviceId)) {
      this.selectedDeviceIds.delete(deviceId);
    } else {
      this.selectedDeviceIds.add(deviceId);
    }

    // Load data types if exactly one device is selected
    this.checkForSingleDeviceSelection();
    this.saveSettings();
  }

  selectAllDevices() {
    this.selectedDeviceIds = new Set(this.devices.map(d => d.id));
    this.checkForSingleDeviceSelection();
    this.saveSettings();
  }

  unselectAllDevices() {
    this.selectedDeviceIds.clear();
    this.checkForSingleDeviceSelection();
    this.saveSettings();
  }

  checkForSingleDeviceSelection() {
    if (this.selectedDeviceIds.size === 1) {
      const deviceId = Array.from(this.selectedDeviceIds)[0];
      this.selectedSingleDevice = this.devices.find(d => d.id === deviceId) || null;
      if (this.selectedSingleDevice) {
        this.loadDataTypesForDevice(this.selectedSingleDevice.id);
      }
    } else {
      this.selectedSingleDevice = null;
      this.dataTypes = [];
      this.lastGeneratedData = null;
      this.generationMessage = '';
    }
  }

  loadDataTypesForDevice(deviceId: string) {
    this.http.get<DataTypeConfig[]>(
      `${environment.backendUrl}/data-types/${deviceId}`
    ).subscribe({
      next: (dataTypes) => {
        this.dataTypes = dataTypes;
        console.log('Data types loaded:', dataTypes);
      },
      error: (err) => {
        console.error('Failed to load data types:', err);
        this.dataTypes = [];
      }
    });
  }

  generateSensorData(dataType: string) {
    if (!this.selectedSingleDevice) return;

    this.generatingDataTypeId = dataType;
    this.generationMessage = '';
    this.lastGeneratedData = null;

    const requestBody: any = {
      deviceId: this.selectedSingleDevice.id,
      dataType: dataType
    };

    // Include location if available
    if (this.selectedSingleDevice.location) {
      requestBody.location = this.selectedSingleDevice.location;
    }

    this.http.post<GenerateSensorResponse>(
      `${environment.backendUrl}/sensor/generate`,
      requestBody
    ).subscribe({
      next: (response) => {
        console.log('Sensor data generated:', response);
        this.lastGeneratedData = response;
        if (response.success) {
          // Handle multi-geofence GPS response (multiple locations)
          if (response.generatedLocations && Array.isArray(response.generatedLocations)) {
            const locationCount = response.generatedLocations.length;
            const locationNames = response.generatedLocations.map((loc: any) => loc.geofenceName).join(', ');
            this.generationMessage = `Successfully generated GPS data for ${locationCount} geofence(s): ${locationNames}`;
          } else {
            // Handle single value response (non-GPS or other data types)
            let formattedValue = '';
            if (typeof response.value === 'object' && response.value !== null) {
              if ('latitude' in response.value && 'longitude' in response.value) {
                formattedValue = `Lat: ${response.value.latitude.toFixed(6)}, Lon: ${response.value.longitude.toFixed(6)}`;
              } else {
                formattedValue = JSON.stringify(response.value);
              }
            } else {
              formattedValue = `${response.value} ${response.unit || ''}`;
            }
            this.generationMessage = `Successfully generated ${response.displayName}: ${formattedValue}`;
          }
        } else {
          this.generationMessage = `Error: ${response.message}`;
        }
        this.generatingDataTypeId = null;
      },
      error: (err) => {
        console.error('Failed to generate sensor data:', err);
        this.generationMessage = `Error: ${err.error?.message || err.message}`;
        this.generatingDataTypeId = null;
      }
    });
  }

  generateModelBasedData() {
    if (!this.selectedSingleDevice || !this.selectedSingleDevice.modelId) return;

    this.generatingModelData = true;
    this.modelDataMessage = '';
    this.lastModelDataResponse = null;

    this.http.post<ModelDataResponse>(
      `${environment.backendUrl}/sensor/generate-model-data/${this.selectedSingleDevice.id}`,
      {}
    ).subscribe({
      next: (response) => {
        console.log('Model-based data generated:', response);
        this.lastModelDataResponse = response;
        if (response.success) {
          this.modelDataMessage = `Successfully generated ${response.successCount} data points for ${response.modelName} (${response.companyName})`;
        } else {
          this.modelDataMessage = response.message || 'Failed to generate model data';
        }
        this.generatingModelData = false;
      },
      error: (err) => {
        console.error('Failed to generate model-based data:', err);
        this.modelDataMessage = `Error: ${err.error?.error || err.message}`;
        this.generatingModelData = false;
      }
    });
  }

  hasMedicationDeviceOnly(): boolean {
    // Only show medication simulation when exactly one device is selected
    // and that device is a medication dispenser
    if (this.selectedDeviceIds.size !== 1) return false;
    const selectedDeviceId = Array.from(this.selectedDeviceIds)[0];
    const device = this.devices.find(d => d.id === selectedDeviceId);
    return device?.deviceType === 'medication';
  }

  simulateMedicationAdherence() {
    if (!this.selectedProfile) return;

    this.simulatingMedication = true;
    this.medicationSimulationMessage = '';
    this.lastMedicationResponse = null;

    this.http.post<MedicationSimulationResponse>(
      `${environment.backendUrl}/medication/simulate/${this.selectedProfile.id}`,
      {}
    ).subscribe({
      next: (response) => {
        console.log('Medication adherence simulated:', response);
        this.lastMedicationResponse = response;
        if (response.success) {
          if (response.totalLogsCreated === 0) {
            this.medicationSimulationMessage = response.message || 'No new logs needed - medication adherence is already up to date';
          } else {
            this.medicationSimulationMessage = `Successfully created ${response.totalLogsCreated} medication logs ` +
              `(Taken: ${response.takenCount}, Late: ${response.lateCount}, Missed: ${response.missedCount})`;
          }
        } else {
          this.medicationSimulationMessage = response.message || 'Failed to simulate medication adherence';
        }
        this.simulatingMedication = false;
      },
      error: (err) => {
        console.error('Failed to simulate medication adherence:', err);
        this.medicationSimulationMessage = `Error: ${err.error?.message || err.message}`;
        this.simulatingMedication = false;
      }
    });
  }

  isDeviceSelected(deviceId: string): boolean {
    return this.selectedDeviceIds.has(deviceId);
  }

  startSimulation() {
    if (!this.selectedProfile) {
      this.message = 'Please select an elderly person';
      return;
    }

    const payload = {
      elderlyPersonId: this.selectedProfile.id,
      deviceIds: Array.from(this.selectedDeviceIds) // Can be empty to simulate all
    };

    console.log('Starting simulation with payload:', payload);

    this.http.post<SimulationResponse>(
      `${environment.backendUrl}/simulation/start`,
      payload
    ).subscribe({
      next: (response) => {
        console.log('Simulation started:', response);
        this.simulationId = response.simulationId;
        this.isSimulating = true;
        this.simulationStatus = 'running';
        this.message = `Simulation started! Generating data for ${this.selectedDeviceIds.size || this.devices.length} device(s)`;

        // Start polling for statistics every 2 seconds
        this.startStatisticsPolling();
      },
      error: (err) => {
        console.error('Failed to start simulation:', err);
        this.message = `Error: ${err.error?.message || err.message}`;
      }
    });
  }

  stopSimulation() {
    if (!this.simulationId) {
      this.message = 'No active simulation to stop';
      return;
    }

    console.log('Stopping simulation:', this.simulationId);

    // Stop statistics polling
    this.stopStatisticsPolling();

    this.http.post<SimulationResponse>(
      `${environment.backendUrl}/simulation/stop`,
      {},
      { params: { simulationId: this.simulationId } }
    ).subscribe({
      next: (response) => {
        console.log('Simulation stopped:', response);
        this.isSimulating = false;
        this.simulationStatus = 'stopped';
        this.simulationId = null;
        this.message = 'Simulation stopped successfully';
      },
      error: (err) => {
        console.error('Failed to stop simulation:', err);
        this.message = `Error stopping simulation: ${err.message}`;
        this.isSimulating = false;
        this.simulationId = null;
      }
    });
  }

  startStatisticsPolling() {
    // Clear any existing subscription
    this.stopStatisticsPolling();

    // Poll statistics every 2 seconds
    this.statisticsSubscription = interval(2000).subscribe(() => {
      this.fetchStatistics();
    });

    // Fetch immediately
    this.fetchStatistics();
  }

  stopStatisticsPolling() {
    if (this.statisticsSubscription) {
      this.statisticsSubscription.unsubscribe();
      this.statisticsSubscription = null;
    }
  }

  fetchStatistics() {
    if (!this.simulationId) return;

    this.http.get<SimulationStatistics>(
      `${environment.backendUrl}/simulation/statistics/${this.simulationId}`
    ).subscribe({
      next: (stats) => {
        this.statistics = stats;
        console.log('Statistics updated:', stats);
      },
      error: (err) => {
        console.error('Failed to fetch statistics:', err);
      }
    });
  }

  saveSettings() {
    if (this.selectedProfile) {
      this.settings = {
        email: this.selectedProfile.email,
        selectedDeviceIds: Array.from(this.selectedDeviceIds)
      };
      localStorage.setItem('simulatorSettings', JSON.stringify(this.settings));
    }
  }

  loadSettings() {
    const saved = localStorage.getItem('simulatorSettings');
    if (saved) {
      this.settings = JSON.parse(saved);
    }
  }

  resetSettings() {
    localStorage.removeItem('simulatorSettings');
    this.settings = null;
    this.selectedProfile = null;
    this.selectedDeviceIds.clear();
    this.devices = [];
    this.statistics = null;
    // Clear individual sensor data generation state
    this.dataTypes = [];
    this.selectedSingleDevice = null;
    this.generatingDataTypeId = null;
    this.lastGeneratedData = null;
    this.generationMessage = '';
    // Clear model-based data generation state
    this.generatingModelData = false;
    this.lastModelDataResponse = null;
    this.modelDataMessage = '';
    // Clear medication simulation state
    this.simulatingMedication = false;
    this.lastMedicationResponse = null;
    this.medicationSimulationMessage = '';
    this.message = 'Settings reset.';
    if (this.isSimulating) {
      this.stopSimulation();
    }
  }
}
