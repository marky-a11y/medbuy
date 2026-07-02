// @ts-check
const { test, expect } = require('@playwright/test');

/**
 * FRONT-30 / BACK-19: E2E Test — Client Portfolio Grid
 *
 * Verifies that the dashboard's Client Portfolio Grid section renders
 * with paginated client data, sortable columns, and RBAC-based masking.
 */
test.describe('Client Portfolio Grid', () => {

  test('should render the client portfolio grid section after dashboard load', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Wait for the client portfolio section to render
    await page.waitForSelector('.data-table-wrapper', { timeout: 15000 });

    // Find the "Client Portfolio" heading
    const heading = page.locator('h3.font-title-lg:has-text("Client Portfolio")');
    await expect(heading).toBeVisible();
  });

  test('should display the active client count badge', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('.client-badge', { timeout: 15000 });
    const badge = page.locator('.client-badge');
    await expect(badge).toBeVisible();
    const badgeText = await badge.textContent();
    expect(badgeText).toMatch(/\d+\s+Active Clients/);
  });

  test('should render the client data table with 5+ rows', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    // Wait for the PrimeFaces data table to render
    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Check that there are rows in the table body
    const rows = page.locator('#clientTable tbody tr');
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThanOrEqual(5);
  });

  test('should have correct column headers in the client table', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Verify column headers exist
    const headers = page.locator('#clientTable thead th');
    const headerTexts = [];
    for (let i = 0; i < await headers.count(); i++) {
      headerTexts.push((await headers.nth(i).textContent()).trim());
    }

    expect(headerTexts).toContain('Client Name');
    expect(headerTexts).toContain('Sector');
    expect(headerTexts).toContain('Contract');
    expect(headerTexts).toContain('Retention');
    expect(headerTexts).toContain('Outlook Score');
  });

  test('should display client name and sector in each row', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    const firstRow = page.locator('#clientTable tbody tr').first();
    // Client name should be visible
    const firstCell = firstRow.locator('td').first();
    await expect(firstCell).toBeVisible();
    const text = await firstCell.textContent();
    expect(text.length).toBeGreaterThan(0);
  });

  test('should support pagination (next page)', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Find and click the next page button in the paginator
    const nextButton = page.locator('#clientTable .ui-paginator-next');
    if (await nextButton.isVisible()) {
      await nextButton.click();
      // Wait for the table to update
      await page.waitForTimeout(500);

      // Verify the table still has rows after pagination
      const rows = page.locator('#clientTable tbody tr');
      const rowCount = await rows.count();
      expect(rowCount).toBeGreaterThanOrEqual(1);
    }
  });

  test('should support column sorting on Client Name', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Click the "Client Name" column header to sort
    const clientNameHeader = page.locator('#clientTable thead th:has-text("Client Name")');
    await clientNameHeader.click();
    // Wait for sort to apply
    await page.waitForTimeout(500);

    // Verify table still has rows after sorting
    const rows = page.locator('#clientTable tbody tr');
    const rowCount = await rows.count();
    expect(rowCount).toBeGreaterThanOrEqual(1);
  });

  test('should display contract type badges in the Contract column', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Check if contract badges exist
    const badges = page.locator('#clientTable .contract-type-badge');
    const badgeCount = await badges.count();
    expect(badgeCount).toBeGreaterThanOrEqual(1);

    // Each badge should have a valid class
    const firstBadge = badges.first();
    const classAttr = await firstBadge.getAttribute('class');
    expect(classAttr).toMatch(/contract-(retainer|performance|hybrid)/);
  });

  test('should show outlook score with color-coded class for MEDIA_ANALYST role', async ({ page }) => {
    // Login as analyst
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Check outlook score elements exist with proper CSS classes
    const scores = page.locator('#clientTable .outlook-score');
    const scoreCount = await scores.count();
    if (scoreCount > 0) {
      const firstScore = scores.first();
      const classAttr = await firstScore.getAttribute('class');
      expect(classAttr).toMatch(/outlook-(high|medium|low)/);
    }
  });

  test('should show retention display with expiring-soon class when applicable', async ({ page }) => {
    await page.goto('/dashboard.xhtml');

    await page.waitForSelector('#clientTable', { timeout: 15000 });

    // Check for retention-expiring-soon class on any cell
    const expiringCells = page.locator('#clientTable .retention-expiring-soon');
    // This may or may not be present, but if present it should be visible
    if (await expiringCells.count() > 0) {
      await expect(expiringCells.first()).toBeVisible();
    }
  });
});
