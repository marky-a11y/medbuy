-- Add missing platforms that KpiSignalAggregator.resolvePlatformId() maps to
INSERT INTO media_buying.platform (name, display_name) VALUES
('google_shopping',  'Google Shopping'),
('yelp_ads',         'Yelp Ads'),
('foursquare_ads',   'Foursquare Ads'),
('bing_ads',         'Bing Ads'),
('skyscanner_ads',   'Skyscanner Ads');

-- Add missing sectors that CompanyPlatformGrouper/KpiSignalAggregator map to
INSERT INTO media_buying.commerce_sector (name, display_name) VALUES
('travel',           'Travel'),
('job_market',       'Job Market'),
('local_business',   'Local Business');
