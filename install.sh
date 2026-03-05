#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# StockValuation.io Automated Install Script
# Usage: curl -fsSL https://raw.githubusercontent.com/stockvaluation-io/stockvaluation_io/main/install.sh | bash
# ==============================================================================

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

echo -e "${BLUE}===========================================================${NC}"
echo -e "${GREEN}   🚀 Installing StockValuation.io (Local-first DCF)   ${NC}"
echo -e "${BLUE}===========================================================${NC}\n"

REPO_URL="https://github.com/stockvaluation-io/stockvaluation_io.git"
CLONE_DIR="stockvaluation_io"

# 1. System Requirements Check
echo -e "${YELLOW}=> Checking system requirements...${NC}"

if ! command -v git &> /dev/null; then
  echo -e "${RED}❌ Error: 'git' is required but not installed.${NC}"
  echo -e "Please install git to continue."
  exit 1
fi

if ! command -v docker &> /dev/null; then
  echo -e "${RED}❌ Error: 'docker' is required but not installed.${NC}"
  echo -e "Please install Docker to continue."
  exit 1
fi

DOCKER_CMD=""
if docker compose version &> /dev/null; then
  DOCKER_CMD="docker compose"
elif command -v docker-compose &> /dev/null; then
  DOCKER_CMD="docker-compose"
else
  echo -e "${RED}❌ Error: Docker Compose is required but not installed.${NC}"
  echo -e "Please install Docker Compose to continue."
  exit 1
fi

echo -e "${GREEN}✔ Requirements met.${NC}\n"

# 2. Clone the repository
if [ -d "$CLONE_DIR" ]; then
  echo -e "${YELLOW}=> Directory '$CLONE_DIR' already exists. Navigating to it...${NC}"
  cd "$CLONE_DIR"
else
  echo -e "${YELLOW}=> Cloning repository into ./$CLONE_DIR...${NC}"
  git clone "$REPO_URL" "$CLONE_DIR"
  cd "$CLONE_DIR"
fi

# 3. Setup Environment
echo -e "\n${YELLOW}=> Setting up environment variables...${NC}"
if [ ! -f .env ]; then
  cp .env.example .env
  echo -e "${GREEN}✔ Created .env from .env.example${NC}"
else
  echo -e "${GREEN}✔ Using existing .env file${NC}"
fi

# 4. Bootstrap Secrets
echo -e "\n${YELLOW}=> Bootstrapping local secrets...${NC}"
chmod +x ./scripts/bootstrap_local_secrets.sh
./scripts/bootstrap_local_secrets.sh

# 5. Interactive API Key Setup
echo -e "\n${BLUE}===========================================================${NC}"
echo -e "${YELLOW} 🔑 API Keys Setup${NC}"
echo -e "${BLUE}===========================================================${NC}"
echo "To run full valuations, you will need:"
echo " 1. An LLM API Key (e.g., Anthropic, OpenAI) for AI analysis."
echo " 2. CurrencyBeacon API Key (CURRENCY_API_KEY)."
echo "    Why is this needed? It is used to fetch FX rates during valuation when"
echo "    the market quote currency and financial-reporting currency differ."
echo "    Get it for free at: https://currencybeacon.com"
echo ""

if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_INPLACE="sed -i ''"
else
  SED_INPLACE="sed -i"
fi

# Only ask for API keys if running interactively
if [ -t 0 ]; then
  read -p "Would you like to enter your Anthropic API Key now? [y/N]: " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    read -sp "Enter ANTHROPIC_API_KEY: " anthropic_key
    echo
    if grep -q "^ANTHROPIC_API_KEY=" .env; then
      $SED_INPLACE "s|^ANTHROPIC_API_KEY=.*|ANTHROPIC_API_KEY=$anthropic_key|" .env
    else
      echo "ANTHROPIC_API_KEY=$anthropic_key" >> .env
    fi
    echo -e "${GREEN}✔ Saved ANTHROPIC_API_KEY${NC}"
  fi

  echo ""
  read -p "Would you like to enter your Currency API Key now? [y/N]: " -n 1 -r
  echo
  if [[ $REPLY =~ ^[Yy]$ ]]; then
    read -sp "Enter CURRENCY_API_KEY: " currency_key
    echo
    if grep -q "^CURRENCY_API_KEY=" .env; then
      $SED_INPLACE "s|^CURRENCY_API_KEY=.*|CURRENCY_API_KEY=$currency_key|" .env
    else
      echo "CURRENCY_API_KEY=$currency_key" >> .env
    fi
    echo -e "${GREEN}✔ Saved CURRENCY_API_KEY${NC}"
  fi
else
  echo -e "${YELLOW}Running in non-interactive mode. Skipping API key prompts.${NC}"
  echo "Please ensure you update .env with your keys manually later."
fi

# Fallback: ensure CURRENCY_API_KEY is not empty so docker-compose doesn't crash
if grep -q "^CURRENCY_API_KEY=$" .env; then
  $SED_INPLACE "s|^CURRENCY_API_KEY=$|CURRENCY_API_KEY=CHANGE_ME_CURRENCY_API_KEY|" .env
fi

# 6. Start Containers
echo -e "\n${YELLOW}=> Starting Docker containers...${NC}"
$DOCKER_CMD -f docker-compose.local.yml up -d --build

echo -e "\n${BLUE}===========================================================${NC}"
echo -e "${GREEN}   ✅ Setup Complete!   ${NC}"
echo -e "${BLUE}===========================================================${NC}"
echo -e "StockValuation.io is now running in the background.\n"
echo -e "Access the application at:"
echo -e "👉 ${GREEN}UI:${NC}             http://localhost:4200"
echo -e "👉 ${GREEN}Valuation API:${NC}  http://localhost:8081"
echo -e "👉 ${GREEN}Agent API:${NC}      http://localhost:5001"
echo -e "👉 ${GREEN}Chat API:${NC}       http://localhost:5002\n"
echo -e "To stop the app, run:"
echo -e "    ${YELLOW}cd $CLONE_DIR && $DOCKER_CMD -f docker-compose.local.yml down${NC}\n"
echo -e "To view logs, run:"
echo -e "    ${YELLOW}cd $CLONE_DIR && $DOCKER_CMD -f docker-compose.local.yml logs -f${NC}"
echo -e "${BLUE}===========================================================${NC}"
