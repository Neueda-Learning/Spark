-- Portfolio Manager Database Schema

CREATE TABLE IF NOT EXISTS stock (
    id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
    symbol          VARCHAR(10)    NOT NULL UNIQUE,
    name            VARCHAR(200)   NOT NULL,
    asset_type      VARCHAR(20)    NOT NULL,
    sector          VARCHAR(50),
    exchange        VARCHAR(20)    NOT NULL,
    currency        VARCHAR(3)     NOT NULL DEFAULT 'USD',
    dividend_yield  DECIMAL(6,4)   NOT NULL DEFAULT 0.0000,
    dividend_date   DATE
);

CREATE TABLE IF NOT EXISTS portfolio (
    id           BIGINT         AUTO_INCREMENT PRIMARY KEY,
    name         VARCHAR(100)   NOT NULL,
    cash_balance DECIMAL(18,2)  NOT NULL DEFAULT 100000.00,
    created_at   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS holding (
    id              BIGINT         AUTO_INCREMENT PRIMARY KEY,
    portfolio_id    BIGINT         NOT NULL,
    stock_id        BIGINT         NOT NULL,
    quantity        DECIMAL(18,4)  NOT NULL DEFAULT 0,
    average_cost    DECIMAL(15,4)  NOT NULL DEFAULT 0,
    CONSTRAINT fk_holding_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
    CONSTRAINT fk_holding_stock     FOREIGN KEY (stock_id) REFERENCES stock(id),
    CONSTRAINT uq_portfolio_stock   UNIQUE (portfolio_id, stock_id)
);

CREATE TABLE IF NOT EXISTS transaction (
    id            BIGINT         AUTO_INCREMENT PRIMARY KEY,
    portfolio_id  BIGINT         NOT NULL,
    stock_id      BIGINT         NOT NULL,
    type          VARCHAR(4)     NOT NULL,
    quantity      DECIMAL(18,4)  NOT NULL,
    unit_price    DECIMAL(15,4)  NOT NULL,
    total_amount  DECIMAL(18,2)  NOT NULL,
    created_at    TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_tx_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
    CONSTRAINT fk_tx_stock     FOREIGN KEY (stock_id) REFERENCES stock(id),
    CONSTRAINT chk_type CHECK (type IN ('BUY','SELL'))
);

CREATE TABLE IF NOT EXISTS portfolio_snapshot (
    id             BIGINT         AUTO_INCREMENT PRIMARY KEY,
    portfolio_id   BIGINT         NOT NULL,
    snapshot_date  DATE           NOT NULL,
    total_value    DECIMAL(18,2)  NOT NULL,
    cash_balance   DECIMAL(18,2)  NOT NULL,
    invested_cost  DECIMAL(18,2)  NOT NULL,
    CONSTRAINT fk_snap_portfolio FOREIGN KEY (portfolio_id) REFERENCES portfolio(id),
    CONSTRAINT uq_portfolio_date UNIQUE (portfolio_id, snapshot_date)
);
