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

-- The six buys use the 2025-07-01 Yahoo close from market_price_daily.
-- The portfolio starts at exactly USD 100,000:
-- USD 78,580.35 invested + USD 21,419.65 cash.
INSERT IGNORE INTO portfolio (id, name, cash_balance)
VALUES (1, 'My Portfolio', 21419.65);

INSERT IGNORE INTO holding
    (id, portfolio_id, stock_id, quantity, average_cost)
VALUES
    (1001, 1, (SELECT id FROM stock WHERE symbol = 'AAPL'), 45.0000, 207.8200),
    (1002, 1, (SELECT id FROM stock WHERE symbol = 'MSFT'), 35.0000, 492.0500),
    (1003, 1, (SELECT id FROM stock WHERE symbol = 'PG'),   80.0000, 161.2200),
    (1004, 1, (SELECT id FROM stock WHERE symbol = 'XOM'),  70.0000, 109.2400),
    (1005, 1, (SELECT id FROM stock WHERE symbol = 'AGG'), 170.0000,  98.7900),
    (1006, 1, (SELECT id FROM stock WHERE symbol = 'BND'), 200.0000,  73.3400);

-- Fixed ids make repeated Spring data.sql execution idempotent.
INSERT IGNORE INTO transaction
    (id, portfolio_id, stock_id, type, quantity, unit_price, total_amount, created_at)
VALUES
    (1001, 1, (SELECT id FROM stock WHERE symbol = 'AAPL'),
        'BUY', 45.0000, 207.8200,  9351.90, '2025-07-01 16:05:00'),
    (1002, 1, (SELECT id FROM stock WHERE symbol = 'MSFT'),
        'BUY', 35.0000, 492.0500, 17221.75, '2025-07-01 16:06:00'),
    (1003, 1, (SELECT id FROM stock WHERE symbol = 'PG'),
        'BUY', 80.0000, 161.2200, 12897.60, '2025-07-01 16:07:00'),
    (1004, 1, (SELECT id FROM stock WHERE symbol = 'XOM'),
        'BUY', 70.0000, 109.2400,  7646.80, '2025-07-01 16:08:00'),
    (1005, 1, (SELECT id FROM stock WHERE symbol = 'AGG'),
        'BUY', 170.0000, 98.7900, 16794.30, '2025-07-01 16:09:00'),
    (1006, 1, (SELECT id FROM stock WHERE symbol = 'BND'),
        'BUY', 200.0000, 73.3400, 14668.00, '2025-07-01 16:10:00');

-- Verified dividend events used by the overview's paid/pending cards.
-- Ex-dates and amounts were cross-checked against Yahoo chart events.
-- Pay dates and exact amounts come from the issuers:
-- Apple: https://investor.apple.com/dividend-history/default.aspx
-- AGG:   https://www.ishares.com/us/products/239458/AGG
-- P&G:   https://us.pg.com/newsroom/news-releases/PG-Declares-Quarterly-Dividend-July-2026/
INSERT IGNORE INTO dividend_history
    (symbol, ex_date, pay_date, dividend_per_share, frequency)
VALUES
    ('AAPL', '2025-08-11', '2025-08-14', 0.2600, 'quarterly'),
    ('AAPL', '2025-11-10', '2025-11-13', 0.2600, 'quarterly'),
    ('AAPL', '2026-02-09', '2026-02-12', 0.2600, 'quarterly'),
    ('AAPL', '2026-05-11', '2026-05-14', 0.2700, 'quarterly'),
    ('AGG',  '2025-09-02', '2025-09-05', 0.3269, 'monthly'),
    ('AGG',  '2025-10-01', '2025-10-06', 0.3252, 'monthly'),
    ('AGG',  '2025-11-03', '2025-11-06', 0.3270, 'monthly'),
    ('AGG',  '2025-12-01', '2025-12-04', 0.3264, 'monthly'),
    ('AGG',  '2025-12-19', '2025-12-24', 0.3340, 'monthly'),
    ('AGG',  '2026-02-02', '2026-02-05', 0.3247, 'monthly'),
    ('AGG',  '2026-03-02', '2026-03-05', 0.3164, 'monthly'),
    ('AGG',  '2026-04-01', '2026-04-07', 0.3367, 'monthly'),
    ('AGG',  '2026-05-01', '2026-05-06', 0.3299, 'monthly'),
    ('AGG',  '2026-06-01', '2026-06-04', 0.3315, 'monthly'),
    ('AGG',  '2026-07-01', '2026-07-07', 0.3307, 'monthly'),
    ('PG',   '2026-07-24', '2026-08-17', 1.0885, 'quarterly');

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
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-20',
     149.559998, 150.100006, 148.429993, 149.130005, 148.024994, 5788300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-20',
     146.500000, 149.089996, 146.229996, 148.360001, 148.360001, 12149200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-20',
     98.169998, 98.180000, 97.900002, 97.949997, 97.949997, 4949400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-20',
     72.830002, 72.839996, 72.620003, 72.680000, 72.680000, 5578300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-21',
     323.130005, 329.600006, 322.220001, 327.739990, 327.739990, 41338900, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-21',
     398.809998, 401.470001, 396.320007, 397.750000, 397.750000, 24126600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-21',
     207.539993, 208.649994, 204.009995, 207.289993, 207.289993, 108685600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-21',
     147.860001, 148.410004, 146.880005, 148.100006, 147.002625, 7027600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-21',
     149.009995, 151.759995, 148.479996, 151.710007, 151.710007, 13145300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-21',
     97.849998, 97.900002, 97.720001, 97.739998, 97.739998, 9517800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-21',
     72.570000, 72.580002, 72.480003, 72.519997, 72.519997, 5679100, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-22',
     327.869995, 329.000000, 323.339996, 325.890015, 325.890015, 38755900, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-22',
     399.579987, 401.000000, 386.959991, 390.339996, 390.339996, 28142500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-22',
     205.809998, 214.389999, 204.949997, 212.059998, 212.059998, 137645600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-22',
     149.500000, 150.460007, 148.820007, 149.130005, 148.024994, 5371200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-22',
     153.860001, 154.800003, 152.949997, 154.449997, 154.449997, 13788700, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-22',
     97.709999, 97.739998, 97.580002, 97.580002, 97.580002, 6313300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-22',
     72.489998, 72.519997, 72.379997, 72.400002, 72.400002, 5863000, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-23',
     321.730011, 323.299988, 319.350006, 321.660004, 321.660004, 40840800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-23',
     389.970001, 391.779999, 377.390015, 381.579987, 381.579987, 30351800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-23',
     209.460007, 210.869995, 205.960007, 208.759995, 208.759995, 110505300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-23',
     146.139999, 147.610001, 145.589996, 146.970001, 145.880997, 6499200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-23',
     156.600006, 158.570007, 156.059998, 156.889999, 156.889999, 15822800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-23',
     97.320000, 97.400002, 97.269997, 97.339996, 97.339996, 6394300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-23',
     72.199997, 72.269997, 72.169998, 72.250000, 72.250000, 7641600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-24',
     321.790009, 334.369995, 321.619995, 333.019989, 333.019989, 47489400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-24',
     387.049988, 389.029999, 380.649994, 381.700012, 381.700012, 27659400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-24',
     207.449997, 211.910004, 204.809998, 206.839996, 206.839996, 114836800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-24',
     146.449997, 147.990005, 145.399994, 147.410004, 147.410004, 6620700, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-24',
     156.990005, 158.710007, 155.679993, 156.940002, 156.940002, 12091600, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-24',
     97.449997, 97.639999, 97.410004, 97.459999, 97.459999, 5908200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-24',
     72.269997, 72.430000, 72.269997, 72.309998, 72.309998, 5695400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-27',
     334.540009, 339.570007, 334.019989, 336.910004, 336.910004, 49604300, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-27',
     390.079987, 394.200012, 387.989990, 389.100006, 389.100006, 27856200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-27',
     208.199997, 208.750000, 195.440002, 196.509995, 196.509995, 154353700, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-27',
     147.850006, 150.250000, 147.850006, 148.630005, 148.630005, 7301800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-27',
     153.070007, 155.419998, 152.500000, 154.770004, 154.770004, 13955800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-27',
     97.639999, 97.730003, 97.589996, 97.690002, 97.690002, 7042100, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-27',
     72.430000, 72.500000, 72.389999, 72.459999, 72.459999, 5667200, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-28',
     340.029999, 342.890015, 335.600006, 340.079987, 340.079987, 51859000, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-28',
     393.160004, 400.320007, 391.299988, 393.350006, 393.350006, 32367500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-28',
     195.000000, 198.699997, 192.740005, 197.009995, 197.009995, 134111500, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-28',
     151.500000, 153.679993, 148.520004, 148.880005, 148.880005, 12848800, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-28',
     154.169998, 156.179993, 151.880005, 153.039993, 153.039993, 16299900, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-28',
     97.790001, 98.010002, 97.769997, 97.919998, 97.919998, 12019400, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-28',
     72.529999, 72.699997, 72.510002, 72.639999, 72.639999, 5279700, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-29',
     339.690002, 344.569885, 337.350098, 338.190002, 338.190002, 48852885, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-29',
     393.399994, 401.250000, 388.743011, 390.540009, 390.540009, 42418496, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'NVDA'), '2026-07-29',
     195.845001, 197.074005, 190.009995, 190.009995, 190.009995, 136609393, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'PG'), '2026-07-29',
     141.380005, 146.375000, 140.199997, 146.100006, 146.100006, 12663401, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'XOM'), '2026-07-29',
     157.720001, 159.072205, 155.865005, 156.750000, 156.750000, 10871460, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-29',
     97.830002, 97.894997, 97.550003, 97.550003, 97.550003, 5740348, 'YAHOO', CURRENT_TIMESTAMP),
    ((SELECT id FROM stock WHERE symbol = 'BND'), '2026-07-29',
     72.559998, 72.629997, 72.370003, 72.379997, 72.379997, 6736886, 'YAHOO', CURRENT_TIMESTAMP)
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
