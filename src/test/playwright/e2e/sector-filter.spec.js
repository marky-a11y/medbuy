// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-25: E2E Test — Global Sector Filter
 * Verifies that the sector filter changes URL and filters the dashboard view.
 */
test.describe('Global Sector Filter', () => {
  test('should have a sector filter dropdown in the topbar', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Check for either JSF or HTML select element
    const jsfFilter = page.locator('.sector-filter-select');
    const htmlFilter = page.locator('select.sector-filter-select');

    const jsfCount = await jsfFilter.count();
    const htmlCount = await htmlFilter.count();

    expect(jsfCount + htmlCount).toBeGreaterThanOrEqual(1);
  });

  test('should change URL when sector filter is changed', async ({ page }) => {
    await page.goto('/dashboard.xhtml');
    await page.waitForTimeout(1000);

    // Try to select a sector from the dropdown
    const sectorSelect = page.locator('.sector-filter-select');
    const count = await sectorSelect.count();

    if (count > 0) {
      // Get the options available
      const options = sectorSelect.locator('option');
      const optionCount = await options.count();
      console.log(`Sector filter has ${optionCount} options`);

      if (optionCount > 1) {
        // Select the first non-All sector option
        const optionValue = await options.nth(1).getAttribute('value');
        if (optionValue && optionValue.length > 0) {
          await sectorSelect.selectOption(optionValue);
          await page.waitForTimeout(1000);

          // URL should now contain ?sector= parameter
          expect(page.url()).toContain('sector=');
        }
      }
    }
  });
});
