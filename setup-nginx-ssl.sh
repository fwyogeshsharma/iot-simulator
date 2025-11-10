#!/bin/bash

# Nginx + SSL Setup Script for IoT Simulator
# This script sets up Nginx as a reverse proxy with Let's Encrypt SSL certificates

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

echo -e "${BLUE}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}║     Nginx + SSL Setup for IoT Simulator             ║${NC}"
echo -e "${BLUE}║                                                      ║${NC}"
echo -e "${BLUE}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

# Check if running as root
if [ "$EUID" -ne 0 ]; then
    echo -e "${RED}ERROR: This script must be run as root${NC}"
    echo "Please run: sudo ./setup-nginx-ssl.sh"
    exit 1
fi

# Get user input
echo -e "${YELLOW}Please provide the following information:${NC}"
echo ""

read -p "Enter your domain for the frontend (e.g., app.yourdomain.com): " FRONTEND_DOMAIN
read -p "Enter your domain for the backend API (e.g., api.yourdomain.com): " BACKEND_DOMAIN
read -p "Enter your email for SSL certificate notifications: " SSL_EMAIL

# Validate input
if [ -z "$FRONTEND_DOMAIN" ] || [ -z "$BACKEND_DOMAIN" ] || [ -z "$SSL_EMAIL" ]; then
    echo -e "${RED}ERROR: All fields are required${NC}"
    exit 1
fi

echo ""
echo -e "${BLUE}Configuration:${NC}"
echo "  Frontend domain: $FRONTEND_DOMAIN"
echo "  Backend domain:  $BACKEND_DOMAIN"
echo "  SSL email:       $SSL_EMAIL"
echo ""
read -p "Is this correct? (y/n): " CONFIRM

if [ "$CONFIRM" != "y" ]; then
    echo "Aborted."
    exit 0
fi

echo ""
echo -e "${YELLOW}Starting setup...${NC}"
echo ""

# Step 1: Update system
echo -e "${BLUE}[1/8] Updating system packages...${NC}"
apt-get update -qq

# Step 2: Install Nginx
echo -e "${BLUE}[2/8] Installing Nginx...${NC}"
if ! command -v nginx &> /dev/null; then
    apt-get install -y nginx
    echo -e "${GREEN}✓ Nginx installed${NC}"
else
    echo -e "${GREEN}✓ Nginx already installed${NC}"
fi

# Step 3: Install Certbot
echo -e "${BLUE}[3/8] Installing Certbot (Let's Encrypt)...${NC}"
if ! command -v certbot &> /dev/null; then
    apt-get install -y certbot python3-certbot-nginx
    echo -e "${GREEN}✓ Certbot installed${NC}"
else
    echo -e "${GREEN}✓ Certbot already installed${NC}"
fi

# Step 4: Configure Firewall
echo -e "${BLUE}[4/8] Configuring firewall...${NC}"
if command -v ufw &> /dev/null; then
    ufw allow 'Nginx Full'
    ufw allow 22/tcp
    ufw --force enable
    echo -e "${GREEN}✓ UFW configured${NC}"
elif command -v firewall-cmd &> /dev/null; then
    firewall-cmd --permanent --add-service=http
    firewall-cmd --permanent --add-service=https
    firewall-cmd --permanent --add-service=ssh
    firewall-cmd --reload
    echo -e "${GREEN}✓ Firewalld configured${NC}"
else
    echo -e "${YELLOW}⚠ No firewall detected, skipping...${NC}"
fi

# Step 5: Create Nginx configuration for Frontend
echo -e "${BLUE}[5/8] Creating Nginx configuration for Frontend...${NC}"
cat > /etc/nginx/sites-available/iot-simulator-frontend << EOF
server {
    listen 80;
    listen [::]:80;
    server_name $FRONTEND_DOMAIN;

    # Redirect to HTTPS (will be configured by Certbot)
    location / {
        proxy_pass http://localhost:4200;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
EOF

# Step 6: Create Nginx configuration for Backend
echo -e "${BLUE}[6/8] Creating Nginx configuration for Backend...${NC}"
cat > /etc/nginx/sites-available/iot-simulator-backend << EOF
server {
    listen 80;
    listen [::]:80;
    server_name $BACKEND_DOMAIN;

    # Redirect to HTTPS (will be configured by Certbot)
    location / {
        proxy_pass http://localhost:3000;
        proxy_http_version 1.1;
        proxy_set_header Upgrade \$http_upgrade;
        proxy_set_header Connection 'upgrade';
        proxy_set_header Host \$host;
        proxy_cache_bypass \$http_upgrade;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;

        # CORS headers for API
        add_header 'Access-Control-Allow-Origin' '*' always;
        add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS' always;
        add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization' always;

        # Handle preflight requests
        if (\$request_method = 'OPTIONS') {
            add_header 'Access-Control-Allow-Origin' '*';
            add_header 'Access-Control-Allow-Methods' 'GET, POST, PUT, DELETE, OPTIONS';
            add_header 'Access-Control-Allow-Headers' 'DNT,User-Agent,X-Requested-With,If-Modified-Since,Cache-Control,Content-Type,Range,Authorization';
            add_header 'Access-Control-Max-Age' 1728000;
            add_header 'Content-Type' 'text/plain; charset=utf-8';
            add_header 'Content-Length' 0;
            return 204;
        }
    }
}
EOF

# Enable sites
ln -sf /etc/nginx/sites-available/iot-simulator-frontend /etc/nginx/sites-enabled/
ln -sf /etc/nginx/sites-available/iot-simulator-backend /etc/nginx/sites-enabled/

# Remove default site if it exists
rm -f /etc/nginx/sites-enabled/default

# Test Nginx configuration
echo -e "${BLUE}[7/8] Testing Nginx configuration...${NC}"
if nginx -t; then
    echo -e "${GREEN}✓ Nginx configuration is valid${NC}"
    systemctl restart nginx
    echo -e "${GREEN}✓ Nginx restarted${NC}"
else
    echo -e "${RED}✗ Nginx configuration test failed${NC}"
    exit 1
fi

# Step 7: Obtain SSL certificates
echo -e "${BLUE}[8/8] Obtaining SSL certificates...${NC}"
echo ""
echo -e "${YELLOW}This will request SSL certificates from Let's Encrypt.${NC}"
echo -e "${YELLOW}Make sure your domains are pointing to this server!${NC}"
echo ""
read -p "Continue? (y/n): " CONTINUE

if [ "$CONTINUE" != "y" ]; then
    echo ""
    echo -e "${YELLOW}Skipping SSL certificate setup.${NC}"
    echo -e "${YELLOW}You can run these commands later:${NC}"
    echo "  sudo certbot --nginx -d $FRONTEND_DOMAIN -d $BACKEND_DOMAIN --email $SSL_EMAIL"
else
    certbot --nginx -d "$FRONTEND_DOMAIN" -d "$BACKEND_DOMAIN" --email "$SSL_EMAIL" --agree-tos --no-eff-email --redirect

    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ SSL certificates obtained successfully${NC}"

        # Set up automatic renewal
        systemctl enable certbot.timer
        systemctl start certbot.timer
        echo -e "${GREEN}✓ Automatic SSL renewal enabled${NC}"
    else
        echo -e "${RED}✗ Failed to obtain SSL certificates${NC}"
        echo ""
        echo "Common issues:"
        echo "1. DNS records not pointing to this server"
        echo "2. Firewall blocking port 80/443"
        echo "3. Domain not accessible from internet"
        echo ""
        echo "You can retry later with:"
        echo "  sudo certbot --nginx -d $FRONTEND_DOMAIN -d $BACKEND_DOMAIN --email $SSL_EMAIL"
    fi
fi

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════════════╗${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}║     ✓ Setup Complete!                               ║${NC}"
echo -e "${GREEN}║                                                      ║${NC}"
echo -e "${GREEN}╚══════════════════════════════════════════════════════╝${NC}"
echo ""

echo -e "${BLUE}Access URLs:${NC}"
echo "  Frontend: https://$FRONTEND_DOMAIN"
echo "  Backend:  https://$BACKEND_DOMAIN"
echo ""

echo -e "${BLUE}Next Steps:${NC}"
echo "1. Update frontend environment to use HTTPS backend:"
echo "   Edit: frontend/iot-simulator-frontend/src/environments/environment.prod.ts"
echo "   Change: backendUrl: 'https://$BACKEND_DOMAIN/api'"
echo ""
echo "2. Rebuild frontend:"
echo "   docker compose down"
echo "   docker compose up -d --build frontend"
echo ""
echo "3. Test your application:"
echo "   https://$FRONTEND_DOMAIN"
echo ""

echo -e "${BLUE}Useful Commands:${NC}"
echo "  Check Nginx status:        sudo systemctl status nginx"
echo "  Reload Nginx:              sudo systemctl reload nginx"
echo "  Check SSL certificates:    sudo certbot certificates"
echo "  Renew SSL (manual):        sudo certbot renew"
echo "  Test SSL renewal:          sudo certbot renew --dry-run"
echo ""

echo -e "${YELLOW}Note: SSL certificates auto-renew every 60 days.${NC}"
