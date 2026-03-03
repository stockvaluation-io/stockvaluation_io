# DCF: Stock Valuation Project

## Overview

DCF (Discounted Cash Flow) is a robust stock valuation project designed to analyze the intrinsic value of companies
based on their financial data and projections. It provides APIs to process financial metrics, calculate DCF, and
generate detailed valuation insights. The project integrates a **Java 17 Spring Boot 3** backend with **PostgreSQL 15**
for data storage, and incorporates **Python 3** for advanced calculations and data analysis.

---

## Features

- **Stock Valuation**: Calculate company valuation using the DCF model.
- **Data Management**: Store and retrieve detailed financial data in PostgreSQL.
- **API Integration**: Provide endpoints to access financial data, metrics, and insights.
- **Python Integration**: Perform advanced analytics and custom calculations.
- **Modular Architecture**: Designed with separate services for streamlined maintainability.

---

## Technology Stack

### Backend

- **Java**: Version 17 (Primary backend logic).
- **Spring Boot**: Version 3 (REST API and application framework).

### Database

- **PostgreSQL**: Version 15 (Database for storing company financial data).

### Additional Services

- **Python**: Version 3 (Used for computationally intensive tasks).

---

## Prerequisites

1. **Java 17** installed.
2. **PostgreSQL 15** database configured and running.
3. **Python 3** with necessary libraries installed (`pandas`, `numpy`,`yfinance`, etc.).
4. **Maven** for building the Java backend.
5. **springboot 3.0**

---

## Setup Instructions

### Clone the Repository

```bash  
 git clone https://github.com/vaibh85/stockvaluation_io_backend
cd dcf-stock-valuation 
```

## Backend Configuration (Spring Boot - Java)

### 1 - Database Setup

- Create a PostgreSQL database (stockvaluation_io).
- Update the database connection details in application.properties.

```databaseconfig
spring.datasource.url=jdbc:postgresql://localhost:5432/stockvaluation_io  
spring.datasource.username=your_username  
spring.datasource.password=your_password 
```

### 2 - Build and Run:

```mvn clean install  
java -jar target/dcf-backend.jar
```

### 3 - Python Service

```
pip install -r requirements.txt  
python3 yfinance/app.py  

```

## Endpoints Overview
