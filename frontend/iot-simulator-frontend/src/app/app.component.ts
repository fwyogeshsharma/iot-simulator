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
  deviceIds?: string[];
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

interface HistoricalDataResponse {
  jobId: string;
  status: string;
  message: string;
  totalDataPointsGenerated?: number;
  daysProcessed?: number;
  elapsedMs?: number;
  deviceDataCounts?: { [deviceId: string]: number };
}

interface HistoricalJobStatus {
  jobId: string;
  status: string;
  progress: number;
  totalDays: number;
  daysProcessed: number;
  dataPointsGenerated: number;
  currentDate?: string;
  errorMessage?: string;
  completionMessage?: string;  // Message with skipped devices info
}

interface Settings {
  email: string;
}

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.css']
})
export class AppComponent implements OnInit, OnDestroy {
  profiles: Profile[] = [];
  devices: Device[] = [];
  loadingDevices = false;

  selectedProfile: Profile | null = null;
  selectedElderlyPersonId: string | null = null;  // Track the actual elderly person ID
  selectedDeviceIds: Set<string> = new Set();

  // Autocomplete properties
  searchText = '';
  filteredProfiles: Profile[] = [];
  showDropdown = false;

  isSimulating = false;
  startingSimulation = false;
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

  // Historical data generation
  generatingHistorical = false;
  historicalJobId: string | null = null;
  historicalStatus: HistoricalJobStatus | null = null;
  historicalStatusSubscription: Subscription | null = null;
  historicalMessage = '';
  selectedFrequency = 'medium';  // Default to medium frequency

  // Tab management
  activeTab: 'realtime' | 'historical' | 'advanced' = 'realtime';

  settings: Settings | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadProfiles();
    this.loadSettings();
    // Ensure frequency is initialized
    if (!this.selectedFrequency) {
      this.selectedFrequency = 'medium';
    }
  }

  ngOnDestroy() {
    // Stop statistics polling
    if (this.statisticsSubscription) {
      this.statisticsSubscription.unsubscribe();
    }
    // Stop historical data polling
    if (this.historicalStatusSubscription) {
      this.historicalStatusSubscription.unsubscribe();
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
    this.http.get<Profile[]>(environment.verifiedProfilesUrl, { headers }).subscribe({
      next: (data) => {
        console.log('Profiles loaded:', data);
        this.profiles = data || [];
        this.filteredProfiles = [...this.profiles];
        if (this.settings) {
          this.selectedProfile = this.profiles.find(p => p.email === this.settings!.email) || null;
          if (this.selectedProfile) {
            this.searchText = this.selectedProfile.email;
            this.onProfileChange();
          }
        }
      },
      error: (err) => {
        console.error('Failed to load profiles:', err);
      }
    });
  }

  onSearchTextChange() {
    const searchLower = this.searchText.toLowerCase();
    this.filteredProfiles = this.profiles.filter(p =>
      p.email.toLowerCase().includes(searchLower) ||
      p.full_name.toLowerCase().includes(searchLower)
    );
    this.showDropdown = this.searchText.length > 0 && this.filteredProfiles.length > 0;
  }

  selectProfile(profile: Profile) {
    this.selectedProfile = profile;
    this.searchText = profile.email;
    this.showDropdown = false;
    this.onProfileChange();
  }

  onSearchFocus() {
    if (this.searchText.length > 0) {
      this.onSearchTextChange();
    } else {
      this.filteredProfiles = [...this.profiles];
      this.showDropdown = this.profiles.length > 0;
    }
  }

  onSearchBlur() {
    // Delay hiding dropdown to allow click event to register
    setTimeout(() => {
      this.showDropdown = false;
    }, 200);
  }

  onProfileChange() {
    // Clear previous state when changing elderly person
    this.selectedDeviceIds.clear();
    this.devices = [];
    this.dataTypes = [];
    this.selectedSingleDevice = null;
    this.lastGeneratedData = null;
    this.loadingDevices = true;
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
          // Store and use the first elderly person's ID
          this.selectedElderlyPersonId = elderlyPersons[0].id;
          this.loadDevicesForElderlyPerson(this.selectedElderlyPersonId!);
        } else {
          this.selectedElderlyPersonId = null;
          this.devices = [];
          this.loadingDevices = false;
        }
      },
      error: (err) => {
        console.error('Failed to load elderly persons:', err);
        this.devices = [];
        this.loadingDevices = false;
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
        this.loadingDevices = false;

        // Select all devices by default
        this.selectedDeviceIds = new Set(this.devices.map(d => d.id));

        // Clear data types for new device selection
        this.dataTypes = [];
        this.selectedSingleDevice = null;
        this.lastModelDataResponse = null;
        this.modelDataMessage = '';

        // Check for active simulation first (overrides local settings)
        this.checkActiveSimulation();
      },
      error: (err) => {
        console.error('Failed to load devices from backend:', err);
        this.devices = [];
        this.loadingDevices = false;
      }
    });
  }

  checkActiveSimulation() {
    if (!this.selectedProfile) {
      this.checkForSingleDeviceSelection();
      return;
    }

    // Check if there's an active simulation on the backend
    this.http.get<SimulationResponse>(
      `${environment.backendUrl}/simulation/active/${this.selectedProfile.id}`
    ).subscribe({
      next: (response) => {
        console.log('Active simulation check:', response);

        if (response.status === 'running' && response.simulationId) {
          // Active simulation found - restore its state
          this.simulationId = response.simulationId;
          this.isSimulating = true;
          this.simulationStatus = 'running';

          // Restore the selected devices from the simulation
          if (response.deviceIds && response.deviceIds.length > 0) {
            this.selectedDeviceIds = new Set(response.deviceIds);
            console.log('Restored device selection from active simulation:', response.deviceIds);
          }

          this.message = `Simulation Running - Generating data for ${this.selectedDeviceIds.size} device(s)`;

          // Start statistics polling
          this.startStatisticsPolling();
        }
        // No active simulation - keep all devices selected (default behavior)

        // Check if only one device is selected and load sensors automatically
        this.checkForSingleDeviceSelection();
      },
      error: (err) => {
        console.error('Failed to check active simulation:', err);
        // On error, keep all devices selected (default behavior)
        this.checkForSingleDeviceSelection();
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
  }

  selectAllDevices() {
    this.selectedDeviceIds = new Set(this.devices.map(d => d.id));
    this.checkForSingleDeviceSelection();
  }

  unselectAllDevices() {
    this.selectedDeviceIds.clear();
    this.checkForSingleDeviceSelection();
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
      // Switch away from Advanced tab if it's currently active
      if (this.activeTab === 'advanced') {
        this.activeTab = 'realtime';
      }
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
    if (!this.selectedProfile || !this.selectedElderlyPersonId) return;

    this.simulatingMedication = true;
    this.medicationSimulationMessage = '';
    this.lastMedicationResponse = null;

    this.http.post<MedicationSimulationResponse>(
      `${environment.backendUrl}/medication/simulate/${this.selectedElderlyPersonId}`,
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

    this.startingSimulation = true;
    this.message = '';

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
        this.startingSimulation = false;
        this.simulationId = response.simulationId;
        this.isSimulating = true;
        this.simulationStatus = 'running';
        this.message = `Simulation started! Generating data for ${this.selectedDeviceIds.size || this.devices.length} device(s)`;

        // Start polling for statistics every 2 seconds
        this.startStatisticsPolling();
      },
      error: (err) => {
        console.error('Failed to start simulation:', err);
        this.startingSimulation = false;
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
        email: this.selectedProfile.email
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

  setActiveTab(tab: 'realtime' | 'historical' | 'advanced') {
    this.activeTab = tab;
  }

  generateHistoricalData() {
    if (!this.selectedProfile) {
      this.historicalMessage = 'Please select an elderly person';
      return;
    }

    if (!this.selectedElderlyPersonId) {
      this.historicalMessage = 'No elderly person found for this profile';
      return;
    }

    if (this.selectedDeviceIds.size === 0) {
      this.historicalMessage = 'Please select at least one device';
      return;
    }

    // Calculate date range: 6 months ago to today
    const endDate = new Date();
    const startDate = new Date();
    startDate.setMonth(startDate.getMonth() - 6);

    const request = {
      elderlyPersonId: this.selectedElderlyPersonId,  // Use actual elderly person ID, not profile ID
      deviceIds: Array.from(this.selectedDeviceIds),
      startDate: startDate.toISOString().split('T')[0], // Format: "2024-06-12"
      endDate: endDate.toISOString().split('T')[0],     // Format: "2024-12-12"
      includeAnomalies: true,
      frequency: this.selectedFrequency  // Add frequency parameter
    };

    console.log('Starting historical data generation:', request);

    this.generatingHistorical = true;
    this.historicalMessage = '';
    this.historicalStatus = null;

    this.http.post<HistoricalDataResponse>(
      `${environment.backendUrl}/simulation/generate-historical`,
      request
    ).subscribe({
      next: (response) => {
        console.log('Historical data generation started:', response);
        this.historicalJobId = response.jobId;
        this.historicalMessage = response.message;

        if (response.status === 'processing') {
          // Start polling for progress
          this.startHistoricalStatusPolling();
        } else {
          this.generatingHistorical = false;
        }
      },
      error: (err) => {
        console.error('Failed to start historical data generation:', err);
        this.historicalMessage = `Error: ${err.error?.message || err.message}`;
        this.generatingHistorical = false;
      }
    });
  }

  startHistoricalStatusPolling() {
    // Clear any existing subscription
    this.stopHistoricalStatusPolling();

    // Poll status every 1 second
    this.historicalStatusSubscription = interval(1000).subscribe(() => {
      this.fetchHistoricalStatus();
    });

    // Fetch immediately
    this.fetchHistoricalStatus();
  }

  stopHistoricalStatusPolling() {
    if (this.historicalStatusSubscription) {
      this.historicalStatusSubscription.unsubscribe();
      this.historicalStatusSubscription = null;
    }
  }

  fetchHistoricalStatus() {
    if (!this.historicalJobId) return;

    this.http.get<HistoricalJobStatus>(
      `${environment.backendUrl}/simulation/historical-status/${this.historicalJobId}`
    ).subscribe({
      next: (status) => {
        this.historicalStatus = status;
        console.log('Historical status updated:', status);

        // Check if completed or failed
        if (status.status === 'completed') {
          this.stopHistoricalStatusPolling();
          this.generatingHistorical = false;
          // Use completionMessage if available (includes skipped devices info), otherwise construct basic message
          this.historicalMessage = status.completionMessage ||
            `Successfully generated ${status.dataPointsGenerated} data points across ${status.daysProcessed} days`;
        } else if (status.status === 'failed') {
          this.stopHistoricalStatusPolling();
          this.generatingHistorical = false;
          this.historicalMessage = `Failed: ${status.errorMessage || 'Unknown error'}`;
        }
      },
      error: (err) => {
        console.error('Failed to fetch historical status:', err);
        // Don't stop polling on error - might be temporary
      }
    });
  }
}
