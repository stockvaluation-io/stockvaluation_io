"""
News processing and sentiment analysis functions.
"""
from typing import List, Union, Optional, Dict, Any
import logging
import re

from config.app_config import LLMConfig
from datetime import datetime, timedelta
from storage.cache.local_cache import (
    fetch_tavily_query_from_cache,
    save_tavily_query_to_cache
)


logger = logging.getLogger(__name__)


def _get_dynamic_query(
    query_type: str,
    ticker: str,
    inputs: Dict[str, Any]
) -> Optional[str]:
    """
    Get a dynamically generated Tavily query using LLM agent.
    
    Checks cache first, then generates via agent if cache miss.
    
    Args:
        query_type: Type of query (earnings, news, macro, segments)
        ticker: Stock ticker for caching (or country for macro)
        inputs: Input data for the query generator agent
        
    Returns:
        Generated query string or None if generation fails
    """
    # Check cache first
    cache_key = ticker.upper() if ticker else inputs.get("country", "UNKNOWN").upper()
    cached_query = fetch_tavily_query_from_cache(cache_key, query_type)
    if cached_query:
        logger.debug(f"Using cached {query_type} query for {cache_key}")
        return cached_query
    
    # Generate new query via agent
    try:
        from orchestration.orchestrator import run_agent
        
        agent_name = f"{query_type}_query_generator"
        result = run_agent(agent_name, inputs)
        
        if result and not result.get("error"):
            generated_query = result.get("primary_query")
            if generated_query:
                # Cache the generated query
                save_tavily_query_to_cache(cache_key, query_type, generated_query)
                logger.debug(f"Generated and cached {query_type} query for {cache_key}: {generated_query[:50]}...")
                return generated_query
            else:
                logger.warning(f"Agent returned empty primary_query for {query_type}")
        else:
            logger.warning(f"Query generator agent failed for {query_type}: {result.get('error', 'Unknown error')}")
            
    except Exception as e:
        logger.error(f"Failed to generate dynamic query for {query_type}: {e}")
    
    return None


def extract_company_domains(company_url: str = None, ticker: str = None) -> List[str]:
    """
    Construct comprehensive investor relations and corporate domain variations from a base company URL.
    
    This function generates 20+ subdomain patterns to capture IR pages across different corporate structures:
    - Main corporate site (provided)
    - Standard IR subdomains (investor.*, investors.*, ir.*, investorrelations.*)
    - Corporate/About subdomains (corporate.*, about.*, company.*)
    - Alternative IR patterns (shareholder.*, relations.*, investorinfo.*)
    - Press/Media subdomains (press.*, media.*, newsroom.*)
    - Annual report subdomains (reports.*, annualreport.*)
    - Ticker-based domains (if ticker provided)
    
    Real-world examples covered:
    - Amazon: investor.amazon.com
    - Truecaller: corporate.truecaller.com ✓
    - Microsoft: microsoft.com/investor
    - Apple: investor.apple.com
    
    Args:
        company_url: Base company domain (e.g., "truecaller.com", "amazon.com", "abc.xyz")
        ticker: Optional stock ticker for additional domain patterns (e.g., "TRUE", "AMZN")
    
    Returns:
        List of 20-30+ domain URLs for Tavily search filtering
    
    Examples:
        >>> extract_company_domains("truecaller.com", "TRUE")
        ['truecaller.com', 'investor.truecaller.com', 'ir.truecaller.com', 
         'corporate.truecaller.com', 'about.truecaller.com', ...]
        
        >>> extract_company_domains("abc.xyz", "GOOGL")
        ['abc.xyz', 'investor.abc.xyz', 'ir.abc.xyz', 'corporate.abc.xyz', ...]
    """
    domains = []
    
    if not company_url:
        return domains
    
    # Clean the base domain (remove http://, https://, www., trailing slashes)
    base_domain = company_url.lower().strip()
    base_domain = re.sub(r'^https?://', '', base_domain)
    base_domain = re.sub(r'^www\.', '', base_domain)
    base_domain = base_domain.rstrip('/')
    
    if not base_domain:
        return domains
    
    # Pattern 1: Main domain
    domains.append(base_domain)
    
    # Pattern 2: Comprehensive IR/corporate subdomain variations
    # Based on real-world examples from global companies
    ir_subdomains = [
        # Standard IR patterns
        "investor",
        "investors", 
        "ir",
        "investorrelations",
        "investor-relations",
        
        # Corporate/About patterns (e.g., Truecaller uses corporate.truecaller.com)
        "corporate",
        "about",
        "company",
        
        # Alternative IR patterns
        "investorinfo",
        "relations",
        "shareholder",
        "shareholders",
        "stockholder",
        "stockholders",
        
        # Press/Media (often linked to IR)
        "media",
        "press",
        "newsroom",
        
        # Annual reports (some companies use dedicated subdomains)
        "reports",
        "annualreport",
        "annualreports"
    ]
    
    # Add all subdomain variations
    domains.extend([f"{subdomain}.{base_domain}" for subdomain in ir_subdomains])
    
    # Pattern 3: If ticker provided, add ticker-based domains
    # (Some companies use ticker as their domain, e.g., jnj.com for Johnson & Johnson)
    if ticker:
        ticker_lower = ticker.lower()
        # Only add if different from base domain
        if ticker_lower not in base_domain:
            domains.extend([
                f"{ticker_lower}.com",
                f"investor.{ticker_lower}.com",
                f"ir.{ticker_lower}.com"
            ])
    
    # Remove duplicates while preserving order
    seen = set()
    unique_domains = []
    for d in domains:
        if d not in seen:
            seen.add(d)
            unique_domains.append(d)
    
    logger.debug(f"Generated {len(unique_domains)} IR/corporate domains from '{company_url}' (showing first 5): {unique_domains[:5]}")
    return unique_domains

def get_latest_earning_report(name: str, max_results: int = None, ticker: str = None, company_url: str = None) -> str:
    """
    Fetch latest earnings reports, transcripts, and guidance using Tavily.
    
    ✅ Focused on quarterly earnings content with longer cache duration
    ✅ Domain filtering for authoritative earnings sources + company IR pages
    ✅ Global coverage (US, Europe, Asia-Pacific earnings platforms)
    ✅ Multi-tier fallback and relaxed thresholds when results are sparse
    
    Args:
        name: Company name (e.g., "Amazon.com, Inc.")
        max_results: Maximum number of results to return
        ticker: Optional stock ticker for domain extraction (e.g., "AMZN")
        company_url: Optional company website URL (e.g., "amazon.com") for IR domain construction
    """
    if max_results is None:
        max_results = LLMConfig.MAX_NEWS_RESULTS

    if not name or name.lower() == "none":
        return "No ticker provided or invalid. Cannot fetch earnings reports."

    try:
        from tavily import TavilyClient
        search_client = TavilyClient()
    except ImportError:
        logger.error("Tavily client not available")
        return "Earnings search service unavailable."

    # Compute date range: last 12 months for earnings
    end_date = datetime.today().strftime("%Y-%m-%d")
    start_date = (datetime.today() - timedelta(days=365)).strftime("%Y-%m-%d")

    # Try dynamic query generation first
    dynamic_query = None
    if ticker:  # Only attempt dynamic query if we have a ticker for caching
        dynamic_query = _get_dynamic_query(
            query_type="earnings",
            ticker=ticker,
            inputs={
                "name": name,
                "ticker": ticker,
                "industry": "",  # Could be enriched if passed
                "description": ""  # Could be enriched if passed
            }
        )
    
    # Static fallback queries (all < 400 chars)
    static_primary_query = (
        f'"{name}" (earnings OR results) (transcript OR call OR guidance OR 10-Q OR 10-K)'
    )
    fallback_query = (
        f'"{name}" earnings transcript OR quarterly results OR earnings call'
    )
    tier3_query = (
        f'"{name}" earnings'
    )
    
    # Use dynamic query as primary if available, otherwise use static
    primary_query = dynamic_query or static_primary_query
    logger.debug(f"[Earnings] Using {'dynamic' if dynamic_query else 'static'} primary query for {name}")

    # Extract company-specific IR domains from provided URL
    company_domains = extract_company_domains(company_url, ticker) if company_url else []
    if company_domains:
        logger.debug(f"[Earnings Search] Adding {len(company_domains)} company-specific IR domains for {name}")

    # Trusted domains for earnings/transcripts - broaden IR/CDN platforms (no wildcards)
    trusted_earnings_domains = [
        # Earnings Transcript Platforms (Primary Sources)
        "seekingalpha.com",
        "fool.com",
        "earningswhispers.com",
        "earningscast.com",
        "yahoo.com",
        "yahoo.co.jp",  
        "bloomberg.com",
        "wsj.com",
        "ft.com",
        "cnbc.com",
        "marketwatch.com",
        "barrons.com",
        "benzinga.com",
        "investing.com",
        "yahoo.com",
        "yahoo.co.jp",
        # Financial Analysis Platforms
        "morningstar.com",
        "zacks.com",
        "tipranks.com",
        "gurufocus.com",

        # Press Release Wires (Earnings Announcements)
        "businesswire.com",
        "prnewswire.com",
        "globenewswire.com",
        "accesswire.com",

        # Global Stock Exchanges (Official Announcements)
        "nyse.com",
        "nasdaq.com",

        # Europe Exchanges & Sources
        "londonstockexchange.com",
        "euronext.com",
        "deutsche-boerse.com",

        # Asia-Pacific Sources
        "jpx.co.jp",
        "hkex.com.hk",
        "sgx.com",
        "asx.com.au",

        # India Sources
        "bseindia.com",
        "nseindia.com",
        "moneycontrol.com",
        "economictimes.indiatimes.com",

        # Other Regional Sources
        "nikkei.com",
        "scmp.com",
        "afr.com"
    ]
    
    # Append company-specific IR domains for earnings
    trusted_earnings_domains.extend(company_domains)
    logger.debug(f"Total earnings domains for {name}: {len(trusted_earnings_domains)}")

    def _execute_earnings_search(query: str, domains: list) -> Optional[Dict[str, Any]]:
        try:
            return search_client.search(
                query=query,
                topic="finance",
                auto_parameters=True,
                include_answer="advanced",
                max_results=max_results,
                chunks_per_source=5,
                search_depth="advanced",
                start_date=start_date,
                end_date=end_date,
                include_domains=domains
            )
        except Exception as e:
            logger.error(f"Earnings search error: {str(e)}")
            return None

    def _has_hits(payload: Optional[Dict[str, Any]]) -> bool:
        return bool(payload and payload.get("results"))

    results = None

    # Prefer official company IR/corporate domains first when available.
    if company_domains:
        logger.debug("Trying company-domain-first earnings search for %s", name)
        results = _execute_earnings_search(primary_query, company_domains)
        if not _has_hits(results):
            results = _execute_earnings_search(fallback_query, company_domains)

    # Broaden to trusted finance/news domains next.
    if not _has_hits(results):
        results = _execute_earnings_search(primary_query, trusted_earnings_domains)
    if not _has_hits(results):
        results = _execute_earnings_search(fallback_query, trusted_earnings_domains)
    if not _has_hits(results):
        # Final attempt with minimal query and no domain restriction to avoid missing IR subdomains
        try:
            results = search_client.search(
                query=tier3_query,
                topic="finance",
                auto_parameters=True,
                include_answer="advanced",
                max_results=max_results,
                chunks_per_source=5,
                search_depth="advanced",
                start_date=start_date,
                end_date=end_date
            )
        except Exception as e:
            logger.error(f"Earnings search error (tier3): {str(e)}")
            results = None

    if not _has_hits(results):
        return "No relevant earnings reports found."

    articles = results["results"]

    # Determine max Tavily score and set dynamic threshold
    max_score = max(r.get("score", 0) for r in articles)
    # Relax threshold if results are sparse
    base_threshold = 0.8 if len(articles) >= 5 else 0.6
    threshold = base_threshold * max_score

    # Filter only sufficiently high-score articles with content
    high_score_articles = [
        r for r in articles
        if r.get("score", 0) >= threshold and r.get("content")
    ]

    if not high_score_articles:
        logger.debug(f"No earnings articles passed {int(base_threshold*100)}% relevance threshold for {name}.")
        # As a final fallback, take top-N by score if any exist
        articles_sorted = sorted([r for r in articles if r.get("content")], key=lambda x: x.get("score", 0), reverse=True)
        high_score_articles = articles_sorted[: max_results]

    # Build the final output
    filtered_articles = []
    for r in high_score_articles[:max_results]:
        title = r.get("title", "").strip()
        url = r.get("url", "").strip()
        content = r.get("content", "").strip()

        if not content:
            continue

        filtered_articles.append(f"- {title}\n{content}\n[Read more]({url})")

    # Include synthesized answer if available
    if results.get("answer") and high_score_articles:
        filtered_articles.insert(0, f"🔹 Earnings Summary:\n{results['answer'].strip()}")

    final_result = "\n\n".join(filtered_articles) if filtered_articles else None

    return final_result

def get_latest_company_news(name: str, max_results: int = None, ticker: str = None, company_url: str = None) -> str:
    """
    Fetch latest general company news using Tavily with enhanced reliability.
    
    ✅ Domain filtering for reputable sources + company-specific IR domains
    ✅ Exact company name matching
    ✅ Prioritizes recent news (3 months)
    ✅ Fallback mechanism for broader search
    ✅ Only high relevance score (>= 80% of max) articles
    
    Args:
        name: Company name (e.g., "Amazon.com, Inc.")
        max_results: Maximum number of results to return
        ticker: Optional stock ticker for domain extraction (e.g., "AMZN")
        company_url: Optional company website URL (e.g., "amazon.com") for IR domain construction
    """
    if max_results is None:
        max_results = LLMConfig.MAX_NEWS_RESULTS

    if not name or name.lower() == "none":
        return "No ticker provided or invalid. Cannot fetch news."

    try:
        from tavily import TavilyClient
        search_client = TavilyClient()
    except ImportError:
        logger.error("Tavily client not available")
        return "News search service unavailable."

    # Compute date range: last 3 months for recent news
    end_date = datetime.today().strftime("%Y-%m-%d")
    start_date = (datetime.today() - timedelta(days=90)).strftime("%Y-%m-%d")

    # Try dynamic query generation first
    dynamic_query = None
    if ticker:  # Only attempt dynamic query if we have a ticker for caching
        dynamic_query = _get_dynamic_query(
            query_type="news",
            ticker=ticker,
            inputs={
                "name": name,
                "ticker": ticker,
                "industry": "",  # Could be enriched if passed
                "description": "",
                "recent_context": ""  # Could add recent events context
            }
        )
    
    # Static fallback queries (under 400 char limit)
    static_primary_query = f'"{name}" (acquisition OR merger OR partnership OR CEO OR lawsuit OR expansion OR analyst OR contract OR guidance)'
    fallback_query = f'"{name}" corporate news announcement'
    tier3_query = f'"{name}" business'
    
    # Use dynamic query as primary if available, otherwise use static
    primary_query = dynamic_query or static_primary_query
    logger.debug(f"[Company News] Using {'dynamic' if dynamic_query else 'static'} primary query for {name}")

    # Extract company-specific IR and corporate domains from provided URL
    company_domains = extract_company_domains(company_url, ticker) if company_url else []
    if company_domains:
        logger.debug(f"[Company News] Adding {len(company_domains)} company-specific IR/corporate domains for {name}")

    # Reputable financial news domains - GLOBAL COVERAGE
    trusted_domains = [
        # Global/US Major Financial News
        "reuters.com",
        "bloomberg.com",
        "wsj.com",
        "ft.com",              # Financial Times (UK/Global)
        "cnbc.com",
        "marketwatch.com",
        "seekingalpha.com",
        "fool.com",
        "barrons.com",
        "benzinga.com",
        "investing.com",       # Global investing platform  
        "yahoo.com",
        "yahoo.co.jp",
        
        # Press Release Wires (Global)
        "businesswire.com",
        "prnewswire.com",
        "globenewswire.com",
        
        # Official Sources
        "sec.gov",             # US SEC
        
        # Europe
        "handelsblatt.com",    # Germany
        "lesechos.fr",         # France
        "ilsole24ore.com",     # Italy
        "expansion.com",       # Spain
        "teleboerse.de",       # Germany
        "boerse.de",           # Germany
        
        # UK
        "telegraph.co.uk",
        "independent.co.uk",
        "theguardian.com",
        
        # Asia-Pacific
        "nikkei.com",          # Japan (Nikkei Asian Review)
        "japantimes.co.jp",    # Japan
        "straitstimes.com",    # Singapore
        "scmp.com",            # South China Morning Post
        "business-standard.com", # India
        "economictimes.indiatimes.com", # India
        "livemint.com",        # India
        "moneycontrol.com",    # India
        "financialexpress.com", # India
        "thehindubusinessline.com", # India
        
        # China/Hong Kong
        "caixin.com",          # China
        "chinadaily.com.cn",   # China
        "xinhuanet.com",       # China
        
        # Australia
        "afr.com",             # Australian Financial Review
        "smh.com.au",          # Sydney Morning Herald
        
        # Middle East
        "arabianbusiness.com",
        "thenational.ae",      # UAE
        
        # Latin America
        "valor.globo.com",     # Brazil
        "eleconomista.com.mx", # Mexico
        
        # Stock Exchanges (for official announcements)
        "nyse.com",
        "nasdaq.com",
        "londonstockexchange.com",
        "jpx.co.jp",           # Japan Exchange
        "hkex.com.hk",         # Hong Kong Exchange
        "bseindia.com",        # Bombay Stock Exchange
        "nseindia.com",        # National Stock Exchange India
        "asx.com.au",          # Australian Securities Exchange
        "sgx.com"              # Singapore Exchange
    ]
    
    # Append company-specific domains (IR pages, corporate sites)
    trusted_domains.extend(company_domains)
    logger.debug(f"Total trusted domains for {name}: {len(trusted_domains)}")

    def _execute_news_search(query: str, domains: list) -> Optional[Dict[str, Any]]:
        """Helper function to execute news search."""
        try:
            return search_client.search(
                query=query,
                topic="finance",
                auto_parameters=True,
                include_answer="advanced",
                max_results=max_results,
                chunks_per_source=5,
                search_depth="advanced",
                start_date=start_date,
                end_date=end_date,
                include_domains=domains
            )
        except Exception as e:
            logger.error(f"News search error: {str(e)}")
            return None

    def _has_hits(payload: Optional[Dict[str, Any]]) -> bool:
        return bool(payload and payload.get("results"))

    results = None

    # Prefer official company domains first for relevance and authority.
    if company_domains:
        logger.debug("Trying company-domain-first news search for %s", name)
        results = _execute_news_search(primary_query, company_domains)
        if not _has_hits(results):
            results = _execute_news_search(fallback_query, company_domains)
        if not _has_hits(results):
            results = _execute_news_search(tier3_query, company_domains)

    # Then broaden to curated trusted domains.
    if not _has_hits(results):
        results = _execute_news_search(primary_query, trusted_domains)
    if not _has_hits(results):
        logger.debug(f"Primary company news query returned no results for {name}, trying tier 2")
        results = _execute_news_search(fallback_query, trusted_domains)
    if not _has_hits(results):
        logger.debug(f"Tier 2 query failed for {name}, trying tier 3")
        results = _execute_news_search(tier3_query, trusted_domains)
    
    # Final fallback: remove domain restriction
    if not _has_hits(results):
        logger.debug(f"All tier queries failed, trying tier 3 without domain restriction")
        results = _execute_news_search(tier3_query, [])

    if not _has_hits(results):
        return "No relevant news articles found."

    articles = results["results"]

    # Determine max Tavily score
    max_score = max(r.get("score", 0) for r in articles)
    threshold = 0.8 * max_score

    # Filter only high-score articles
    high_score_articles = [
        r for r in articles
        if r.get("score", 0) >= threshold and r.get("content")
    ]

    if not high_score_articles:
        logger.debug(f"No news articles passed 80% relevance threshold for {name}.")
        return "No high-relevance news articles found."

    # Build the final output
    filtered_articles = []
    for r in high_score_articles[:max_results]:
        title = r.get("title", "").strip()
        url = r.get("url", "").strip()
        content = r.get("content", "").strip()

        if not content:
            continue

        filtered_articles.append(f"- {title}\n{content}\n[Read more]({url})")

    # Include synthesized answer if available
    if results.get("answer") and high_score_articles:
        filtered_articles.insert(0, f"🔹 News Summary:\n{results['answer'].strip()}")

    final_result = "\n\n".join(filtered_articles) if filtered_articles else None

    return final_result

def get_latest_macro_news(country: str, max_results: int = None) -> str:
    """
    Fetch latest macroeconomic news with enhanced reliability.
    
    ✅ Prioritizes official sources (central banks, government agencies)
    ✅ Domain filtering for authoritative economic sources
    ✅ Recent focus (6 months) for relevant economic context
    ✅ Structured query for key economic indicators
    ✅ Fallback mechanism for broader search
    ✅ Only high relevance score (>= 80% of max) articles
    """
    if max_results is None:
        max_results = LLMConfig.MAX_NEWS_RESULTS

    if not country or country.lower() == "none":
        return "No country provided or invalid. Cannot fetch news."

    try:
        from tavily import TavilyClient
        search_client = TavilyClient()
    except ImportError:
        logger.error("Tavily client not available")
        return "News search service unavailable."

    # Compute date range: last 6 months for relevant macro context
    end_date = datetime.today().strftime("%Y-%m-%d")
    start_date = (datetime.today() - timedelta(days=180)).strftime("%Y-%m-%d")

    # Try dynamic query generation first (use country as cache key)
    dynamic_query = _get_dynamic_query(
        query_type="macro",
        ticker=country,  # Use country as cache key for macro queries
        inputs={
            "country": country,
            "industry": "",  # Could be enriched if caller provides industry context
            "economic_sensitivities": ""  # Could be enriched with sector sensitivity
        }
    )
    
    # Static fallback queries (under 400 char limit)
    static_primary_query = f'"{country}" (GDP OR inflation OR CPI OR unemployment OR "interest rate" OR "central bank" OR recession OR economy)'
    fallback_query = f'"{country}" economic policy outlook'
    tier3_query = f'"{country}" economy'
    
    # Use dynamic query as primary if available, otherwise use static
    primary_query = dynamic_query or static_primary_query
    logger.debug(f"[Macro News] Using {'dynamic' if dynamic_query else 'static'} primary query for {country}")

    # Authoritative economic news sources - GLOBAL COVERAGE
    trusted_macro_domains = [
        # Global Financial News
        "reuters.com",
        "bloomberg.com",
        "wsj.com",
        "ft.com",
        "economist.com",
        "cnbc.com",
        "marketwatch.com",
        "tradingeconomics.com",
        "investing.com",
        
        # International Organizations
        "imf.org",              # International Monetary Fund
        "worldbank.org",        # World Bank
        "oecd.org",            # OECD
        "bis.org",             # Bank for International Settlements
        "wto.org",             # World Trade Organization
        "adb.org",             # Asian Development Bank
        "iadb.org",            # Inter-American Development Bank
        
        # United States
        "federalreserve.gov",  # Federal Reserve
        "bea.gov",             # Bureau of Economic Analysis
        "bls.gov",             # Bureau of Labor Statistics
        "census.gov",          # Census Bureau
        "treasury.gov",        # US Treasury
        "eia.gov",             # Energy Information Administration
        "frb.org",             # Federal Reserve Banks
        
        # Europe
        "ecb.europa.eu",       # European Central Bank
        "ec.europa.eu",        # European Commission
        "eurostat.ec.europa.eu", # Eurostat
        "bundesbank.de",       # Deutsche Bundesbank (Germany)
        "banque-france.fr",    # Bank of France
        "bancaditalia.it",     # Bank of Italy
        "bde.es",              # Bank of Spain
        "destatis.de",         # German Federal Statistical Office
        "insee.fr",            # French National Institute of Statistics
        "istat.it",            # Italian National Institute of Statistics
        "ine.es",              # Spanish Statistical Office
        
        # United Kingdom
        "bankofengland.co.uk", # Bank of England
        "ons.gov.uk",          # Office for National Statistics
        "gov.uk",              # UK Government
        
        # Asia-Pacific
        "boj.or.jp",           # Bank of Japan
        "stat.go.jp",          # Statistics Bureau of Japan
        "mof.go.jp",           # Ministry of Finance Japan
        "rbi.org.in",          # Reserve Bank of India
        "mospi.gov.in",        # Ministry of Statistics India
        "pib.gov.in",          # Press Information Bureau India
        "stats.gov.cn",        # China National Bureau of Statistics
        "pbc.gov.cn",          # People's Bank of China
        "pboc.gov.cn",         # PBOC alternate
        "hkma.gov.hk",         # Hong Kong Monetary Authority
        "mas.gov.sg",          # Monetary Authority of Singapore
        "rba.gov.au",          # Reserve Bank of Australia
        "abs.gov.au",          # Australian Bureau of Statistics
        "rbnz.govt.nz",        # Reserve Bank of New Zealand
        
        # Middle East
        "sama.gov.sa",         # Saudi Arabian Monetary Authority
        "centralbank.ae",      # Central Bank of UAE
        
        # Latin America
        "bcb.gov.br",          # Central Bank of Brazil
        "ibge.gov.br",         # Brazilian Institute of Statistics
        "banxico.org.mx",      # Bank of Mexico
        "inegi.org.mx",        # Mexican Statistics Institute
        
        # Other Major Economies
        "cbr.ru",              # Central Bank of Russia
        "gks.ru",              # Russian Federal Statistics
        "treasury.gov.za",     # South African Treasury
        "resbank.co.za",       # South African Reserve Bank
        
        # Global Statistics
        "stats.oecd.org",      # OECD Statistics
        "data.worldbank.org",  # World Bank Data
        "unstats.un.org"       # UN Statistics Division
    ]

    def _execute_macro_search(query: str, domains: list) -> Optional[Dict[str, Any]]:
        """Helper function to execute macro news search."""
        try:
            search_params = {
                "query": query,
                "topic": "finance",
                "auto_parameters": True,
                "include_answer": "advanced",
                "max_results": max_results,
                "chunks_per_source": 5,
                "search_depth": "advanced",
                "start_date": start_date,
                "end_date": end_date
            }
            if domains:
                search_params["include_domains"] = domains
            return search_client.search(**search_params)
        except Exception as e:
            logger.error(f"Macro search error: {str(e)}")
            return None

    # Try 3-tier fallback strategy
    results = _execute_macro_search(primary_query, trusted_macro_domains)
    
    if not results or "results" not in results or not results["results"]:
        logger.debug(f"Primary macro query returned no results for {country}, trying tier 2")
        results = _execute_macro_search(fallback_query, trusted_macro_domains)
    
    if not results or "results" not in results or not results["results"]:
        logger.debug(f"Tier 2 macro query failed for {country}, trying tier 3")
        results = _execute_macro_search(tier3_query, trusted_macro_domains)
    
    # Final fallback: remove domain restriction
    if not results or "results" not in results or not results["results"]:
        logger.debug(f"All tier queries failed, trying tier 3 without domain restriction")
        results = _execute_macro_search(tier3_query, [])

    if not results or "results" not in results or not results["results"]:
        return "No relevant macro news articles found."

    articles = results["results"]

    # Determine the max Tavily score
    max_score = max(r.get("score", 0) for r in articles)
    threshold = 0.8 * max_score

    # Filter only articles meeting the 80% score threshold
    high_score_articles = [r for r in articles if r.get("score", 0) >= threshold and r.get("content")]

    if not high_score_articles:
        logger.debug(f"No articles passed the 80% relevance threshold for {country}. Ignoring Tavily 'answer'.")
        return "No high-relevance macro news articles found."

    # Build the final output
    filtered_articles = []
    for r in high_score_articles[:max_results]:
        title = r.get("title", "").strip()
        url = r.get("url", "").strip()
        content = r.get("content", "").strip()

        if not content:
            continue

        filtered_articles.append(f"- {title}\n{content}\n[Read more]({url})")

    final_result = "\n\n".join(filtered_articles) if filtered_articles else None

    return final_result

def get_company_segments(name: str, max_results: int = 10) -> Union[str, Dict[str, Any]]:
    """
    Step 1: Segment / Business Unit Decomposition

    Uses Tavily to search for company segment disclosures (business segments,
    reportable segments, principal activities).

    ✅ Enhanced query targeting SEC filings and official disclosures
    ✅ Fallback query if primary search returns insufficient results
    ✅ Filters by Tavily's own relevance score (>= 80% of max score)
    ✅ Ranks results by Tavily score descending
    ✅ Returns a combined, clean text block for LLM input
    """
    if not name or name.lower() == "none":
        return "No company provided. Cannot fetch segment info."

    try:
        from tavily import TavilyClient
        search_client = TavilyClient()
    except ImportError:
        logger.error("Tavily client not available")
        return "Segment search service unavailable."

    # Compute date range: last 2 years for segment information (more stable than news)
    end_date = datetime.today().strftime("%Y-%m-%d")
    start_date = (datetime.today() - timedelta(days=730)).strftime("%Y-%m-%d")

    # Try dynamic query generation first (use company name as cache key)
    # Create a simplified cache key from company name
    cache_key = name.replace(" ", "_").replace(",", "").replace(".", "")[:20].upper()
    dynamic_query = _get_dynamic_query(
        query_type="segments",
        ticker=cache_key,  # Use company name derivative as cache key
        inputs={
            "name": name,
            "ticker": "",  # Not available in this function
            "industry": "",  # Could be enriched if passed
            "description": ""  # Could be enriched if passed
        }
    )
    
    # Static fallback queries
    # Enhanced query to capture multiple business segments/sectors
    # Target SEC filings (10-K, 10-Q), annual reports, and investor presentations
    static_primary_query = (
        f'"{name}" AND ('
        f'"business segments" OR "reportable segments" OR "operating segments" OR '
        f'"segment revenue" OR "segment information" OR "segment reporting" OR '
        f'"lines of business" OR "principal activities" OR "revenue by segment" OR '
        f'"business units" OR "operating units" OR "product segments" OR '
        f'"diversified business" OR "multiple sectors"'
        f')'
    )
    fallback_query = f'"{name}" ("10-K" OR "annual report") AND ("business description" OR "business overview" OR "company description")'
    
    # Use dynamic query as primary if available, otherwise use static
    primary_query = dynamic_query or static_primary_query
    logger.debug(f"[Segments] Using {'dynamic' if dynamic_query else 'static'} primary query for {name}")

    def _execute_search(query: str) -> Optional[Dict[str, Any]]:
        """Helper function to execute search with consistent parameters."""
        try:
            return search_client.search(
                query=query,
                topic="finance",
                include_answer="advanced",
                max_results=max_results,
                search_depth="advanced",
                start_date=start_date,
                end_date=end_date,
                include_domains=[
                    # US Official Filings
                    "sec.gov",                    # US SEC filings (10-K, 10-Q)
                    "edgar.sec.gov",              # EDGAR database
                    
                    # Annual Reports & Proxy
                    "annualreports.com",
                    "proxyvote.com",
                    
                    # Europe Regulatory
                    "londonstockexchange.com",    # UK regulatory news
                    "rns-pdf.londonstockexchange.com",  # UK RNS announcements
                    "euronext.com",              # Pan-European exchange
                    "deutsche-boerse.com",       # Germany
                    "bourse.fr",                 # France
                    "borsaitaliana.it",          # Italy
                    "bolsademadrid.es",          # Spain
                    
                    # Asia-Pacific Regulatory
                    "jpx.co.jp",                 # Japan Exchange Group
                    "tse.or.jp",                 # Tokyo Stock Exchange
                    "hkexnews.hk",               # Hong Kong Exchange News
                    "sgx.com",                   # Singapore Exchange
                    "asx.com.au",                # Australian Securities Exchange
                    "nzx.com",                   # New Zealand Exchange
                    
                    # India Regulatory
                    "bseindia.com",              # Bombay Stock Exchange
                    "nseindia.com",              # National Stock Exchange India
                    "sebi.gov.in",               # Securities and Exchange Board of India
                    
                    # China/Hong Kong
                    "sse.com.cn",                # Shanghai Stock Exchange
                    "szse.cn",                   # Shenzhen Stock Exchange
                    "hkex.com.hk",               # Hong Kong Exchange
                    
                    # Other Major Markets
                    "bmfbovespa.com.br",         # Brazil (B3)
                    "b3.com.br",                 # Brazil B3 (new domain)
                    "bmv.com.mx",                # Mexican Stock Exchange
                    "krx.co.kr",                 # Korea Exchange
                    "twse.com.tw",               # Taiwan Stock Exchange
                    "set.or.th",                 # Stock Exchange of Thailand
                    "idx.co.id",                 # Indonesia Stock Exchange
                    "bursamalaysia.com",         # Malaysia
                    
                    # --- Financial / Market Data Providers ---
                    "morningstar.com",
                    "marketscreener.com",
                    "reuters.com",
                    "bloomberg.com",
                    "ft.com",
                    "investing.com",
                    "yahoo.com",
                    "yahoo.co.jp",
                    "marketwatch.com",
                    "seekingalpha.com",
                    "fool.com",
                    "barrons.com",
                    "benzinga.com",
                    "accesswire.com",
                    "prnewswire.com",
                    "globenewswire.com",
                    "businesswire.com",
                    "globenewswire.com",
                    "prnewswire.com",
                    "accesswire.com",
                    "benzinga.com",
                    "barrons.com",
                    "fool.com",
                    "seekingalpha.com",
                    "marketwatch.com",
                    "yahoo.co.jp",
                    "yahoo.com",
                    "investing.com",
                    "ft.com",
                    "bloomberg.com",
                    "reuters.com",
                    "morningstar.com",
                    "marketscreener.com"
                ]
            )
        except Exception as e:
            logger.error(f"Search execution error: {str(e)}")
            return None

    # Try primary query first
    results = _execute_search(primary_query)
    
    if not results or "results" not in results or not results["results"]:
        logger.debug(f"Primary segment query returned no results for {name}, trying fallback query")
        results = _execute_search(fallback_query)

    if not results or "results" not in results:
        return None

    all_results: List[Dict[str, Any]] = results.get("results", [])
    if not all_results:
        return None

    # ✅ Determine the top score
    max_score = max(r.get("score", 0) for r in all_results)

    # ✅ Keep only those with score ≥ 80% of max
    threshold = 0.8 * max_score
    filtered_results = [
        r for r in all_results
        if r.get("score", 0) >= threshold and r.get("content")
    ]

    # ✅ Sort filtered results by score (descending)
    filtered_results.sort(key=lambda x: x.get("score", 0), reverse=True)

    # ✅ Build structured output
    extracted = {"company": name, "segments_raw": []}

    # Include Tavily's synthesized 'answer' if available
    if results.get("answer"):
        extracted["segments_raw"].append({
            "source": "tavily_answer",
            "snippet": results["answer"].strip(),
            "score": 1.0  # artificial max
        })

    # Add filtered Tavily results
    for r in filtered_results[:max_results]:
        extracted["segments_raw"].append({
            "source": r.get("url"),
            "snippet": r.get("content", "").strip(),
            "score": round(r.get("score", 0), 4)
        })

    if not extracted["segments_raw"]:
        return None

    # ✅ Combine top snippets into a single text block
    combined_text = "\n\n".join(
        f"[Rank {i+1} | Score={s['score']}] {s['snippet']}"
        for i, s in enumerate(extracted["segments_raw"])
    )

    extracted["combined_text"] = combined_text

    return extracted
