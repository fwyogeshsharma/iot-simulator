#!/bin/bash

# Immediate Fix for Unhealthy Container
# Run this on your VM to fix the unhealthy container error

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║     Fixing Unhealthy Container Error                ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${YELLOW}Step 1: Stopping all containers...${NC}"
docker compose down
echo -e "${GREEN}✅ Containers stopped${NC}"
echo ""

echo -e "${YELLOW}Step 2: Removing old containers (including unhealthy ones)...${NC}"
docker compose rm -f
echo -e "${GREEN}✅ Old containers removed${NC}"
echo ""

echo -e "${YELLOW}Step 3: Removing dangling images...${NC}"
docker image prune -f
echo -e "${GREEN}✅ Old images cleaned${NC}"
echo ""

echo -e "${YELLOW}Step 4: Starting services with new configuration...${NC}"
echo -e "${BLUE}   This will take 2-3 minutes. Please wait...${NC}"
docker compose up -d --build
echo ""

echo -e "${YELLOW}Step 5: Waiting for services to start (60 seconds)...${NC}"
sleep 60
echo ""

echo -e "${YELLOW}Step 6: Checking container status...${NC}"
docker compose ps
echo ""

# Check health status
BACKEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' iot-simulator-backend 2>/dev/null || echo "not found")
FRONTEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' iot-simulator-frontend 2>/dev/null || echo "not found")

echo -e "${YELLOW}Health Status:${NC}"
echo "  Backend:  $BACKEND_HEALTH"
echo "  Frontend: $FRONTEND_HEALTH"
echo ""

if [ "$BACKEND_HEALTH" = "healthy" ] && [ "$FRONTEND_HEALTH" = "healthy" ]; then
    echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                                                      ║${NC}"
    echo -e "${GREEN}║  🎉 SUCCESS! All services are healthy!              ║${NC}"
    echo -e "${GREEN}║                                                      ║${NC}"
    echo -e "${GREEN}║  Frontend: http://localhost:4200                    ║${NC}"
    echo -e "${GREEN}║  Backend:  http://localhost:3000                    ║${NC}"
    echo -e "${GREEN}║                                                      ║${NC}"
    echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
elif [ "$BACKEND_HEALTH" = "starting" ] || [ "$FRONTEND_HEALTH" = "starting" ]; then
    echo -e "${YELLOW}⏳ Services are still starting...${NC}"
    echo ""
    echo -e "${BLUE}Wait another 60 seconds and check again:${NC}"
    echo "  docker compose ps"
    echo ""
    echo -e "${BLUE}Or watch the logs:${NC}"
    echo "  docker compose logs -f"
else
    echo -e "${RED}⚠️  Some services are still unhealthy${NC}"
    echo ""
    echo -e "${YELLOW}View logs to diagnose:${NC}"
    echo "  docker compose logs backend"
    echo "  docker compose logs frontend"
    echo ""
    echo -e "${YELLOW}Or run full diagnostics:${NC}"
    echo "  ./diagnose-docker.sh"
fi
