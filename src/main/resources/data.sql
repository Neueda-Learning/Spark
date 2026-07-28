-- Seed 20 investment items (14 stocks + 4 bonds + 2 cash equivalents)
-- Using INSERT IGNORE to avoid duplicate key errors on restart

-- STOCKS (14)
INSERT IGNORE INTO stock (symbol, name, asset_type, sector, exchange, currency) VALUES
('AAPL',  'Apple Inc.',           'STOCK', 'Technology',      'NASDAQ', 'USD'),
('MSFT',  'Microsoft Corp.',      'STOCK', 'Technology',      'NASDAQ', 'USD'),
('GOOGL', 'Alphabet Inc.',        'STOCK', 'Technology',      'NASDAQ', 'USD'),
('AMZN',  'Amazon.com Inc.',      'STOCK', 'Consumer',        'NASDAQ', 'USD'),
('NVDA',  'NVIDIA Corp.',         'STOCK', 'Technology',      'NASDAQ', 'USD'),
('TSLA',  'Tesla Inc.',           'STOCK', 'Automotive',      'NASDAQ', 'USD'),
('META',  'Meta Platforms Inc.',  'STOCK', 'Technology',      'NASDAQ', 'USD'),
('JPM',   'JPMorgan Chase & Co.', 'STOCK', 'Finance',         'NYSE',   'USD'),
('JNJ',   'Johnson & Johnson',    'STOCK', 'Healthcare',      'NYSE',   'USD'),
('V',     'Visa Inc.',            'STOCK', 'Finance',         'NYSE',   'USD'),
('PG',    'Procter & Gamble Co.', 'STOCK', 'Consumer',        'NYSE',   'USD'),
('XOM',   'Exxon Mobil Corp.',    'STOCK', 'Energy',          'NYSE',   'USD'),
('UNH',   'UnitedHealth Group',   'STOCK', 'Healthcare',      'NYSE',   'USD'),
('MA',    'Mastercard Inc.',      'STOCK', 'Finance',         'NYSE',   'USD');

-- BONDS (4)
INSERT IGNORE INTO stock (symbol, name, asset_type, sector, exchange, currency) VALUES
('AGG',  'iShares Core US Aggregate Bond ETF', 'BOND', 'Fixed Income', 'NYSE', 'USD'),
('BND',  'Vanguard Total Bond Market ETF',     'BOND', 'Fixed Income', 'NASDAQ', 'USD'),
('TLT',  'iShares 20+ Year Treasury Bond ETF', 'BOND', 'Fixed Income', 'NASDAQ', 'USD'),
('LQD',  'iShares iBoxx Investment Grade Corp Bond ETF', 'BOND', 'Fixed Income', 'NYSE', 'USD');

-- CASH EQUIVALENTS (2)
INSERT IGNORE INTO stock (symbol, name, asset_type, sector, exchange, currency) VALUES
('USD',    'US Dollar',              'CASH', 'Cash', 'FOREX', 'USD'),
('USDMONEY', 'US Money Market Fund', 'CASH', 'Cash', 'FUND',  'USD');

-- Default portfolio with $100,000 initial cash
INSERT IGNORE INTO portfolio (id, name, cash_balance) VALUES
<<<<<<< HEAD
(1, 'My Portfolio', 91830.00);

-- Align the default portfolio cash with the seeded positions on first startup.
UPDATE portfolio
SET cash_balance = 91830.00
WHERE id = 1 AND cash_balance = 100000.00;

-- Seed a small live portfolio so overview, holdings, and weekly performance are not empty.
INSERT IGNORE INTO holding (id, portfolio_id, stock_id, quantity, average_cost) VALUES
(1001, 1, (SELECT id FROM stock WHERE symbol = 'AAPL'), 15.0000, 190.0000),
(1002, 1, (SELECT id FROM stock WHERE symbol = 'MSFT'), 8.0000, 415.0000),
(1003, 1, (SELECT id FROM stock WHERE symbol = 'AGG'), 20.0000, 100.0000);

-- Seed matching trade history. Fixed ids keep this block idempotent across restarts.
INSERT IGNORE INTO transaction (id, portfolio_id, stock_id, type, quantity, unit_price, total_amount, created_at) VALUES
(1001, 1, (SELECT id FROM stock WHERE symbol = 'AAPL'), 'BUY', 15.0000, 190.0000, 2850.00, '2026-07-18 09:35:00'),
(1002, 1, (SELECT id FROM stock WHERE symbol = 'MSFT'), 'BUY', 8.0000, 415.0000, 3320.00, '2026-07-19 10:10:00'),
(1003, 1, (SELECT id FROM stock WHERE symbol = 'AGG'), 'BUY', 20.0000, 100.0000, 2000.00, '2026-07-23 14:20:00');

-- -- Seed the latest 7 trade dates used by /api/portfolio/weekly-performance.
-- INSERT IGNORE INTO market_price_daily (stock_id, trade_date, open_price, high_price, low_price, close_price, adjusted_close, volume, source, fetched_at) VALUES
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-21', 187.500000, 188.800000, 186.900000, 188.000000, 188.000000, 53120000, 'SEED', '2026-07-21 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-22', 189.600000, 191.400000, 189.200000, 191.000000, 191.000000, 49870000, 'SEED', '2026-07-22 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-23', 191.900000, 193.700000, 191.200000, 193.000000, 193.000000, 52040000, 'SEED', '2026-07-23 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-24', 192.300000, 193.100000, 191.100000, 192.000000, 192.000000, 47680000, 'SEED', '2026-07-24 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-25', 195.400000, 196.500000, 194.600000, 196.000000, 196.000000, 40230000, 'SEED', '2026-07-25 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-26', 197.200000, 198.500000, 196.700000, 198.000000, 198.000000, 38950000, 'SEED', '2026-07-26 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AAPL'), '2026-07-27', 199.800000, 201.600000, 199.100000, 201.000000, 201.000000, 44510000, 'SEED', '2026-07-27 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-21', 409.200000, 411.800000, 408.600000, 410.000000, 410.000000, 24100000, 'SEED', '2026-07-21 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-22', 411.500000, 413.600000, 410.900000, 412.000000, 412.000000, 22950000, 'SEED', '2026-07-22 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-23', 416.400000, 419.200000, 415.800000, 418.000000, 418.000000, 26280000, 'SEED', '2026-07-23 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-24', 419.500000, 421.900000, 418.800000, 421.000000, 421.000000, 25520000, 'SEED', '2026-07-24 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-25', 418.300000, 420.100000, 417.500000, 419.000000, 419.000000, 21460000, 'SEED', '2026-07-25 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-26', 423.100000, 425.900000, 422.400000, 425.000000, 425.000000, 23890000, 'SEED', '2026-07-26 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'MSFT'), '2026-07-27', 428.000000, 430.800000, 427.300000, 430.000000, 430.000000, 27110000, 'SEED', '2026-07-27 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-21', 99.100000, 99.400000, 98.900000, 99.200000, 99.200000, 7150000, 'SEED', '2026-07-21 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-22', 99.300000, 99.600000, 99.100000, 99.400000, 99.400000, 6840000, 'SEED', '2026-07-22 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-23', 99.700000, 100.000000, 99.500000, 99.800000, 99.800000, 7020000, 'SEED', '2026-07-23 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-24', 100.000000, 100.300000, 99.800000, 100.100000, 100.100000, 6930000, 'SEED', '2026-07-24 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-25', 100.200000, 100.500000, 100.000000, 100.300000, 100.300000, 6480000, 'SEED', '2026-07-25 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-26', 100.000000, 100.300000, 99.900000, 100.150000, 100.150000, 6310000, 'SEED', '2026-07-26 16:00:00'),
-- ((SELECT id FROM stock WHERE symbol = 'AGG'), '2026-07-27', 100.300000, 100.700000, 100.100000, 100.500000, 100.500000, 6670000, 'SEED', '2026-07-27 16:00:00');
=======
(1, 'My Portfolio', 100000.00);

-- ============================================
-- Dividend History (2025 Q3 ~ 2026 Q3)
-- ============================================

-- AAPL: $0.25/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('AAPL', '2025-08-08', '2025-08-14', 0.2500, 'quarterly'),
('AAPL', '2025-11-07', '2025-11-13', 0.2500, 'quarterly'),
('AAPL', '2026-02-06', '2026-02-12', 0.2500, 'quarterly'),
('AAPL', '2026-05-08', '2026-05-14', 0.2500, 'quarterly');

-- MSFT: $0.75/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('MSFT', '2025-08-14', '2025-09-04', 0.7500, 'quarterly'),
('MSFT', '2025-11-20', '2025-12-11', 0.7500, 'quarterly'),
('MSFT', '2026-02-19', '2026-03-12', 0.7500, 'quarterly'),
('MSFT', '2026-05-14', '2026-06-11', 0.7500, 'quarterly');

-- NVDA: $0.01/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('NVDA', '2025-09-05', '2025-09-12', 0.0100, 'quarterly'),
('NVDA', '2025-12-05', '2025-12-12', 0.0100, 'quarterly'),
('NVDA', '2026-03-06', '2026-03-13', 0.0100, 'quarterly'),
('NVDA', '2026-06-12', '2026-06-19', 0.0100, 'quarterly');

-- META: $0.50/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('META', '2025-09-15', '2025-09-25', 0.5000, 'quarterly'),
('META', '2025-12-15', '2026-01-09', 0.5000, 'quarterly'),
('META', '2026-03-15', '2026-03-26', 0.5000, 'quarterly'),
('META', '2026-06-15', '2026-06-26', 0.5000, 'quarterly');

-- JPM: $1.05/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('JPM', '2025-09-04', '2025-10-01', 1.0500, 'quarterly'),
('JPM', '2025-12-04', '2026-01-02', 1.0500, 'quarterly'),
('JPM', '2026-03-05', '2026-04-01', 1.0500, 'quarterly'),
('JPM', '2026-06-05', '2026-07-01', 1.0500, 'quarterly');

-- JNJ: $1.24/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('JNJ', '2025-08-25', '2025-09-09', 1.2400, 'quarterly'),
('JNJ', '2025-11-24', '2025-12-09', 1.2400, 'quarterly'),
('JNJ', '2026-02-23', '2026-03-10', 1.2400, 'quarterly'),
('JNJ', '2026-05-25', '2026-06-09', 1.2400, 'quarterly');

-- V: $0.56/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('V', '2025-09-04', '2025-10-01', 0.5600, 'quarterly'),
('V', '2025-12-04', '2026-01-02', 0.5600, 'quarterly'),
('V', '2026-03-05', '2026-04-01', 0.5600, 'quarterly'),
('V', '2026-06-04', '2026-07-01', 0.5600, 'quarterly');

-- PG: $1.0175/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('PG', '2025-10-23', '2025-11-17', 1.0175, 'quarterly'),
('PG', '2026-01-22', '2026-02-15', 1.0175, 'quarterly'),
('PG', '2026-04-20', '2026-05-15', 1.0175, 'quarterly'),
('PG', '2026-07-23', '2026-08-15', 1.0175, 'quarterly');

-- XOM: $0.95/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('XOM', '2025-08-14', '2025-09-10', 0.9500, 'quarterly'),
('XOM', '2025-11-13', '2025-12-10', 0.9500, 'quarterly'),
('XOM', '2026-02-12', '2026-03-10', 0.9500, 'quarterly'),
('XOM', '2026-05-14', '2026-06-10', 0.9500, 'quarterly');

-- UNH: $2.00/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('UNH', '2025-09-05', '2025-09-30', 2.0000, 'quarterly'),
('UNH', '2025-12-05', '2025-12-30', 2.0000, 'quarterly'),
('UNH', '2026-03-06', '2026-03-30', 2.0000, 'quarterly'),
('UNH', '2026-06-05', '2026-06-30', 2.0000, 'quarterly');

-- MA: $0.77/share quarterly
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('MA', '2025-08-07', '2025-08-20', 0.7700, 'quarterly'),
('MA', '2025-11-06', '2025-11-20', 0.7700, 'quarterly'),
('MA', '2026-02-05', '2026-02-20', 0.7700, 'quarterly'),
('MA', '2026-05-07', '2026-05-20', 0.7700, 'quarterly');

-- AGG: $0.93/share quarterly (bond ETF)
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('AGG', '2025-09-03', '2025-09-10', 0.9300, 'quarterly'),
('AGG', '2025-12-03', '2025-12-10', 0.9300, 'quarterly'),
('AGG', '2026-03-04', '2026-03-11', 0.9300, 'quarterly'),
('AGG', '2026-06-03', '2026-06-10', 0.9300, 'quarterly');

-- BND: $0.44/share quarterly (bond ETF)
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('BND', '2025-09-04', '2025-09-10', 0.4400, 'quarterly'),
('BND', '2025-12-04', '2025-12-10', 0.4400, 'quarterly'),
('BND', '2026-03-05', '2026-03-11', 0.4400, 'quarterly'),
('BND', '2026-06-04', '2026-06-10', 0.4400, 'quarterly');

-- TLT: $0.66/share quarterly (bond ETF)
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('TLT', '2025-09-02', '2025-09-08', 0.6600, 'quarterly'),
('TLT', '2025-12-02', '2025-12-08', 0.6600, 'quarterly'),
('TLT', '2026-03-03', '2026-03-09', 0.6600, 'quarterly'),
('TLT', '2026-06-02', '2026-06-08', 0.6600, 'quarterly');

-- LQD: $0.65/share quarterly (bond ETF)
INSERT IGNORE INTO dividend_history (symbol, ex_date, pay_date, dividend_per_share, frequency) VALUES
('LQD', '2025-09-03', '2025-09-10', 0.6500, 'quarterly'),
('LQD', '2025-12-03', '2025-12-10', 0.6500, 'quarterly'),
('LQD', '2026-03-04', '2026-03-11', 0.6500, 'quarterly'),
('LQD', '2026-06-03', '2026-06-10', 0.6500, 'quarterly');
>>>>>>> origin/feature/Dividend-Income
