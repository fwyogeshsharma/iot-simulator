#!/bin/bash
#
# Deploy the simulator backend on the VM.
#
# Pulls the deploy branch, rebuilds the backend container and waits until the
# API answers. The frontend is not touched - it is deployed separately.
#
# Usage:
#   ./deploy-backend.sh                 # pull main-prod, rebuild backend
#   ./deploy-backend.sh --branch master # deploy a different branch
#   ./deploy-backend.sh --no-pull       # rebuild what is already checked out
#   ./deploy-backend.sh --full          # rebuild backend and frontend
#   ./deploy-backend.sh --force         # proceed despite uncommitted changes
#
set -euo pipefail

GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

BRANCH="main-prod"
DO_PULL=1
SERVICES="backend"
FORCE=0
PORT="${SIMULATOR_PORT:-3000}"

while [ $# -gt 0 ]; do
    case "$1" in
        --branch)   BRANCH="${2:-}"; shift 2 ;;
        --no-pull)  DO_PULL=0; shift ;;
        --full)     SERVICES=""; shift ;;
        --force)    FORCE=1; shift ;;
        -h|--help)
            sed -n '3,13p' "$0" | sed 's/^# \{0,1\}//'
            exit 0 ;;
        *)
            echo -e "${RED}Unknown option: $1${NC}"
            echo "Try --help"
            exit 1 ;;
    esac
done

cd "$(dirname "$0")"
REPO_DIR="$(pwd)"

echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo -e "${BLUE}   IoT Simulator - Backend Deploy               ${NC}"
echo -e "${BLUE}════════════════════════════════════════════════${NC}"
echo "   repo:   $REPO_DIR"
echo "   branch: $BRANCH"
echo ""

# ---------------------------------------------------------------- pre-flight

# Which compose CLI is present. The VM has v1 (docker-compose); newer hosts
# ship the v2 plugin (docker compose). Support both.
if docker compose version >/dev/null 2>&1; then
    COMPOSE="docker compose"
elif command -v docker-compose >/dev/null 2>&1; then
    COMPOSE="docker-compose"
else
    echo -e "${RED}ERROR: neither 'docker compose' nor 'docker-compose' is available${NC}"
    exit 1
fi

# Most VM users need sudo for the docker socket; skip it if docker works bare.
SUDO=""
if ! docker info >/dev/null 2>&1; then
    if sudo -n docker info >/dev/null 2>&1 || sudo docker info >/dev/null 2>&1; then
        SUDO="sudo"
    else
        echo -e "${RED}ERROR: cannot talk to the Docker daemon (with or without sudo)${NC}"
        echo "Is Docker running?  sudo systemctl start docker"
        exit 1
    fi
fi
echo -e "${GREEN}✓${NC} docker ready (${SUDO:-no sudo}, using '$COMPOSE')"

if [ ! -f .env ]; then
    echo -e "${YELLOW}⚠  no .env file - the container will fall back to the values"
    echo -e "   committed in application.yml${NC}"
fi

# ---------------------------------------------------------------- git update

if [ "$DO_PULL" -eq 1 ]; then
    # A repo cloned by another user (usually root) trips git's ownership check.
    if ! git rev-parse --git-dir >/dev/null 2>&1; then
        echo -e "${RED}ERROR: not a usable git repository${NC}"
        echo "If you see 'dubious ownership', the checkout is owned by another user:"
        echo "    sudo chown -R \$USER:\$USER $REPO_DIR"
        exit 1
    fi

    if [ -n "$(git status --porcelain)" ] && [ "$FORCE" -eq 0 ]; then
        echo -e "${RED}ERROR: uncommitted changes in the working tree${NC}"
        git status --short | head -20
        echo ""
        echo "Commit, stash, or re-run with --force to build them as-is."
        exit 1
    fi

    BEFORE="$(git rev-parse --short HEAD)"
    echo -e "${BLUE}→${NC} fetching..."
    git fetch origin "$BRANCH"

    CURRENT="$(git rev-parse --abbrev-ref HEAD)"
    if [ "$CURRENT" != "$BRANCH" ]; then
        echo -e "${YELLOW}→${NC} switching branch: $CURRENT → $BRANCH"
        git checkout "$BRANCH"
    fi

    git merge --ff-only "origin/$BRANCH"
    AFTER="$(git rev-parse --short HEAD)"

    if [ "$BEFORE" = "$AFTER" ]; then
        echo -e "${GREEN}✓${NC} already up to date at $AFTER"
    else
        echo -e "${GREEN}✓${NC} updated $BEFORE → $AFTER"
        git --no-pager log --oneline "$BEFORE..$AFTER" | sed 's/^/     /'
    fi
else
    echo -e "${YELLOW}→${NC} --no-pull: building the current checkout ($(git rev-parse --short HEAD 2>/dev/null || echo unknown))"
fi
echo ""

# ---------------------------------------------------------------- rebuild

echo -e "${BLUE}→${NC} rebuilding${SERVICES:+ }$SERVICES..."
# shellcheck disable=SC2086
$SUDO $COMPOSE up --build -d $SERVICES
echo ""

# ---------------------------------------------------------------- verify

echo -e "${BLUE}→${NC} waiting for the API on port $PORT..."
READY=0
for _ in $(seq 1 60); do
    if curl -sf -m 3 "http://localhost:$PORT/api/companies" >/dev/null 2>&1; then
        READY=1
        break
    fi
    sleep 2
done

if [ "$READY" -eq 0 ]; then
    echo -e "${RED}✗ API did not come up within 2 minutes${NC}"
    echo "Recent logs:"
    # shellcheck disable=SC2086
    $SUDO $COMPOSE logs --tail=40 backend
    exit 1
fi
echo -e "${GREEN}✓${NC} API responding"

# Endpoint added with the disease-profile work: a good canary for "is the
# running image actually the new build".
PROFILES="$(curl -sf -m 10 "http://localhost:$PORT/api/disease-profiles" 2>/dev/null || echo '')"
COUNT="$(printf '%s' "$PROFILES" | grep -o '"code"' | wc -l | tr -d ' ')"

echo ""
if [ -z "$PROFILES" ]; then
    echo -e "${YELLOW}⚠  /api/disease-profiles did not respond - the container may be"
    echo -e "   running an older image than the checkout.${NC}"
elif [ "$COUNT" -eq 0 ]; then
    echo -e "${YELLOW}⚠  /api/disease-profiles returned no rows. The build is current but"
    echo -e "   the backend could not read disease_profiles - check the Supabase key"
    echo -e "   in .env, and that the table migration has been applied.${NC}"
else
    echo -e "${GREEN}✓${NC} /api/disease-profiles returned $COUNT profiles"
fi

echo ""
# shellcheck disable=SC2086
$SUDO $COMPOSE ps
echo ""
echo -e "${GREEN}════════════════════════════════════════════════${NC}"
echo -e "${GREEN}   Deploy complete                              ${NC}"
echo -e "${GREEN}════════════════════════════════════════════════${NC}"
echo "   commit:  $(git rev-parse --short HEAD 2>/dev/null || echo unknown)"
echo "   logs:    ${SUDO:+sudo }$COMPOSE logs -f backend"
