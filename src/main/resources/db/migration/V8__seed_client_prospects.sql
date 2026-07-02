-- ============================================================
-- V8: Seed data for client_prospects
-- Description: 25 sample client records across all 5 sectors
-- ============================================================

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
