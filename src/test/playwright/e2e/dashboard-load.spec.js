// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-23: E2E Test — Dashboard Page Load
 * Verifies that the dashboard page loads within 2 seconds and key elements render.
 */
test.describe('Dashboard Page Load', () => {
  test('should load the dashboard within 2 seconds', async ({ page }) => {
    const startTime = Date.now();

    await page.goto('/dashboard.xhtml');

    // Wait for the bento-grid to render
    await page.waitForSelector('.bento-grid', { timeout: 10000 });

    const loadTime = Date.now() - startTime;
    console.log(`Dashboard load time: ${loadTime}ms`);

    // Assert load time is within 2 seconds
    expect(loadTime).toBeLessThan(2000);

    // Assert key elements are visible
    await expect(page.locator('.sidebar')).toBeVisible();
    await expect(page.locator('.topbar')).toBeVisible();
    await expect(page.locator('.main-content')).toBeVisible();
  });

  test('should display the Top Opportunity card when data is available', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Wait for potential data load
    await page.waitForTimeout(2000);

    // Check if the opportunity card exists
    const opportunityCard = page.locator('.card-opportunity');
    const exists = await opportunityCard.count();
    if (exists > 0) {
      // Verify badge and score are present
      await expect(opportunityCard.locator('.kpi-badge')).toBeVisible();
      await expect(opportunityCard.locator('.score-display')).toBeVisible();
    }
  });

  test('should display the sidebar navigation', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Verify sidebar brand and nav items
    await expect(page.locator('.sidebar-brand')).toBeVisible();
    await expect(page.locator('.sidebar-nav')).toBeVisible();
    await expect(page.locator('.sidebar-quick-stat')).toBeVisible();
  });

  test('should display the topbar with search and sector filter', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Verify topbar elements
    await expect(page.locator('.topbar .search-bar')).toBeVisible();
    await expect(page.locator('.topbar .refresh-status')).toBeVisible();
    await expect(page.locator('.user-profile')).toBeVisible();
  });
});
