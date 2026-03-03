from langchain_core.tools import tool

# Your reference data
industry_mapping = [
    {"name": "Chemical (Specialty)", "sector": "specialty-chemicals", "industry": "basic-materials"},
    {"name": "Precious Metals", "sector": "gold", "industry": "basic-materials"},
    {"name": "Building Materials", "sector": "building-materials", "industry": "basic-materials"},
    {"name": "Metals & Mining", "sector": "copper", "industry": "basic-materials"},
    {"name": "Steel", "sector": "steel", "industry": "basic-materials"},
    {"name": "Farming/Agriculture", "sector": "agricultural-inputs", "industry": "basic-materials"},
    {"name": "Chemical (Diversified)", "sector": "chemicals", "industry": "basic-materials"},
    {"name": "Metals & Mining", "sector": "other-industrial-metals-mining", "industry": "basic-materials"},
    {"name": "Paper/Forest Products", "sector": "lumber-wood-production", "industry": "basic-materials"},
    {"name": "Metals & Mining", "sector": "aluminum", "industry": "basic-materials"},
    {"name": "Precious Metals", "sector": "other-precious-metals-mining", "industry": "basic-materials"},
    {"name": "Coal & Related Energy", "sector": "coking-coal", "industry": "basic-materials"},
    {"name": "Paper/Forest Products", "sector": "paper-paper-products", "industry": "basic-materials"},
    {"name": "Precious Metals", "sector": "silver", "industry": "basic-materials"},
    {"name": "Software (Internet)", "sector": "internet-content-information", "industry": "communication-services"},
    {"name": "Telecom. Services", "sector": "telecom-services", "industry": "communication-services"},
    {"name": "Entertainment", "sector": "entertainment", "industry": "communication-services"},
    {"name": "Software (Entertainment)", "sector": "electronic-gaming-multimedia", "industry": "communication-services"},
    {"name": "Advertising", "sector": "advertising-agencies", "industry": "communication-services"},
    {"name": "Broadcasting", "sector": "broadcasting", "industry": "communication-services"},
    {"name": "Publishing & Newspapers", "sector": "publishing", "industry": "communication-services"},
    {"name": "Retail (General)", "sector": "internet-retail", "industry": "consumer-cyclical"},
    {"name": "Auto & Truck", "sector": "auto-manufacturers", "industry": "consumer-cyclical"},
    {"name": "Restaurant/Dining", "sector": "restaurants", "industry": "consumer-cyclical"},
    {"name": "Retail (Building Supply)", "sector": "home-improvement-retail", "industry": "consumer-cyclical"},
    {"name": "Business & Consumer Services", "sector": "travel-services", "industry": "consumer-cyclical"},
    {"name": "Retail (Special Lines)", "sector": "specialty-retail", "industry": "consumer-cyclical"},
    {"name": "Retail (Special Lines)", "sector": "apparel-retail", "industry": "consumer-cyclical"},
    {"name": "Homebuilding", "sector": "residential-construction", "industry": "consumer-cyclical"},
    {"name": "Shoe", "sector": "footwear-accessories", "industry": "consumer-cyclical"},
    {"name": "Packaging & Container", "sector": "packaging-containers", "industry": "consumer-cyclical"},
    {"name": "Hotel/Gaming", "sector": "lodging", "industry": "consumer-cyclical"},
    {"name": "Auto Parts", "sector": "auto-parts", "industry": "consumer-cyclical"},
    {"name": "Retail (Automotive)", "sector": "auto-truck-dealerships", "industry": "consumer-cyclical"},
    {"name": "Hotel/Gaming", "sector": "gambling", "industry": "consumer-cyclical"},
    {"name": "Hotel/Gaming", "sector": "resorts-casinos", "industry": "consumer-cyclical"},
    {"name": "Recreation", "sector": "leisure", "industry": "consumer-cyclical"},
    {"name": "Apparel", "sector": "apparel-manufacturing", "industry": "consumer-cyclical"},
    {"name": "Business & Consumer Services", "sector": "personal-services", "industry": "consumer-cyclical"},
    {"name": "Furn/Home Furnishings", "sector": "furnishings-fixtures-appliances", "industry": "consumer-cyclical"},
    {"name": "Recreation", "sector": "recreational-vehicles", "industry": "consumer-cyclical"},
    {"name": "Apparel", "sector": "luxury-goods", "industry": "consumer-cyclical"},
    {"name": "Retail (General)", "sector": "department-stores", "industry": "consumer-cyclical"},
    {"name": "Apparel", "sector": "textile-manufacturing", "industry": "consumer-cyclical"},
    {"name": "Retail (General)", "sector": "discount-stores", "industry": "consumer-defensive"},
    {"name": "Beverage (Soft)", "sector": "beverages-non-alcoholic", "industry": "consumer-defensive"},
    {"name": "Household Products", "sector": "household-personal-products", "industry": "consumer-defensive"},
    {"name": "Food Processing", "sector": "packaged-foods", "industry": "consumer-defensive"},
    {"name": "Tobacco", "sector": "tobacco", "industry": "consumer-defensive"},
    {"name": "Food Processing", "sector": "confectioners", "industry": "consumer-defensive"},
    {"name": "Farming/Agriculture", "sector": "farm-products", "industry": "consumer-defensive"},
    {"name": "Food Wholesalers", "sector": "food-distribution", "industry": "consumer-defensive"},
    {"name": "Retail (Grocery and Food)", "sector": "grocery-stores", "industry": "consumer-defensive"},
    {"name": "Beverage (Alcoholic)", "sector": "beverages-brewers", "industry": "consumer-defensive"},
    {"name": "Education", "sector": "education-training-services", "industry": "consumer-defensive"},
    {"name": "Beverage (Alcoholic)", "sector": "beverages-wineries-distilleries", "industry": "consumer-defensive"},
    {"name": "Oil/Gas (Integrated)", "sector": "oil-gas-integrated", "industry": "energy"},
    {"name": "Oil/Gas Distribution", "sector": "oil-gas-midstream", "industry": "energy"},
    {"name": "Oil/Gas (Production and Exploration)", "sector": "oil-gas-e-p", "industry": "energy"},
    {"name": "Oilfield Svcs/Equip.", "sector": "oil-gas-equipment-services", "industry": "energy"},
    {"name": "Oil/Gas Distribution", "sector": "oil-gas-refining-marketing", "industry": "energy"},
    {"name": "Metals & Mining", "sector": "uranium", "industry": "energy"},
    {"name": "Oilfield Svcs/Equip.", "sector": "oil-gas-drilling", "industry": "energy"},
    {"name": "Coal & Related Energy", "sector": "thermal-coal", "industry": "energy"},
    {"name": "Bank (Money Center)", "sector": "banks-diversified", "industry": "financial-services"},
    {"name": "Financial Svcs. (Non-bank & Insurance)", "sector": "credit-services", "industry": "financial-services"},
    {"name": "Investments & Asset Management", "sector": "asset-management", "industry": "financial-services"},
    {"name": "Insurance (General)", "sector": "insurance-diversified", "industry": "financial-services"},
    {"name": "Banks (Regional)", "sector": "banks-regional", "industry": "financial-services"},
    {"name": "Brokerage & Investment Banking", "sector": "capital-markets", "industry": "financial-services"},
    {"name": "Information Services", "sector": "financial-data-stock-exchanges", "industry": "financial-services"},
    {"name": "Insurance (Prop/Cas.)", "sector": "insurance-property-casualty", "industry": "financial-services"},
    {"name": "Financial Svcs. (Non-bank & Insurance)", "sector": "insurance-brokers", "industry": "financial-services"},
    {"name": "Insurance (Life)", "sector": "insurance-life", "industry": "financial-services"},
    {"name": "Insurance (General)", "sector": "insurance-specialty", "industry": "financial-services"},
    {"name": "Financial Svcs. (Non-bank & Insurance)", "sector": "mortgage-finance", "industry": "financial-services"},
    {"name": "Reinsurance", "sector": "insurance-reinsurance", "industry": "financial-services"},
    {"name": "Missing", "sector": "shell-companies", "industry": "financial-services"},
    {"name": "Diversified", "sector": "financial-conglomerates", "industry": "financial-services"},
    {"name": "Drugs (Pharmaceutical)", "sector": "drug-manufacturers-general", "industry": "healthcare"},
    {"name": "Healthcare Support Services", "sector": "healthcare-plans", "industry": "healthcare"},
    {"name": "Drugs (Biotechnology)", "sector": "biotechnology", "industry": "healthcare"},
    {"name": "Healthcare Products", "sector": "medical-devices", "industry": "healthcare"},
    {"name": "Healthcare Information and Technology", "sector": "diagnostics-research", "industry": "healthcare"},
    {"name": "Healthcare Products", "sector": "medical-instruments-supplies", "industry": "healthcare"},
    {"name": "Hospitals/Healthcare Facilities", "sector": "medical-care-facilities", "industry": "healthcare"},
    {"name": "Drugs (Pharmaceutical)", "sector": "drug-manufacturers-specialty-generic", "industry": "healthcare"},
    {"name": "Healthcare Information and Technology", "sector": "health-information-services", "industry": "healthcare"},
    {"name": "Healthcare Support Services", "sector": "medical-distribution", "industry": "healthcare"},
    {"name": "Retail (Special Lines)", "sector": "pharmaceutical-retailers", "industry": "healthcare"},
    {"name": "Aerospace/Defense", "sector": "aerospace-defense", "industry": "industrials"},
    {"name": "Machinery", "sector": "specialty-industrial-machinery", "industry": "industrials"},
    {"name": "Transportation (Railroads)", "sector": "railroads", "industry": "industrials"},
    {"name": "Construction Supplies", "sector": "building-products-equipment", "industry": "industrials"},
    {"name": "Machinery", "sector": "farm-heavy-construction-machinery", "industry": "industrials"},
    {"name": "Business & Consumer Services", "sector": "specialty-business-services", "industry": "industrials"},
    {"name": "Transportation", "sector": "integrated-freight-logistics", "industry": "industrials"},
    {"name": "Environmental & Waste Services", "sector": "waste-management", "industry": "industrials"},
    {"name": "Diversified", "sector": "conglomerates", "industry": "industrials"},
    {"name": "Business & Consumer Services", "sector": "industrial-distribution", "industry": "industrials"},
    {"name": "Engineering/Construction", "sector": "engineering-construction", "industry": "industrials"},
    {"name": "Business & Consumer Services", "sector": "rental-leasing-services", "industry": "industrials"},
    {"name": "Business & Consumer Services", "sector": "consulting-services", "industry": "industrials"},
    {"name": "Trucking", "sector": "trucking", "industry": "industrials"},
    {"name": "Electrical Equipment", "sector": "electrical-equipment-parts", "industry": "industrials"},
    {"name": "Air Transport", "sector": "airlines", "industry": "industrials"},
    {"name": "Machinery", "sector": "tools-accessories", "industry": "industrials"},
    {"name": "Environmental & Waste Services", "sector": "pollution-treatment-controls", "industry": "industrials"},
    {"name": "Business & Consumer Services", "sector": "security-protection-services", "industry": "industrials"},
    {"name": "Shipbuilding & Marine", "sector": "marine-shipping", "industry": "industrials"},
    {"name": "Metals & Mining", "sector": "metal-fabrication", "industry": "industrials"},
    {"name": "Real Estate (Operations & Services)", "sector": "infrastructure-operations", "industry": "industrials"},
    {"name": "Business & Consumer Services", "sector": "staffing-employment-services", "industry": "industrials"},
    {"name": "Air Transport", "sector": "airports-air-services", "industry": "industrials"},
    {"name": "Office Equipment & Services", "sector": "business-equipment-supplies", "industry": "industrials"},
    {"name": "R.E.I.T.", "sector": "reit-specialty", "industry": "real-estate"},
    {"name": "R.E.I.T.", "sector": "reit-industrial", "industry": "real-estate"},
    {"name": "Retail (REITs)", "sector": "reit-retail", "industry": "real-estate"},
    {"name": "R.E.I.T.", "sector": "reit-residential", "industry": "real-estate"},
    {"name": "R.E.I.T.", "sector": "reit-healthcare-facilities", "industry": "real-estate"},
    {"name": "Real Estate (Operations & Services)", "sector": "real-estate-services", "industry": "real-estate"},
    {"name": "R.E.I.T.", "sector": "reit-office", "industry": "real-estate"},
    {"name": "R.E.I.T.", "sector": "reit-diversified", "industry": "real-estate"},
    {"name": "Real Estate (General/Diversified)", "sector": "reit-mortgage", "industry": "real-estate"},
    {"name": "R.E.I.T.", "sector": "reit-hotel-motel", "industry": "real-estate"},
    {"name": "Real Estate (Development)", "sector": "real-estate-development", "industry": "real-estate"},
    {"name": "Real Estate (General/Diversified)", "sector": "real-estate-diversified", "industry": "real-estate"},
    {"name": "Software (System & Application)", "sector": "software-infrastructure", "industry": "technology"},
    {"name": "Semiconductor", "sector": "semiconductors", "industry": "technology"},
    {"name": "Electronics (Consumer & Office)", "sector": "consumer-electronics", "industry": "technology"},
    {"name": "Software (System & Application)", "sector": "software-application", "industry": "technology"},
    {"name": "Computer Services", "sector": "information-technology-services", "industry": "technology"},
    {"name": "Semiconductor Equip", "sector": "semiconductor-equipment-materials", "industry": "technology"},
    {"name": "Telecom. Equipment", "sector": "communication-equipment", "industry": "technology"},
    {"name": "Computers/Peripherals", "sector": "computer-hardware", "industry": "technology"},
    {"name": "Electronics (General)", "sector": "electronic-components", "industry": "technology"},
    {"name": "Electronics (General)", "sector": "scientific-technical-instruments", "industry": "technology"},
    {"name": "Green & Renewable Energy", "sector": "solar", "industry": "technology"},
    {"name": "Electronics (General)", "sector": "electronics-computer-distribution", "industry": "technology"},
    {"name": "Utility (General)", "sector": "utilities-regulated-electric", "industry": "utilities"},
    {"name": "Green & Renewable Energy", "sector": "utilities-renewable", "industry": "utilities"},
    {"name": "Utility (General)", "sector": "utilities-diversified", "industry": "utilities"},
    {"name": "Utility (General)", "sector": "utilities-regulated-gas", "industry": "utilities"},
    {"name": "Power", "sector": "utilities-independent-power-producers", "industry": "utilities"},
    {"name": "Utility (Water)", "sector": "utilities-regulated-water", "industry": "utilities"},
]

INDUSTRY_CONTEXTS = {
    "technology": """
    Focus on innovation cycles, product scaling, network effects, and the sustainability of intangible reinvestment.
    Discuss scalability of platforms, switching costs, and the competitive moat in software, hardware, or semiconductors.
    Evaluate growth through user adoption, ecosystem lock-in, and the transition from growth to profitability.
    """,
    "financial-services": """
    Focus on net interest margins, risk management, credit quality, capital adequacy, and regulatory constraints.
    Discuss leverage discipline, growth in assets under management, and efficiency ratios as margin indicators.
    Evaluate capital efficiency in terms of risk-adjusted returns and cost of equity.
    """,
    "energy": """
    Focus on commodity price cycles, reserve replacement rates, cost discipline, and reinvestment needs.
    Discuss exposure to geopolitical risk, production efficiency, and capital intensity of exploration and refining.
    Evaluate risk sensitivity to energy price volatility and environmental regulation.
    """,
    "healthcare": """
    Focus on R&D pipeline productivity, patent cliffs, pricing regulation, and market access.
    Discuss margin sustainability through product mix, innovation payoff, and reimbursement risk.
    Evaluate capital efficiency based on R&D returns and successful commercialization.
    """,
    "consumer-cyclical": """
    Focus on brand strength, discretionary demand elasticity, product innovation, and cost structure.
    Discuss sensitivity to economic cycles, consumer sentiment, and input costs.
    Evaluate margin recovery and reinvestment discipline across cycles.
    """,
    "consumer-defensive": """
    Focus on stable demand, pricing power, and brand durability in low-growth, inflation-sensitive environments.
    Discuss margin resilience and capital allocation between growth, dividends, and buybacks.
    Evaluate risks from input inflation, private-label competition, or changing consumer preferences.
    """,
    "industrials": """
    Focus on capacity utilization, cyclicality, scale efficiency, and cost competitiveness.
    Discuss capital allocation in equipment, infrastructure, and logistics.
    Evaluate risks from input costs, supply chains, and macro-driven demand fluctuations.
    """,
    "utilities": """
    Focus on regulation, rate-base growth, cost pass-through, and debt leverage.
    Discuss capital intensity, allowed return on equity, and stability of cash flows.
    Evaluate risks from regulatory change, interest rate shifts, and transition to renewables.
    """,
    "real-estate": """
    Focus on occupancy, rental yields, leverage, and property development pipelines.
    Discuss exposure to cap rates, refinancing risk, and economic cycles.
    Evaluate capital efficiency through asset turnover and cash yield stability.
    """,
    "basic-materials": """
    Focus on commodity cycles, pricing power, production costs, and reinvestment discipline.
    Discuss margins as a function of global demand-supply balance and input cost structure.
    Evaluate risk from volatility, environmental regulation, and cyclical overinvestment.
    """,
    "communication-services": """
    Focus on subscriber growth, ad monetization, content economics, and ARPU trends.
    Discuss margins through content cost control and scale economics.
    Evaluate risk from platform regulation, competition, and audience fragmentation.
    """,
    "default": """
    Focus on fundamental valuation drivers: revenue growth, profitability trajectory,
    capital reinvestment discipline, and key operational or macroeconomic risks.
    """,
}

SECTOR_CONTEXT = {
    "default": "Evaluate the company generically across growth, margins, capital efficiency, and risk, using the Damodaran framework.",

    # --- Basic Materials ---
    "steel": "Steel is a cyclical commodity business. Focus on capacity utilization, input cost volatility, global infrastructure demand, and capital intensity.",
    "gold": "Gold miners' value is tied to reserve quality, extraction costs, and gold price cycles. Growth is limited; capital efficiency and risk management are crucial.",
    "copper": "Copper producers are cyclical and linked to industrial demand. Focus on production costs, global supply, and price volatility.",
    "aluminum": "Aluminum firms are commodity-exposed; consider smelting efficiency, energy costs, and global demand.",
    "coking-coal": "Coking coal value depends on steel production cycles, pricing, and extraction efficiency.",
    "thermal-coal": "Coal firms face long-term decline risk, but near-term prices hinge on energy policy and Asian demand.",
    "other-industrial-metals-mining": "Diversified industrial metal miners face cyclical demand, commodity pricing, and cost control challenges.",
    "other-precious-metals-mining": "Precious metal miners rely on reserves, extraction efficiency, and gold/silver price cycles.",
    "lumber-wood-production": "Timber and wood producers depend on housing cycles and pulp/wood prices. Reinvestment and yield management are key.",
    "paper-paper-products": "Paper product companies rely on demand from packaging and publishing. Input costs and efficiency drive value.",
    "specialty-chemicals": "Chemical producers face margin cyclicality tied to raw material costs and industrial demand. Efficiency and product mix drive value.",
    "chemicals": "Similar to specialty chemicals, focus on margins, product portfolio, and capital efficiency.",
    "agricultural-inputs": "Agriculture inputs rely on crop prices, farm incomes, and global supply chains. Watch for fertilizer demand and energy-linked input costs.",
    "building-materials": "Building materials depend on construction cycles, input costs, and regional demand elasticity. Margins vary with housing and infrastructure trends.",

    # --- Communication Services ---
    "telecom-services": "Telecoms have stable cash flows but heavy capex needs. Focus on ARPU trends, spectrum investments, and competitive pricing pressures.",
    "internet-content-information": "Internet platforms scale rapidly with network effects. Analyze user growth, monetization models, and regulatory scrutiny.",
    "advertising-agencies": "Ad agencies track marketing cycles. Margins hinge on media shifts and client retention.",
    "broadcasting": "Broadcasting depends on audience reach, ad revenue, and streaming competition.",
    "publishing": "Publishing firms face digital disruption. Focus on subscription trends, content efficiency, and margin stability.",
    "electronic-gaming-multimedia": "Gaming companies rely on IP, monetization models, and console cycles. Growth and retention drive valuation.",
    "internet-retail": "E-commerce firms scale via traffic, conversion, and logistics efficiency. Margins are thin initially, growing with scale.",

    # --- Consumer Cyclical ---
    "auto-manufacturers": "Auto manufacturers are cyclical with capital intensity. Examine EV transition, pricing discipline, and global demand trends.",
    "auto-parts": "Auto parts suppliers track OEM cycles. Margins depend on scale, integration, and commodity cost pass-through.",
    "auto-truck-dealerships": "Dealerships rely on sales volume and financing margins. Margins are cyclical with auto demand.",
    "restaurants": "Restaurants hinge on consumer sentiment, same-store sales, and input inflation. Capital efficiency varies by franchise model.",
    "specialty-retail": "Retail margins depend on brand power, store productivity, and digital adoption.",
    "home-improvement-retail": "Home improvement chains benefit from DIY trends and renovation cycles. Margins scale with volume.",
    "lodging": "Hotels and gaming are cyclical; focus on occupancy, RevPAR, and macro tourism trends.",
    "resorts-casinos": "Casinos depend on visitation, gaming trends, and discretionary spending. Capital intensity is high.",
    "leisure": "Leisure companies rely on consumer discretionary spending and seasonal patterns.",
    "apparel-manufacturing": "Fashion cycles and brand differentiation drive growth. Supply chain efficiency impacts margins.",
    "footwear-accessories": "Driven by brand strength, fashion trends, and global distribution efficiency.",
    "luxury-goods": "Luxury brands focus on exclusivity, global demand, and pricing power.",
    "department-stores": "Department stores face secular e-commerce pressure. Margins depend on cost control and traffic trends.",
    "discount-stores": "Value retailers scale with volume and supply chain efficiency.",

    # --- Consumer Defensive ---
    "packaged-foods": "Packaged food firms grow steadily but face pricing vs. volume trade-offs. Margins depend on cost control and brand strength.",
    "beverages-non-alcoholic": "Non-alcoholic beverages are brand-driven with steady demand. Scale and distribution efficiency matter.",
    "tobacco": "Tobacco has high margins and stable cash flows but faces long-term volume decline and regulation.",
    "confectioners": "Candy makers rely on brand power, seasonal sales, and efficient distribution.",
    "farm-products": "Farm product companies are commodity-linked. Production efficiency, pricing, and weather impact margins.",
    "food-distribution": "Distributors rely on logistics efficiency and scale. Margins are thin but recurring.",
    "grocery-stores": "Low-margin defensive sector driven by scale, logistics, and consumer loyalty.",
    "beverages-brewers": "Brewers benefit from brand loyalty, distribution scale, and margin control.",
    "beverages-wineries-distilleries": "Wine and spirits rely on brand strength, global demand, and capital efficiency.",
    "household-personal-products": "Consumer staples with stable demand. Efficiency, brand strength, and margins are key.",

    # --- Energy ---
    "oil-gas-e-p": "Exploration and production firms’ value is tied to reserve replacement, production efficiency, and oil price volatility.",
    "oil-gas-integrated": "Integrated oil companies balance upstream cyclicality with refining stability. Capital allocation and transition risk matter.",
    "oil-gas-midstream": "Midstream firms rely on stable fees from transportation. Leverage and throughput growth drive value.",
    "oil-gas-equipment-services": "Oilfield service firms are leveraged to upstream capex cycles; asset utilization drives margins.",
    "oil-gas-refining-marketing": "Refiners rely on spread management, throughput, and energy cost control.",
    "oil-gas-drilling": "Drilling companies are highly cyclical; focus on rig utilization, contract backlog, and capex efficiency.",
    "uranium": "Uranium miners are exposed to nuclear demand cycles and long-term commodity price trends.",
    "thermal-coal": "Coal firms face long-term decline risk, but near-term prices hinge on energy policy and Asian demand.",

    # --- Financial Services ---
    "banks-diversified": "Banks depend on credit growth, interest margins, and capital adequacy. Risk lies in credit quality and rate cycles.",
    "banks-regional": "Regional banks focus on localized loan quality and capital adequacy. Revenue growth is more predictable than large banks.",
    "credit-services": "Credit firms depend on consumer credit cycles, default trends, and funding cost management.",
    "asset-management": "Asset managers’ growth depends on AUM flows, fee compression, and performance consistency.",
    "insurance-diversified": "Insurance relies on underwriting discipline and investment yields. Capital efficiency is key.",
    "insurance-property-casualty": "P&C insurers focus on loss ratios, underwriting profitability, and investment returns.",
    "insurance-life": "Life insurers rely on mortality assumptions and investment yields; normalized ROE is key.",
    "insurance-specialty": "Specialty insurers manage niche risk; focus on underwriting margins and capital efficiency.",
    "mortgage-finance": "Mortgage firms depend on interest spreads, default rates, and securitization volume.",
    "insurance-reinsurance": "Reinsurers take on large-scale risk; focus on underwriting margins and capital efficiency.",
    "shell-companies": "Shell companies often hold cash for acquisitions; optionality and capital allocation are key.",
    "financial-conglomerates": "Conglomerates combine multiple financial services; segment-level normalized earnings matter.",
    "capital-markets": "Capital market firms depend on trading and underwriting cycles. Normalized margins and growth assumptions are key.",
    "financial-data-stock-exchanges": "Exchanges rely on recurring data and transaction fees; margin stability is key.",

    # --- Healthcare ---
    "biotechnology": "Biotech value hinges on R&D pipeline probability, regulatory approvals, and cash burn.",
    "drug-manufacturers-general": "Pharma firms depend on patent cycles, R&D productivity, and pricing regulation.",
    "medical-devices": "Device firms benefit from innovation-driven growth, scale, and regulatory compliance discipline.",
    "medical-instruments-supplies": "Instruments suppliers rely on recurring hospital demand and efficiency.",
    "diagnostics-research": "Diagnostics firms grow via adoption of new tests and regulatory approvals.",
    "medical-care-facilities": "Hospitals rely on occupancy rates, payer mix, and operational efficiency.",
    "drug-manufacturers-specialty-generic": "Specialty and generic pharma focus on patent expirations and cost control.",
    "health-information-services": "Health IT firms depend on recurring contracts and scale efficiency.",
    "medical-distribution": "Distributors manage high-volume, low-margin supply chains efficiently.",
    "pharmaceutical-retailers": "Pharmacy chains scale through location density and repeat customers.",
    "healthcare-plans": "Insurers rely on membership growth, claims management, and capital efficiency.",

    # --- Industrials ---
    "aerospace-defense": "Defense contractors have stable government contracts; margins rely on scale and execution.",
    "specialty-industrial-machinery": "Machinery firms are cyclical, with orders tied to industrial investment. Working capital management drives efficiency.",
    "railroads": "Railroads have regulated pricing and stable demand; focus on traffic growth and capital efficiency.",
    "building-products-equipment": "Construction equipment depends on industrial cycles and capital turnover.",
    "farm-heavy-construction-machinery": "Heavy machinery relies on cyclical demand and capital efficiency.",
    "specialty-business-services": "Contracted business services grow via recurring revenue and margin efficiency.",
    "integrated-freight-logistics": "Freight firms rely on volume growth, network efficiency, and capital turnover.",
    "waste-management": "Stable cash flows from municipal/industrial contracts; capex efficiency drives value.",
    "conglomerates": "Industrial conglomerates combine diverse operations; segment-level analysis is key.",
    "industrial-distribution": "Distributors rely on volume and working capital efficiency.",
    "engineering-construction": "Project-based, cyclical business where backlog, execution, and leverage are key.",
    "rental-leasing-services": "Asset-intensive rental firms depend on utilization and capex efficiency.",
    "consulting-services": "Consulting firms rely on human capital and recurring projects; margins scale with utilization.",
    "trucking": "Transport companies depend on freight rates, fuel efficiency, and logistics networks.",
    "electrical-equipment-parts": "Electrical equipment manufacturers follow industrial demand cycles.",
    "airlines": "Airlines are cyclical, fuel-sensitive, and capital-intensive. Margin and utilization management are key.",
    "tools-accessories": "Tool manufacturers rely on industrial and consumer demand cycles.",
    "pollution-treatment-controls": "Environmental services firms rely on regulatory demand and contract stability.",
    "security-protection-services": "Security companies depend on recurring contracts and operating margin efficiency.",
    "marine-shipping": "Marine transport is cyclical; focus on volume growth, rates, and fleet efficiency.",
    "metal-fabrication": "Metal fabricators depend on industrial demand and production efficiency.",
    "infrastructure-operations": "Infrastructure firms rely on long-term contracts; capex efficiency is crucial.",
    "staffing-employment-services": "Staffing firms track employment cycles; margin stability is key.",
    "airports-air-services": "Airports rely on regulated fees and traffic growth; capex efficiency drives returns.",
    "business-equipment-supplies": "Suppliers rely on scale, efficiency, and stable demand.",

    # --- Real Estate (REITs) ---
    "reit-specialty": "Specialty REITs focus on niche properties; occupancy and cap rate stability matter.",
    "reit-industrial": "Industrial REITs benefit from logistics demand and low vacancy. Cap rate spreads and rent growth drive returns.",
    "reit-retail": "Retail REITs depend on tenant stability, occupancy, and e-commerce disruption. Leverage and FFO growth drive value.",
    "reit-residential": "Residential REITs depend on occupancy, rent growth, and leverage.",
    "reit-healthcare-facilities": "Healthcare REITs rely on lease stability and occupancy.",
    "reit-office": "Office REITs focus on tenancy, lease duration, and market occupancy.",
    "reit-diversified": "Diversified REITs combine multiple property types; weighted occupancy and cap rates drive value.",
    "reit-mortgage": "Mortgage REITs rely on interest spreads, leverage, and credit risk.",
    "reit-hotel-motel": "Hotel REITs depend on occupancy, ADR, and tourism cycles.",
    "real-estate-services": "Real estate service firms rely on transaction volumes, recurring fees, and margin stability.",
    "real-estate-development": "Developers face high cyclicality; project pipeline, leverage, and market liquidity are key.",
    "real-estate-diversified": "Diversified real estate companies hold multiple property types; focus on normalized earnings and capital allocation.",

    # --- Technology ---
    "software-infrastructure": "Software infrastructure companies scale rapidly with recurring revenue. Margins depend on retention and R&D leverage.",
    "semiconductors": "Semiconductor firms are cyclical, capital-intensive, and innovation-driven. Capex discipline and product cycles are key.",
    "consumer-electronics": "Consumer electronics companies face short product cycles and brand competition. Focus on revenue growth, margin stability, and capex efficiency.",
    "software-application": "Application software firms are scalable and margin-rich; growth depends on adoption, retention, and reinvestment.",
    "information-technology-services": "IT services rely on contract renewals, wage inflation, and client diversification.",
    "semiconductor-equipment-materials": "Semiconductor equipment firms are cyclical with high capex; utilization and reinvestment efficiency are key.",
    "communication-equipment": "Networking and communication hardware face obsolescence risk; focus on margins, growth, and competitive advantage.",
    "computer-hardware": "Hardware firms face short product cycles and intense competition; margins and capex efficiency are key.",
    "electronic-components": "Component manufacturers track cyclical demand; normalized margins and capex efficiency drive value.",
    "scientific-technical-instruments": "Scientific instrument firms rely on niche demand and innovation; reinvestment and margin stability are key.",
    "solar": "Solar companies face policy risk, capex intensity, and adoption cycles. Normalize margins and growth assumptions.",
    "electronics-computer-distribution": "Distribution relies on volume, working capital efficiency, and margin stability.",

    # --- Utilities ---
    "utilities-regulated-electric": "Regulated electric utilities have stable cash flows but low growth. Returns depend on allowed ROE and rate base growth.",
    "utilities-renewable": "Renewable utilities balance growth with policy-driven incentives and capital intensity.",
    "utilities-diversified": "Diversified utilities combine regulated and unregulated operations; normalized cash flows across segments matter.",
    "utilities-regulated-gas": "Regulated gas utilities rely on stable rate base and allowed ROE; cash flows are predictable.",
    "utilities-independent-power-producers": "IPP firms face market risk for power prices and contracts; margin stability is key.",
    "utilities-regulated-water": "Water utilities rely on regulated rates and capital investment; normalize cash flows and terminal growth."
}

NARRATIVE_INDUSTRY_CONTEXTS = {
    "technology": {
        "context": """
        Growth assumptions dominate valuation. Emphasize scalability, recurring revenue, R&D productivity, and network effects.
        Cash flows often lag growth due to reinvestment. Explain why high reinvestment may still create value.
        Cost of capital reflects equity volatility, not debt risk.
        Competitive advantage comes from switching costs, platform effects, or intellectual property.
        """,
        "sensitivity_focus": "terminal growth rate and margin sustainability"
    },

    "financial-services": {
        "context": """
        Cash flows stem from interest spreads, fee income, and capital efficiency (ROE vs. cost of equity).
        Growth depends on balance sheet expansion, regulatory capital, and credit cycles.
        Risk is driven by leverage, asset quality, and liquidity buffers.
        Competitive advantage is trust, scale, and access to cheap deposits or unique deal flow.
        """,
        "sensitivity_focus": "cost of capital and credit risk"
    },

    "energy": {
        "context": """
        Cash flow estimation is cyclical, driven by commodity prices and production efficiency.
        Growth assumptions hinge on reserve replacement, production costs, and energy transition investments.
        Risk parameters include commodity volatility, geopolitical exposure, and environmental regulation.
        Competitive advantage lies in cost position, asset quality, and integration along the value chain.
        """,
        "sensitivity_focus": "oil price and reinvestment needs"
    },

    "healthcare": {
        "context": """
        Cash flow estimation must reflect R&D uncertainty, regulatory risk, and patent expiration.
        Growth comes from successful innovation, pricing power, and demographic demand.
        Risk depends on clinical trial outcomes, reimbursement policy, and pipeline concentration.
        Competitive advantage arises from brand reputation, patents, and distribution networks.
        """,
        "sensitivity_focus": "R&D success probability and pricing regulation"
    },

    "consumer-cyclical": {
        "context": """
        Cash flows are sensitive to economic cycles and discretionary spending.
        Growth depends on brand expansion, consumer sentiment, and geographic diversification.
        Margins fluctuate with input costs and demand elasticity.
        Competitive advantage comes from brand strength, distribution, and cost leadership.
        """,
        "sensitivity_focus": "revenue growth and margin volatility"
    },

    "consumer-defensive": {
        "context": """
        Cash flows are stable with moderate growth and high visibility.
        Growth assumptions rely on market share, pricing power, and product innovation.
        Risk is primarily inflation and shifts in consumer behavior.
        Competitive advantage comes from brand loyalty, scale, and pricing resilience.
        """,
        "sensitivity_focus": "pricing power and inflation impact"
    },

    "industrials": {
        "context": """
        Cash flows depend on capacity utilization and economic cycles.
        Growth relates to infrastructure spending, manufacturing demand, and efficiency gains.
        Risk comes from cost inflation, supply chain fragility, and leverage.
        Competitive advantage lies in process efficiency, scale, and switching barriers.
        """,
        "sensitivity_focus": "operating leverage and demand elasticity"
    },

    "utilities": {
        "context": """
        Cash flows are regulated and predictable, but growth is capped by allowed returns.
        Growth depends on rate-base expansion and infrastructure investment.
        Risk is mainly regulatory and interest rate exposure.
        Competitive advantage lies in geographic monopoly and regulatory relationships.
        """,
        "sensitivity_focus": "WACC and rate-base growth"
    },

    "real-estate": {
        "context": """
        Cash flows depend on occupancy rates, rental yields, and leverage structure.
        Growth is driven by property development, rent escalation, and geographic diversification.
        Risk stems from refinancing, cap rate compression, and macro cycles.
        Competitive advantage comes from location quality and management skill.
        """,
        "sensitivity_focus": "cap rate and occupancy"
    },

    "basic-materials": {
        "context": """
        Cash flows fluctuate with commodity cycles and production efficiency.
        Growth depends on capacity additions and global demand trends.
        Risk includes price volatility, cost inflation, and environmental constraints.
        Competitive advantage is cost leadership, resource access, and operational discipline.
        """,
        "sensitivity_focus": "commodity prices and cost control"
    },

    "communication-services": {
        "context": """
        Cash flows rely on ad revenue, subscriptions, and user retention.
        Growth depends on audience engagement, pricing strategy, and content investment.
        Risk is driven by competition, regulation, and platform fatigue.
        Competitive advantage comes from network effects, brand, and content exclusivity.
        """,
        "sensitivity_focus": "user growth and ARPU assumptions"
    },

    "default": {
        "context": """
        Emphasize balanced interpretation of growth, margins, reinvestment, and risk.
        Align cash flow realism with growth story. Acknowledge uncertainty and capital structure dynamics.
        """,
        "sensitivity_focus": "growth vs. cost of capital balance"
    }
}

NARRATIVE_SECTOR_CONTEXTS = {
    "default": {
        "context": "Use Damodaran’s core valuation pillars — cash flows, risk, growth, and competitive advantage — balancing all assumptions without industry bias.",
        "sensitivity_focus": "Growth rates and cost of capital."
    },

    # --- BASIC MATERIALS ---
    "specialty-chemicals": {
        "context": "Specialty chemical companies rely on niche products and stable demand. Damodaran focuses on growth, margins, and reinvestment discipline.",
        "sensitivity_focus": "Revenue growth, margins, and reinvestment efficiency."
    },
    "gold": {
        "context": "Gold miners’ valuations depend on long-term commodity prices, production costs, and reserves. Damodaran models normalized commodity prices and reinvestment efficiency.",
        "sensitivity_focus": "Commodity price and reinvestment efficiency."
    },
    "building-materials": {
        "context": "Construction material companies track infrastructure and real estate cycles. Damodaran emphasizes capacity utilization, pricing, and reinvestment discipline.",
        "sensitivity_focus": "Revenue cyclicality and reinvestment rates."
    },
    "copper": {
        "context": "Copper producers face cyclicality, cost pressures, and capex needs. Damodaran emphasizes normalized margins, reinvestment, and commodity sensitivity.",
        "sensitivity_focus": "Commodity prices and operating leverage."
    },
    "steel": {
        "context": "Steel producers operate in cyclical commodity markets where demand, capacity, and raw material costs drive margins. Damodaran normalizes earnings across cycles.",
        "sensitivity_focus": "Operating margin cycles, cost of capital, revenue sensitivity."
    },
    "agricultural-inputs": {
        "context": "Fertilizer and seed producers depend on crop cycles and global demand. Damodaran stresses operating leverage and working capital efficiency.",
        "sensitivity_focus": "Input cost volatility and working capital."
    },
    "chemicals": {
        "context": "Diversified chemical companies rely on spread management between inputs and outputs. Damodaran uses normalized margins and capital efficiency.",
        "sensitivity_focus": "Margins and reinvestment discipline."
    },
    "other-industrial-metals-mining": {
        "context": "Other metal miners are cyclical and capital-intensive. Damodaran emphasizes normalized earnings, commodity sensitivity, and reinvestment efficiency.",
        "sensitivity_focus": "Commodity price and capital efficiency."
    },
    "lumber-wood-production": {
        "context": "Forest product companies track timber prices and cyclical demand. Damodaran normalizes margins and reinvestment needs.",
        "sensitivity_focus": "Revenue cyclicality and capital efficiency."
    },
    "aluminum": {
        "context": "Aluminum producers are cyclical and capital-intensive. Damodaran focuses on normalized margins, reinvestment ratios, and commodity sensitivity.",
        "sensitivity_focus": "Margins and capex efficiency."
    },
    "other-precious-metals-mining": {
        "context": "Silver, platinum, and other precious metal miners depend on commodity pricing and cost management. Damodaran uses normalized earnings and ROIC.",
        "sensitivity_focus": "Commodity prices and capital efficiency."
    },
    "coking-coal": {
        "context": "Coking coal producers are tied to steel demand cycles. Valuation emphasizes normalized margins, capital efficiency, and commodity volatility.",
        "sensitivity_focus": "Commodity prices and operating leverage."
    },
    "paper-paper-products": {
        "context": "Paper manufacturers face cyclical demand and input costs. Damodaran emphasizes normalized margins, capital efficiency, and reinvestment discipline.",
        "sensitivity_focus": "Margins and working capital."
    },
    "silver": {
        "context": "Silver miners’ valuations depend on commodity prices, production efficiency, and capital structure. Damodaran normalizes earnings and ROIC.",
        "sensitivity_focus": "Commodity prices and capital efficiency."
    },

    # --- COMMUNICATION SERVICES ---
    "internet-content-information": {
        "context": "Internet content companies have scalable business models. Damodaran focuses on revenue growth, margins, and competitive moat.",
        "sensitivity_focus": "User growth and margin expansion."
    },
    "telecom-services": {
        "context": "Telecom operators have regulated pricing and network costs. Damodaran emphasizes normalized margins, capital intensity, and cost of capital.",
        "sensitivity_focus": "Revenue stability and capex intensity."
    },
    "entertainment": {
        "context": "Entertainment firms rely on content success and distribution. Damodaran emphasizes revenue growth, margin stability, and competitive positioning.",
        "sensitivity_focus": "Content success and operating leverage."
    },
    "electronic-gaming-multimedia": {
        "context": "Gaming and multimedia companies depend on hit products and digital distribution. Damodaran focuses on revenue growth, margins, and R&D efficiency.",
        "sensitivity_focus": "Product success and margin volatility."
    },
    "advertising-agencies": {
        "context": "Advertising firms depend on client demand and campaign performance. Damodaran focuses on revenue growth and margin predictability.",
        "sensitivity_focus": "Client concentration and operating leverage."
    },
    "broadcasting": {
        "context": "Broadcasting companies rely on audience reach and ad revenues. Damodaran models normalized margins and cash flow predictability.",
        "sensitivity_focus": "Ad revenue and margin stability."
    },
    "publishing": {
        "context": "Publishing companies face declining print revenues but digital opportunities. Damodaran focuses on revenue normalization and cost efficiency.",
        "sensitivity_focus": "Revenue shift and operating margin."
    },

    # --- CONSUMER CYCLICAL ---
    "internet-retail": {
        "context": "E-commerce firms have scalable margins and network effects. Damodaran emphasizes revenue growth, reinvestment, and margin expansion.",
        "sensitivity_focus": "Revenue growth and capital efficiency."
    },
    "auto-manufacturers": {
        "context": "Automakers are cyclical and capital-intensive. Damodaran focuses on normalized EBIT margins and reinvestment needs.",
        "sensitivity_focus": "Revenue cyclicality and reinvestment rate."
    },
    "restaurants": {
        "context": "Restaurants rely on brand power, same-store sales, and unit growth. Damodaran models steady growth fading to inflation-level.",
        "sensitivity_focus": "Operating margin and growth rate."
    },
    "home-improvement-retail": {
        "context": "Retailers of home improvement products depend on housing cycles. Damodaran emphasizes normalized margins and revenue cyclicality.",
        "sensitivity_focus": "Revenue growth and margin stability."
    },
    "travel-services": {
        "context": "Travel and booking firms are cyclical and rely on consumer confidence. Damodaran emphasizes normalized margins and reinvestment efficiency.",
        "sensitivity_focus": "Revenue cyclicality and operating leverage."
    },
    "specialty-retail": {
        "context": "Retailers depend on niche demand and cost control. Damodaran emphasizes stable margins and moderate growth.",
        "sensitivity_focus": "Revenue growth and gross margin."
    },
    "apparel-retail": {
        "context": "Apparel retail companies rely on brand and fashion cycles. Damodaran focuses on normalized margins and inventory turnover.",
        "sensitivity_focus": "Inventory management and operating margin."
    },
    "residential-construction": {
        "context": "Homebuilders are cyclical and capital-intensive. Damodaran focuses on normalized margins, capex, and housing market sensitivity.",
        "sensitivity_focus": "Revenue cyclicality and reinvestment rate."
    },
    "footwear-accessories": {
        "context": "Footwear and accessories rely on brand and consumer trends. Damodaran emphasizes revenue growth and operating margin sustainability.",
        "sensitivity_focus": "Brand strength and margin stability."
    },
    "packaging-containers": {
        "context": "Packaging companies face commodity cost pressures. Damodaran models normalized margins and capital efficiency.",
        "sensitivity_focus": "Input costs and capital turnover."
    },
    "lodging": {
        "context": "Hotels’ valuations depend on occupancy and room rates. Damodaran focuses on normalized margins through cycles.",
        "sensitivity_focus": "Occupancy and discount rate."
    },
    "auto-parts": {
        "context": "Auto parts manufacturers depend on OEM demand. Damodaran emphasizes normalized margins and cyclicality.",
        "sensitivity_focus": "Revenue cyclicality and operating leverage."
    },
    "auto-truck-dealerships": {
        "context": "Dealerships depend on new vehicle sales and margins. Damodaran models stable margins and working capital needs.",
        "sensitivity_focus": "Sales volume and margin sustainability."
    },
    "gambling": {
        "context": "Casinos and gaming firms depend on discretionary spend. Damodaran uses normalized cash flows and risk-adjusted discount rates.",
        "sensitivity_focus": "Revenue cyclicality and capex intensity."
    },
    "resorts-casinos": {
        "context": "Resort casinos rely on visitor traffic and high-margin services. Damodaran models normalized earnings and capital intensity.",
        "sensitivity_focus": "Occupancy and operating leverage."
    },
    "leisure": {
        "context": "Leisure companies depend on consumer discretionary spending. Damodaran emphasizes normalized margins and capital efficiency.",
        "sensitivity_focus": "Revenue sensitivity and operating leverage."
    },
    "apparel-manufacturing": {
        "context": "Manufacturers face fashion cycles and input costs. Damodaran models normalized margins and capital efficiency.",
        "sensitivity_focus": "Revenue cyclicality and operating margin."
    },
    "personal-services": {
        "context": "Personal service firms rely on recurring customers. Damodaran focuses on margin stability and reinvestment discipline.",
        "sensitivity_focus": "Revenue retention and operating margin."
    },
    "furnishings-fixtures-appliances": {
        "context": "Home furnishings depend on consumer discretionary trends. Damodaran emphasizes normalized margins and reinvestment.",
        "sensitivity_focus": "Revenue cyclicality and capital efficiency."
    },
    "recreational-vehicles": {
        "context": "RV makers are cyclical and discretionary-focused. Damodaran emphasizes normalized margins and cyclicality risk.",
        "sensitivity_focus": "Revenue cyclicality and reinvestment."
    },
    "luxury-goods": {
        "context": "Luxury firms rely on brand power and pricing. Damodaran focuses on margins, reinvestment, and growth sustainability.",
        "sensitivity_focus": "Brand strength and margin expansion."
    },
    "department-stores": {
        "context": "Department stores are sensitive to consumer trends and e-commerce competition. Damodaran emphasizes normalized margins and revenue stability.",
        "sensitivity_focus": "Revenue cyclicality and gross margins."
    },
    "textile-manufacturing": {
        "context": "Textile firms face input cost volatility and commodity cyclicality. Damodaran emphasizes margins and reinvestment efficiency.",
        "sensitivity_focus": "Input costs and operating margins."
    },

    # --- CONSUMER DEFENSIVE ---
    "discount-stores": {
        "context": "Discount retailers focus on high-volume, low-margin models. Damodaran models stable revenue growth and operating margins.",
        "sensitivity_focus": "Revenue growth and margin consistency."
    },
    "beverages-non-alcoholic": {
        "context": "Non-alcoholic beverage firms have stable brands. Damodaran focuses on steady margins and reinvestment discipline.",
        "sensitivity_focus": "Revenue growth and operating margin."
    },
    "household-personal-products": {
        "context": "Household products companies rely on brand loyalty. Damodaran models normalized margins and capital efficiency.",
        "sensitivity_focus": "Revenue stability and reinvestment efficiency."
    },
    "packaged-foods": {
        "context": "Packaged food companies depend on brand and cost control. Damodaran uses normalized cash flows and margin analysis.",
        "sensitivity_focus": "Margins and reinvestment rates."
    },
    "tobacco": {
        "context": "Tobacco companies have stable revenues but regulatory risks. Damodaran focuses on normalized earnings and risk-adjusted discount rates.",
        "sensitivity_focus": "Regulatory risk and margin sustainability."
    },
    "confectioners": {
        "context": "Candy makers depend on brand and consumer trends. Damodaran models steady margins and reinvestment discipline.",
        "sensitivity_focus": "Revenue cyclicality and operating margin."
    },
    "farm-products": {
        "context": "Farm product producers face commodity cycles and input costs. Damodaran emphasizes normalized margins and capital efficiency.",
        "sensitivity_focus": "Commodity price and margin stability."
    },
    "food-distribution": {
        "context": "Food distributors depend on volume and logistics efficiency. Damodaran emphasizes operating margins and capital turnover.",
        "sensitivity_focus": "Volume growth and cost efficiency."
    },
    "grocery-stores": {
        "context": "Grocery firms have stable demand with low margins. Damodaran models normalized earnings and reinvestment discipline.",
        "sensitivity_focus": "Margin consistency and capital efficiency."
    },
    "beverages-brewers": {
        "context": "Breweries rely on brand and stable consumption. Damodaran emphasizes steady margins and reinvestment efficiency.",
        "sensitivity_focus": "Revenue stability and margin control."
    },
    "education-training-services": {
        "context": "Education firms depend on enrollment and government policies. Damodaran models normalized margins and reinvestment needs.",
        "sensitivity_focus": "Enrollment stability and operating margin."
    },
    "beverages-wineries-distilleries": {
        "context": "Alcoholic beverage firms rely on brand loyalty and stable demand. Damodaran emphasizes normalized margins and cash flow predictability.",
        "sensitivity_focus": "Revenue growth and margin stability."
    },

    # --- ENERGY ---
    "oil-gas-integrated": {
        "context": "Integrated oil majors combine volatile upstream and stable downstream. Damodaran focuses on ROIC stability and capital discipline.",
        "sensitivity_focus": "Capex ratio and oil price assumption."
    },
    "oil-gas-midstream": {
        "context": "Midstream firms have fee-based revenues. Damodaran emphasizes throughput stability and leverage.",
        "sensitivity_focus": "Leverage and throughput growth."
    },
    "oil-gas-e-p": {
        "context": "Exploration and production companies’ valuations depend on reserves and commodity prices. Damodaran uses normalized margins and scenario-weighted oil prices.",
        "sensitivity_focus": "Oil price, reserve life, and capex efficiency."
    },
    "oil-gas-equipment-services": {
        "context": "Oilfield service firms are cyclical and capital-intensive. Damodaran focuses on normalized earnings and capacity utilization.",
        "sensitivity_focus": "Revenue cyclicality and operating leverage."
    },
    "oil-gas-refining-marketing": {
        "context": "Refiners have margin volatility linked to spreads. Damodaran emphasizes normalized margins and capital turnover.",
        "sensitivity_focus": "Crack spread and operating leverage."
    },
    "uranium": {
        "context": "Uranium producers are commodity-driven with long cycles. Damodaran focuses on normalized margins and capital efficiency.",
        "sensitivity_focus": "Commodity price and reinvestment efficiency."
    },
    "oil-gas-drilling": {
        "context": "Drilling companies are cyclical and heavily leveraged. Damodaran models normalized margins and risk-adjusted cost of capital.",
        "sensitivity_focus": "Revenue cyclicality and capex."
    },
    "thermal-coal": {
        "context": "Coal producers face secular decline and regulatory risk. Damodaran applies elevated discount rates and terminal decline assumptions.",
        "sensitivity_focus": "Commodity price and regulatory risk."
    },

    # --- FINANCIAL SERVICES ---
    "banks-diversified": {
        "context": "Banks are valued on normalized net interest margins and loan losses. Damodaran focuses on capital adequacy and cost of equity.",
        "sensitivity_focus": "Cost of equity and loan loss provisions."
    },
    "credit-services": {
        "context": "Credit bureaus and lenders depend on credit cycles. Damodaran emphasizes default risk and regulatory capital.",
        "sensitivity_focus": "Funding cost and default risk."
    },
    "asset-management": {
        "context": "Asset managers rely on AUM growth and fee stability. Damodaran models normalized cash flows and growth assumptions.",
        "sensitivity_focus": "AUM growth and margin stability."
    },
    "insurance-diversified": {
        "context": "Diversified insurers’ value rests on underwriting and investment returns. Damodaran models sustainable ROE and risk-adjusted discount rates.",
        "sensitivity_focus": "Loss ratios and investment yield."
    },
    "banks-regional": {
        "context": "Regional banks focus on loan portfolio quality and cost of capital. Damodaran emphasizes normalized net interest margins and loan losses.",
        "sensitivity_focus": "Loan quality and capital adequacy."
    },
    "capital-markets": {
        "context": "Capital market firms depend on trading and underwriting cycles. Damodaran models normalized margins and growth potential.",
        "sensitivity_focus": "Revenue cyclicality and operating leverage."
    },
    "financial-data-stock-exchanges": {
        "context": "Exchanges and data providers have recurring revenues. Damodaran focuses on margins, growth, and competitive moat.",
        "sensitivity_focus": "Revenue growth and operating margin."
    },
    "insurance-property-casualty": {
        "context": "P&C insurers focus on underwriting profitability and investment yield. Damodaran models normalized ROE and cost of capital.",
        "sensitivity_focus": "Loss ratios and reserve adequacy."
    },
    "insurance-brokers": {
        "context": "Insurance brokers earn fees from policy distribution. Damodaran emphasizes margin stability and reinvestment efficiency.",
        "sensitivity_focus": "Revenue retention and operating margins."
    },
    "insurance-life": {
        "context": "Life insurers rely on mortality assumptions and investment income. Damodaran models normalized earnings and discount rates.",
        "sensitivity_focus": "Mortality assumptions and investment yield."
    },
    "insurance-specialty": {
        "context": "Specialty insurers handle niche risks. Damodaran emphasizes underwriting margins and capital allocation.",
        "sensitivity_focus": "Loss ratios and capital efficiency."
    },
    "mortgage-finance": {
        "context": "Mortgage finance firms rely on loan origination and securitization. Damodaran models default risk and net interest margins.",
        "sensitivity_focus": "Default rate and cost of capital."
    },
    "insurance-reinsurance": {
        "context": "Reinsurers take on risk from primary insurers. Damodaran models normalized underwriting profit and capital efficiency.",
        "sensitivity_focus": "Underwriting margins and capital allocation."
    },
    "shell-companies": {
        "context": "Shell companies often hold cash or acquire assets. Damodaran focuses on cash value and optionality in investments.",
        "sensitivity_focus": "Cash holdings and acquisition risk."
    },
    "financial-conglomerates": {
        "context": "Conglomerates combine multiple financial operations. Damodaran models normalized earnings per segment and capital allocation.",
        "sensitivity_focus": "Segment returns and capital efficiency."
    },

    # --- HEALTHCARE ---
    "drug-manufacturers-general": {
        "context": "Pharma firms’ value comes from patents and pipeline. Damodaran normalizes R&D spending and terminal growth.",
        "sensitivity_focus": "Patent life and R&D productivity."
    },
    "healthcare-plans": {
        "context": "Health insurers rely on membership growth and claims management. Damodaran emphasizes normalized margins and discount rates.",
        "sensitivity_focus": "Membership growth and loss ratios."
    },
    "biotechnology": {
        "context": "Biotech firms depend on binary R&D outcomes. Damodaran uses probability-weighted cash flows and higher discount rates.",
        "sensitivity_focus": "R&D success probability and discount rate."
    },
    "medical-devices": {
        "context": "Device firms have recurring sales and moderate R&D risk. Damodaran emphasizes reinvestment rate and operating margin.",
        "sensitivity_focus": "Reinvestment rate and margin expansion."
    },
    "diagnostics-research": {
        "context": "Diagnostics firms rely on new tests and adoption. Damodaran models normalized margins and reinvestment efficiency.",
        "sensitivity_focus": "R&D adoption and margins."
    },
    "medical-instruments-supplies": {
        "context": "Medical instrument makers have stable demand and recurring sales. Damodaran focuses on margin sustainability and reinvestment.",
        "sensitivity_focus": "Operating margin and capital efficiency."
    },
    "medical-care-facilities": {
        "context": "Hospitals and clinics depend on occupancy and reimbursement rates. Damodaran models normalized cash flows and margins.",
        "sensitivity_focus": "Occupancy and reimbursement trends."
    },
    "drug-manufacturers-specialty-generic": {
        "context": "Specialty and generic pharma companies rely on patent expirations and cost control. Damodaran emphasizes normalized margins and cash flows.",
        "sensitivity_focus": "Patent expirations and margin stability."
    },
    "health-information-services": {
        "context": "Health IT companies rely on recurring contracts and growth. Damodaran models revenue growth, margins, and capital efficiency.",
        "sensitivity_focus": "Revenue growth and operating margin."
    },
    "medical-distribution": {
        "context": "Medical distributors handle high volumes at low margins. Damodaran emphasizes margin efficiency and working capital.",
        "sensitivity_focus": "Working capital and operating margin."
    },
    "pharmaceutical-retailers": {
        "context": "Pharmacy chains rely on brand loyalty and volume. Damodaran focuses on normalized margins and reinvestment discipline.",
        "sensitivity_focus": "Volume growth and margin stability."
    },

    # --- INDUSTRIALS ---
    "aerospace-defense": {
        "context": "Defense and aerospace companies rely on government contracts. Damodaran emphasizes normalized margins and backlog visibility.",
        "sensitivity_focus": "Contract timing and margin predictability."
    },
    "specialty-industrial-machinery": {
        "context": "Specialty machinery firms depend on order cycles. Damodaran models normalized margins and revenue cyclicality.",
        "sensitivity_focus": "Order backlog and margin stability."
    },
    "railroads": {
        "context": "Railroads have regulated pricing and stable demand. Damodaran emphasizes normalized cash flows and reinvestment efficiency.",
        "sensitivity_focus": "Traffic growth and capital turnover."
    },
    "building-products-equipment": {
        "context": "Construction equipment firms are cyclical. Damodaran models normalized margins and reinvestment rates.",
        "sensitivity_focus": "Revenue cyclicality and capital efficiency."
    },
    "farm-heavy-construction-machinery": {
        "context": "Farm and heavy equipment companies are cyclical and capital-intensive. Damodaran emphasizes margins and reinvestment discipline.",
        "sensitivity_focus": "Revenue cyclicality and capital turnover."
    },
    "specialty-business-services": {
        "context": "Business service firms rely on contracts and recurring demand. Damodaran models margin stability and reinvestment efficiency.",
        "sensitivity_focus": "Revenue retention and operating leverage."
    },
    "integrated-freight-logistics": {
        "context": "Freight companies rely on transportation volumes. Damodaran emphasizes normalized margins and capital efficiency.",
        "sensitivity_focus": "Volume growth and operating margin."
    },
    "waste-management": {
        "context": "Waste management firms have stable cash flows from contracts. Damodaran models margin stability and capital efficiency.",
        "sensitivity_focus": "Revenue stability and capex."
    },
    "conglomerates": {
        "context": "Industrial conglomerates combine diverse operations. Damodaran emphasizes segment-level normalized earnings and capital allocation.",
        "sensitivity_focus": "Segment returns and capital efficiency."
    },
    "industrial-distribution": {
        "context": "Industrial distributors rely on volume and working capital. Damodaran emphasizes operating margins and capital turnover.",
        "sensitivity_focus": "Volume growth and working capital."
    },
    "engineering-construction": {
        "context": "Construction firms are project-driven and cyclical. Damodaran focuses on contract visibility, margins, and capital efficiency.",
        "sensitivity_focus": "Project timing and margin predictability."
    },
    "rental-leasing-services": {
        "context": "Equipment rental firms are cyclical and asset-intensive. Damodaran emphasizes utilization, capex, and normalized margins.",
        "sensitivity_focus": "Asset utilization and operating margin."
    },
    "consulting-services": {
        "context": "Consulting firms rely on human capital and recurring projects. Damodaran models revenue growth, margin stability, and reinvestment.",
        "sensitivity_focus": "Utilization rate and operating margin."
    },
    "trucking": {
        "context": "Trucking companies depend on freight volumes and fuel costs. Damodaran emphasizes normalized margins and capital efficiency.",
        "sensitivity_focus": "Fuel cost and revenue cyclicality."
    },
    "electrical-equipment-parts": {
        "context": "Electrical equipment makers rely on industrial demand cycles. Damodaran models normalized margins and reinvestment efficiency.",
        "sensitivity_focus": "Revenue cyclicality and capex."
    },
    "airlines": {
        "context": "Airlines are cyclical, fuel-intensive, and leverage-sensitive. Damodaran models normalized margins and capital structure risk.",
        "sensitivity_focus": "Revenue cyclicality and fuel cost."
    },
    "tools-accessories": {
        "context": "Tool manufacturers depend on industrial and consumer demand. Damodaran models normalized margins and reinvestment efficiency.",
        "sensitivity_focus": "Revenue cyclicality and operating margin."
    },
    "pollution-treatment-controls": {
        "context": "Environmental services firms rely on regulatory demand. Damodaran emphasizes margin stability and capital efficiency.",
        "sensitivity_focus": "Revenue stability and reinvestment."
    },
    "security-protection-services": {
        "context": "Security service companies depend on contracts. Damodaran models margin stability and reinvestment discipline.",
        "sensitivity_focus": "Contract stability and operating margin."
    },
    "marine-shipping": {
        "context": "Marine shipping firms face cyclical global trade. Damodaran models normalized margins and capital turnover.",
        "sensitivity_focus": "Trade volume cyclicality and operating margin."
    },
    "metal-fabrication": {
        "context": "Metal fabricators depend on industrial demand. Damodaran models normalized margins and reinvestment efficiency.",
        "sensitivity_focus": "Revenue cyclicality and operating leverage."
    },
    "infrastructure-operations": {
        "context": "Infrastructure companies rely on long-term contracts. Damodaran emphasizes margin stability and capital efficiency.",
        "sensitivity_focus": "Contract visibility and capex."
    },
    "staffing-employment-services": {
        "context": "Staffing firms depend on employment cycles. Damodaran models revenue cyclicality and margin stability.",
        "sensitivity_focus": "Employment demand and operating margin."
    },
    "airports-air-services": {
        "context": "Airports have regulated pricing and volume-dependent cash flows. Damodaran models normalized margins and capex.",
        "sensitivity_focus": "Passenger volumes and operating margin."
    },
    "business-equipment-supplies": {
        "context": "Supplies distributors rely on volume and efficiency. Damodaran models normalized margins and capital efficiency.",
        "sensitivity_focus": "Volume growth and operating margin."
    },

    # --- REAL ESTATE (REITs) ---
    "reit-specialty": {
        "context": "Specialty REITs focus on niche properties. Damodaran models occupancy and cap rate stability.",
        "sensitivity_focus": "Occupancy and cap rate."
    },
    "reit-industrial": {
        "context": "Industrial REITs rely on rent growth and occupancy. Damodaran models cap rate-based value and leverage.",
        "sensitivity_focus": "Cap rate and rent growth."
    },
    "reit-retail": {
        "context": "Retail REITs face tenant risk and e-commerce disruption. Damodaran emphasizes occupancy and cap rate.",
        "sensitivity_focus": "Vacancy and cap rate spread."
    },
    "reit-residential": {
        "context": "Residential REITs rely on occupancy and rent growth. Damodaran models normalized cash flows and cap rate.",
        "sensitivity_focus": "Occupancy and rent growth."
    },
    "reit-healthcare-facilities": {
        "context": "Healthcare REITs depend on occupancy and stable lease contracts. Damodaran emphasizes normalized margins and cap rate.",
        "sensitivity_focus": "Occupancy and cap rate."
    },
    "reit-office": {
        "context": "Office REITs depend on tenant occupancy and lease duration. Damodaran emphasizes normalized margins and cap rate.",
        "sensitivity_focus": "Occupancy and cap rate."
    },
    "reit-diversified": {
        "context": "Diversified REITs hold multiple property types. Damodaran emphasizes weighted normalized cash flows.",
        "sensitivity_focus": "Occupancy and cap rate across properties."
    },
    "reit-mortgage": {
        "context": "Mortgage REITs rely on interest spreads and leverage. Damodaran emphasizes risk-adjusted yield and capital allocation.",
        "sensitivity_focus": "Interest spreads and leverage."
    },
    "reit-hotel-motel": {
        "context": "Hotel REITs depend on occupancy and ADR. Damodaran models normalized cash flows and discount rates.",
        "sensitivity_focus": "Occupancy and ADR variability."
    },
    "real-estate-services": {
        "context": "RE services firms rely on transaction volume and fee revenue. Damodaran emphasizes normalized margins and growth assumptions.",
        "sensitivity_focus": "Transaction volume and operating margins."
    },
    "real-estate-development": {
        "context": "Developers’ value depends on project timing, leverage, and inventory turnover. Damodaran discounts cash flows at higher project risk rates.",
        "sensitivity_focus": "Leverage and project duration."
    },
    "real-estate-diversified": {
        "context": "Diversified RE companies hold multiple property types. Damodaran focuses on normalized earnings and capital allocation.",
        "sensitivity_focus": "Segment returns and capex efficiency."
    },

    # --- TECHNOLOGY ---
    "software-infrastructure": {
        "context": "Software infrastructure companies have scalable, high-margin models. Damodaran focuses on revenue growth, margins, and reinvestment discipline.",
        "sensitivity_focus": "Revenue growth and operating margin expansion."
    },
    "semiconductors": {
        "context": "Semiconductor firms are cyclical with high capex. Damodaran models normalized margins, utilization, and reinvestment.",
        "sensitivity_focus": "Capex ratio and utilization rates."
    },
    "consumer-electronics": {
        "context": "Consumer electronics companies face short product cycles and brand competition. Damodaran emphasizes revenue growth, operating margins, and reinvestment efficiency.",
        "sensitivity_focus": "Revenue growth, margin stability, and capex efficiency."
    },
    "software-application": {
        "context": "Application software firms are scalable and margin-rich. Damodaran focuses on revenue growth, reinvestment, and sustainable margins.",
        "sensitivity_focus": "Revenue growth and operating margins."
    },
    "information-technology-services": {
        "context": "IT services firms are people-intensive with wage pressures. Damodaran emphasizes operating leverage, retention, and margins.",
        "sensitivity_focus": "Employee cost, retention, and margin expansion."
    },
    "semiconductor-equipment-materials": {
        "context": "Semiconductor equipment firms are cyclical with high capex. Damodaran models normalized margins, utilization, and reinvestment.",
        "sensitivity_focus": "Capex and utilization efficiency."
    },
    "communication-equipment": {
        "context": "Telecom and networking equipment companies rely on technology cycles and global demand. Damodaran focuses on margins, growth, and competitive advantage.",
        "sensitivity_focus": "Revenue growth and margin expansion."
    },
    "computer-hardware": {
        "context": "Hardware companies face short product cycles and competition. Damodaran emphasizes revenue growth, margin control, and reinvestment efficiency.",
        "sensitivity_focus": "Revenue growth and margin stability."
    },
    "electronic-components": {
        "context": "Component manufacturers serve cyclical industrial and consumer demand. Damodaran models normalized margins and reinvestment needs.",
        "sensitivity_focus": "Revenue cyclicality and capex."
    },
    "scientific-technical-instruments": {
        "context": "Scientific instrument companies have niche markets. Damodaran emphasizes revenue growth, margins, and reinvestment efficiency.",
        "sensitivity_focus": "Revenue growth and operating margins."
    },
    "solar": {
        "context": "Solar companies face technology adoption and policy risk. Damodaran models normalized margins and cost of capital sensitivity.",
        "sensitivity_focus": "Policy risk and capex efficiency."
    },
    "electronics-computer-distribution": {
        "context": "Electronics distributors rely on volume and working capital. Damodaran emphasizes margin stability and capital efficiency.",
        "sensitivity_focus": "Revenue growth and working capital."
    },

    # --- UTILITIES ---
    "utilities-regulated-electric": {
        "context": "Regulated electric utilities rely on allowed returns and rate base growth. Damodaran models stable cash flows and modest terminal growth.",
        "sensitivity_focus": "Regulated ROE and cost of debt."
    },
    "utilities-renewable": {
        "context": "Renewable utilities have higher capex and policy risk. Damodaran emphasizes capex efficiency and cost of capital sensitivity.",
        "sensitivity_focus": "Capex efficiency and cost of capital."
    },
    "utilities-diversified": {
        "context": "Diversified utilities combine regulated and unregulated operations. Damodaran models normalized cash flows across segments.",
        "sensitivity_focus": "Segment returns and capex."
    },
    "utilities-regulated-gas": {
        "context": "Regulated gas utilities rely on stable rate base and allowed ROE. Damodaran models stable cash flows and terminal growth.",
        "sensitivity_focus": "Rate base growth and ROE."
    },
    "utilities-independent-power-producers": {
        "context": "IPP firms are exposed to commodity and contract risk. Damodaran emphasizes normalized margins and discount rates.",
        "sensitivity_focus": "Power price and contract stability."
    },
    "utilities-regulated-water": {
        "context": "Water utilities rely on regulated rates and capital investment. Damodaran models normalized cash flows and terminal growth.",
        "sensitivity_focus": "Regulated rate growth and capex efficiency."
    }
}

SECTOR_OPTION_TYPES = {
    'internet-content-information': {
        'expansion': ['content expansion', 'platform scaling', 'user acquisition', 'international expansion'],
        'abandonment': ['content cancellation', 'platform shutdown', 'market withdrawal'],
        'timing': ['content launch timing', 'platform timing', 'seasonal timing'],
        'switching': ['content switching', 'platform switching', 'monetization switching'],
        'learning': ['audience analytics', 'content optimization', 'engagement research']
    },
    'telecom-services': {
        'expansion': ['network expansion', 'spectrum acquisition', 'service expansion', '5G rollout'],
        'abandonment': ['network shutdown', 'service discontinuation', 'market exit'],
        'timing': ['spectrum auction timing', 'technology deployment timing', 'regulatory timing'],
        'switching': ['technology switching', 'service switching', 'frequency switching'],
        'learning': ['technology development', 'network optimization', 'customer analytics']
    },
    'entertainment': {
        'expansion': ['content expansion', 'platform scaling', 'user acquisition', 'international expansion'],
        'abandonment': ['content cancellation', 'platform shutdown', 'market withdrawal'],
        'timing': ['content launch timing', 'platform timing', 'seasonal timing'],
        'switching': ['content switching', 'platform switching', 'monetization switching'],
        'learning': ['audience analytics', 'content optimization', 'engagement research']
    },
    'electronic-gaming-multimedia': {
        'expansion': ['game portfolio expansion', 'platform expansion', 'user base growth', 'international rollout'],
        'abandonment': ['game discontinuation', 'platform exit', 'studio closure'],
        'timing': ['release timing', 'platform launch timing', 'seasonal timing'],
        'switching': ['platform switching', 'genre switching', 'monetization model switching'],
        'learning': ['user behavior analysis', 'engagement optimization', 'technology development']
    },
    'advertising-agencies': {
        'expansion': ['client expansion', 'service expansion', 'geographic expansion', 'digital capabilities'],
        'abandonment': ['client termination', 'service discontinuation', 'market exit'],
        'timing': ['campaign timing', 'pitch timing', 'market entry timing'],
        'switching': ['channel switching', 'client switching', 'service switching'],
        'learning': ['campaign effectiveness', 'audience research', 'technology adoption']
    },
    'broadcasting': {
        'expansion': ['channel expansion', 'content expansion', 'market expansion', 'streaming capabilities'],
        'abandonment': ['channel shutdown', 'content cancellation', 'market exit'],
        'timing': ['programming timing', 'launch timing', 'advertising cycle timing'],
        'switching': ['format switching', 'content switching', 'distribution switching'],
        'learning': ['audience measurement', 'content optimization', 'technology development']
    },
    'publishing': {
        'expansion': ['title expansion', 'author acquisition', 'format expansion', 'international expansion'],
        'abandonment': ['title discontinuation', 'imprint closure', 'market exit'],
        'timing': ['publication timing', 'marketing timing', 'seasonal timing'],
        'switching': ['format switching', 'genre switching', 'distribution switching'],
        'learning': ['reader analytics', 'market research', 'digital transformation']
    },
    'internet-retail': {
        'expansion': ['product expansion', 'market expansion', 'platform expansion', 'fulfillment expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'platform closure'],
        'timing': ['launch timing', 'seasonal timing', 'promotional timing'],
        'switching': ['channel switching', 'supplier switching', 'fulfillment switching'],
        'learning': ['customer analytics', 'demand forecasting', 'personalization development']
    },

    # Consumer Cyclical
    'auto-manufacturers': {
        'expansion': ['model expansion', 'geographic expansion', 'manufacturing capacity', 'EV transition'],
        'abandonment': ['model discontinuation', 'plant closure', 'market exit'],
        'timing': ['launch timing', 'cycle timing', 'technology timing'],
        'switching': ['platform switching', 'powertrain switching', 'market switching'],
        'learning': ['autonomous driving', 'battery technology', 'manufacturing innovation']
    },
    'restaurants': {
        'expansion': ['unit expansion', 'concept expansion', 'geographic expansion'],
        'abandonment': ['unit closure', 'concept abandonment', 'market exit'],
        'timing': ['expansion timing', 'seasonal timing', 'location timing'],
        'switching': ['concept switching', 'format switching', 'menu switching'],
        'learning': ['customer preferences', 'operational efficiency', 'delivery innovation']
    },
    'home-improvement-retail': {
        'expansion': ['store expansion', 'format expansion', 'e-commerce expansion', 'category expansion'],
        'abandonment': ['store closure', 'format exit', 'category withdrawal'],
        'timing': ['seasonal timing', 'expansion timing', 'format timing'],
        'switching': ['format switching', 'channel switching', 'category switching'],
        'learning': ['customer analytics', 'seasonal patterns', 'digital transformation']
    },
    'travel-services': {
        'expansion': ['service expansion', 'geographic expansion', 'platform expansion', 'partner expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'platform closure'],
        'timing': ['seasonal timing', 'economic timing', 'technology timing'],
        'switching': ['service switching', 'platform switching', 'partner switching'],
        'learning': ['demand forecasting', 'customer preferences', 'technology development']
    },
    'specialty-retail': {
        'expansion': ['store expansion', 'format expansion', 'e-commerce expansion', 'brand expansion'],
        'abandonment': ['store closure', 'format exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'expansion timing', 'format timing'],
        'switching': ['format switching', 'channel switching', 'brand switching'],
        'learning': ['customer analytics', 'inventory optimization', 'digital transformation']
    },
    'apparel-retail': {
        'expansion': ['store expansion', 'brand expansion', 'e-commerce expansion', 'international expansion'],
        'abandonment': ['store closure', 'brand exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'fashion timing', 'expansion timing'],
        'switching': ['brand switching', 'channel switching', 'format switching'],
        'learning': ['fashion trends', 'customer analytics', 'supply chain optimization']
    },
    'residential-construction': {
        'expansion': ['market expansion', 'project expansion', 'product expansion', 'geographic expansion'],
        'abandonment': ['project cancellation', 'market exit', 'product discontinuation'],
        'timing': ['market timing', 'interest rate timing', 'regulatory timing'],
        'switching': ['market switching', 'product switching', 'geographic switching'],
        'learning': ['market research', 'regulatory analysis', 'construction innovation']
    },
    'footwear-accessories': {
        'expansion': ['product expansion', 'brand expansion', 'geographic expansion', 'channel expansion'],
        'abandonment': ['product discontinuation', 'brand exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'fashion timing', 'launch timing'],
        'switching': ['product switching', 'channel switching', 'brand switching'],
        'learning': ['fashion trends', 'consumer research', 'supply chain optimization']
    },
    'packaging-containers': {
        'expansion': ['capacity expansion', 'product expansion', 'market expansion', 'technology advancement'],
        'abandonment': ['facility closure', 'product exit', 'market withdrawal'],
        'timing': ['capacity timing', 'technology timing', 'market timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['sustainability research', 'material development', 'customer needs']
    },
    'lodging': {
        'expansion': ['property expansion', 'brand expansion', 'geographic expansion', 'service expansion'],
        'abandonment': ['property closure', 'brand exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'economic timing', 'development timing'],
        'switching': ['brand switching', 'segment switching', 'service switching'],
        'learning': ['guest analytics', 'revenue optimization', 'service innovation']
    },
    'auto-parts': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement', 'customer expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'customer loss'],
        'timing': ['product timing', 'technology timing', 'market timing'],
        'switching': ['product switching', 'technology switching', 'customer switching'],
        'learning': ['automotive trends', 'technology development', 'customer requirements']
    },
    'auto-truck-dealerships': {
        'expansion': ['dealership expansion', 'brand expansion', 'service expansion', 'geographic expansion'],
        'abandonment': ['dealership closure', 'brand exit', 'market withdrawal'],
        'timing': ['economic timing', 'seasonal timing', 'model timing'],
        'switching': ['brand switching', 'service switching', 'format switching'],
        'learning': ['customer analytics', 'market trends', 'service optimization']
    },
    'gambling': {
        'expansion': ['venue expansion', 'game expansion', 'geographic expansion', 'online expansion'],
        'abandonment': ['venue closure', 'game discontinuation', 'market exit'],
        'timing': ['regulatory timing', 'seasonal timing', 'economic timing'],
        'switching': ['game switching', 'venue switching', 'channel switching'],
        'learning': ['player analytics', 'game optimization', 'regulatory compliance']
    },
    'resorts-casinos': {
        'expansion': ['property expansion', 'amenity expansion', 'geographic expansion', 'entertainment expansion'],
        'abandonment': ['property closure', 'amenity reduction', 'market exit'],
        'timing': ['seasonal timing', 'economic timing', 'regulatory timing'],
        'switching': ['property switching', 'amenity switching', 'market switching'],
        'learning': ['guest analytics', 'revenue optimization', 'entertainment trends']
    },
    'leisure': {
        'expansion': ['activity expansion', 'facility expansion', 'geographic expansion', 'service expansion'],
        'abandonment': ['facility closure', 'activity discontinuation', 'market exit'],
        'timing': ['seasonal timing', 'economic timing', 'demographic timing'],
        'switching': ['activity switching', 'facility switching', 'service switching'],
        'learning': ['customer preferences', 'demographic trends', 'experience optimization']
    },
    'apparel-manufacturing': {
        'expansion': ['capacity expansion', 'product expansion', 'market expansion', 'brand expansion'],
        'abandonment': ['facility closure', 'product exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'fashion timing', 'capacity timing'],
        'switching': ['product switching', 'market switching', 'manufacturing switching'],
        'learning': ['fashion trends', 'manufacturing efficiency', 'supply chain optimization']
    },
    'personal-services': {
        'expansion': ['service expansion', 'location expansion', 'customer expansion', 'technology expansion'],
        'abandonment': ['service discontinuation', 'location closure', 'market exit'],
        'timing': ['economic timing', 'seasonal timing', 'technology timing'],
        'switching': ['service switching', 'delivery switching', 'technology switching'],
        'learning': ['customer analytics', 'service optimization', 'technology adoption']
    },
    'furnishings-fixtures-appliances': {
        'expansion': ['product expansion', 'market expansion', 'channel expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'market exit', 'channel closure'],
        'timing': ['economic timing', 'housing timing', 'technology timing'],
        'switching': ['product switching', 'channel switching', 'technology switching'],
        'learning': ['consumer trends', 'housing market analysis', 'technology development']
    },
    'recreational-vehicles': {
        'expansion': ['product expansion', 'market expansion', 'manufacturing expansion', 'dealer expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'facility closure'],
        'timing': ['economic timing', 'seasonal timing', 'demographic timing'],
        'switching': ['product switching', 'market switching', 'channel switching'],
        'learning': ['demographic trends', 'lifestyle research', 'manufacturing optimization']
    },
    'luxury-goods': {
        'expansion': ['product expansion', 'brand expansion', 'geographic expansion', 'channel expansion'],
        'abandonment': ['product discontinuation', 'brand exit', 'market withdrawal'],
        'timing': ['economic timing', 'seasonal timing', 'fashion timing'],
        'switching': ['product switching', 'brand switching', 'channel switching'],
        'learning': ['luxury trends', 'brand positioning', 'customer analytics']
    },
    'department-stores': {
        'expansion': ['store expansion', 'format expansion', 'e-commerce expansion', 'brand expansion'],
        'abandonment': ['store closure', 'format exit', 'brand withdrawal'],
        'timing': ['seasonal timing', 'economic timing', 'real estate timing'],
        'switching': ['format switching', 'channel switching', 'brand switching'],
        'learning': ['customer analytics', 'retail trends', 'digital transformation']
    },
    'textile-manufacturing': {
        'expansion': ['capacity expansion', 'product expansion', 'market expansion', 'technology advancement'],
        'abandonment': ['facility closure', 'product exit', 'market withdrawal'],
        'timing': ['capacity timing', 'fashion timing', 'technology timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['textile innovation', 'manufacturing efficiency', 'sustainability development']
    },
    'discount-stores': {
        'expansion': ['store expansion', 'format expansion', 'market expansion', 'category expansion'],
        'abandonment': ['store closure', 'format exit', 'market withdrawal'],
        'timing': ['economic timing', 'real estate timing', 'competitive timing'],
        'switching': ['format switching', 'category switching', 'supplier switching'],
        'learning': ['value positioning', 'operational efficiency', 'customer analytics']
    },

    # Consumer Staples
    'beverages-non-alcoholic': {
        'expansion': ['brand expansion', 'geographic expansion', 'product line extension', 'distribution expansion'],
        'abandonment': ['brand discontinuation', 'market exit', 'product termination'],
        'timing': ['launch timing', 'seasonal timing', 'marketing timing'],
        'switching': ['brand switching', 'channel switching', 'format switching'],
        'learning': ['consumer research', 'flavor development', 'health trends analysis']
    },
    'household-personal-products': {
        'expansion': ['product expansion', 'brand expansion', 'geographic expansion', 'category expansion'],
        'abandonment': ['product discontinuation', 'brand exit', 'market withdrawal'],
        'timing': ['launch timing', 'seasonal timing', 'regulatory timing'],
        'switching': ['product switching', 'brand switching', 'channel switching'],
        'learning': ['consumer research', 'innovation development', 'sustainability trends']
    },
    'packaged-foods': {
        'expansion': ['product expansion', 'brand expansion', 'geographic expansion', 'category expansion'],
        'abandonment': ['product discontinuation', 'brand exit', 'market withdrawal'],
        'timing': ['launch timing', 'seasonal timing', 'health trend timing'],
        'switching': ['product switching', 'brand switching', 'channel switching'],
        'learning': ['consumer preferences', 'health trends', 'supply chain optimization']
    },
    'tobacco': {
        'expansion': ['product expansion', 'geographic expansion', 'alternative products', 'brand extension'],
        'abandonment': ['product discontinuation', 'market exit', 'brand withdrawal'],
        'timing': ['regulatory timing', 'product timing', 'market timing'],
        'switching': ['product switching', 'market switching', 'alternative switching'],
        'learning': ['regulatory analysis', 'alternative products', 'harm reduction research']
    },
    'confectioners': {
        'expansion': ['product expansion', 'brand expansion', 'geographic expansion', 'category expansion'],
        'abandonment': ['product discontinuation', 'brand exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'launch timing', 'health trend timing'],
        'switching': ['product switching', 'brand switching', 'ingredient switching'],
        'learning': ['consumer preferences', 'health trends', 'ingredient innovation']
    },
    'farm-products': {
        'expansion': ['crop expansion', 'land acquisition', 'processing expansion', 'market expansion'],
        'abandonment': ['crop discontinuation', 'land divestiture', 'market exit'],
        'timing': ['seasonal timing', 'commodity timing', 'weather timing'],
        'switching': ['crop switching', 'market switching', 'processing switching'],
        'learning': ['agricultural research', 'sustainability practices', 'market intelligence']
    },
    'food-distribution': {
        'expansion': ['network expansion', 'service expansion', 'customer expansion', 'technology expansion'],
        'abandonment': ['network reduction', 'service discontinuation', 'market exit'],
        'timing': ['seasonal timing', 'economic timing', 'technology timing'],
        'switching': ['customer switching', 'service switching', 'technology switching'],
        'learning': ['supply chain optimization', 'cold chain management', 'customer analytics']
    },
    'grocery-stores': {
        'expansion': ['store expansion', 'format expansion', 'e-commerce expansion', 'service expansion'],
        'abandonment': ['store closure', 'format exit', 'market withdrawal'],
        'timing': ['economic timing', 'real estate timing', 'competitive timing'],
        'switching': ['format switching', 'service switching', 'supplier switching'],
        'learning': ['customer analytics', 'inventory optimization', 'omnichannel development']
    },
    'beverages-brewers': {
        'expansion': ['brand expansion', 'geographic expansion', 'production expansion', 'channel expansion'],
        'abandonment': ['brand discontinuation', 'market exit', 'facility closure'],
        'timing': ['seasonal timing', 'regulatory timing', 'marketing timing'],
        'switching': ['brand switching', 'channel switching', 'production switching'],
        'learning': ['consumer preferences', 'brewing innovation', 'distribution optimization']
    },
    'education-training-services': {
        'expansion': ['program expansion', 'geographic expansion', 'technology expansion', 'customer expansion'],
        'abandonment': ['program discontinuation', 'market exit', 'technology withdrawal'],
        'timing': ['academic timing', 'technology timing', 'regulatory timing'],
        'switching': ['program switching', 'delivery switching', 'technology switching'],
        'learning': ['educational research', 'learning analytics', 'technology development']
    },
    'beverages-wineries-distilleries': {
        'expansion': ['product expansion', 'brand expansion', 'geographic expansion', 'production expansion'],
        'abandonment': ['product discontinuation', 'brand exit', 'market withdrawal'],
        'timing': ['seasonal timing', 'regulatory timing', 'aging timing'],
        'switching': ['product switching', 'brand switching', 'channel switching'],
        'learning': ['consumer preferences', 'production optimization', 'market intelligence']
    },

    # Energy
    'oil-gas-integrated': {
        'expansion': ['upstream expansion', 'downstream expansion', 'international expansion', 'value chain integration'],
        'abandonment': ['asset divestiture', 'geographic exit', 'business line exit'],
        'timing': ['investment timing', 'acquisition timing', 'development timing'],
        'switching': ['portfolio rebalancing', 'geographic switching', 'business mix switching'],
        'learning': ['technology development', 'market intelligence', 'operational optimization']
    },
    'oil-gas-midstream': {
        'expansion': ['pipeline expansion', 'storage capacity', 'processing facilities', 'distribution network'],
        'abandonment': ['pipeline shutdown', 'facility closure', 'route abandonment'],
        'timing': ['construction timing', 'capacity timing', 'regulatory approval delays'],
        'switching': ['route switching', 'capacity switching', 'service switching'],
        'learning': ['demand forecasting', 'route optimization', 'technology adoption']
    },
    'oil-gas-e-p': {
        'expansion': ['drilling expansion', 'field development', 'enhanced recovery', 'acreage acquisition'],
        'abandonment': ['well abandonment', 'field shutdown', 'asset divestiture'],
        'timing': ['drilling timing', 'development delays', 'price timing', 'regulatory delays'],
        'switching': ['extraction method switching', 'field switching', 'production rate adjustment'],
        'learning': ['seismic analysis', 'pilot drilling', 'reservoir assessment', 'technology testing']
    },
    'oil-gas-equipment-services': {
        'expansion': ['service expansion', 'geographic expansion', 'technology development', 'fleet expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'equipment retirement'],
        'timing': ['service timing', 'technology timing', 'market entry timing'],
        'switching': ['service switching', 'technology switching', 'market switching'],
        'learning': ['technology development', 'operational efficiency', 'market analysis']
    },
    'oil-gas-refining-marketing': {
        'expansion': ['refinery capacity', 'upgrading units', 'product diversification', 'retail expansion'],
        'abandonment': ['refinery closure', 'unit shutdown', 'market exit'],
        'timing': ['maintenance timing', 'upgrade timing', 'product launch timing'],
        'switching': ['crude switching', 'product mix switching', 'process switching'],
        'learning': ['process optimization', 'catalyst development', 'efficiency improvements']
    },
    'uranium': {
        'expansion': ['mine development', 'processing capacity', 'reserve acquisition'],
        'abandonment': ['mine closure', 'asset disposal', 'operations shutdown'],
        'timing': ['commodity timing', 'development delays', 'regulatory timing'],
        'switching': ['grade switching', 'market switching', 'extraction switching'],
        'learning': ['resource assessment', 'technology development', 'market analysis']
    },
    'oil-gas-drilling': {
        'expansion': ['rig expansion', 'technology advancement', 'geographic expansion', 'service diversification'],
        'abandonment': ['rig stacking', 'service exit', 'geographic withdrawal'],
        'timing': ['drilling timing', 'technology timing', 'market cycle timing'],
        'switching': ['rig switching', 'technology switching', 'service switching'],
        'learning': ['drilling efficiency', 'technology development', 'operational optimization']
    },
    'thermal-coal': {
        'expansion': ['mine development', 'processing capacity', 'transportation infrastructure'],
        'abandonment': ['mine closure', 'asset retirement', 'operations shutdown'],
        'timing': ['commodity timing', 'development delays', 'environmental permits'],
        'switching': ['seam switching', 'market switching', 'quality optimization'],
        'learning': ['resource assessment', 'extraction efficiency', 'environmental compliance']
    },

    # Financial Services
    'banks-diversified': {
        'expansion': ['branch expansion', 'product expansion', 'geographic expansion', 'digital platform expansion'],
        'abandonment': ['branch closure', 'product discontinuation', 'market exit'],
        'timing': ['credit cycle timing', 'expansion timing', 'regulatory timing'],
        'switching': ['portfolio switching', 'risk switching', 'business model switching'],
        'learning': ['credit risk modeling', 'digital transformation', 'customer analytics']
    },
    'credit-services': {
        'expansion': ['product expansion', 'market expansion', 'technology expansion', 'customer segment expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'segment withdrawal'],
        'timing': ['credit cycle timing', 'product timing', 'regulatory timing'],
        'switching': ['product switching', 'segment switching', 'technology switching'],
        'learning': ['credit modeling', 'risk assessment', 'customer analytics']
    },
    'asset-management': {
        'expansion': ['product expansion', 'AUM growth', 'geographic expansion'],
        'abandonment': ['fund closure', 'strategy abandonment', 'market exit'],
        'timing': ['launch timing', 'market timing', 'strategy timing'],
        'switching': ['strategy switching', 'asset class switching', 'style switching'],
        'learning': ['investment research', 'factor modeling', 'market analysis']
    },
    'insurance-diversified': {
        'expansion': ['product expansion', 'geographic expansion', 'distribution expansion'],
        'abandonment': ['product exit', 'market withdrawal', 'line discontinuation'],
        'timing': ['underwriting cycle timing', 'expansion timing', 'regulatory timing'],
        'switching': ['risk switching', 'product mix switching', 'channel switching'],
        'learning': ['actuarial modeling', 'risk assessment', 'catastrophe modeling']
    },
    'banks-regional': {
        'expansion': ['branch expansion', 'product expansion', 'geographic expansion', 'digital expansion'],
        'abandonment': ['branch closure', 'product discontinuation', 'market exit'],
        'timing': ['credit cycle timing', 'expansion timing', 'regulatory timing'],
        'switching': ['portfolio switching', 'risk switching', 'delivery switching'],
        'learning': ['local market intelligence', 'credit risk modeling', 'customer analytics']
    },
    'capital-markets': {
        'expansion': ['service expansion', 'market expansion', 'product expansion', 'technology expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'product withdrawal'],
        'timing': ['market timing', 'regulatory timing', 'technology timing'],
        'switching': ['service switching', 'market switching', 'technology switching'],
        'learning': ['market research', 'technology development', 'regulatory analysis']
    },
    'financial-data-stock-exchanges': {
        'expansion': ['data expansion', 'service expansion', 'technology expansion', 'market expansion'],
        'abandonment': ['service discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['product timing', 'technology timing', 'regulatory timing'],
        'switching': ['technology switching', 'service switching', 'data switching'],
        'learning': ['data analytics', 'technology development', 'market intelligence']
    },
    'insurance-property-casualty': {
        'expansion': ['product expansion', 'geographic expansion', 'distribution expansion'],
        'abandonment': ['product exit', 'market withdrawal', 'line discontinuation'],
        'timing': ['underwriting cycle timing', 'expansion timing', 'regulatory timing'],
        'switching': ['risk switching', 'product mix switching', 'channel switching'],
        'learning': ['actuarial modeling', 'catastrophe modeling', 'risk assessment']
    },
    'insurance-brokers': {
        'expansion': ['client expansion', 'service expansion', 'geographic expansion', 'specialty expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'specialty withdrawal'],
        'timing': ['market timing', 'service timing', 'regulatory timing'],
        'switching': ['service switching', 'market switching', 'specialty switching'],
        'learning': ['risk assessment', 'market intelligence', 'client analytics']
    },
    'insurance-life': {
        'expansion': ['product expansion', 'geographic expansion', 'distribution expansion'],
        'abandonment': ['product exit', 'market withdrawal', 'distribution discontinuation'],
        'timing': ['product timing', 'market timing', 'regulatory timing'],
        'switching': ['product switching', 'investment switching', 'distribution switching'],
        'learning': ['actuarial modeling', 'longevity research', 'investment analysis']
    },
    'insurance-specialty': {
        'expansion': ['specialty expansion', 'geographic expansion', 'product expansion'],
        'abandonment': ['specialty exit', 'market withdrawal', 'product discontinuation'],
        'timing': ['market timing', 'regulatory timing', 'specialty timing'],
        'switching': ['specialty switching', 'market switching', 'product switching'],
        'learning': ['specialized risk modeling', 'market research', 'regulatory analysis']
    },
    'mortgage-finance': {
        'expansion': ['product expansion', 'geographic expansion', 'channel expansion', 'technology expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'channel closure'],
        'timing': ['interest rate timing', 'regulatory timing', 'market timing'],
        'switching': ['product switching', 'channel switching', 'technology switching'],
        'learning': ['credit modeling', 'interest rate analysis', 'regulatory compliance']
    },
    'insurance-reinsurance': {
        'expansion': ['risk expansion', 'geographic expansion', 'product expansion'],
        'abandonment': ['risk exit', 'market withdrawal', 'product discontinuation'],
        'timing': ['catastrophe timing', 'market timing', 'regulatory timing'],
        'switching': ['risk switching', 'geographic switching', 'product switching'],
        'learning': ['catastrophe modeling', 'risk assessment', 'market intelligence']
    },
    'shell-companies': {
        'expansion': ['acquisition expansion', 'investment expansion', 'geographic expansion'],
        'abandonment': ['investment exit', 'asset disposal', 'market withdrawal'],
        'timing': ['acquisition timing', 'investment timing', 'exit timing'],
        'switching': ['investment switching', 'strategy switching', 'asset switching'],
        'learning': ['market research', 'due diligence', 'valuation analysis']
    },
    'financial-conglomerates': {
        'expansion': ['business expansion', 'geographic expansion', 'service expansion'],
        'abandonment': ['business exit', 'market withdrawal', 'service discontinuation'],
        'timing': ['investment timing', 'regulatory timing', 'market timing'],
        'switching': ['business switching', 'geographic switching', 'service switching'],
        'learning': ['portfolio optimization', 'market research', 'regulatory analysis']
    },

    # Healthcare
    'drug-manufacturers-general': {
        'expansion': ['indication expansion', 'geographic rollout', 'manufacturing scale-up', 'line extension'],
        'abandonment': ['trial termination', 'indication discontinuation', 'program shutdown'],
        'timing': ['trial timing', 'regulatory submission timing', 'launch timing', 'patent timing'],
        'switching': ['indication switching', 'formulation switching', 'delivery method switching'],
        'learning': ['clinical trials', 'research pipeline', 'drug discovery', 'biomarker development']
    },
    'healthcare-plans': {
        'expansion': ['market expansion', 'product expansion', 'membership growth'],
        'abandonment': ['market exit', 'product discontinuation', 'plan termination'],
        'timing': ['enrollment timing', 'product launch timing', 'regulatory timing'],
        'switching': ['plan switching', 'network switching', 'benefit switching'],
        'learning': ['actuarial analysis', 'risk assessment', 'utilization patterns']
    },
    'biotechnology': {
        'expansion': ['indication expansion', 'geographic rollout', 'manufacturing scale-up', 'line extension'],
        'abandonment': ['trial termination', 'indication discontinuation', 'program shutdown'],
        'timing': ['trial timing', 'regulatory submission timing', 'launch timing', 'patent timing'],
        'switching': ['indication switching', 'formulation switching', 'delivery method switching'],
        'learning': ['clinical trials', 'research pipeline', 'drug discovery', 'biomarker development']
    },
    'medical-devices': {
        'expansion': ['product line expansion', 'geographic expansion', 'indication expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'technology abandonment'],
        'timing': ['regulatory approval timing', 'product launch timing', 'technology timing'],
        'switching': ['platform switching', 'indication switching', 'technology switching'],
        'learning': ['clinical studies', 'technology development', 'regulatory pathway optimization']
    },
    'diagnostics-research': {
        'expansion': ['test expansion', 'platform expansion', 'geographic expansion', 'technology advancement'],
        'abandonment': ['test discontinuation', 'platform exit', 'market withdrawal'],
        'timing': ['regulatory timing', 'technology timing', 'market entry timing'],
        'switching': ['platform switching', 'technology switching', 'application switching'],
        'learning': ['clinical validation', 'technology development', 'biomarker research']
    },
    'medical-instruments-supplies': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'market exit', 'technology exit'],
        'timing': ['product launch timing', 'regulatory timing', 'technology timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['technology development', 'clinical research', 'market analysis']
    },
    'medical-care-facilities': {
        'expansion': ['facility expansion', 'service expansion', 'geographic expansion'],
        'abandonment': ['facility closure', 'service discontinuation', 'market exit'],
        'timing': ['expansion timing', 'regulatory timing', 'reimbursement timing'],
        'switching': ['service switching', 'technology switching', 'delivery switching'],
        'learning': ['outcome research', 'technology assessment', 'market research']
    },
    'drug-manufacturers-specialty-generic': {
        'expansion': ['product expansion', 'geographic expansion', 'manufacturing expansion', 'pipeline development'],
        'abandonment': ['product discontinuation', 'manufacturing exit', 'market withdrawal'],
        'timing': ['launch timing', 'regulatory timing', 'generic entry timing'],
        'switching': ['product switching', 'manufacturing switching', 'market switching'],
        'learning': ['regulatory intelligence', 'manufacturing optimization', 'market research']
    },
    'health-information-services': {
        'expansion': ['service expansion', 'technology expansion', 'client expansion', 'data expansion'],
        'abandonment': ['service discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['service launch timing', 'technology timing', 'regulatory timing'],
        'switching': ['platform switching', 'service switching', 'technology switching'],
        'learning': ['data analytics', 'technology development', 'regulatory compliance']
    },
    'medical-distribution': {
        'expansion': ['distribution expansion', 'service expansion', 'geographic expansion', 'product expansion'],
        'abandonment': ['service discontinuation', 'geographic exit', 'product exit'],
        'timing': ['expansion timing', 'technology timing', 'regulatory timing'],
        'switching': ['service switching', 'technology switching', 'supplier switching'],
        'learning': ['supply chain optimization', 'technology adoption', 'market analysis']
    },
    'pharmaceutical-retailers': {
        'expansion': ['store expansion', 'service expansion', 'geographic expansion', 'digital expansion'],
        'abandonment': ['store closure', 'service discontinuation', 'market exit'],
        'timing': ['expansion timing', 'service timing', 'digital timing'],
        'switching': ['format switching', 'service switching', 'technology switching'],
        'learning': ['customer analytics', 'service optimization', 'technology development']
    },

    # Industrials
    'aerospace-defense': {
        'expansion': ['program expansion', 'international expansion', 'technology development', 'capacity expansion'],
        'abandonment': ['program termination', 'contract exit', 'technology abandonment'],
        'timing': ['contract timing', 'development timing', 'regulatory approval timing'],
        'switching': ['platform switching', 'technology switching', 'market switching'],
        'learning': ['R&D investment', 'technology development', 'certification processes']
    },
    'specialty-industrial-machinery': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement', 'service expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'technology abandonment'],
        'timing': ['product timing', 'market timing', 'technology timing'],
        'switching': ['technology switching', 'market switching', 'application switching'],
        'learning': ['technology development', 'application research', 'market intelligence']
    },
    'railroads': {
        'expansion': ['network expansion', 'capacity expansion', 'service expansion', 'technology advancement'],
        'abandonment': ['route abandonment', 'service discontinuation', 'asset disposal'],
        'timing': ['capacity timing', 'infrastructure timing', 'regulatory timing'],
        'switching': ['route switching', 'service switching', 'technology switching'],
        'learning': ['traffic optimization', 'infrastructure planning', 'technology adoption']
    },
    'building-products-equipment': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement', 'capacity expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'facility closure'],
        'timing': ['construction timing', 'economic timing', 'technology timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['construction trends', 'material innovation', 'sustainability development']
    },
    'farm-heavy-construction-machinery': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement', 'service expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'service withdrawal'],
        'timing': ['economic timing', 'agricultural timing', 'technology timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['agricultural trends', 'automation development', 'sustainability innovation']
    },
    'specialty-business-services': {
        'expansion': ['service expansion', 'market expansion', 'technology expansion', 'customer expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'customer termination'],
        'timing': ['economic timing', 'technology timing', 'market timing'],
        'switching': ['service switching', 'technology switching', 'market switching'],
        'learning': ['industry expertise', 'technology development', 'customer analytics']
    },
    'integrated-freight-logistics': {
        'expansion': ['network expansion', 'service expansion', 'technology expansion', 'capacity expansion'],
        'abandonment': ['route termination', 'service discontinuation', 'facility closure'],
        'timing': ['economic timing', 'seasonal timing', 'technology timing'],
        'switching': ['mode switching', 'route switching', 'service switching'],
        'learning': ['logistics optimization', 'technology adoption', 'demand forecasting']
    },
    'waste-management': {
        'expansion': ['service expansion', 'geographic expansion', 'technology advancement', 'facility expansion'],
        'abandonment': ['service discontinuation', 'facility closure', 'market exit'],
        'timing': ['regulatory timing', 'technology timing', 'economic timing'],
        'switching': ['technology switching', 'service switching', 'processing switching'],
        'learning': ['environmental technology', 'recycling innovation', 'regulatory compliance']
    },
    'conglomerates': {
        'expansion': ['business expansion', 'geographic expansion', 'acquisition expansion', 'diversification'],
        'abandonment': ['business divestiture', 'market exit', 'portfolio pruning'],
        'timing': ['acquisition timing', 'divestiture timing', 'investment timing'],
        'switching': ['portfolio switching', 'geographic switching', 'business switching'],
        'learning': ['portfolio optimization', 'market intelligence', 'synergy development']
    },
    'industrial-distribution': {
        'expansion': ['product expansion', 'market expansion', 'channel expansion', 'service expansion'],
        'abandonment': ['product exit', 'market withdrawal', 'channel closure'],
        'timing': ['inventory timing', 'market timing', 'technology timing'],
        'switching': ['supplier switching', 'channel switching', 'service switching'],
        'learning': ['supply chain optimization', 'customer analytics', 'market intelligence']
    },
    'engineering-construction': {
        'expansion': ['project expansion', 'market expansion', 'service expansion', 'geographic expansion'],
        'abandonment': ['project termination', 'market exit', 'service discontinuation'],
        'timing': ['project timing', 'economic timing', 'regulatory timing'],
        'switching': ['project switching', 'service switching', 'market switching'],
        'learning': ['engineering innovation', 'project management', 'regulatory compliance']
    },
    'rental-leasing-services': {
        'expansion': ['fleet expansion', 'service expansion', 'market expansion', 'equipment expansion'],
        'abandonment': ['fleet reduction', 'service discontinuation', 'market exit'],
        'timing': ['economic timing', 'seasonal timing', 'replacement timing'],
        'switching': ['equipment switching', 'service switching', 'market switching'],
        'learning': ['utilization optimization', 'fleet management', 'customer analytics']
    },
    'consulting-services': {
        'expansion': ['service expansion', 'market expansion', 'expertise expansion', 'geographic expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'expertise withdrawal'],
        'timing': ['economic timing', 'technology timing', 'market timing'],
        'switching': ['service switching', 'market switching', 'expertise switching'],
        'learning': ['industry expertise', 'methodology development', 'technology adoption']
    },
    'trucking': {
        'expansion': ['fleet expansion', 'route expansion', 'service expansion', 'geographic expansion'],
        'abandonment': ['route termination', 'service discontinuation', 'market exit'],
        'timing': ['economic timing', 'seasonal timing', 'fuel timing'],
        'switching': ['route switching', 'service switching', 'equipment switching'],
        'learning': ['route optimization', 'fuel efficiency', 'technology adoption']
    },
    'electrical-equipment-parts': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement', 'capacity expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'technology abandonment'],
        'timing': ['economic timing', 'technology timing', 'infrastructure timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['electrical innovation', 'grid technology', 'energy efficiency']
    },
    'airlines': {
        'expansion': ['route expansion', 'fleet expansion', 'hub development', 'service expansion'],
        'abandonment': ['route termination', 'hub closure', 'fleet retirement'],
        'timing': ['capacity timing', 'route timing', 'fleet timing'],
        'switching': ['aircraft switching', 'route switching', 'alliance switching'],
        'learning': ['demand forecasting', 'route optimization', 'fuel efficiency']
    },
    'tools-accessories': {
        'expansion': ['product expansion', 'market expansion', 'technology advancement', 'brand expansion'],
        'abandonment': ['product discontinuation', 'market exit', 'brand withdrawal'],
        'timing': ['product timing', 'seasonal timing', 'technology timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['user research', 'technology development', 'market intelligence']
    },
    'pollution-treatment-controls': {
        'expansion': ['technology expansion', 'market expansion', 'service expansion', 'regulatory expansion'],
        'abandonment': ['technology exit', 'market withdrawal', 'service discontinuation'],
        'timing': ['regulatory timing', 'technology timing', 'environmental timing'],
        'switching': ['technology switching', 'application switching', 'market switching'],
        'learning': ['environmental technology', 'regulatory compliance', 'pollution research']
    },
    'security-protection-services': {
        'expansion': ['service expansion', 'market expansion', 'technology expansion', 'geographic expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'technology withdrawal'],
        'timing': ['threat timing', 'technology timing', 'regulatory timing'],
        'switching': ['service switching', 'technology switching', 'market switching'],
        'learning': ['threat intelligence', 'security technology', 'risk assessment']
    },
    'marine-shipping': {
        'expansion': ['fleet expansion', 'route expansion', 'service expansion', 'port expansion'],
        'abandonment': ['route termination', 'fleet reduction', 'service discontinuation'],
        'timing': ['economic timing', 'seasonal timing', 'trade timing'],
        'switching': ['route switching', 'vessel switching', 'service switching'],
        'learning': ['trade patterns', 'route optimization', 'fuel efficiency']
    },
    'metal-fabrication': {
        'expansion': ['capacity expansion', 'product expansion', 'market expansion', 'technology advancement'],
        'abandonment': ['facility closure', 'product exit', 'market withdrawal'],
        'timing': ['economic timing', 'capacity timing', 'technology timing'],
        'switching': ['product switching', 'technology switching', 'market switching'],
        'learning': ['fabrication innovation', 'material science', 'customer requirements']
    },
    'infrastructure-operations': {
        'expansion': ['asset expansion', 'service expansion', 'geographic expansion', 'technology advancement'],
        'abandonment': ['asset disposal', 'service discontinuation', 'market exit'],
        'timing': ['economic timing', 'regulatory timing', 'technology timing'],
        'switching': ['asset switching', 'service switching', 'technology switching'],
        'learning': ['infrastructure planning', 'operational efficiency', 'technology adoption']
    },
    'staffing-employment-services': {
        'expansion': ['service expansion', 'market expansion', 'technology expansion', 'specialization expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'specialization withdrawal'],
        'timing': ['economic timing', 'seasonal timing', 'technology timing'],
        'switching': ['service switching', 'market switching', 'technology switching'],
        'learning': ['labor market analysis', 'skills assessment', 'technology adoption']
    },
    'airports-air-services': {
        'expansion': ['service expansion', 'capacity expansion', 'route expansion', 'terminal expansion'],
        'abandonment': ['service discontinuation', 'route termination', 'facility closure'],
        'timing': ['capacity timing', 'economic timing', 'regulatory timing'],
        'switching': ['service switching', 'route switching', 'technology switching'],
        'learning': ['traffic optimization', 'passenger analytics', 'operational efficiency']
    },
    'business-equipment-supplies': {
        'expansion': ['product expansion', 'market expansion', 'service expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'market exit', 'service withdrawal'],
        'timing': ['economic timing', 'technology timing', 'replacement timing'],
        'switching': ['product switching', 'technology switching', 'service switching'],
        'learning': ['workplace trends', 'technology development', 'customer analytics']
    },

    # Real Estate
    'reit-specialty': {
        'expansion': ['property acquisition', 'portfolio expansion', 'development projects', 'geographic expansion'],
        'abandonment': ['asset disposition', 'portfolio pruning', 'market exit'],
        'timing': ['acquisition timing', 'development timing', 'market cycle timing'],
        'switching': ['property type switching', 'geographic switching', 'tenant switching'],
        'learning': ['market research', 'tenant analysis', 'development feasibility']
    },
    'reit-industrial': {
        'expansion': ['warehouse acquisition', 'development projects', 'market expansion', 'tenant diversification'],
        'abandonment': ['asset disposition', 'development cancellation', 'market exit'],
        'timing': ['acquisition timing', 'development timing', 'lease timing'],
        'switching': ['property switching', 'market switching', 'tenant switching'],
        'learning': ['logistics trends', 'e-commerce impact', 'tenant requirements']
    },
    'reit-retail': {
        'expansion': ['retail property acquisition', 'redevelopment projects', 'format diversification'],
        'abandonment': ['asset disposition', 'redevelopment abandonment', 'format exit'],
        'timing': ['acquisition timing', 'redevelopment timing', 'retail cycle timing'],
        'switching': ['format switching', 'tenant switching', 'location switching'],
        'learning': ['retail trends', 'consumer behavior', 'omnichannel impact']
    },
    'reit-residential': {
        'expansion': ['apartment acquisition', 'development projects', 'market expansion'],
        'abandonment': ['asset disposition', 'development cancellation', 'market exit'],
        'timing': ['acquisition timing', 'development timing', 'rental cycle timing'],
        'switching': ['property switching', 'market switching', 'demographic switching'],
        'learning': ['demographic trends', 'rental demand', 'development feasibility']
    },
    'reit-healthcare-facilities': {
        'expansion': ['healthcare property acquisition', 'development projects', 'specialty expansion'],
        'abandonment': ['asset disposition', 'development cancellation', 'specialty exit'],
        'timing': ['acquisition timing', 'development timing', 'healthcare timing'],
        'switching': ['property switching', 'specialty switching', 'operator switching'],
        'learning': ['healthcare trends', 'demographic analysis', 'operator requirements']
    },
    'real-estate-services': {
        'expansion': ['service expansion', 'market expansion', 'technology expansion', 'client expansion'],
        'abandonment': ['service discontinuation', 'market exit', 'client termination'],
        'timing': ['market timing', 'technology timing', 'economic timing'],
        'switching': ['service switching', 'market switching', 'technology switching'],
        'learning': ['market intelligence', 'technology development', 'client analytics']
    },
    'reit-office': {
        'expansion': ['office property acquisition', 'development projects', 'market expansion'],
        'abandonment': ['asset disposition', 'development cancellation', 'market exit'],
        'timing': ['acquisition timing', 'development timing', 'lease cycle timing'],
        'switching': ['property switching', 'market switching', 'tenant switching'],
        'learning': ['workplace trends', 'tenant requirements', 'remote work impact']
    },
    'reit-diversified': {
        'expansion': ['multi-sector acquisition', 'development diversification', 'geographic expansion'],
        'abandonment': ['selective disposition', 'sector exit', 'geographic withdrawal'],
        'timing': ['sector timing', 'acquisition timing', 'development timing'],
        'switching': ['sector switching', 'geographic switching', 'property switching'],
        'learning': ['sector analysis', 'portfolio optimization', 'market intelligence']
    },
    'reit-mortgage': {
        'expansion': ['portfolio expansion', 'credit expansion', 'product diversification'],
        'abandonment': ['portfolio reduction', 'credit tightening', 'product exit'],
        'timing': ['interest rate timing', 'credit timing', 'market timing'],
        'switching': ['product switching', 'credit switching', 'geographic switching'],
        'learning': ['credit analysis', 'interest rate modeling', 'real estate valuation']
    },
    'reit-hotel-motel': {
        'expansion': ['hotel acquisition', 'development projects', 'brand diversification'],
        'abandonment': ['asset disposition', 'development cancellation', 'brand exit'],
        'timing': ['acquisition timing', 'development timing', 'travel cycle timing'],
        'switching': ['brand switching', 'market switching', 'segment switching'],
        'learning': ['travel trends', 'hospitality analytics', 'revenue optimization']
    },
    'real-estate-development': {
        'expansion': ['project expansion', 'market expansion', 'product diversification', 'land banking'],
        'abandonment': ['project cancellation', 'market exit', 'land disposal'],
        'timing': ['market timing', 'construction timing', 'financing timing'],
        'switching': ['project switching', 'market switching', 'product switching'],
        'learning': ['market research', 'feasibility analysis', 'construction innovation']
    },
    'real-estate-diversified': {
        'expansion': ['multi-sector expansion', 'geographic diversification', 'service expansion'],
        'abandonment': ['sector exit', 'geographic withdrawal', 'service discontinuation'],
        'timing': ['sector timing', 'market timing', 'investment timing'],
        'switching': ['sector switching', 'geographic switching', 'service switching'],
        'learning': ['sector analysis', 'market intelligence', 'portfolio optimization']
    },

    # Technology
    'software-infrastructure': {
        'expansion': ['platform expansion', 'user base scaling', 'feature expansion', 'geographic rollout'],
        'abandonment': ['product discontinuation', 'platform shutdown', 'market exit'],
        'timing': ['product launch timing', 'market entry timing', 'technology timing'],
        'switching': ['platform switching', 'technology stack switching', 'business model switching'],
        'learning': ['AI research', 'algorithm development', 'user behavior analysis', 'technology R&D']
    },
    'semiconductors': {
        'expansion': ['fab capacity', 'node advancement', 'product portfolio expansion'],
        'abandonment': ['fab closure', 'technology abandonment', 'product line exit'],
        'timing': ['capacity timing', 'technology transition timing', 'cycle timing'],
        'switching': ['process switching', 'node switching', 'application switching'],
        'learning': ['process development', 'yield optimization', 'technology research']
    },
    'consumer-electronics': {
        'expansion': ['product development', 'market expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['launch timing', 'technology timing', 'market timing'],
        'switching': ['technology switching', 'product switching', 'market switching'],
        'learning': ['R&D investment', 'technology development', 'market research']
    },
    'software-application': {
        'expansion': ['application expansion', 'user base growth', 'feature development', 'market expansion'],
        'abandonment': ['application shutdown', 'feature removal', 'market exit'],
        'timing': ['release timing', 'feature timing', 'market entry timing'],
        'switching': ['platform switching', 'business model switching', 'technology switching'],
        'learning': ['user analytics', 'feature optimization', 'technology development']
    },
    'information-technology-services': {
        'expansion': ['service expansion', 'client expansion', 'geographic expansion', 'capability development'],
        'abandonment': ['service discontinuation', 'market exit', 'capability retirement'],
        'timing': ['service launch timing', 'technology timing', 'market entry timing'],
        'switching': ['service switching', 'technology switching', 'delivery switching'],
        'learning': ['technology development', 'service optimization', 'client analytics']
    },
    'semiconductor-equipment-materials': {
        'expansion': ['equipment development', 'market expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['product timing', 'technology timing', 'market timing'],
        'switching': ['technology switching', 'product switching', 'market switching'],
        'learning': ['technology development', 'process optimization', 'market research']
    },
    'communication-equipment': {
        'expansion': ['product development', 'market expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['launch timing', 'technology timing', 'market timing'],
        'switching': ['technology switching', 'product switching', 'market switching'],
        'learning': ['R&D investment', 'technology development', 'market research']
    },
    'computer-hardware': {
        'expansion': ['product development', 'market expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['launch timing', 'technology timing', 'market timing'],
        'switching': ['technology switching', 'product switching', 'market switching'],
        'learning': ['R&D investment', 'technology development', 'market research']
    },
    'electronic-components': {
        'expansion': ['product development', 'market expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'technology exit', 'market withdrawal'],
        'timing': ['launch timing', 'technology timing', 'market timing'],
        'switching': ['technology switching', 'product switching', 'market switching'],
        'learning': ['R&D investment', 'technology development', 'market research']
    },
    'scientific-technical-instruments': {
        'expansion': ['instrument development', 'market expansion', 'application expansion', 'technology advancement'],
        'abandonment': ['product discontinuation', 'market exit', 'application withdrawal'],
        'timing': ['product timing', 'technology timing', 'market entry timing'],
        'switching': ['technology switching', 'application switching', 'market switching'],
        'learning': ['R&D development', 'application research', 'technology optimization']
    },
    'solar': {
        'expansion': ['capacity expansion', 'technology advancement', 'geographic expansion'],
        'abandonment': ['facility closure', 'technology abandonment', 'market exit'],
        'timing': ['deployment timing', 'technology timing', 'policy timing'],
        'switching': ['technology switching', 'market switching', 'application switching'],
        'learning': ['efficiency research', 'material development', 'cost optimization']
    },
    'electronics-computer-distribution': {
        'expansion': ['distribution expansion', 'product expansion', 'market expansion', 'channel expansion'],
        'abandonment': ['channel closure', 'product exit', 'market withdrawal'],
        'timing': ['expansion timing', 'product timing', 'technology timing'],
        'switching': ['channel switching', 'supplier switching', 'product switching'],
        'learning': ['supply chain optimization', 'market analysis', 'technology trends']
    },

    # Utilities
    'utilities-regulated-electric': {
        'expansion': ['generation capacity', 'transmission expansion', 'distribution expansion', 'renewable integration'],
        'abandonment': ['plant retirement', 'infrastructure decommission', 'service termination'],
        'timing': ['capacity timing', 'infrastructure timing', 'regulatory timing'],
        'switching': ['fuel switching', 'technology switching', 'supplier switching'],
        'learning': ['demand forecasting', 'grid modernization', 'renewable integration']
    },
    'utilities-renewable': {
        'expansion': ['renewable capacity', 'technology diversification', 'grid expansion', 'storage development'],
        'abandonment': ['project cancellation', 'technology exit', 'asset retirement'],
        'timing': ['project timing', 'regulatory timing', 'technology timing'],
        'switching': ['technology switching', 'fuel switching', 'market switching'],
        'learning': ['technology development', 'grid integration', 'storage solutions']
    },
    'utilities-diversified': {
        'expansion': ['multi-utility expansion', 'service diversification', 'geographic expansion'],
        'abandonment': ['service exit', 'geographic withdrawal', 'asset disposal'],
        'timing': ['regulatory timing', 'investment timing', 'market timing'],
        'switching': ['service switching', 'fuel switching', 'technology switching'],
        'learning': ['regulatory analysis', 'portfolio optimization', 'technology assessment']
    },
    'utilities-regulated-gas': {
        'expansion': ['pipeline expansion', 'distribution expansion', 'storage expansion', 'renewable gas'],
        'abandonment': ['pipeline retirement', 'service termination', 'infrastructure abandonment'],
        'timing': ['infrastructure timing', 'regulatory timing', 'demand timing'],
        'switching': ['fuel switching', 'supplier switching', 'technology switching'],
        'learning': ['demand forecasting', 'infrastructure planning', 'renewable gas development']
    },
    'utilities-independent-power-producers': {
        'expansion': ['generation expansion', 'technology diversification', 'market expansion', 'contract expansion'],
        'abandonment': ['plant retirement', 'technology exit', 'market withdrawal'],
        'timing': ['capacity timing', 'market timing', 'contract timing'],
        'switching': ['fuel switching', 'technology switching', 'market switching'],
        'learning': ['market analysis', 'technology development', 'contract optimization']
    },
    'utilities-regulated-water': {
        'expansion': ['system expansion', 'treatment expansion', 'service expansion', 'technology advancement'],
        'abandonment': ['system retirement', 'service termination', 'infrastructure abandonment'],
        'timing': ['infrastructure timing', 'regulatory timing', 'demand timing'],
        'switching': ['source switching', 'treatment switching', 'technology switching'],
        'learning': ['demand forecasting', 'water management', 'treatment innovation']
    },

    # Default
    'default': {
        'expansion': ['capacity expansion', 'market expansion', 'product expansion'],
        'abandonment': ['asset disposal', 'market exit', 'operation shutdown'],
        'timing': ['investment timing', 'market timing', 'regulatory timing'],
        'switching': ['technology switching', 'market switching', 'product switching'],
        'learning': ['market research', 'technology development', 'operational optimization']
    }

}

# Define a LangChain tool that can be called
@tool
def lookup_industry(query: str) -> dict:
    """
    Look up sector and industry information by providing a query.
    The query can match the 'name' or 'sector' field.
    Returns a dictionary with name, sector, and industry.
    """
    q = query.lower().strip()
    for entry in industry_mapping:
        if q in entry["name"].lower() or q in entry["sector"].lower():
            return entry
    return {"error": f"No match found for '{query}'"}