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
(1, 'My Portfolio', 100000.00);
