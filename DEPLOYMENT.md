# IoT Simulator - VM Deployment Guide

This guide will help you deploy the IoT Simulator on a Linux VM (Ubuntu/Debian).

## Prerequisites

- Linux VM (Ubuntu 20.04+ or Debian 11+ recommended)
- Minimum 2GB RAM, 2 CPU cores
- Docker and Docker Compose installed
- Git installed
- Open ports: 8080 (backend), 4200 (frontend)

## Step 1: Install Docker and Docker Compose

```bash
# Update package index
sudo apt-get update

# Install required packages
sudo apt-get install -y ca-certificates curl gnupg lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up Docker repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine and Docker Compose
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Add your user to docker group (to run docker without sudo)
sudo usermod -aG docker $USER

# Log out and log back in for group changes to take effect
```

## Step 2: Clone the Repository

```bash
# Clone the repository to your home directory
cd ~
git clone <your-repo-url> iot-simulator
cd iot-simulator
```

## Step 3: Configure Environment Variables

The application is pre-configured with Supabase URLs and API keys in `docker-compose.yml`. If you need to customize:

```bash
# Optional: Create a .env file for custom configuration
cp .env.example .env
# Edit .env file with your settings
nano .env
```

## Step 4: Verify Project Structure

Make sure your directory structure looks like this:

```
iot-simulator/
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
├── frontend/
│   └── iot-simulator-frontend/
│       ├── Dockerfile
│       ├── nginx.conf
│       ├── package.json
│       └── src/
├── docker-compose.yml
├── start-docker.sh
└── .env.example
```

**IMPORTANT**: The docker-compose.yml file MUST be at the root level, not in a subdirectory!

## Step 5: Make Start Script Executable

```bash
chmod +x start-docker.sh
```

## Step 6: Deploy the Application

### Option A: Using the start script (Recommended)

```bash
# Start with automatic build
./start-docker.sh

# Or with rebuild flag
./start-docker.sh --rebuild

# Or with clean start (removes old containers/images)
./start-docker.sh --clean
```

### Option B: Using docker-compose directly

```bash
# Build and start all services
docker compose up --build -d

# Check status
docker compose ps

# View logs
docker compose logs -f
```

## Step 7: Verify Deployment

```bash
# Check if containers are running
docker compose ps

# Check backend health
curl http://localhost:8080/

# Check frontend (from your browser or curl)
curl http://localhost:4200/
```

Expected output:
- Backend: Should return a response (health check endpoint)
- Frontend: Should return HTML content

## Accessing the Application

- **Frontend**: http://your-vm-ip:4200
- **Backend API**: http://your-vm-ip:8080

## Troubleshooting

### Issue: "build path ... either does not exist"

This error occurs when docker-compose.yml is in the wrong location.

**Solution:**
```bash
# Ensure docker-compose.yml is at the root level
cd ~/iot-simulator
ls -la docker-compose.yml  # Should exist here

# If it's in a subdirectory, move it:
mv iot-simulator/docker-compose.yml .
mv iot-simulator/start-docker.sh .
```

### Issue: Port already in use

```bash
# Find process using port 8080
sudo lsof -i :8080
# Or
sudo netstat -tulpn | grep 8080

# Kill the process if needed
sudo kill -9 <PID>
```

### Issue: Permission denied for Docker

```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Log out and back in, or run:
newgrp docker
```

### Issue: Out of memory during build

```bash
# Increase Docker memory limit in daemon.json
sudo nano /etc/docker/daemon.json
```

Add:
```json
{
  "default-ulimits": {
    "memlock": {
      "Hard": -1,
      "Name": "memlock",
      "Soft": -1
    }
  }
}
```

Then restart Docker:
```bash
sudo systemctl restart docker
```

## Viewing Logs

```bash
# All services
docker compose logs -f

# Backend only
docker compose logs -f backend

# Frontend only
docker compose logs -f frontend

# Last 100 lines
docker compose logs --tail=100
```

## Stopping the Application

```bash
# Stop all services
docker compose down

# Stop and remove volumes (clears all data)
docker compose down -v

# Stop and remove images
docker compose down --rmi all
```

## Updating the Application

```bash
# Pull latest changes
git pull

# Rebuild and restart
./start-docker.sh --rebuild
```

## Firewall Configuration

If you're using UFW (Ubuntu):

```bash
# Allow SSH (if not already allowed)
sudo ufw allow 22/tcp

# Allow backend
sudo ufw allow 8080/tcp

# Allow frontend
sudo ufw allow 4200/tcp

# Enable firewall
sudo ufw enable

# Check status
sudo ufw status
```

## Production Recommendations

1. **Use a reverse proxy (Nginx/Caddy)**: Configure SSL/TLS certificates
2. **Change default ports**: Use 80/443 with reverse proxy
3. **Set up monitoring**: Use tools like Prometheus + Grafana
4. **Configure backups**: Regularly backup your data
5. **Set resource limits**: Configure Docker resource constraints
6. **Use Docker secrets**: For sensitive environment variables
7. **Enable logging**: Configure centralized logging

## Support

For issues or questions, please refer to the main README.md or open an issue in the repository.
