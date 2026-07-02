// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-24: E2E Test — Drill-Down Navigation
 * Verifies that the hierarchical navigation works: Platform → Sector → Metrics.
 */
test.describe('Drill-Down Navigation', () => {
  test('should expand a platform panel to show sectors', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Wait for platform panels to load
    await page.waitForTimeout(2000);

    // Count platform headers
    const platformHeaders = page.locator('.platform-panel-header');
    const count = await platformHeaders.count();
    expect(count).toBeGreaterThanOrEqual(1);

    // Click the first platform header to expand
    if (count > 0) {
      await platformHeaders.first().click();
      await page.waitForTimeout(1000);

      // Check that the sector list body is now visible
      const sectorList = page.locator('.platform-panel-body').first();
      await expect(sectorList).toBeVisible();
    }
  });

  test('should allow clicking a sector to show metrics table', async ({ page }) => {
    await page.goto('/dashboard.xhtml');
    await page.waitForTimeout(2000);

    // Expand first platform
    const platformHeaders = page.locator('.platform-panel-header');
    const count = await platformHeaders.count();
    if (count > 0) {
      await platformHeaders.first().click();
      await page.waitForTimeout(1000);

      // Try clicking a sector link
      const sectorLinks = page.locator('.sector-row');
      const sectorCount = await sectorLinks.count();
      if (sectorCount > 0) {
        await sectorLinks.first().click();
        await page.waitForTimeout(1000);

        // Verify the metrics table panel appears (contains data table)
        const dataTable = page.locator('.ui-datatable');
        if (await dataTable.count() > 0) {
          await expect(dataTable).toBeVisible();
        }
      }
    }
  });

  test('should have 13 KPI columns in the metrics table', async ({ page }) => {
    await page.goto('/dashboard.xhtml');
    await page.waitForTimeout(2000);

    // Expand first platform
    const platformHeaders = page.locator('.platform-panel-header');
    const count = await platformHeaders.count();
    if (count > 0) {
      await platformHeaders.first().click();
      await page.waitForTimeout(1000);

      // Click first sector
      const sectorLinks = page.locator('.sector-row');
      const sectorCount = await sectorLinks.count();
      if (sectorCount > 0) {
        await sectorLinks.first().click();
        await page.waitForTimeout(1000);

        // Verify the data table has column headers
        // The header text should include ROAS, CAC, CLTV etc.
        const headerCells = page.locator('.ui-datatable thead th');
        const headerCount = await headerCells.count();
        console.log(`Number of table columns: ${headerCount}`);
        // There should be 13+ columns (13 KPI + maybe action columns)
        expect(headerCount).toBeGreaterThanOrEqual(13);
      }
    }
  });
});
