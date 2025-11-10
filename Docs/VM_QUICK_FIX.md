# Quick Fix for VM Port Conflict

## Your Current Error:

```
Error starting userland proxy: listen tcp4 0.0.0.0:3000: bind: address already in use
```

## What This Means:

Port 3000 is already being used by another process on your VM. This is likely because:
1. The Spring Boot backend is running outside of Docker
2. Another application is using port 3000

## Quick Solution (Choose One):

### Option 1: Use the Automated Fix Script (EASIEST) ✅

```bash
# On your VM
cd ~/iot-simulator

# Pull latest changes (includes the fix script)
git pull

# Make the script executable
chmod +x fix-port-conflict.sh

# Run it - it will help you find and kill the process
./fix-port-conflict.sh

# Then start Docker
./start-docker.sh
```

### Option 2: Manual Fix

```bash
# Find what's using port 3000
sudo lsof -i :3000

# You'll see output like:
# COMMAND   PID USER   FD   TYPE  DEVICE SIZE/OFF NODE NAME
# java    12345 user   123u  IPv6  123456      0t0  TCP *:3000 (LISTEN)

# Kill the process (replace 12345 with your actual PID)
sudo kill -9 12345

# Verify it's gone
sudo lsof -i :3000   # Should return nothing

# Now start Docker
./start-docker.sh
```

### Option 3: Change Docker Ports

If you want to keep the existing service running on port 3000:

```bash
# Edit docker-compose.yml
nano docker-compose.yml

# Change the ports section for backend:
services:
  backend:
    ports:
      - "8081:3000"  # Changed from "3000:3000"

# Save and exit (Ctrl+X, then Y, then Enter)

# Start Docker
./start-docker.sh
```

Then access your application at:
- Backend: http://your-vm-ip:8081
- Frontend: http://your-vm-ip:4200

## Common Scenarios:

### If you're running Spring Boot manually:

```bash
# Find Java processes
ps aux | grep java

# Kill the Spring Boot process
sudo kill -9 <PID>

# Or stop it properly if you started it with systemd:
sudo systemctl stop iot-simulator
```

### If you used Maven to start it:

```bash
# Find Maven processes
ps aux | grep mvn

# Kill them
sudo killall -9 java
```

### If you're not sure what's using the port:

```bash
# This command shows exactly what process is using port 3000
sudo lsof -i :3000 -P

# Or
sudo netstat -tulpn | grep 3000
```

## After Fixing the Port Issue:

```bash
# Make sure Docker is clean
docker compose down

# Start fresh
./start-docker.sh --clean
```

## Verify Everything is Working:

```bash
# Check containers are running
docker compose ps

# Check logs
docker compose logs -f

# Test backend
curl http://localhost:3000/

# Test frontend
curl http://localhost:4200/
```

## Still Having Issues?

Check the comprehensive troubleshooting guide:
- See `DEPLOYMENT.md` for detailed troubleshooting
- See `QUICK_REFERENCE.md` for common commands
- Run `docker compose logs backend` to see backend logs
- Run `docker compose logs frontend` to see frontend logs

## Need to Start Over?

```bash
# Stop everything
docker compose down

# Remove all containers and images
docker compose down --rmi all -v

# Pull latest code
git pull

# Clean start
./start-docker.sh --clean
```
