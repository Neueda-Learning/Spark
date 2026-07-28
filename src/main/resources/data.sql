-- Seed 20 investment items (14 stocks + 4 bonds + 2 cash equivalents)
-- Using INSERT IGNORE to avoid duplicate key errors on restart
-- dividend_yield = 年化股息率（%），0 表示不分红

-- STOCKS (14)
-- dividend_date: 最近一次除息日，用于判断是否获得分红
INSERT IGNORE INTO stock (symbol, name, asset_type, sector, exchange, currency, dividend_yield, dividend_date) VALUES
('AAPL',  'Apple Inc.',           'STOCK', 'Technology',      'NASDAQ', 'USD', 0.0050, '2026-05-10'),
('MSFT',  'Microsoft Corp.',      'STOCK', 'Technology',      'NASDAQ', 'USD', 0.0080, '2026-05-15'),
('GOOGL', 'Alphabet Inc.',        'STOCK', 'Technology',      'NASDAQ', 'USD', 0.0000, NULL),
('AMZN',  'Amazon.com Inc.',      'STOCK', 'Consumer',        'NASDAQ', 'USD', 0.0000, NULL),
('NVDA',  'NVIDIA Corp.',         'STOCK', 'Technology',      'NASDAQ', 'USD', 0.0004, '2026-06-12'),
('TSLA',  'Tesla Inc.',           'STOCK', 'Automotive',      'NASDAQ', 'USD', 0.0000, NULL),
('META',  'Meta Platforms Inc.',  'STOCK', 'Technology',      'NASDAQ', 'USD', 0.0040, '2026-06-20'),
('JPM',   'JPMorgan Chase & Co.', 'STOCK', 'Finance',         'NYSE',   'USD', 0.0300, '2026-06-05'),
('JNJ',   'Johnson & Johnson',    'STOCK', 'Healthcare',      'NYSE',   'USD', 0.0320, '2026-05-25'),
('V',     'Visa Inc.',            'STOCK', 'Finance',         'NYSE',   'USD', 0.0075, '2026-06-01'),
('PG',    'Procter & Gamble Co.', 'STOCK', 'Consumer',        'NYSE',   'USD', 0.0250, '2026-04-20'),
('XOM',   'Exxon Mobil Corp.',    'STOCK', 'Energy',          'NYSE',   'USD', 0.0360, '2026-05-14'),
('UNH',   'UnitedHealth Group',   'STOCK', 'Healthcare',      'NYSE',   'USD', 0.0150, '2026-06-10'),
('MA',    'Mastercard Inc.',      'STOCK', 'Finance',         'NYSE',   'USD', 0.0060, '2026-05-08');

-- BONDS (4) — 债券 ETF 有分红（利息分配）
INSERT IGNORE INTO stock (symbol, name, asset_type, sector, exchange, currency, dividend_yield, dividend_date) VALUES
('AGG',  'iShares Core US Aggregate Bond ETF', 'BOND', 'Fixed Income', 'NYSE',   'USD', 0.0380, '2026-06-03'),
('BND',  'Vanguard Total Bond Market ETF',     'BOND', 'Fixed Income', 'NASDAQ', 'USD', 0.0360, '2026-06-04'),
('TLT',  'iShares 20+ Year Treasury Bond ETF', 'BOND', 'Fixed Income', 'NASDAQ', 'USD', 0.0420, '2026-06-02'),
('LQD',  'iShares iBoxx Investment Grade Corp Bond ETF', 'BOND', 'Fixed Income', 'NYSE', 'USD', 0.0400, '2026-06-03');

-- CASH EQUIVALENTS (2) — 现金类无分红
INSERT IGNORE INTO stock (symbol, name, asset_type, sector, exchange, currency, dividend_yield, dividend_date) VALUES
('USD',    'US Dollar',              'CASH', 'Cash', 'FOREX', 'USD', 0.0000, NULL),
('USDMONEY', 'US Money Market Fund', 'CASH', 'Cash', 'FUND',  'USD', 0.0000, NULL);

-- Default portfolio with $100,000 initial cash
INSERT IGNORE INTO portfolio (id, name, cash_balance) VALUES
(1, 'My Portfolio', 100000.00);
