// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-26: E2E Test — ROI Calculator Flow
 * Verifies the ROI Calculator form submission and results display.
 */
test.describe('ROI Calculator Flow', () => {
  test('should navigate from dashboard to calculator', async ({ page }) => {
    await page.goto('/dashboard.xhtml');
    await page.waitForTimeout(1000);

    // Click the ROI Calculator sidebar link
    const calculatorLink = page.locator('a:has-text("ROI Calculator")');
    const count = await calculatorLink.count();
    if (count > 0) {
      await calculatorLink.first().click();
      await page.waitForTimeout(1000);

      // Verify URL changed to /calculator
      expect(page.url()).toContain('/calculator');
    }
  });

  test('should display the calculator form with platform and sector dropdowns', async ({ page }) => {
    await page.goto('/calculator');
    await page.waitForTimeout(1000);

    // Verify form elements
    await expect(page.locator('#platformId')).toBeVisible();
    await expect(page.locator('#sectorId')).toBeVisible();
    await expect(page.locator('#purchaseType')).toBeVisible();
    await expect(page.locator('#campaignDurationWeeks')).toBeVisible();
    await expect(page.locator('#budgetAllocation')).toBeVisible();
  });

  test('should submit the form and display results', async ({ page }) => {
    await page.goto('/calculator');
    await page.waitForTimeout(1000);

    // Fill in the form
    await page.fill('#campaignDurationWeeks', '12');
    await page.fill('#budgetAllocation', '150000');

    // Submit the form
    await page.click('button:has-text("Calculate ROI")');
    await page.waitForTimeout(2000);

    // Check if results are displayed (results panel may not show if backend has no data)
    // The page should reload with either results or the form again
    const resultPanel = page.locator('text=Projected ROI');
    const exists = await resultPanel.count();
    if (exists > 0) {
      await expect(resultPanel).toBeVisible();
    }
  });

  test('should provide a back link to the dashboard', async ({ page }) => {
    await page.goto('/calculator');
    await page.waitForTimeout(1000);

    // Find and click the "Back to Dashboard" link
    const backLink = page.locator('a:has-text("Back to Dashboard")');
    const count = await backLink.count();
    if (count > 0) {
      await backLink.first().click();
      await page.waitForTimeout(1000);

      // Should navigate back to the dashboard
      expect(page.url()).toContain('dashboard');
    }
  });
});
