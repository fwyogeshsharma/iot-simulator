# IoT Simulator - Quick Setup Guide

This guide will help you get the IoT Simulator running with Docker.

## Prerequisites

- Docker installed and running
- Docker Compose installed
- Git (to clone the repository)

## Quick Start (3 Steps)

### Step 1: Clone and Setup

```bash
# Clone the repository
git clone <your-repo-url> iot-simulator
cd iot-simulator

# Copy the environment file
cp .env.example .env

# Edit .env with your credentials (if needed)
nano .env
```

### Step 2: Start the Application

```bash
# Make the startup script executable
chmod +x start.sh

# Start everything
./start.sh
```

### Step 3: Wait and Access

Wait 2-3 minutes for services to start, then access:

- **Frontend**: http://localhost:4200 or http://your-vm-ip:4200
- **Backend**: http://localhost:3000 or http://your-vm-ip:3000

## Detailed Instructions

### 1. Environment Configuration

The `.env` file contains your Supabase credentials. It's already configured with the project defaults.

**Important**: The `.env` file is in `.gitignore` and should **NOT** be committed to Git.

### 2. Starting Services

**Option A: Use the startup script (Recommended)**

```bash
./start.sh
```

This script will:
- Check if Docker is running
- Check if .env exists
- Stop old containers
- Build and start services
- Show access URLs

**Option B: Manual Docker commands**

```bash
# Stop existing containers
docker compose down

# Remove old containers
docker compose rm -f

# Start services
docker compose up -d --build
```

### 3. Verify Services

```bash
# Check container status
docker compose ps

# Expected output:
# NAME                       STATUS
# iot-simulator-backend      Up (healthy)
# iot-simulator-frontend     Up (healthy)

# View logs
docker compose logs -f

# Test services
curl http://localhost:3000/  # Backend
curl http://localhost:4200/  # Frontend
```

## Port Configuration

| Service | Internal Port | External Port | Description |
|---------|---------------|---------------|-------------|
| Backend | 8080 | **3000** | Spring Boot API |
| Frontend | 80 | **4200** | Angular UI (via Nginx) |

## Common Commands

```bash
# Start services
./start.sh
# or
docker compose up -d

# Stop services
docker compose down

# Restart services
docker compose restart

# View logs (all services)
docker compose logs -f

# View logs (specific service)
docker compose logs -f backend
docker compose logs -f frontend

# Check status
docker compose ps

# Rebuild from scratch
docker compose down
docker compose build --no-cache
docker compose up -d
```

## Troubleshooting

### Services show as "unhealthy"

Wait 2-3 minutes. Services need time to start and pass health checks.

```bash
# Check logs for errors
docker compose logs backend
docker compose logs frontend

# Use diagnostic script
chmod +x diagnose-docker.sh
./diagnose-docker.sh
```

### Port conflicts (address already in use)

```bash
# Use the port conflict fix script
chmod +x fix-port-conflict.sh
./fix-port-conflict.sh
```

### .env file not found

```bash
# Create from example
cp .env.example .env
```

### Docker not running

```bash
# Check Docker status
docker info

# Start Docker daemon (Linux)
sudo systemctl start docker
```

### Can't access from outside VM

Check firewall rules:

```bash
# UFW (Ubuntu)
sudo ufw allow 3000/tcp
sudo ufw allow 4200/tcp

# Firewalld (CentOS)
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --permanent --add-port=4200/tcp
sudo firewall-cmd --reload
```

Also check cloud security groups (AWS/Azure/GCP).

## File Structure

```
iot-simulator/
├── .env                      # Your credentials (NOT in git)
├── .env.example              # Template for .env
├── docker-compose.yml        # Docker configuration
├── start.sh                  # Startup script
├── backend/                  # Spring Boot backend
│   ├── Dockerfile
│   └── src/
└── frontend/                 # Angular frontend
    └── iot-simulator-frontend/
        ├── Dockerfile
        ├── nginx.conf
        └── src/
```

## Environment Variables

The `.env` file contains:

```bash
# Java Options
JAVA_OPTS=-Xmx512m -Xms256m

# Supabase URLs
SUPABASE_PROFILES_URL=https://your-project.supabase.co/rest/v1/profiles
SUPABASE_DEVICES_URL=https://your-project.supabase.co/rest/v1/devices
# ... more Supabase URLs

# Supabase API Key
SUPABASE_APIKEY=your-service-role-key

# Device Ingest URL
SIMULATOR_DEVICE_INGEST_URL=https://your-project.supabase.co/functions/v1/device-ingest
```

## Health Checks

Docker automatically checks if services are healthy:

**Backend:**
- URL: http://localhost:8080/
- Interval: Every 30 seconds
- Start period: 60 seconds grace period

**Frontend:**
- URL: http://localhost/
- Interval: Every 30 seconds
- Start period: 120 seconds grace period

## Stopping the Application

```bash
# Stop all services
docker compose down

# Stop and remove volumes
docker compose down -v

# Stop and remove everything (including images)
docker compose down --rmi all -v
```

## Updating the Application

```bash
# Pull latest changes
git pull

# Rebuild and restart
docker compose down
docker compose up -d --build
```

## Production Recommendations

1. **Use a reverse proxy** (Nginx/Caddy) with SSL/TLS
2. **Change default ports** to 80/443 via reverse proxy
3. **Set up monitoring** (Prometheus, Grafana)
4. **Configure backups** for your data
5. **Use Docker secrets** for sensitive credentials
6. **Set resource limits** in docker-compose.yml
7. **Enable centralized logging**

## Getting Help

- **Troubleshooting**: See [DEPLOYMENT.md](DEPLOYMENT.md)
- **Quick commands**: See [QUICK_REFERENCE.md](QUICK_REFERENCE.md)
- **Port changes**: See [PORT_CHANGES.md](PORT_CHANGES.md)
- **Diagnostics**: Run `./diagnose-docker.sh`

## Success Indicators

You'll know everything is working when:

1. `docker compose ps` shows both containers as "healthy"
2. You can access http://localhost:4200 and see the UI
3. You can access http://localhost:3000 and get a response
4. The UI loads without errors in browser console

## Next Steps

Once running:

1. Open http://localhost:4200 in your browser
2. Select an elderly person from the dropdown
3. Select devices to simulate (or leave all selected)
4. Click "Start Simulation"
5. Watch real-time statistics appear

Enjoy your IoT Simulator! 🚀
