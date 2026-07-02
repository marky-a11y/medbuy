-- ============================================================
-- V12: Seed data for client table
-- Description: 28 sample client records across all 5 sectors
-- ============================================================

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
