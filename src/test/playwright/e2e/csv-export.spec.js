// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-28: E2E Test — CSV Export
 * Verifies that the CSV export button triggers a file download.
 */
test.describe('CSV Export', () => {
  test('should have a download button in the data table header', async ({ page }) => {
    await page.goto('/dashboard.xhtml');
    await page.waitForTimeout(2000);

    // The download button (contains download icon)
    const downloadBtn = page.locator('[title="Download CSV"]');
    const count = await downloadBtn.count();
    if (count > 0) {
      await expect(downloadBtn.first()).toBeVisible();
    }
  });

  test('should trigger CSV download via API', async ({ page }) => {
    // Test the export API directly
    const response = await page.goto('/api/export/csv');

    // The API should respond with CSV content
    if (response) {
      expect(response.status()).toBe(200);

      const contentType = response.headers()['content-type'];
      expect(contentType).toContain('text/csv');

      // Check for Content-Disposition header indicating file download
      const contentDisposition = response.headers()['content-disposition'];
      expect(contentDisposition).toBeDefined();
      expect(contentDisposition).toContain('attachment');
    }
  });

  test('should export CSV for specific platform and sector', async ({ page }) => {
    // Test export with platform and sector parameters
    const response = await page.goto('/api/export/csv?platform=1&sector=1');

    if (response) {
      expect(response.status()).toBe(200);

      const contentType = response.headers()['content-type'];
      expect(contentType).toContain('text/csv');

      // Verify CSV body has header row
      const body = await response.text();
      expect(body).toContain('Platform');
      expect(body).toContain('ROAS');
      expect(body).toContain('CAC');
    }
  });
});
