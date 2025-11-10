#!/bin/bash

# Debug Frontend Container Script
# This script helps diagnose why the frontend container is unhealthy

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║     Frontend Container Debug Tool                   ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if container exists
if ! docker ps -a | grep -q iot-simulator-frontend; then
    echo -e "${RED}❌ Frontend container does not exist${NC}"
    echo "Run: docker compose up -d"
    exit 1
fi

# Get container status
STATUS=$(docker inspect --format='{{.State.Status}}' iot-simulator-frontend 2>/dev/null)
HEALTH=$(docker inspect --format='{{.State.Health.Status}}' iot-simulator-frontend 2>/dev/null)

echo -e "${YELLOW}1. Container Status:${NC}"
echo "   Status: $STATUS"
echo "   Health: $HEALTH"
echo ""

# Check if container is running
if [ "$STATUS" != "running" ]; then
    echo -e "${RED}❌ Container is not running!${NC}"
    echo ""
    echo -e "${YELLOW}Container logs:${NC}"
    docker logs iot-simulator-frontend --tail=50
    exit 1
fi

# Check logs
echo -e "${YELLOW}2. Recent Logs (last 30 lines):${NC}"
docker logs iot-simulator-frontend --tail=30
echo ""

# Check if nginx is running
echo -e "${YELLOW}3. Checking Nginx Process:${NC}"
if docker exec iot-simulator-frontend ps aux | grep -v grep | grep -q nginx; then
    echo -e "${GREEN}✅ Nginx is running${NC}"
    docker exec iot-simulator-frontend ps aux | grep nginx
else
    echo -e "${RED}❌ Nginx is NOT running!${NC}"
fi
echo ""

# Check if files exist
echo -e "${YELLOW}4. Checking Files in Container:${NC}"
docker exec iot-simulator-frontend ls -lh /usr/share/nginx/html/ | head -10
echo ""

# Check if index.html exists
if docker exec iot-simulator-frontend test -f /usr/share/nginx/html/index.html; then
    echo -e "${GREEN}✅ index.html exists${NC}"
    SIZE=$(docker exec iot-simulator-frontend stat -c%s /usr/share/nginx/html/index.html)
    echo "   Size: $SIZE bytes"
else
    echo -e "${RED}❌ index.html NOT FOUND!${NC}"
    echo "   This means the Angular build failed or files weren't copied"
fi
echo ""

# Check nginx config
echo -e "${YELLOW}5. Testing Nginx Configuration:${NC}"
if docker exec iot-simulator-frontend nginx -t 2>&1 | grep -q "successful"; then
    echo -e "${GREEN}✅ Nginx config is valid${NC}"
else
    echo -e "${RED}❌ Nginx config has errors${NC}"
    docker exec iot-simulator-frontend nginx -t 2>&1
fi
echo ""

# Check if port 80 is listening
echo -e "${YELLOW}6. Checking Port 80:${NC}"
if docker exec iot-simulator-frontend netstat -tlnp 2>/dev/null | grep -q ":80"; then
    echo -e "${GREEN}✅ Port 80 is listening${NC}"
    docker exec iot-simulator-frontend netstat -tlnp | grep ":80"
else
    echo -e "${RED}❌ Port 80 is NOT listening${NC}"
fi
echo ""

# Try to access from inside container
echo -e "${YELLOW}7. Testing HTTP Request from Inside Container:${NC}"
RESPONSE=$(docker exec iot-simulator-frontend wget -q -O- http://localhost/ 2>&1 | head -1)
if [ -n "$RESPONSE" ]; then
    echo -e "${GREEN}✅ Can access http://localhost/ from inside container${NC}"
    echo "   First line of response: $RESPONSE"
else
    echo -e "${RED}❌ Cannot access http://localhost/ from inside container${NC}"
fi
echo ""

# Try to access from host
echo -e "${YELLOW}8. Testing HTTP Request from Host:${NC}"
if curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/ 2>/dev/null | grep -q "200"; then
    echo -e "${GREEN}✅ Can access http://localhost:4200/ from host${NC}"
else
    echo -e "${RED}❌ Cannot access http://localhost:4200/ from host${NC}"
fi
echo ""

# Check health check command
echo -e "${YELLOW}9. Testing Health Check Command:${NC}"
if docker exec iot-simulator-frontend wget --quiet --tries=1 --spider http://localhost/ 2>&1; then
    echo -e "${GREEN}✅ Health check command succeeds${NC}"
else
    echo -e "${RED}❌ Health check command fails${NC}"
    echo "   Exit code: $?"
fi
echo ""

# Check health check logs
echo -e "${YELLOW}10. Health Check Logs:${NC}"
docker inspect --format='{{range .State.Health.Log}}Exit: {{.ExitCode}} | {{.Output}}{{"\n"}}{{end}}' iot-simulator-frontend | tail -5
echo ""

# Summary
echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║     Diagnosis Summary                                ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$HEALTH" = "healthy" ]; then
    echo -e "${GREEN}✅ Container is healthy!${NC}"
    echo "Access at: http://localhost:4200/"
elif [ "$HEALTH" = "starting" ]; then
    echo -e "${YELLOW}⏳ Container is still starting...${NC}"
    echo "Wait a bit longer and check again"
else
    echo -e "${RED}❌ Container is unhealthy${NC}"
    echo ""
    echo "Common fixes:"
    echo "1. Rebuild: docker compose up --build -d frontend"
    echo "2. Check logs: docker compose logs frontend"
    echo "3. Restart: docker compose restart frontend"
    echo "4. Full reset: docker compose down && docker compose up -d"
fi
