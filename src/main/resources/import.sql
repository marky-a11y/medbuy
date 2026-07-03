-- ============================================================================
-- import.sql — Seed data for H2 in-memory database (dev / Railway profile)
-- Runs automatically after Hibernate ddl-auto:update creates the schema and
-- tables.  This is Hibernate's native bootstrapping mechanism, not Spring's
-- DataSourceInitializer, so it avoids the SmartInitializingSingleton hang
-- that was occurring on Railway with Java 11 fat-jar classloaders.
-- ============================================================================

-- Platform seed data
INSERT INTO media_buying.platform (name, display_name, api_reference) VALUES
('google_ads',   'Google Ads',   'https://developers.google.com/google-ads/api'),
('meta_ads',     'Meta Ads',     'https://developers.facebook.com/docs/marketing-apis/'),
('tiktok_ads',   'TikTok Ads',   'https://ads.tiktok.com/marketing_api/docs'),
('linkedin_ads', 'LinkedIn Ads', 'https://learn.microsoft.com/en-us/linkedin/marketing/'),
('iheart_radio', 'iHeart Radio', 'https://www.iheartmedia.com/advertise');

-- Sector seed data
INSERT INTO media_buying.commerce_sector (name, display_name) VALUES
('technology',       'Technology'),
('finance',          'Finance'),
('manufacturing',    'Manufacturing'),
('retail',           'Retail'),
('health_wellness',  'Health & Wellness');

-- Default roles
INSERT INTO media_buying.roles (name) VALUES ('ADMIN'), ('MEDIA_ANALYST'), ('VIEWER');

-- Default users (passwords are BCrypt encoded: "password123")
INSERT INTO media_buying.users (username, email, password_hash, first_name, last_name, is_active) VALUES
('admin', 'admin@autoresolve.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Admin', 'User', TRUE),
('analyst', 'analyst@autoresolve.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'Media', 'Analyst', TRUE),
('viewer', 'viewer@autoresolve.com', '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'View', 'Only', TRUE);

-- Assign roles
INSERT INTO media_buying.user_roles (user_id, role_id)
SELECT u.id, r.id FROM media_buying.users u, media_buying.roles r
WHERE u.username = 'admin' AND r.name = 'ADMIN';

INSERT INTO media_buying.user_roles (user_id, role_id)
SELECT u.id, r.id FROM media_buying.users u, media_buying.roles r
WHERE u.username = 'analyst' AND r.name = 'MEDIA_ANALYST';

INSERT INTO media_buying.user_roles (user_id, role_id)
SELECT u.id, r.id FROM media_buying.users u, media_buying.roles r
WHERE u.username = 'viewer' AND r.name = 'VIEWER';

-- Default scoring weights (sum = 1.0)
INSERT INTO media_buying.scoring_weights (kpi_name, weight) VALUES
('ROAS',                0.25),
('CAC',                 0.20),
('CLTV',                0.20),
('CONVERSION_RATE',     0.15),
('SCALABILITY',         0.10),
('ATTRIBUTION_ACCURACY',0.10);

-- Client prospects (sector_id references commerce_sector)
INSERT INTO media_buying.client_prospects
    (sector_id, company_name, est_annual_revenue, yoy_growth_rate, est_ad_budget, industry_vertical, notes)
VALUES
    -- Technology (sector_id = 1)
    (1, 'Salesforce',   34800000000.00, 11.00, 450000000.00, 'Enterprise SaaS',          'Leading CRM platform, strong enterprise presence'),
    (1, 'Adobe',        19400000000.00, 12.00, 310000000.00, 'Creative & Marketing Cloud', 'Creative software suite, expanding into experience cloud'),
    (1, 'ServiceNow',    9000000000.00, 24.00, 165000000.00, 'IT Workflow SaaS',         'Digital workflow automation leader'),
    (1, 'Snowflake',     3200000000.00, 36.00, 120000000.00, 'Cloud Data Platform',      'Cloud data warehousing, high growth'),
    (1, 'CrowdStrike',   3700000000.00, 33.00,  95000000.00, 'Cybersecurity SaaS',       'Endpoint security, rapid enterprise adoption'),
    -- Finance (sector_id = 2)
    (2, 'Stripe',       14000000000.00, 25.00,  85000000.00, 'Payment Processing',        'Online payment infrastructure leader'),
    (2, 'Block (Square)', 23500000000.00, 9.00, 210000000.00, 'Fintech Ecosystem',       'Point-of-sale and financial services platform'),
    (2, 'Robinhood',     2400000000.00, 18.00,  65000000.00, 'Retail Trading',           'Commission-free trading platform'),
    (2, 'Chime',         1500000000.00, 42.00,  55000000.00, 'Digital Banking',          'Neobank with rapid user growth'),
    (2, 'Plaid',          350000000.00, 20.00,  18000000.00, 'Open Banking API',         'Financial data aggregation platform'),
    -- Manufacturing (sector_id = 3)
    (3, 'Caterpillar',  67100000000.00,  7.00, 160000000.00, 'Heavy Equipment',          'Global heavy machinery manufacturer'),
    (3, '3M',           32700000000.00, -2.00,  95000000.00, 'Diversified Industrials',   'Multi-industry conglomerate'),
    (3, 'Deere & Co.',  61300000000.00,  5.00, 125000000.00, 'Agricultural Machinery',    'Farm equipment and precision agriculture'),
    (3, 'GE Aerospace', 32000000000.00, 16.00,  80000000.00, 'Aviation Engines',         'Jet engine manufacturing and services'),
    (3, 'Honeywell',    36700000000.00,  4.00,  90000000.00, 'Building & Aerospace Tech', 'Industrial automation and aerospace'),
    -- Retail (sector_id = 4)
    (4, 'Nike',         51200000000.00,  1.00, 420000000.00, 'Athletic Footwear & Apparel', 'Global sportswear brand'),
    (4, 'Lululemon',     9600000000.00, 16.00, 135000000.00, 'Premium Athletic Apparel',  'Athletic leisurewear with strong DTC presence'),
    (4, 'Chewy',        11100000000.00,  7.00, 190000000.00, 'Pet E-Commerce',           'Online pet food and supplies retailer'),
    (4, 'Wayfair',      12000000000.00, -4.00, 210000000.00, 'Home Goods E-Commerce',     'Online home furnishings marketplace'),
    (4, 'Ulta Beauty',  11200000000.00,  5.00, 115000000.00, 'Beauty Retail',             'Cosmetics and beauty products retailer'),
    -- Health & Wellness (sector_id = 5)
    (5, 'Hims & Hers',   1500000000.00, 46.00, 220000000.00, 'DTC Telehealth & Wellness', 'Direct-to-consumer healthcare platform'),
    (5, 'Noom',           450000000.00, 22.00, 130000000.00, 'Digital Weight Loss',       'Behavioral health and weight management app'),
    (5, 'Calm',           300000000.00, 14.00,  55000000.00, 'Mental Wellness App',      'Meditation and sleep improvement app'),
    (5, 'Headspace',      220000000.00, 11.00,  42000000.00, 'Meditation & Mindfulness',  'Guided meditation and mental health app'),
    (5, 'Ro',             500000000.00, 38.00,  85000000.00, 'DTC Men''s Health',        'Men''s health telehealth platform');

-- Data sources (platform_id references platform)
INSERT INTO media_buying.data_source (platform_id, source_name, source_type, source_url, license_type) VALUES
(1, 'Google Ads API v17', 'API', 'https://developers.google.com/google-ads/api', 'PROPRIETARY'),
(2, 'Meta Marketing API', 'API', 'https://developers.facebook.com/docs/marketing-apis/', 'PROPRIETARY'),
(3, 'TikTok Ads Manager API', 'API', 'https://ads.tiktok.com/marketing_api/docs', 'PROPRIETARY'),
(4, 'LinkedIn Marketing API', 'API', 'https://learn.microsoft.com/en-us/linkedin/marketing/', 'PROPRIETARY'),
(5, 'iHeart Media (estimated)', 'REPORT', 'https://www.iheartmedia.com/advertise', 'PROPRIETARY');

-- Client records (sector_id references commerce_sector)
INSERT INTO media_buying.client
    (client_name, sector_id, contract_type, contract_value, contract_start_date, contract_end_date, satisfaction_rating, growth_yoy_pct, engagement_score, outlook_score, is_active)
VALUES
    -- Technology (sector_id = 1) - 6 clients
    ('OmniTech Solutions',     1, 'RETAINER',    45000.00,  '2025-01-15', '2027-12-31', 4, 22.50, 85, 92, TRUE),
    ('CloudNexus Systems',     1, 'PERFORMANCE', 32000.00,  '2025-03-01', '2026-06-30', 5, 35.00, 92, 88, TRUE),
    ('DataPulse Analytics',    1, 'HYBRID',      28000.00,  '2025-06-01', '2026-09-30', 3, 18.00, 65, 72, TRUE),
    ('CyberShield Inc.',       1, 'RETAINER',    55000.00,  '2025-02-01', '2027-05-31', 5, 42.00, 95, 95, TRUE),
    ('AI Innovate Labs',       1, 'PERFORMANCE', 18000.00,  '2025-09-01', '2026-03-31', 4, 28.00, 78, 82, TRUE),
    ('QuantumLeap Tech',       1, 'HYBRID',      42000.00,  '2025-04-15', '2026-10-15', 2,  8.00, 45, 55, TRUE),
    -- Finance (sector_id = 2) - 6 clients
    ('FinCore Partners',       2, 'RETAINER',    62000.00,  '2025-01-01', '2027-06-30', 5, 15.00, 88, 90, TRUE),
    ('WealthBridge Capital',   2, 'HYBRID',      38000.00,  '2025-05-01', '2026-11-30', 4, 12.00, 72, 78, TRUE),
    ('InsureTech Global',      2, 'PERFORMANCE', 25000.00,  '2025-07-01', '2026-04-30', 3, 25.00, 60, 68, TRUE),
    ('PayFlow Solutions',      2, 'RETAINER',    48000.00,  '2025-03-15', '2027-03-14', 4, 30.00, 82, 85, TRUE),
    ('CryptoVault Advisors',   2, 'HYBRID',      35000.00,  '2025-08-01', '2026-08-31', 2,  5.00, 35, 45, TRUE),
    ('AgriCapital Finance',    2, 'PERFORMANCE', 15000.00,  '2025-10-01', '2026-01-31', 5, 20.00, 90, 88, TRUE),
    -- Manufacturing (sector_id = 3) - 5 clients
    ('GreenLeaf Manufacturing',3, 'RETAINER',    52000.00,  '2025-02-01', '2027-04-30', 4, 10.00, 75, 80, TRUE),
    ('Precision Auto Parts',   3, 'PERFORMANCE', 22000.00,  '2025-06-15', '2026-05-31', 3,  8.00, 55, 62, TRUE),
    ('SteelCore Industries',   3, 'HYBRID',      41000.00,  '2025-04-01', '2026-12-31', 5, 14.00, 80, 82, TRUE),
    ('EcoBuild Materials',     3, 'RETAINER',    33000.00,  '2025-09-15', '2027-01-31', 4, 18.00, 70, 76, TRUE),
    ('AeroDyne Systems',       3, 'PERFORMANCE', 19000.00,  '2025-11-01', '2026-02-28', 2, -2.00, 40, 48, TRUE),
    -- Retail (sector_id = 4) - 6 clients
    ('StyleHub Retail',        4, 'HYBRID',      36000.00,  '2025-03-01', '2026-09-30', 4, 16.00, 78, 84, TRUE),
    ('FreshCart Grocers',      4, 'RETAINER',    44000.00,  '2025-01-15', '2027-02-28', 5, 12.00, 85, 86, TRUE),
    ('Urban Boutique Co.',     4, 'PERFORMANCE', 16000.00,  '2025-08-01', '2026-07-31', 3, 30.00, 62, 70, TRUE),
    ('HomeStyle Decor',        4, 'RETAINER',    29000.00,  '2025-05-01', '2026-10-31', 4,  8.00, 72, 74, TRUE),
    ('ElectroMart Online',     4, 'HYBRID',      39000.00,  '2025-07-15', '2027-01-14', 5, 22.00, 88, 91, TRUE),
    ('Luxe Fashion House',     4, 'PERFORMANCE', 12000.00,  '2025-12-01', '2026-03-31', 1, 45.00, 30, 50, FALSE),
    -- Health & Wellness (sector_id = 5) - 5 clients
    ('VitalCare Health',       5, 'RETAINER',    58000.00,  '2025-01-01', '2027-08-31', 5, 25.00, 92, 94, TRUE),
    ('PureLife Organics',      5, 'HYBRID',      26000.00,  '2025-04-01', '2026-06-30', 4, 32.00, 75, 80, TRUE),
    ('MindWell Therapies',     5, 'PERFORMANCE', 20000.00,  '2025-10-01', '2026-04-30', 3, 18.00, 58, 65, TRUE),
    ('SeniorCare Connect',     5, 'RETAINER',    47000.00,  '2025-06-01', '2027-03-31', 4, 10.00, 80, 78, TRUE),
    ('FitLife Nutrition',      5, 'HYBRID',      31000.00,  '2025-08-15', '2026-12-31', 5, 38.00, 88, 90, TRUE);

-- Additional platforms from V14 (for KpiSignalAggregator.resolvePlatformId())
INSERT INTO media_buying.platform (name, display_name) VALUES
('google_shopping',  'Google Shopping'),
('yelp_ads',         'Yelp Ads'),
('foursquare_ads',   'Foursquare Ads'),
('bing_ads',         'Bing Ads'),
('skyscanner_ads',   'Skyscanner Ads');

-- Additional sectors from V14 (for CompanyPlatformGrouper/KpiSignalAggregator)
INSERT INTO media_buying.commerce_sector (name, display_name) VALUES
('travel',           'Travel'),
('job_market',       'Job Market'),
('local_business',   'Local Business');
