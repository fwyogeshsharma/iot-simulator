# Port Configuration Changes

## Summary

The IoT Simulator ports have been updated to avoid conflicts:

- **Backend**: Changed from port `8080` → `3000`
- **Frontend**: Remains on port `4200`

## Why This Change?

Port 8080 is commonly used by many applications (Tomcat, Jenkins, other Spring Boot apps, etc.), causing conflicts during deployment. Port 3000 is less commonly used and will reduce deployment issues.

## What Was Changed?

### 1. Docker Configuration
**File:** `docker-compose.yml`
```yaml
services:
  backend:
    ports:
      - "3000:8080"  # External:Internal
```

The container still runs on port 8080 internally, but it's exposed as port 3000 externally.

### 2. Frontend Configuration
**Files:**
- `frontend/iot-simulator-frontend/src/environments/environment.ts`
- `frontend/iot-simulator-frontend/src/environments/environment.prod.ts`

```typescript
backendUrl: 'http://localhost:3000/api'  // Changed from 8080
```

### 3. Scripts
**File:** `start-docker.sh`
- Updated startup messages to show port 3000
- Updated success messages to show correct URL

### 4. Documentation
All documentation files updated:
- `README.md`
- `DEPLOYMENT.md`
- `QUICK_REFERENCE.md`
- `VM_QUICK_FIX.md`
- `FIXES_APPLIED.md`
- `fix-port-conflict.sh`

## New Access URLs

### Local Development (Windows/Mac/Linux)
- **Frontend**: http://localhost:4200
- **Backend**: http://localhost:3000

### VM/Server Deployment
- **Frontend**: http://your-vm-ip:4200
- **Backend**: http://your-vm-ip:3000

### Inside Docker Network
Containers can communicate with each other using service names:
- Backend: `http://backend:8080` (internal port)
- Frontend: `http://frontend:80` (internal port)

## Testing the Changes

### 1. On Local Machine (Docker)

```bash
# Start the services
./start-docker.sh

# Test backend
curl http://localhost:3000/api/simulation/status

# Test frontend
curl http://localhost:4200/
```

### 2. On VM

```bash
# Pull latest changes
cd ~/iot-simulator
git pull

# Deploy
./start-docker.sh

# Test backend
curl http://localhost:3000/

# Test frontend
curl http://localhost:4200/
```

## Troubleshooting

### Port 3000 Already in Use?

```bash
# Find what's using port 3000
sudo lsof -i :3000

# Kill the process
sudo kill -9 <PID>

# Or use the automated script
./fix-port-conflict.sh
```

### Need to Change Ports Again?

If you need to use different ports, edit these files:

**1. docker-compose.yml**
```yaml
backend:
  ports:
    - "YOUR_PORT:8080"  # Change YOUR_PORT to your desired port
```

**2. Frontend environment files**
```typescript
// src/environments/environment.ts
// src/environments/environment.prod.ts
backendUrl: 'http://localhost:YOUR_PORT/api'
```

**3. Rebuild and restart**
```bash
./start-docker.sh --rebuild
```

## Firewall Configuration

### UFW (Ubuntu)
```bash
# Remove old rule (if it exists)
sudo ufw delete allow 8080/tcp

# Add new rule
sudo ufw allow 3000/tcp

# Check status
sudo ufw status
```

### Firewalld (CentOS/RHEL)
```bash
# Remove old rule (if it exists)
sudo firewall-cmd --permanent --remove-port=8080/tcp

# Add new rule
sudo firewall-cmd --permanent --add-port=3000/tcp
sudo firewall-cmd --reload
```

### Cloud Provider Security Groups
If you're using AWS, Azure, GCP, etc., update your security groups:

**AWS Example:**
- Remove: Inbound rule for TCP port 8080
- Add: Inbound rule for TCP port 3000 from 0.0.0.0/0 (or your IP)

**Azure Example:**
- Remove: Inbound security rule for port 8080
- Add: Inbound security rule for port 3000

## API Endpoints (Backend)

All backend API endpoints now use port 3000:

| Endpoint | Method | URL |
|----------|--------|-----|
| Get Devices | GET | `http://localhost:3000/api/devices/{elderlyPersonId}` |
| Get Data Types | GET | `http://localhost:3000/api/data-types/{deviceId}` |
| Start Simulation | POST | `http://localhost:3000/api/simulation/start` |
| Stop Simulation | POST | `http://localhost:3000/api/simulation/stop` |
| Get Status | GET | `http://localhost:3000/api/simulation/status/{simulationId}` |
| Get Statistics | GET | `http://localhost:3000/api/simulation/statistics/{simulationId}` |

## Health Check Endpoints

| Service | URL | Expected Response |
|---------|-----|-------------------|
| Backend | `http://localhost:3000/` | HTTP 200 |
| Frontend | `http://localhost:4200/` | HTML content |

## Nginx Configuration (if using reverse proxy)

If you want to use a reverse proxy with custom domains:

```nginx
# Backend proxy
server {
    listen 80;
    server_name api.yourdomain.com;

    location / {
        proxy_pass http://localhost:3000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}

# Frontend proxy
server {
    listen 80;
    server_name app.yourdomain.com;

    location / {
        proxy_pass http://localhost:4200;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

## Environment Variables

If you need to configure ports via environment variables (future enhancement):

```bash
# In docker-compose.yml (example for future use)
environment:
  - SERVER_PORT=8080  # Internal port (stays 8080)
  - EXTERNAL_PORT=3000  # External port (new default)
```

## Migration Checklist

If you have an existing deployment:

- [ ] Pull latest code: `git pull`
- [ ] Stop existing containers: `docker compose down`
- [ ] Update firewall rules (remove 8080, add 3000)
- [ ] Update cloud security groups (if applicable)
- [ ] Rebuild containers: `./start-docker.sh --rebuild`
- [ ] Update any external monitoring/health check URLs
- [ ] Update any API client configurations
- [ ] Test all endpoints with new port 3000
- [ ] Update documentation/wiki (if you have one)

## Rollback (if needed)

If you need to rollback to port 8080:

```bash
# Edit docker-compose.yml
nano docker-compose.yml
# Change "3000:8080" back to "8080:8080"

# Edit environment files
nano frontend/iot-simulator-frontend/src/environments/environment.ts
# Change port back to 8080

# Rebuild
./start-docker.sh --rebuild
```

## Questions?

- See `DEPLOYMENT.md` for deployment issues
- See `QUICK_REFERENCE.md` for common commands
- See `VM_QUICK_FIX.md` for port conflict solutions
