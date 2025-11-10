# Quick Fix: Unhealthy Container Error

## Your Current Error:

```
ERROR: for frontend  Container "xyz" is unhealthy.
ERROR: Encountered errors while bringing up the project.
```

## What This Means:

The frontend container started but failed its health check. This usually means:
1. The container needs more time to start (most common)
2. The Angular build failed
3. Nginx isn't serving correctly

## IMMEDIATE FIX (Try This First) ✅

### Option 1: Wait and Check (EASIEST)

The health check now has a 60-second grace period. Just wait and check:

```bash
# Wait 60 seconds
sleep 60

# Check status again
docker compose ps

# If still unhealthy, check logs
docker compose logs frontend --tail=50
```

### Option 2: Restart the Frontend Container

```bash
# Restart just the frontend
docker compose restart frontend

# Wait 30 seconds
sleep 30

# Check status
docker compose ps
```

### Option 3: Rebuild Frontend

```bash
# Stop and rebuild frontend only
docker compose stop frontend
docker compose rm -f frontend
docker compose up -d --build frontend

# Monitor logs
docker compose logs -f frontend
```

### Option 4: Clean Restart Everything

```bash
# Stop everything
docker compose down

# Clean start
docker compose up -d --build

# Monitor progress
docker compose logs -f
```

## Use the Diagnostic Script

```bash
# Pull latest code (includes the diagnostic script)
git pull

# Make it executable
chmod +x diagnose-docker.sh

# Run it
./diagnose-docker.sh
```

The script will:
- Show you exactly what's wrong
- Display relevant logs
- Offer quick fix options interactively

## Check What's Actually Wrong

### 1. Check Container Health Status

```bash
# Check both containers
docker compose ps

# Detailed health check
docker inspect --format='{{.State.Health.Status}}' iot-simulator-frontend
docker inspect --format='{{.State.Health.Status}}' iot-simulator-backend

# Check health check logs
docker inspect --format='{{range .State.Health.Log}}{{.Output}}{{end}}' iot-simulator-frontend
```

### 2. View Frontend Logs

```bash
# Last 50 lines
docker compose logs frontend --tail=50

# Live logs
docker compose logs -f frontend

# All logs from start
docker compose logs frontend
```

### 3. Check if Frontend is Actually Running

```bash
# Test frontend directly
curl -v http://localhost:4200/

# Check if nginx is listening
docker exec iot-simulator-frontend netstat -tlnp | grep 80

# Check nginx process
docker exec iot-simulator-frontend ps aux | grep nginx
```

## Common Issues and Solutions

### Issue 1: Frontend takes too long to start

**Solution:** The health check now waits 60 seconds before checking. If it still fails:

```bash
# Edit docker-compose.yml to increase start_period
nano docker-compose.yml

# Find the frontend healthcheck section and change:
start_period: 60s  # to something like 90s or 120s

# Restart
docker compose down
docker compose up -d
```

### Issue 2: Angular build failed

**Check build logs:**
```bash
docker compose logs frontend | grep -i error
docker compose logs frontend | grep -i failed
```

**Solution:**
```bash
# Rebuild with no cache
docker compose build --no-cache frontend
docker compose up -d frontend
```

### Issue 3: Nginx configuration error

**Test nginx config:**
```bash
docker exec iot-simulator-frontend nginx -t
```

**If config is invalid:**
```bash
# Check the nginx.conf file
cat frontend/iot-simulator-frontend/nginx.conf

# Fix any issues, then rebuild
docker compose up -d --build frontend
```

### Issue 4: Files not copied correctly

**Check if files exist in container:**
```bash
docker exec iot-simulator-frontend ls -la /usr/share/nginx/html/

# Should see index.html and other Angular files
```

**If files are missing:**
```bash
# Rebuild from scratch
docker compose down
docker compose build --no-cache frontend
docker compose up -d
```

## Step-by-Step Troubleshooting Checklist

Run these commands in order:

```bash
# 1. Check what's actually running
docker compose ps
echo "---"

# 2. Check health status
docker inspect --format='Health: {{.State.Health.Status}}' iot-simulator-frontend
docker inspect --format='Health: {{.State.Health.Status}}' iot-simulator-backend
echo "---"

# 3. Can you access the services?
echo "Testing frontend..."
curl -I http://localhost:4200/ 2>&1 | head -1

echo "Testing backend..."
curl -I http://localhost:3000/ 2>&1 | head -1
echo "---"

# 4. Check recent logs
echo "Frontend logs (last 20 lines):"
docker compose logs frontend --tail=20
echo "---"

echo "Backend logs (last 20 lines):"
docker compose logs backend --tail=20
```

## If Nothing Works: Nuclear Option

```bash
# Stop everything
docker compose down -v

# Remove all images
docker compose down --rmi all

# Prune Docker system (WARNING: removes all unused containers/images)
docker system prune -a -f

# Start fresh
git pull
./start-docker.sh --clean
```

## Prevention: Health Check Configuration

The `docker-compose.yml` now has these settings:

```yaml
frontend:
  healthcheck:
    test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost/"]
    interval: 30s      # Check every 30 seconds
    timeout: 10s       # 10 seconds per check
    retries: 5         # Try 5 times
    start_period: 60s  # Wait 60 seconds before first check
```

This means:
- Docker waits 60 seconds before checking health
- Then checks every 30 seconds
- Tries 5 times before marking as unhealthy
- Total time before "unhealthy": ~60 + (5 × 30) = 210 seconds

## Quick Reference Commands

```bash
# Status
docker compose ps

# Restart frontend
docker compose restart frontend

# Rebuild frontend
docker compose up -d --build frontend

# View logs
docker compose logs -f frontend

# Check health
docker inspect --format='{{.State.Health.Status}}' iot-simulator-frontend

# Clean restart
docker compose down && docker compose up -d --build

# Run diagnostics
./diagnose-docker.sh
```

## Still Having Issues?

1. Check `DEPLOYMENT.md` for comprehensive troubleshooting
2. Run `./diagnose-docker.sh` for automated diagnostics
3. Check frontend build output: `docker compose logs frontend | grep -A 10 "npm run build"`
4. Verify disk space: `df -h` and `docker system df`
5. Check Docker version: `docker --version` (should be 20.10+)

## Success Indicators

You'll know it's working when:

```bash
docker compose ps
# Shows:
# iot-simulator-backend    running (healthy)
# iot-simulator-frontend   running (healthy)

curl http://localhost:3000/
# Returns HTTP 200 or 404 (both mean it's running)

curl http://localhost:4200/
# Returns HTML content
```

Access the application:
- Frontend: http://your-vm-ip:4200
- Backend: http://your-vm-ip:3000
