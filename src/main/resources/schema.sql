-- Spark Portfolio Manager - unified MySQL 8 schema
-- Run this file before 02_data.sql.

-- portfolio_snapshot has no active writer and is not used by the 7-day
-- performance flow. Drop it when upgrading an existing database.
DROP TABLE IF EXISTS portfolio_snapshot;

CREATE TABLE IF NOT EXISTS stock (
                                     id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                     symbol          VARCHAR(10)    NOT NULL UNIQUE,
                                     name            VARCHAR(200)   NOT NULL,
                                     asset_type      VARCHAR(20)    NOT NULL,
                                     sector          VARCHAR(50),
                                     exchange        VARCHAR(20)    NOT NULL,
                                     currency        VARCHAR(3)     NOT NULL DEFAULT 'USD'
);

CREATE TABLE IF NOT EXISTS portfolio (
                                         id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                         name            VARCHAR(100)   NOT NULL,
                                         cash_balance    DECIMAL(18,2)  NOT NULL DEFAULT 100000.00,
                                         created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS holding (
                                       id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                       portfolio_id    BIGINT         NOT NULL,
                                       stock_id        BIGINT         NOT NULL,
                                       quantity        DECIMAL(18,4)  NOT NULL DEFAULT 0,
                                       average_cost    DECIMAL(15,4)  NOT NULL DEFAULT 0,
                                       CONSTRAINT fk_holding_portfolio
                                           FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
                                       CONSTRAINT fk_holding_stock
                                           FOREIGN KEY (stock_id) REFERENCES stock(id),
                                       CONSTRAINT uq_portfolio_stock
                                           UNIQUE (portfolio_id, stock_id)
);

CREATE TABLE IF NOT EXISTS transaction (
                                           id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                           portfolio_id    BIGINT         NOT NULL,
                                           stock_id        BIGINT         NOT NULL,
                                           type            VARCHAR(4)     NOT NULL,
                                           quantity        DECIMAL(18,4)  NOT NULL,
                                           unit_price      DECIMAL(15,4)  NOT NULL,
                                           total_amount    DECIMAL(18,2)  NOT NULL,
                                           created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                           CONSTRAINT fk_tx_portfolio
                                               FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
                                           CONSTRAINT fk_tx_stock
                                               FOREIGN KEY (stock_id) REFERENCES stock(id),
                                           CONSTRAINT chk_type
                                               CHECK (type IN ('BUY', 'SELL'))
);

CREATE TABLE IF NOT EXISTS portfolio_performance_cache (
                                                           id                 BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                                           portfolio_id       BIGINT         NOT NULL,
                                                           performance_date   DATE           NOT NULL,
                                                           invested_cost      DECIMAL(18,2)  NOT NULL,
                                                           cumulative_profit  DECIMAL(18,2)  NOT NULL,
                                                           return_rate        DECIMAL(10,4)  NOT NULL,
                                                           total_value        DECIMAL(18,2)  NOT NULL,
                                                           refreshed_at       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                           CONSTRAINT fk_perf_cache_portfolio
                                                               FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
                                                           CONSTRAINT uq_perf_cache_portfolio_date
                                                               UNIQUE (portfolio_id, performance_date)
);

CREATE TABLE IF NOT EXISTS market_price_daily (
                                                  id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                                  stock_id        BIGINT         NOT NULL,
                                                  trade_date      DATE           NOT NULL,
                                                  open_price      DECIMAL(18,6)  NOT NULL,
                                                  high_price      DECIMAL(18,6)  NOT NULL,
                                                  low_price       DECIMAL(18,6)  NOT NULL,
                                                  close_price     DECIMAL(18,6)  NOT NULL,
                                                  adjusted_close  DECIMAL(18,6),
                                                  volume          BIGINT         NOT NULL DEFAULT 0,
                                                  source          VARCHAR(20)    NOT NULL DEFAULT 'YAHOO',
                                                  fetched_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                  INDEX idx_market_price_trade_date (trade_date),
                                                  CONSTRAINT fk_market_price_stock
                                                      FOREIGN KEY (stock_id) REFERENCES stock(id),
                                                  CONSTRAINT uq_market_price_stock_date
                                                      UNIQUE (stock_id, trade_date),
                                                  CONSTRAINT chk_market_price_values CHECK (
                                                      open_price > 0
                                                          AND high_price > 0
                                                          AND low_price > 0
                                                          AND close_price > 0
                                                          AND high_price >= open_price
                                                          AND high_price >= close_price
                                                          AND low_price <= open_price
                                                          AND low_price <= close_price
                                                          AND volume >= 0
                                                      )
);

CREATE TABLE IF NOT EXISTS dividend_history (
                                                id                  BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                                symbol              VARCHAR(20)    NOT NULL,
                                                ex_date             DATE           NOT NULL,
                                                pay_date            DATE           NOT NULL,
                                                dividend_per_share  DECIMAL(10,4)  NOT NULL,
                                                frequency           VARCHAR(20)    DEFAULT 'quarterly',
                                                created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
                                                CONSTRAINT uq_dividend_symbol_ex_date
                                                    UNIQUE (symbol, ex_date)
);

CREATE TABLE IF NOT EXISTS user_dividend (
                                             id                  BIGINT         AUTO_INCREMENT PRIMARY KEY,
                                             portfolio_id        BIGINT         NOT NULL,
                                             symbol              VARCHAR(20)    NOT NULL,
                                             ex_date             DATE           NOT NULL,
                                             pay_date            DATE           NOT NULL,
                                             shares_held         INT            NOT NULL,
                                             dividend_per_share  DECIMAL(10,4)  NOT NULL,
                                             gross_amount        DECIMAL(12,2)  NOT NULL,
                                             tax_rate            DECIMAL(6,4)   NOT NULL DEFAULT 0.0000,
                                             net_amount          DECIMAL(12,2)  NOT NULL,
                                             status              VARCHAR(20)    NOT NULL DEFAULT 'pending',
                                             paid_at             TIMESTAMP,
                                             created_at          TIMESTAMP      DEFAULT CURRENT_TIMESTAMP,
                                             CONSTRAINT uq_user_dividend_portfolio_symbol_ex
                                                 UNIQUE (portfolio_id, symbol, ex_date)
);
