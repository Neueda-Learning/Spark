# Spark Portfolio Manager

Spring Boot + MySQL portfolio demo project. In production, the application reads
post-close daily Yahoo Finance market data from the database. The `demo` and `test`
profiles use deterministic simulated prices. The frontend is served directly by
Spring Boot and includes a portfolio dashboard, trading page, candlestick charts,
and an AI investment assistant.

## Requirements

- JDK 21
- Maven 3.8+
- MySQL 8.0+

## Configuration

The application reads an optional `.env` file from the project root. You can also
provide the same values through environment variables:

```properties
SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/portfoliodb?useSSL=false&allowPublicKeyRetrieval=true&createDatabaseIfNotExist=true
SPRING_DATASOURCE_USERNAME=root
SPRING_DATASOURCE_PASSWORD=your-password

AI_PROVIDER=qwen
AI_CHAT_URL=https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions
AI_MODEL=qwen-plus
AI_API_KEY=your-api-key
```

`.env` is already ignored by Git. Do not commit database passwords or AI API keys.

## Start

```bash
mvn spring-boot:run
```

Then open <http://localhost:8080>.

## Initialize Market Data

Before the first run, make sure MySQL is available and that the database connection
settings are provided through environment variables or local configuration.
The daily market data table is created automatically by `src/main/resources/schema.sql`.

Historical backfill on first startup is enabled by default. When the database is new
or history coverage is incomplete, the app fills the most recent 60 months of data.
When history is already complete, it only syncs the most recent 10-day overlap window.

To disable startup backfill temporarily:

```bash
mvn spring-boot:run \
  -Dspring-boot.run.arguments=--market-data.backfill-on-startup=false
```

Backfill isolates failures by stock and performs idempotent updates through the
unique key `(stock_id, trade_date)`. During normal operation, the application syncs
post-close daily bars at 18:30 New York time on trading weekdays, plus a reconciliation
sync at 08:00.

## Initialize Demo Portfolio

A new database is seeded with a 100,000 USD demo portfolio containing stocks, bond
ETFs, and cash. The six initial buys use Yahoo closing prices from 2025-07-01:

- Stocks: AAPL 45 shares, MSFT 35 shares, PG 80 shares, XOM 70 shares
- Bond ETFs: AGG 170 shares, BND 200 shares
- Initial invested capital: 78,580.35 USD
- Initial cash: 21,419.65 USD

Using the seeded prices as of 2026-07-29 and the first overview request after
historical dividends are processed, the initial allocation is approximately
49.3% stocks, 29.7% bonds, and 21.0% cash. Initialization also includes verified
dividend records for AAPL, AGG, and PG cross-checked against issuer pages and Yahoo
events so the app can show both paid and pending dividends. `data.sql` ships with the
latest 8 common trading days of prices for all seeded positions, which allows the app
to generate 7-day portfolio performance before Yahoo backfill finishes.

## Demo Mode

If you do not need real market data, run with simulated prices:

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=demo
```

## Weekly Candles API

```http
GET /api/stocks/{id}/candles?interval=WEEKLY&limit=52
```

Clicking a non-CASH instrument row on the trades page opens the weekly candlestick
modal. Buy and sell operations still use the latest available post-close price stored
in the database, not a real-time execution price.

## AI Investment Assistant

The assistant supports both standard and streaming responses:

```http
POST /api/portfolio/ai-assistant/chat
POST /api/portfolio/ai-assistant/chat/stream
```

By default, the assistant uses internal system market data as context. To allow it to
query Yahoo Finance directly, set:

```properties
AI_MARKET_DATA_MODE=yahoo
```

## Verification

```bash
mvn test
```
