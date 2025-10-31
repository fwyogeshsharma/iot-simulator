# IoT Device Simulator

A comprehensive IoT device simulator system with both backend (Java/Spring Boot) and frontend (React) components. This simulator generates realistic IoT sensor data and provides a dashboard to visualize and manage the simulations.

## Project Structure

```
iot-simulator/
├── backend/              # Java Spring Boot backend
│   ├── src/
│   ├── pom.xml
│   └── mvnw
├── frontend/             # React frontend
│   ├── iot-simulator-frontend/
│   └── package.json
├── README.md
└── .gitignore
```

## Backend - IoT Simulator (Java/Spring Boot)

Located in `/backend`

### Features
- Device simulation and management
- Realistic sensor data generation
- REST API endpoints for device control
- Database integration
- Multi-threaded simulation support

### Prerequisites
- Java 11+
- Maven 3.6+

### Getting Started

```bash
cd backend
mvn clean install
mvn spring-boot:run
```

The backend will start on `http://localhost:8080`

## Frontend - Simulator Dashboard (React)

Located in `/frontend`

### Features
- Device management interface
- Simulation control dashboard
- Real-time data visualization
- Device configuration management

### Prerequisites
- Node.js 16+
- npm 7+

### Getting Started

```bash
cd frontend/iot-simulator-frontend
npm install
npm start
```

The frontend will start on `http://localhost:3000`

## API Documentation

### Start Simulation
```
POST /api/simulation/start
{
  "elderlyPersonId": "uuid",
  "deviceIds": ["optional", "specific", "device", "ids"]
}
```

### Stop Simulation
```
POST /api/simulation/stop
```

## Contributing

1. Create a feature branch
2. Make your changes
3. Commit and push
4. Create a pull request

## License

MIT License
