import { Component, OnInit, OnDestroy } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../environments/environment';

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
}

interface SimulationResponse {
  simulationId: string;
  status: string;
  message: string;
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

  settings: Settings | null = null;

  constructor(private http: HttpClient) {}

  ngOnInit() {
    this.loadProfiles();
    this.loadSettings();
  }

  ngOnDestroy() {
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
    if (!this.selectedProfile) return;
    const devicesUrl = `${environment.backendUrl}/devices/${this.selectedProfile.id}`;
    console.log('Loading devices from:', devicesUrl);
    this.http.get<Device[]>(devicesUrl).subscribe({
      next: (data) => {
        console.log('Devices loaded:', data);
        this.devices = data || [];

        // Select all devices by default
        this.selectedDeviceIds = new Set(this.devices.map(d => d.id));

        // Restore previously selected devices from settings if available
        if (this.settings && this.settings.selectedDeviceIds && this.settings.selectedDeviceIds.length > 0) {
          this.selectedDeviceIds = new Set(this.settings.selectedDeviceIds);
        }
      },
      error: (err) => {
        console.error('Failed to load devices:', err);
        this.devices = [];
      }
    });
    this.saveSettings();
  }

  toggleDeviceSelection(deviceId: string) {
    if (this.selectedDeviceIds.has(deviceId)) {
      this.selectedDeviceIds.delete(deviceId);
    } else {
      this.selectedDeviceIds.add(deviceId);
    }
    this.saveSettings();
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
    this.message = 'Settings reset.';
    if (this.isSimulating) {
      this.stopSimulation();
    }
  }
}
