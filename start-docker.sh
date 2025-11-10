#!/bin/bash

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Print banner
echo -e "${BLUE}"
echo "╔══════════════════════════════════════════════════════╗"
echo "║                                                      ║"
echo "║          🏥 SymBIOT IoT Simulator                   ║"
echo "║          Docker Startup Script                       ║"
echo "║                                                      ║"
echo "╚══════════════════════════════════════════════════════╝"
echo -e "${NC}"

# Check if Docker is installed
echo -e "${BLUE}Checking Docker installation...${NC}"
if ! command -v docker &> /dev/null; then
    echo -e "${RED}❌ Docker is not installed. Please install Docker first.${NC}"
    exit 1
fi

if ! command -v docker-compose &> /dev/null && ! docker compose version &> /dev/null; then
    echo -e "${RED}❌ Docker Compose is not installed. Please install Docker Compose first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker is installed${NC}"

# Check if Docker daemon is running
if ! docker info &> /dev/null; then
    echo -e "${RED}❌ Docker daemon is not running. Please start Docker first.${NC}"
    exit 1
fi

echo -e "${GREEN}✅ Docker daemon is running${NC}"

# Function to handle cleanup
cleanup() {
    echo -e "\n${YELLOW}Stopping containers...${NC}"
    docker-compose down
    exit 0
}

# Trap Ctrl+C
trap cleanup SIGINT SIGTERM

# Parse command line arguments
REBUILD=false
CLEAN=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --rebuild)
            REBUILD=true
            shift
            ;;
        --clean)
            CLEAN=true
            shift
            ;;
        --help)
            echo "Usage: ./start-docker.sh [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --rebuild    Rebuild Docker images before starting"
            echo "  --clean      Remove all containers, images, and volumes before starting"
            echo "  --help       Show this help message"
            exit 0
            ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Use --help for usage information"
            exit 1
            ;;
    esac
done

# Clean if requested
if [ "$CLEAN" = true ]; then
    echo -e "${YELLOW}🧹 Cleaning up Docker containers, images, and volumes...${NC}"
    docker-compose down -v --rmi all
    echo -e "${GREEN}✅ Cleanup complete${NC}"
fi

# Build images if requested or if they don't exist
if [ "$REBUILD" = true ]; then
    echo -e "${BLUE}🔨 Building Docker images...${NC}"
    docker-compose build --no-cache
    echo -e "${GREEN}✅ Build complete${NC}"
fi

# Start the services
echo -e "${BLUE}🚀 Starting IoT Simulator services...${NC}"
echo -e "${BLUE}   - Backend (Spring Boot): http://localhost:3000${NC}"
echo -e "${BLUE}   - Frontend (Angular): http://localhost:4200${NC}"
echo ""

# Start docker-compose with build if images don't exist
if [ "$REBUILD" = false ]; then
    docker-compose up --build -d
else
    docker-compose up -d
fi

# Wait for services to be healthy
echo ""
echo -e "${YELLOW}⏳ Waiting for services to be ready...${NC}"
echo ""

# Function to check service health
check_health() {
    local service=$1
    local max_attempts=60
    local attempt=1

    while [ $attempt -le $max_attempts ]; do
        if docker-compose ps | grep $service | grep -q "healthy\|Up"; then
            return 0
        fi
        echo -e "${YELLOW}   Waiting for $service... (attempt $attempt/$max_attempts)${NC}"
        sleep 2
        attempt=$((attempt + 1))
    done
    return 1
}

# Check backend health
if check_health "backend"; then
    echo -e "${GREEN}✅ Backend is ready${NC}"
else
    echo -e "${RED}❌ Backend failed to start${NC}"
    docker-compose logs backend
    exit 1
fi

# Check frontend health
if check_health "frontend"; then
    echo -e "${GREEN}✅ Frontend is ready${NC}"
else
    echo -e "${RED}❌ Frontend failed to start${NC}"
    docker-compose logs frontend
    exit 1
fi

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║  🎉 IoT Simulator is now running!                   ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║  📊 Frontend: ${BLUE}http://localhost:4200${GREEN}                 ║${NC}"
echo -e "${GREEN}║  🔧 Backend:  ${BLUE}http://localhost:3000${GREEN}                 ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║  Press Ctrl+C to stop all services                  ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Show logs
echo -e "${BLUE}📋 Showing live logs (press Ctrl+C to stop)...${NC}"
echo ""
docker-compose logs -f
