# 🏥 SymBIOT IoT Device Simulator

A comprehensive IoT device simulator system designed for elderly care monitoring. Features a Java/Spring Boot backend and Angular frontend with real-time statistics, Docker support, and an intuitive dashboard interface.

## 🌟 Features

- 🎯 **Multi-Device Simulation**: Simulate multiple IoT devices simultaneously
- 📊 **Real-time Statistics**: Live monitoring with success rates, data points/minute, and elapsed time
- 🏥 **Healthcare Focus**: Specialized sensors for elderly care (heart rate, temperature, movement, etc.)
- 🐳 **Docker Ready**: One-command deployment with Docker Compose
- 📈 **Modern UI**: Responsive Angular dashboard with live updates
- 🔄 **Flexible Scheduling**: Configurable data generation frequencies per device type

## 📁 Project Structure

```
iot-simulator/
├── backend/                        # Java Spring Boot backend
│   ├── src/
│   ├── Dockerfile
│   ├── pom.xml
│   └── mvnw
├── frontend/                       # Angular frontend
│   ├── iot-simulator-frontend/
│   │   ├── src/
│   │   ├── Dockerfile
│   │   ├── nginx.conf
│   │   └── package.json
├── docker-compose.yml              # Docker orchestration
├── start-docker.sh                 # Easy startup script
├── README.md
└── .gitignore
```

## 🚀 Quick Start (Docker - Recommended)

The easiest way to run the entire application:

```bash
./start-docker.sh
```

This will:
- ✅ Check Docker installation
- 🔨 Build both backend and frontend images
- 🚀 Start all services
- ⏳ Wait for health checks
- 📋 Display live logs

**Access the application:**
- **Frontend**: http://localhost:4200
- **Backend API**: http://localhost:8080

### Additional Options

```bash
# Rebuild images from scratch
./start-docker.sh --rebuild

# Clean everything and start fresh
./start-docker.sh --clean

# Show help
./start-docker.sh --help
```

### Manual Docker Commands

```bash
# Start services
docker-compose up -d

# View logs
docker-compose logs -f

# Stop services
docker-compose down

# Rebuild and start
docker-compose up --build
```

## 🌐 VM/Server Deployment

For deploying on a Linux VM or server, see the comprehensive [DEPLOYMENT.md](DEPLOYMENT.md) guide which covers:

- Installing Docker and Docker Compose on Ubuntu/Debian
- Cloning and configuring the application
- Troubleshooting common deployment issues
- Firewall configuration
- Production recommendations

**Quick VM Deploy:**

```bash
# Clone the repository
git clone <your-repo-url> iot-simulator
cd iot-simulator

# Make script executable
chmod +x start-docker.sh

# Deploy
./start-docker.sh
```

**Important**: Ensure the `docker-compose.yml` file is at the root level of the project, not in a subdirectory!

## 💻 Manual Setup (Without Docker)

### Prerequisites
- Java 11+
- Maven 3.6+
- Node.js 16+
- npm 7+

## Backend - IoT Simulator (Java/Spring Boot)

Located in `/backend`

### Features
- Device simulation and management
- Realistic sensor data generation
- REST API endpoints for device control
- Supabase integration for data persistence
- Multi-threaded simulation support with configurable frequency
- Real-time statistics tracking

### Getting Started

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/devices/{elderlyPersonId}` | Get all devices for an elderly person |
| GET | `/api/data-types/{deviceId}` | Get data type configs for a device |
| POST | `/api/simulation/start` | Start simulation |
| POST | `/api/simulation/stop` | Stop simulation |
| GET | `/api/simulation/status/{simulationId}` | Get simulation status |
| GET | `/api/simulation/statistics/{simulationId}` | Get real-time statistics |

## Frontend - Simulator Dashboard (Angular)

Located in `/frontend/iot-simulator-frontend`

### Features
- 👤 User/Profile selection
- 📱 Multi-device selection with checkboxes
- 🎮 Simulation control (Start/Stop/Reset)
- 📊 Real-time statistics dashboard
- 📈 Live progress tracking
- 💾 Settings persistence (localStorage)
- 📱 Responsive design

### Getting Started

```bash
cd frontend/iot-simulator-frontend
npm install
npm start
```

The frontend will start on `http://localhost:4200`

### Build for Production

```bash
npm run build -- --configuration production
```

## 🔌 Supported Device Types

The simulator supports various IoT sensors for elderly care:

### Health Monitoring
- ❤️ Heart Rate Monitor (60-100 bpm)
- 🌡️ Temperature Sensor (36-42°C)
- 🫁 Oxygen Saturation (90-100%)
- 🩸 Blood Pressure (systolic/diastolic)

### Motion & Activity
- 🚶 Movement Sensor (0-100%)
- 🎯 Activity Level (0-100%)
- 🧭 Orientation Sensor (0-360°)
- 👁️ Presence Detection (boolean)

### Environmental
- 🚪 Door Status (open/closed)
- 🛏️ Bed Occupancy (occupied/vacant)
- 💺 Seat Occupancy (occupied/vacant)

### Lifestyle
- ⚖️ BMI Tracker (18.5-30 kg/m²)
- ⏱️ Duration/Sleep Tracker (0-1440 minutes)

## 📊 Real-time Statistics

The dashboard displays:
- 📈 Total data points generated
- ✅ Successful transmissions
- ❌ Failed transmissions
- 🎯 Success rate percentage
- ⚡ Data points per minute
- ⏱️ Elapsed simulation time

Statistics update every 2 seconds while simulation is running.

## 🏗️ Architecture

### Backend Architecture
```
SimulatorController
    ↓
SimulatorService (Device & Config Management)
    ↓
SimulationManager (Thread Pool Executor)
    ↓
Data Generation & Supabase Ingest
```

### Frontend Architecture
```
AppComponent
    ├── Profile Selection
    ├── Device Multi-Select
    ├── Simulation Controls
    └── Real-time Statistics (polling)
```

### Data Flow
```
Angular Frontend (Port 4200)
    ↓ HTTP REST API
Spring Boot Backend (Port 8080)
    ↓ REST API
Supabase (Database + Edge Functions)
    ↓
Device Data Storage
```

## 🐳 Docker Details

### Images
- **Backend**: Multi-stage build with Maven + OpenJDK 11
- **Frontend**: Multi-stage build with Node.js + Nginx

### Networking
- Bridge network: `iot-network`
- Internal communication between services
- Exposed ports: 8080 (backend), 4200 (frontend)

### Health Checks
- Backend: Checks `/actuator/health` endpoint
- Frontend: Checks Nginx root path
- Docker Compose waits for backend health before starting frontend

## 🔧 Configuration

### Backend Configuration
Edit `backend/src/main/resources/application.yml`:
- Server port
- Database settings
- Supabase URLs and API keys
- Logging levels

### Frontend Configuration
Edit `frontend/iot-simulator-frontend/src/environments/environment.ts`:
- Backend URL
- Supabase profiles URL
- API keys

## 🤝 Contributing

1. Create a feature branch
2. Make your changes
3. Test with Docker: `./start-docker.sh --rebuild`
4. Commit and push
5. Create a pull request

## 📝 License

MIT License

## 🆘 Troubleshooting

### Docker Issues
```bash
# Check Docker is running
docker info

# View service logs
docker-compose logs backend
docker-compose logs frontend

# Restart services
docker-compose restart

# Clean rebuild
./start-docker.sh --clean
```

### Port Conflicts
If ports 8080 or 4200 are in use, edit `docker-compose.yml`:
```yaml
ports:
  - "8081:8080"  # Backend on 8081 instead
  - "4201:80"    # Frontend on 4201 instead
```

### Backend Not Starting
- Check Java version: `java -version` (needs 11+)
- Check Supabase connectivity
- Review logs: `docker-compose logs backend`

### Frontend Not Connecting
- Ensure backend is healthy: `docker-compose ps`
- Check browser console for errors
- Verify backend URL in environment files

## 📚 Additional Resources

- [Spring Boot Documentation](https://spring.io/projects/spring-boot)
- [Angular Documentation](https://angular.io/docs)
- [Docker Documentation](https://docs.docker.com/)
- [Supabase Documentation](https://supabase.com/docs)
