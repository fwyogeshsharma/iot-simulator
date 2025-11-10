#!/bin/bash

# Simple Docker Startup Script for IoT Simulator
# This script starts the application in detached mode

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   IoT Simulator - Starting Services           ${NC}"
echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo ""

# Check if .env exists
if [ ! -f .env ]; then
    echo -e "${RED}ERROR: .env file not found!${NC}"
    echo ""
    echo "Please create .env file from .env.example:"
    echo "  cp .env.example .env"
    echo ""
    echo "Then edit .env with your Supabase credentials"
    exit 1
fi

# Check Docker
if ! command -v docker &> /dev/null; then
    echo -e "${RED}ERROR: Docker is not installed${NC}"
    exit 1
fi

if ! docker info &> /dev/null; then
    echo -e "${RED}ERROR: Docker daemon is not running${NC}"
    exit 1
fi

echo -e "${GREEN}✓ Docker is running${NC}"
echo -e "${GREEN}✓ .env file found${NC}"
echo ""

# Stop any existing containers
echo -e "${YELLOW}Stopping existing containers...${NC}"
docker compose down 2>/dev/null

# Remove old containers if they exist
echo -e "${YELLOW}Cleaning up old containers...${NC}"
docker compose rm -f 2>/dev/null

echo ""
echo -e "${BLUE}Building and starting services...${NC}"
echo -e "${YELLOW}This will take 2-3 minutes on first run...${NC}"
echo ""

# Start services
docker compose up -d --build

if [ $? -eq 0 ]; then
    echo ""
    echo -e "${GREEN}════════════════════════════════════════════════${NC}"
    echo -e "${GREEN}   ✓ Services Started Successfully!            ${NC}"
    echo -e "${GREEN}════════════════════════════════════════════════${NC}"
    echo ""
    echo -e "${BLUE}Access URLs:${NC}"
    echo -e "  Frontend: ${GREEN}http://localhost:4200${NC}"
    echo -e "  Backend:  ${GREEN}http://localhost:3000${NC}"
    echo ""
    echo -e "${YELLOW}Useful Commands:${NC}"
    echo "  View status:  docker compose ps"
    echo "  View logs:    docker compose logs -f"
    echo "  Stop:         docker compose down"
    echo ""
    echo -e "${YELLOW}Wait 2-3 minutes for services to be healthy.${NC}"
else
    echo ""
    echo -e "${RED}════════════════════════════════════════════════${NC}"
    echo -e "${RED}   ERROR: Failed to start services              ${NC}"
    echo -e "${RED}════════════════════════════════════════════════${NC}"
    echo ""
    echo "Check logs with: docker compose logs"
    exit 1
fi
