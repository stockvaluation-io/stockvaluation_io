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
echo "To run full valuations, you MUST provide the following keys:"
echo " 1. One LLM API Key (Anthropic, OpenAI, Gemini, or Groq)"
echo " 2. Tavily API Key (TAVILY_API_KEY) - for web search"
echo " 3. CurrencyBeacon API Key (CURRENCY_API_KEY) - for FX rates"
echo "    Get it for free at: https://currencybeacon.com"
echo ""

if [[ "$OSTYPE" == "darwin"* ]]; then
  SED_INPLACE="sed -i ''"
else
  SED_INPLACE="sed -i"
fi

check_env() {
  local var_name=$1
  # Matches any printable non-whitespace char right after '='
  grep -E -q "^${var_name}=[^[:space:]]+" .env
}

prompt_for_key() {
  local var_name=$1
  local prompt_text=$2
  local key_val

  while ! check_env "$var_name"; do
    if [ -t 0 ]; then
      read -sp "$prompt_text " key_val
      echo
      if [[ -n "$key_val" ]]; then
        if grep -q "^${var_name}=" .env; then
          $SED_INPLACE "s|^${var_name}=.*|${var_name}=$key_val|" .env
        else
          echo "${var_name}=$key_val" >> .env
        fi
        echo -e "${GREEN}✔ Saved $var_name${NC}"
      else
        echo -e "${RED}❌ $var_name is required. Please enter a valid key.${NC}"
      fi
    else
      echo -e "${RED}❌ $var_name is missing. In non-interactive mode, you must set $var_name in .env before running.${NC}"
      exit 1
    fi
  done
}

# 1) Prompt for LLM Key
has_llm_key=false
for key in ANTHROPIC_API_KEY OPENAI_API_KEY GEMINI_API_KEY GROQ_API_KEY; do
  if check_env "$key"; then
    has_llm_key=true
    break
  fi
done

if [ "$has_llm_key" = false ]; then
  if [ -t 0 ]; then
    while [ "$has_llm_key" = false ]; do
      echo -e "${YELLOW}Choose an LLM provider to configure:${NC}"
      echo "  1) Anthropic"
      echo "  2) OpenAI"
      echo "  3) Gemini"
      echo "  4) Groq"
      read -p "Enter Choice [1-4]: " llm_choice

      case $llm_choice in
        1) llm_var="ANTHROPIC_API_KEY"; llm_name="Anthropic" ;;
        2) llm_var="OPENAI_API_KEY"; llm_name="OpenAI" ;;
        3) llm_var="GEMINI_API_KEY"; llm_name="Gemini" ;;
        4) llm_var="GROQ_API_KEY"; llm_name="Groq" ;;
        *) echo -e "${RED}❌ Invalid choice.${NC}"; echo ""; continue ;;
      esac
      
      prompt_for_key "$llm_var" "Enter $llm_name API Key:"
      has_llm_key=true
    done
  else
    echo -e "${RED}❌ No LLM API Key found. In non-interactive mode, you must set an LLM key in .env.${NC}"
    exit 1
  fi
else
    echo -e "${GREEN}✔ LLM API Key already configured in .env${NC}"
fi

# 2) Prompt for TAVILY
echo ""
if check_env "TAVILY_API_KEY"; then
  echo -e "${GREEN}✔ TAVILY_API_KEY already configured in .env${NC}"
else
  prompt_for_key "TAVILY_API_KEY" "Enter Tavily API Key (TAVILY_API_KEY):"
fi

# 3) Prompt for CURRENCY
echo ""
if check_env "CURRENCY_API_KEY"; then
  echo -e "${GREEN}✔ CURRENCY_API_KEY already configured in .env${NC}"
else
  prompt_for_key "CURRENCY_API_KEY" "Enter CurrencyBeacon API Key (CURRENCY_API_KEY):"
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
