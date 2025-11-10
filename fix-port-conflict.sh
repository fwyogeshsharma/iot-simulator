#!/bin/bash

# Fix Port Conflict Script
# This script helps identify and resolve port conflicts for the IoT Simulator

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║          Port Conflict Resolution Tool              ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Check for port 3000
echo -e "${YELLOW}Checking port 3000...${NC}"
PORT_3000=$(sudo lsof -i :3000 2>/dev/null | grep LISTEN)

if [ -z "$PORT_3000" ]; then
    echo -e "${GREEN}✅ Port 3000 is available${NC}"
else
    echo -e "${RED}❌ Port 3000 is in use:${NC}"
    echo "$PORT_3000"
    echo ""

    # Extract PID
    PID=$(echo "$PORT_3000" | awk '{print $2}' | head -1)
    PROCESS=$(echo "$PORT_3000" | awk '{print $1}' | head -1)

    echo -e "${YELLOW}Process: $PROCESS (PID: $PID)${NC}"
    echo ""

    # Ask user what to do
    echo "What would you like to do?"
    echo "1) Kill the process using port 3000"
    echo "2) Show me the full process details"
    echo "3) Exit (I'll handle it manually)"
    echo ""
    read -p "Enter your choice (1-3): " choice

    case $choice in
        1)
            echo -e "${YELLOW}Killing process $PID...${NC}"
            sudo kill -9 $PID
            sleep 2

            # Verify it's killed
            if sudo lsof -i :3000 &>/dev/null; then
                echo -e "${RED}❌ Failed to kill process${NC}"
                exit 1
            else
                echo -e "${GREEN}✅ Process killed successfully${NC}"
            fi
            ;;
        2)
            echo -e "${BLUE}Full process details:${NC}"
            ps aux | grep $PID
            echo ""
            echo -e "${YELLOW}To kill it, run: sudo kill -9 $PID${NC}"
            exit 0
            ;;
        3)
            echo -e "${BLUE}Exiting...${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}Invalid choice${NC}"
            exit 1
            ;;
    esac
fi

# Check for port 4200
echo ""
echo -e "${YELLOW}Checking port 4200...${NC}"
PORT_4200=$(sudo lsof -i :4200 2>/dev/null | grep LISTEN)

if [ -z "$PORT_4200" ]; then
    echo -e "${GREEN}✅ Port 4200 is available${NC}"
else
    echo -e "${RED}❌ Port 4200 is in use:${NC}"
    echo "$PORT_4200"
    echo ""

    # Extract PID
    PID=$(echo "$PORT_4200" | awk '{print $2}' | head -1)
    PROCESS=$(echo "$PORT_4200" | awk '{print $1}' | head -1)

    echo -e "${YELLOW}Process: $PROCESS (PID: $PID)${NC}"
    echo ""

    # Ask user what to do
    echo "What would you like to do?"
    echo "1) Kill the process using port 4200"
    echo "2) Show me the full process details"
    echo "3) Skip (leave it running)"
    echo ""
    read -p "Enter your choice (1-3): " choice

    case $choice in
        1)
            echo -e "${YELLOW}Killing process $PID...${NC}"
            sudo kill -9 $PID
            sleep 2

            # Verify it's killed
            if sudo lsof -i :4200 &>/dev/null; then
                echo -e "${RED}❌ Failed to kill process${NC}"
                exit 1
            else
                echo -e "${GREEN}✅ Process killed successfully${NC}"
            fi
            ;;
        2)
            echo -e "${BLUE}Full process details:${NC}"
            ps aux | grep $PID
            echo ""
            echo -e "${YELLOW}To kill it, run: sudo kill -9 $PID${NC}"
            exit 0
            ;;
        3)
            echo -e "${BLUE}Skipping...${NC}"
            ;;
        *)
            echo -e "${RED}Invalid choice${NC}"
            exit 1
            ;;
    esac
fi

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║  ✅ All ports are now available!                     ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║  You can now run: ./start-docker.sh                 ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""
