#!/bin/bash

# Docker Diagnostics Script
# This script helps diagnose issues with the IoT Simulator Docker deployment

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║          Docker Diagnostics Tool                     ║${NC}"
echo -e "${BLUE}║          IoT Simulator                               ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Check Docker is running
echo -e "${YELLOW}1. Checking Docker daemon...${NC}"
if docker info &> /dev/null; then
    echo -e "${GREEN}✅ Docker daemon is running${NC}"
else
    echo -e "${RED}❌ Docker daemon is not running${NC}"
    echo -e "${YELLOW}   Please start Docker and try again${NC}"
    exit 1
fi
echo ""

# Check container status
echo -e "${YELLOW}2. Container Status:${NC}"
docker compose ps
echo ""

# Check container health
echo -e "${YELLOW}3. Container Health Details:${NC}"
BACKEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' iot-simulator-backend 2>/dev/null || echo "not found")
FRONTEND_HEALTH=$(docker inspect --format='{{.State.Health.Status}}' iot-simulator-frontend 2>/dev/null || echo "not found")

echo "Backend:  $BACKEND_HEALTH"
echo "Frontend: $FRONTEND_HEALTH"
echo ""

# If containers are unhealthy, show logs
if [ "$BACKEND_HEALTH" = "unhealthy" ] || [ "$BACKEND_HEALTH" = "starting" ]; then
    echo -e "${RED}⚠️  Backend is $BACKEND_HEALTH${NC}"
    echo -e "${YELLOW}Last 20 lines of backend logs:${NC}"
    docker compose logs --tail=20 backend
    echo ""
fi

if [ "$FRONTEND_HEALTH" = "unhealthy" ] || [ "$FRONTEND_HEALTH" = "starting" ]; then
    echo -e "${RED}⚠️  Frontend is $FRONTEND_HEALTH${NC}"
    echo -e "${YELLOW}Last 20 lines of frontend logs:${NC}"
    docker compose logs --tail=20 frontend
    echo ""
fi

# Check port bindings
echo -e "${YELLOW}4. Port Bindings:${NC}"
echo "Expected:"
echo "  - Backend:  3000 -> 8080"
echo "  - Frontend: 4200 -> 80"
echo ""
echo "Actual:"
docker compose ps --format "table {{.Service}}\t{{.Ports}}"
echo ""

# Check if ports are accessible
echo -e "${YELLOW}5. Testing Port Accessibility:${NC}"

# Test backend
if curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ 2>/dev/null | grep -q "200\|404"; then
    echo -e "${GREEN}✅ Backend port 3000 is accessible${NC}"
else
    echo -e "${RED}❌ Backend port 3000 is NOT accessible${NC}"
fi

# Test frontend
if curl -s -o /dev/null -w "%{http_code}" http://localhost:4200/ 2>/dev/null | grep -q "200"; then
    echo -e "${GREEN}✅ Frontend port 4200 is accessible${NC}"
else
    echo -e "${RED}❌ Frontend port 4200 is NOT accessible${NC}"
fi
echo ""

# Check network
echo -e "${YELLOW}6. Docker Network:${NC}"
docker network inspect iot-simulator_iot-network --format='{{range .Containers}}{{.Name}}: {{.IPv4Address}}{{"\n"}}{{end}}' 2>/dev/null || echo "Network not found"
echo ""

# Check disk space
echo -e "${YELLOW}7. Docker Disk Usage:${NC}"
docker system df
echo ""

# Resource usage
echo -e "${YELLOW}8. Container Resource Usage:${NC}"
docker stats --no-stream --format "table {{.Container}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.NetIO}}"
echo ""

# Common issues and fixes
echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║          Common Issues & Fixes                       ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

if [ "$BACKEND_HEALTH" = "unhealthy" ]; then
    echo -e "${YELLOW}Backend is unhealthy:${NC}"
    echo "  1. Check backend logs: docker compose logs backend"
    echo "  2. Verify Java heap size is sufficient"
    echo "  3. Check Supabase connectivity"
    echo "  4. Restart: docker compose restart backend"
    echo ""
fi

if [ "$FRONTEND_HEALTH" = "unhealthy" ]; then
    echo -e "${YELLOW}Frontend is unhealthy:${NC}"
    echo "  1. Check frontend logs: docker compose logs frontend"
    echo "  2. Verify nginx configuration"
    echo "  3. Check if Angular build completed successfully"
    echo "  4. Restart: docker compose restart frontend"
    echo "  5. Rebuild: docker compose up --build -d frontend"
    echo ""
fi

if [ "$BACKEND_HEALTH" = "not found" ] || [ "$FRONTEND_HEALTH" = "not found" ]; then
    echo -e "${YELLOW}Containers not found:${NC}"
    echo "  1. Start services: ./start-docker.sh"
    echo "  2. Or: docker compose up -d"
    echo ""
fi

# Quick action menu
echo -e "${BLUE}Quick Actions:${NC}"
echo "1) View all logs (live)"
echo "2) View backend logs only"
echo "3) View frontend logs only"
echo "4) Restart all services"
echo "5) Restart backend only"
echo "6) Restart frontend only"
echo "7) Rebuild and restart all"
echo "8) Exit"
echo ""
read -p "Enter your choice (1-8): " choice

case $choice in
    1)
        echo -e "${YELLOW}Showing all logs (Ctrl+C to stop)...${NC}"
        docker compose logs -f
        ;;
    2)
        echo -e "${YELLOW}Showing backend logs (Ctrl+C to stop)...${NC}"
        docker compose logs -f backend
        ;;
    3)
        echo -e "${YELLOW}Showing frontend logs (Ctrl+C to stop)...${NC}"
        docker compose logs -f frontend
        ;;
    4)
        echo -e "${YELLOW}Restarting all services...${NC}"
        docker compose restart
        echo -e "${GREEN}✅ Services restarted${NC}"
        ;;
    5)
        echo -e "${YELLOW}Restarting backend...${NC}"
        docker compose restart backend
        echo -e "${GREEN}✅ Backend restarted${NC}"
        ;;
    6)
        echo -e "${YELLOW}Restarting frontend...${NC}"
        docker compose restart frontend
        echo -e "${GREEN}✅ Frontend restarted${NC}"
        ;;
    7)
        echo -e "${YELLOW}Rebuilding and restarting...${NC}"
        docker compose down
        docker compose up --build -d
        echo -e "${GREEN}✅ Services rebuilt and restarted${NC}"
        ;;
    8)
        echo -e "${BLUE}Exiting...${NC}"
        exit 0
        ;;
    *)
        echo -e "${RED}Invalid choice${NC}"
        exit 1
        ;;
esac
