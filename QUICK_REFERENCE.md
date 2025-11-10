# IoT Simulator - Quick Reference Card

## VM Deployment (One-Time Setup)

```bash
# 1. Clone repository
git clone <your-repo-url> iot-simulator
cd iot-simulator

# 2. Verify structure (docker-compose.yml must be at root!)
ls -la
# Should see: docker-compose.yml, start-docker.sh, backend/, frontend/

# 3. Make script executable
chmod +x start-docker.sh

# 4. Deploy
./start-docker.sh
```

## Common Commands

### Start/Stop Application

```bash
# Start application
./start-docker.sh

# Rebuild and start
./start-docker.sh --rebuild

# Clean start (removes old containers)
./start-docker.sh --clean

# Stop application
docker compose down

# Stop and remove all data
docker compose down -v
```

### View Logs

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

### Check Status

```bash
# Container status
docker compose ps

# Resource usage
docker stats

# Backend health
curl http://localhost:3000/

# Frontend health
curl http://localhost:4200/
```

### Update Application

```bash
# Pull latest code
git pull

# Rebuild and restart
./start-docker.sh --rebuild
```

### Troubleshooting

```bash
# Check Docker is running
docker info

# Fix port conflicts (automated)
./fix-port-conflict.sh

# Check port usage (manual)
sudo lsof -i :3000
sudo lsof -i :4200
sudo netstat -tulpn | grep -E '3000|4200'

# Kill process using a port
sudo kill -9 <PID>

# Restart a service
docker compose restart backend
docker compose restart frontend

# Force rebuild
docker compose build --no-cache
docker compose up -d

# View real-time logs
docker compose logs -f --tail=50
```

## Access URLs

- **Frontend**: http://your-vm-ip:4200
- **Backend**: http://your-vm-ip:3000

## File Locations

- Config: `docker-compose.yml`
- Backend: `backend/`
- Frontend: `frontend/iot-simulator-frontend/`
- Logs: `docker compose logs`

## Emergency Cleanup

```bash
# Stop everything
docker compose down

# Remove all containers and images
docker compose down --rmi all

# Nuclear option (removes ALL Docker data)
docker system prune -a --volumes
```

## Firewall (UFW)

```bash
# Allow ports
sudo ufw allow 22/tcp   # SSH
sudo ufw allow 3000/tcp # Backend
sudo ufw allow 4200/tcp # Frontend

# Check firewall status
sudo ufw status
```

## Quick Health Check Script

Create `health-check.sh`:

```bash
#!/bin/bash
echo "Checking IoT Simulator Health..."
echo ""
echo "1. Docker status:"
docker compose ps
echo ""
echo "2. Backend health:"
curl -s http://localhost:3000/ && echo "✅ Backend OK" || echo "❌ Backend DOWN"
echo ""
echo "3. Frontend health:"
curl -s http://localhost:4200/ > /dev/null && echo "✅ Frontend OK" || echo "❌ Frontend DOWN"
echo ""
echo "4. Resource usage:"
docker stats --no-stream
```

Make it executable: `chmod +x health-check.sh`

Run it: `./health-check.sh`
