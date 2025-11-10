# Nginx + SSL Setup Guide for IoT Simulator

This guide will help you set up Nginx as a reverse proxy with Let's Encrypt SSL certificates for secure HTTPS access.

## Prerequisites

Before running the setup script:

1. **Domain Names**: You need two domain names pointing to your server:
   - One for the frontend (e.g., `app.yourdomain.com`)
   - One for the backend API (e.g., `api.yourdomain.com`)

2. **DNS Configuration**: Ensure both domains have A records pointing to your server's IP:
   ```
   app.yourdomain.com  → A → your-server-ip
   api.yourdomain.com  → A → your-server-ip
   ```

3. **Server Requirements**:
   - Root/sudo access
   - Ports 80 and 443 open (for HTTP and HTTPS)
   - Ubuntu/Debian or CentOS/RHEL

4. **IoT Simulator Running**: Make sure your Docker containers are running on ports 3000 (backend) and 4200 (frontend)

## Quick Setup

### Step 1: Make Script Executable

```bash
chmod +x setup-nginx-ssl.sh
```

### Step 2: Run the Script

```bash
sudo ./setup-nginx-ssl.sh
```

The script will ask for:
- **Frontend domain**: Your domain for the UI (e.g., `app.yourdomain.com`)
- **Backend domain**: Your domain for the API (e.g., `api.yourdomain.com`)
- **Email**: Your email for SSL certificate notifications

### Step 3: Wait for Setup

The script will:
1. ✅ Update system packages
2. ✅ Install Nginx
3. ✅ Install Certbot (Let's Encrypt client)
4. ✅ Configure firewall
5. ✅ Create Nginx configurations
6. ✅ Obtain SSL certificates
7. ✅ Configure automatic renewal

## What the Script Does

### 1. Nginx Installation
- Installs Nginx web server
- Configures it as a reverse proxy

### 2. SSL Certificates
- Obtains free SSL certificates from Let's Encrypt
- Configures automatic renewal (every 60 days)

### 3. Firewall Configuration
- Opens ports 80 (HTTP) and 443 (HTTPS)
- Ensures SSH port 22 remains open

### 4. Reverse Proxy Setup
- Routes `app.yourdomain.com` → `localhost:4200` (frontend)
- Routes `api.yourdomain.com` → `localhost:3000` (backend)
- Adds CORS headers for API
- Enables HTTPS redirect

## After Setup

### Update Frontend Configuration

Edit the production environment file to use HTTPS:

```bash
nano frontend/iot-simulator-frontend/src/environments/environment.prod.ts
```

Change:
```typescript
backendUrl: 'http://localhost:3000/api'
```

To:
```typescript
backendUrl: 'https://api.yourdomain.com/api'  // Use your actual domain
```

### Rebuild Frontend

```bash
docker compose down
docker compose up -d --build frontend
```

### Test Your Application

Access your application:
- **Frontend**: https://app.yourdomain.com
- **Backend**: https://api.yourdomain.com

## Architecture After Setup

```
Internet
    ↓
[Nginx with SSL]
    ├── app.yourdomain.com (HTTPS:443)
    │   ↓ (reverse proxy)
    │   → localhost:4200 (Frontend - Angular/Nginx in Docker)
    │
    └── api.yourdomain.com (HTTPS:443)
        ↓ (reverse proxy)
        → localhost:3000 (Backend - Spring Boot in Docker)
```

## Nginx Configuration Files

After setup, you'll have:

### Frontend Config
Location: `/etc/nginx/sites-available/iot-simulator-frontend`

```nginx
server {
    listen 443 ssl;
    server_name app.yourdomain.com;

    # SSL certificates (added by Certbot)
    ssl_certificate /etc/letsencrypt/live/app.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/app.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:4200;
        # ... other proxy settings
    }
}
```

### Backend Config
Location: `/etc/nginx/sites-available/iot-simulator-backend`

```nginx
server {
    listen 443 ssl;
    server_name api.yourdomain.com;

    # SSL certificates (added by Certbot)
    ssl_certificate /etc/letsencrypt/live/api.yourdomain.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.yourdomain.com/privkey.pem;

    location / {
        proxy_pass http://localhost:3000;
        # ... CORS headers and proxy settings
    }
}
```

## Useful Commands

### Nginx Management

```bash
# Check Nginx status
sudo systemctl status nginx

# Start Nginx
sudo systemctl start nginx

# Stop Nginx
sudo systemctl stop nginx

# Restart Nginx
sudo systemctl restart nginx

# Reload configuration (no downtime)
sudo systemctl reload nginx

# Test configuration
sudo nginx -t

# View error logs
sudo tail -f /var/log/nginx/error.log

# View access logs
sudo tail -f /var/log/nginx/access.log
```

### SSL Certificate Management

```bash
# List all certificates
sudo certbot certificates

# Renew all certificates (manual)
sudo certbot renew

# Test renewal process (dry run)
sudo certbot renew --dry-run

# Revoke a certificate
sudo certbot revoke --cert-path /etc/letsencrypt/live/yourdomain.com/cert.pem

# Delete a certificate
sudo certbot delete --cert-name yourdomain.com
```

### Check Certificate Auto-Renewal

```bash
# Check if renewal timer is active
sudo systemctl status certbot.timer

# View renewal logs
sudo journalctl -u certbot.renew.service
```

## Troubleshooting

### SSL Certificate Failed

**Error**: "Challenge failed for domain"

**Solutions**:
1. Check DNS is pointing to your server:
   ```bash
   dig app.yourdomain.com
   nslookup api.yourdomain.com
   ```

2. Check firewall allows port 80:
   ```bash
   sudo ufw status
   # or
   sudo firewall-cmd --list-all
   ```

3. Verify Nginx is serving correctly:
   ```bash
   curl http://app.yourdomain.com
   ```

4. Retry certificate:
   ```bash
   sudo certbot --nginx -d app.yourdomain.com -d api.yourdomain.com --email your@email.com
   ```

### "Address already in use" Error

**Solution**: Check if something is using port 80 or 443:

```bash
sudo lsof -i :80
sudo lsof -i :443

# If it's not Nginx, stop it:
sudo systemctl stop apache2  # if Apache is installed
```

### Frontend Can't Connect to Backend

**Problem**: CORS errors or connection refused

**Solutions**:

1. Update frontend environment to use HTTPS backend
2. Check Nginx is proxying correctly:
   ```bash
   curl https://api.yourdomain.com
   ```
3. View Nginx error logs:
   ```bash
   sudo tail -f /var/log/nginx/error.log
   ```

### Certificate Renewal Fails

**Check renewal configuration**:
```bash
# Test renewal
sudo certbot renew --dry-run

# If it fails, check logs
sudo journalctl -u certbot.renew.service

# Manually renew
sudo certbot renew --force-renewal
```

## Manual Nginx Configuration (Advanced)

If you need to customize the Nginx configuration:

### Edit Configuration

```bash
# Edit frontend config
sudo nano /etc/nginx/sites-available/iot-simulator-frontend

# Edit backend config
sudo nano /etc/nginx/sites-available/iot-simulator-backend
```

### Test and Apply

```bash
# Test configuration
sudo nginx -t

# If test passes, reload
sudo systemctl reload nginx
```

### Add Custom Headers

Edit the backend config to add security headers:

```nginx
location / {
    # ... existing proxy settings ...

    # Security headers
    add_header X-Frame-Options "SAMEORIGIN" always;
    add_header X-Content-Type-Options "nosniff" always;
    add_header X-XSS-Protection "1; mode=block" always;
    add_header Referrer-Policy "no-referrer-when-downgrade" always;
}
```

## Firewall Configuration

### UFW (Ubuntu/Debian)

```bash
# Allow Nginx Full (HTTP + HTTPS)
sudo ufw allow 'Nginx Full'

# Check status
sudo ufw status
```

### Firewalld (CentOS/RHEL)

```bash
# Allow HTTP and HTTPS
sudo firewall-cmd --permanent --add-service=http
sudo firewall-cmd --permanent --add-service=https
sudo firewall-cmd --reload

# Check status
sudo firewall-cmd --list-all
```

## Cloud Provider Security Groups

### AWS

Add inbound rules:
- HTTP: Port 80, Source: 0.0.0.0/0
- HTTPS: Port 443, Source: 0.0.0.0/0

### Azure

Add inbound security rules:
- HTTP: Priority 100, Port 80, Source: Any
- HTTPS: Priority 101, Port 443, Source: Any

### GCP

Add firewall rules:
- http-server: Port 80
- https-server: Port 443

## SSL Best Practices

1. **Use Strong Ciphers**: Let's Encrypt configures this by default
2. **Enable HSTS**: Add this to Nginx config:
   ```nginx
   add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
   ```
3. **Regular Updates**: Keep Certbot and Nginx updated
4. **Monitor Expiry**: Set up alerts for certificate expiration
5. **Test Configuration**: Use [SSL Labs](https://www.ssllabs.com/ssltest/) to test your SSL setup

## Rollback to HTTP-Only

If you need to remove SSL:

```bash
# Remove Nginx configs
sudo rm /etc/nginx/sites-enabled/iot-simulator-*
sudo rm /etc/nginx/sites-available/iot-simulator-*

# Restart Nginx
sudo systemctl restart nginx

# Revoke certificates (optional)
sudo certbot revoke --cert-path /etc/letsencrypt/live/yourdomain.com/cert.pem
```

## Support

For issues:
- Check Nginx error logs: `/var/log/nginx/error.log`
- Check Certbot logs: `/var/log/letsencrypt/letsencrypt.log`
- Test SSL: https://www.ssllabs.com/ssltest/

## Summary

After setup, you'll have:
- ✅ Secure HTTPS access to your application
- ✅ Automatic SSL certificate renewal
- ✅ Professional URLs (no port numbers)
- ✅ CORS configured for API access
- ✅ Improved security with SSL/TLS

Your IoT Simulator is now production-ready! 🚀
