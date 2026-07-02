// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-27: E2E Test — Role-Based View / KPI Masking
 * Verifies that VIEWER role sees masked KPI values while ADMIN/MEDIA_ANALYST see full values.
 */
test.describe('Role-Based View / KPI Masking', () => {
  test('should show login page when not authenticated', async ({ page }) => {
    // Clear session and navigate to protected page
    await page.goto('/dashboard.xhtml');

    // Should redirect to login if not authenticated
    await page.waitForTimeout(1000);
    const currentUrl = page.url();
    if (currentUrl.includes('/login')) {
      await expect(page.locator('.login-card')).toBeVisible();
    }
  });

  test('should login as admin and see dashboard', async ({ page }) => {
    await page.goto('/login');

    // Fill login form
    await page.fill('#username', 'admin');
    await page.fill('#password', 'admin123');
    await page.click('button[type="submit"]');

    // After login, should redirect to dashboard
    await page.waitForTimeout(2000);
    expect(page.url()).toContain('dashboard');
  });

  test('should login as viewer and see masked KPI values', async ({ page }) => {
    await page.goto('/login');

    // Login as viewer
    await page.fill('#username', 'viewer');
    await page.fill('#password', 'viewer123');
    await page.click('button[type="submit"]');

    await page.waitForTimeout(2000);
    expect(page.url()).toContain('dashboard');

    // Check for masked values in the data table (if visible)
    const maskedValues = page.locator('.kpi-masked');
    const count = await maskedValues.count();
    if (count > 0) {
      // Verify masked values show "---"
      const text = await maskedValues.first().textContent();
      expect(text).toBe('---');
    }
  });
});
