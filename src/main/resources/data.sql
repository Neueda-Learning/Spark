-- Spark Portfolio Manager - unified seed data
-- Source of the OHLCV rows: the current project's market_price_daily table.
-- Those rows were fetched by the existing Yahoo provider and have source=YAHOO.
-- Snapshot copied on 2026-07-30; market dates cover 2026-07-20..2026-07-29.

-- Keep the full instrument catalogue available to the investment page.
INSERT IGNORE INTO stock
    (symbol, name, asset_type, sector, exchange, currency)
VALUES
    ('AAPL', 'Apple Inc.', 'STOCK', 'Technology', 'NASDAQ', 'USD'),
    ('MSFT', 'Microsoft Corp.', 'STOCK', 'Technology', 'NASDAQ', 'USD'),
    ('GOOGL', 'Alphabet Inc.', 'STOCK', 'Technology', 'NASDAQ', 'USD'),
    ('AMZN', 'Amazon.com Inc.', 'STOCK', 'Consumer', 'NASDAQ', 'USD'),
    ('NVDA', 'NVIDIA Corp.', 'STOCK', 'Technology', 'NASDAQ', 'USD'),
    ('TSLA', 'Tesla Inc.', 'STOCK', 'Automotive', 'NASDAQ', 'USD'),
    ('META', 'Meta Platforms Inc.', 'STOCK', 'Technology', 'NASDAQ', 'USD'),
    ('JPM', 'JPMorgan Chase & Co.', 'STOCK', 'Finance', 'NYSE', 'USD'),
    ('JNJ', 'Johnson & Johnson', 'STOCK', 'Healthcare', 'NYSE', 'USD'),
    ('V', 'Visa Inc.', 'STOCK', 'Finance', 'NYSE', 'USD'),
    ('PG', 'Procter & Gamble Co.', 'STOCK', 'Consumer', 'NYSE', 'USD'),
    ('XOM', 'Exxon Mobil Corp.', 'STOCK', 'Energy', 'NYSE', 'USD'),
    ('UNH', 'UnitedHealth Group', 'STOCK', 'Healthcare', 'NYSE', 'USD'),
    ('MA', 'Mastercard Inc.', 'STOCK', 'Finance', 'NYSE', 'USD'),
    ('AGG', 'iShares Core US Aggregate Bond ETF', 'BOND', 'Fixed Income', 'NYSE', 'USD'),
    ('BND', 'Vanguard Total Bond Market ETF', 'BOND', 'Fixed Income', 'NASDAQ', 'USD'),
    ('TLT', 'iShares 20+ Year Treasury Bond ETF', 'BOND', 'Fixed Income', 'NASDAQ', 'USD'),
    ('LQD', 'iShares iBoxx Investment Grade Corp Bond ETF', 'BOND', 'Fixed Income', 'NYSE', 'USD'),
    ('USD', 'US Dollar', 'CASH', 'Cash', 'FOREX', 'USD'),
    ('USDMONEY', 'US Money Market Fund', 'CASH', 'Cash', 'FUND', 'USD');

-- The three buys use the 2026-07-20 Yahoo close as their unit cost.
-- 10 * 326.5900 + 8 * 402.2900 + 20 * 203.2800 = 10,549.82.
INSERT IGNORE INTO portfolio (id, name, cash_balance)
VALUES (1, 'My Portfolio', 89450.18);

INSERT IGNORE INTO holding
    (id, portfolio_id, stock_id, quantity, average_cost)
VALUES
    (1001, 1, (SELECT id FROM stock WHERE symbol = 'AAPL'), 10.0000, 326.5900),
    (1002, 1, (SELECT id FROM stock WHERE symbol = 'MSFT'), 8.0000, 402.2900),
    (1003, 1, (SELECT id FROM stock WHERE symbol = 'NVDA'), 20.0000, 203.2800);

-- Fixed ids make repeated Spring data.sql execution idempotent.
INSERT IGNORE INTO transaction
    (id, portfolio_id, stock_id, type, quantity, unit_price, total_amount, created_at)
VALUES
    (1001, 1, (SELECT id FROM stock WHERE symbol = 'AAPL'),
        'BUY', 10.0000, 326.5900, 3265.90, '2026-07-20 16:05:00'),
    (1002, 1, (SELECT id FROM stock WHERE symbol = 'MSFT'),
        'BUY', 8.0000, 402.2900, 3218.32, '2026-07-20 16:06:00'),
    (1003, 1, (SELECT id FROM stock WHERE symbol = 'NVDA'),
        'BUY', 20.0000, 203.2800, 4065.60, '2026-07-20 16:07:00');

-- Eight common market dates are included. The service selects the latest seven:
-- 2026-07-21, 07-22, 07-23, 07-24, 07-27, 07-28, and 07-29.
INSERT INTO market_price_daily
(stock_id, trade_date, open_price, high_price, low_price, close_price,
 adjusted_close, volume, source, fetched_at)
VALUES
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-20',
     333.510010, 333.709991, 323.679993, 326.589996, 326.589996, 53468000, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-20',
     391.410004, 403.179993, 389.649994, 402.290009, 402.290009, 27915800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-20',
     205.869995, 207.740005, 202.279999, 203.279999, 203.279999, 88701500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-21',
     323.130005, 329.600006, 322.220001, 327.739990, 327.739990, 41338900, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-21',
     398.809998, 401.470001, 396.320007, 397.750000, 397.750000, 24126600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-21',
     207.539993, 208.649994, 204.009995, 207.289993, 207.289993, 108685600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-22',
     327.869995, 329.000000, 323.339996, 325.890015, 325.890015, 38755900, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-22',
     399.579987, 401.000000, 386.959991, 390.339996, 390.339996, 28142500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-22',
     205.809998, 214.389999, 204.949997, 212.059998, 212.059998, 137645600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-23',
     321.730011, 323.299988, 319.350006, 321.660004, 321.660004, 40840800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-23',
     389.970001, 391.779999, 377.390015, 381.579987, 381.579987, 30351800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-23',
     209.460007, 210.869995, 205.960007, 208.759995, 208.759995, 110505300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-24',
     321.790009, 334.369995, 321.619995, 333.019989, 333.019989, 47489400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-24',
     387.049988, 389.029999, 380.649994, 381.700012, 381.700012, 27659400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-24',
     207.449997, 211.910004, 204.809998, 206.839996, 206.839996, 114836800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-27',
     334.540009, 339.570007, 334.019989, 336.910004, 336.910004, 49604300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-27',
     390.079987, 394.200012, 387.989990, 389.100006, 389.100006, 27856200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-27',
     208.199997, 208.750000, 195.440002, 196.509995, 196.509995, 154353700, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-28',
     340.029999, 342.890015, 335.600006, 340.079987, 340.079987, 51859000, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-28',
     393.160004, 400.320007, 391.299988, 393.350006, 393.350006, 32367500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-28',
     195.000000, 198.699997, 192.740005, 197.009995, 197.009995, 134111500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-29',
     339.690002, 344.569885, 337.350098, 338.190002, 338.190002, 48852885, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-29',
     393.399994, 401.250000, 388.743011, 390.540009, 390.540009, 42418496, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-29',
     195.845001, 197.074005, 190.009995, 190.009995, 190.009995, 136609393, 'YAHOO', CURRENT_TIMESTAMP)
    ON DUPLICATE KEY UPDATE
                         open_price = VALUES(open_price),
                         high_price = VALUES(high_price),
                         low_price = VALUES(low_price),
                         close_price = VALUES(close_price),
                         adjusted_close = VALUES(adjusted_close),
                         volume = VALUES(volume),
                         source = VALUES(source),
                         fetched_at = VALUES(fetched_at);

-- portfolio_performance_cache and user_dividend are derived data. Leave them
-- empty so the application builds them from transactions and market prices.
