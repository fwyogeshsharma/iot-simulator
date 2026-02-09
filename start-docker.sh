#!/bin/bash

# IoT Simulator - Docker Startup Script
# Simple and reliable startup without errors

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Print banner
echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════════════╗"
echo "║                                                      ║"
echo "║          🏥 IoT Simulator - Startup                 ║"
echo "║                                                      ║"
echo "╚══════════════════════════════════════════════════════╝"
echo -e "${NC}"
echo ""

# Check prerequisites
echo -e "${BLUE}[1/5] Checking prerequisites...${NC}"

if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker not installed${NC}"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo -e "${RED}❌ Docker daemon not running${NC}"
    exit 1
fi

if [ ! -f .env ]; then
    echo -e "${RED}❌ .env file not found${NC}"
    echo "   Run: cp .env.example .env"
    exit 1
fi

echo -e "${GREEN}✅ Prerequisites OK${NC}"
echo ""

# Parse arguments
REBUILD=false
CLEAN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --rebuild) REBUILD=true; shift ;;
        --clean) CLEAN=true; shift ;;
        --help)
            echo "Usage: ./start-docker.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --rebuild    Rebuild Docker images"
            echo "  --clean      Remove all containers and images first"
            echo "  --help       Show this help"
            exit 0
            ;;
        *) echo -e "${RED}Unknown option: $1${NC}"; exit 1 ;;
    esac
done

# Clean if requested
if [ "$CLEAN" = true ]; then
    echo -e "${BLUE}[2/5] Cleaning up...${NC}"
    docker-compose down -v --rmi all 2>/dev/null
    echo -e "${GREEN}✅ Cleanup complete${NC}"
    echo ""
else
    echo -e "${BLUE}[2/5] Stopping existing containers...${NC}"
    docker-compose down 2>/dev/null
    echo -e "${GREEN}✅ Stopped${NC}"
    echo ""
fi

# Remove old unhealthy containers
echo -e "${BLUE}[3/5] Removing old containers...${NC}"
docker-compose rm -f 2>/dev/null
echo -e "${GREEN}✅ Removed${NC}"
echo ""

# Build if requested
if [ "$REBUILD" = true ]; then
    echo -e "${BLUE}[4/5] Building images (this takes 2-3 minutes)...${NC}"
    docker-compose build --no-cache
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Build failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Build complete${NC}"
    echo ""
else
    echo -e "${BLUE}[4/5] Building images (if needed)...${NC}"
    docker-compose build
    if [ $? -ne 0 ]; then
        echo -e "${RED}❌ Build failed${NC}"
        exit 1
    fi
    echo -e "${GREEN}✅ Build complete${NC}"
    echo ""
fi

# Start services in background
echo -e "${BLUE}[5/5] Starting services...${NC}"
docker-compose up -d --remove-orphans

if [ $? -ne 0 ]; then
    echo -e "${RED}❌ Failed to start services${NC}"
    echo ""
    echo "Check logs with: docker-compose logs"
    exit 1
fi

echo -e "${GREEN}✅ Services started${NC}"
echo ""

# Wait for containers to be running
echo -e "${YELLOW}⏳ Waiting for containers to start...${NC}"
sleep 5

# Check if containers are running
BACKEND_STATUS=$(docker inspect -f '{{.State.Status}}' iot-simulator-backend 2>/dev/null)

if [ "$BACKEND_STATUS" != "running" ]; then
    echo -e "${RED}❌ Backend container not running${NC}"
    echo ""
    echo "Check logs: docker compose logs backend"
    exit 1
fi

# Wait for services to be ready (simple check)
echo -e "${YELLOW}⏳ Waiting for services to be ready (30 seconds)...${NC}"
sleep 30

# Final status check
echo ""
echo -e "${BLUE}Checking service status...${NC}"
docker-compose ps

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║  ✅ IoT Simulator Started Successfully!             ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${BLUE}Access URLs:${NC}"
echo "  Backend:  ${GREEN}http://localhost:3000${NC}"
echo ""
echo "  Or from external:"
echo "  Backend:  ${GREEN}http://34.93.247.3:3000${NC}"
echo ""
echo "  Note: Frontend is deployed on Netlify"
echo ""

echo -e "${BLUE}Useful Commands:${NC}"
echo "  View logs:        ${YELLOW}docker-compose logs -f${NC}"
echo "  Check status:     ${YELLOW}docker-compose ps${NC}"
echo "  Stop services:    ${YELLOW}docker-compose down${NC}"
echo "  Restart service:  ${YELLOW}docker-compose restart backend${NC}"
echo ""

echo -e "${YELLOW}💡 Tip: Wait 1-2 minutes for full initialization${NC}"
echo ""
