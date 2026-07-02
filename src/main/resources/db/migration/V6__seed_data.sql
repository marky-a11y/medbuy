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
-- BCrypt hash for 'password123': $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy
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
