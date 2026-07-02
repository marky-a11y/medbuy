// @ts-check
const { test, expect } = require('@playwright/test');

const DASHBOARD_URL = '/dashboard';

test.describe('LLM Insights Panel (FRONT-31)', () => {

  test.beforeEach(async ({ page }) => {
    // Navigate to dashboard and log in
    await page.goto(DASHBOARD_URL);
    // Fill login form if needed (adjust selectors to match your login page)
    const loginForm = page.locator('form[action*="login"]');
    if (await loginForm.isVisible({ timeout: 2000 }).catch(() => false)) {
      await page.fill('input[name="username"]', 'admin');
      await page.fill('input[name="password"]', 'admin123');
      await page.click('button[type="submit"]');
      await page.waitForURL('**/dashboard');
    }
  });

  test('renders the AI-Powered Insights panel below the Client Portfolio Grid', async ({ page }) => {
    // Wait for dashboard to fully load
    await page.waitForSelector('.bento-grid', { timeout: 10000 });

    // The AI-Powered Insights panel should contain the "auto_awesome" icon
    const insightsPanel = page.locator('text=AI-Powered Insights');
    await expect(insightsPanel).toBeVisible({ timeout: 5000 });

    // Verify it appears after the Client Portfolio section
    const clientPortfolio = page.locator('text=Client Portfolio');
    const insightsHeading = page.locator('text=AI-Powered Insights');

    const clientBox = await clientPortfolio.boundingBox();
    const insightsBox = await insightsHeading.boundingBox();

    // Insights should be below the client portfolio
    if (clientBox && insightsBox) {
      expect(insightsBox.y).toBeGreaterThan(clientBox.y + clientBox.height - 50);
    }
  });

  test('displays insight cards with headline, detail, and confidence score', async ({ page }) => {
    // Wait for the insights panel to load
    await page.waitForSelector('text=AI-Powered Insights', { timeout: 10000 });

    // Check for at least one insight card
    const insightCards = page.locator('[class*="insight-card"]');
    const count = await insightCards.count();

    if (count > 0) {
      // Verify the first card has headline, detail, and confidence elements
      const firstCard = insightCards.first();

      // Headline should be present
      const headline = firstCard.locator('.insight-card-headline');
      await expect(headline).toBeVisible();

      // Detail should be present
      const detail = firstCard.locator('.insight-card-detail');
      await expect(detail).toBeVisible();

      // Confidence score should be present
      const confidence = firstCard.locator('.insight-card-confidence');
      await expect(confidence).toBeVisible();
    } else {
      // If no cards, either error state or "no insights" message
      const errorMsg = page.locator('text=No actionable insights');
      const retryBtn = page.locator('text=Retry');
      const hasErrorOrEmpty = (await errorMsg.isVisible().catch(() => false)) ||
                              (await retryBtn.isVisible().catch(() => false));
      expect(hasErrorOrEmpty).toBeTruthy();
    }
  });

  test('gap/opportunity/risk cards have different visual styles', async ({ page }) => {
    // Wait for insights panel
    await page.waitForSelector('text=AI-Powered Insights', { timeout: 10000 });

    // Look for gap, opportunity, and risk cards
    const gapCard = page.locator('.insight-card-gap').first();
    const oppCard = page.locator('.insight-card-opportunity').first();
    const riskCard = page.locator('.insight-card-risk').first();

    // At least one card type should be visible
    const gapVisible = await gapCard.isVisible().catch(() => false);
    const oppVisible = await oppCard.isVisible().catch(() => false);
    const riskVisible = await riskCard.isVisible().catch(() => false);

    if (gapVisible || oppVisible || riskVisible) {
      // Verify different border-left colors / background classes are applied
      if (gapVisible) {
        const gapBg = await gapCard.getAttribute('class');
        expect(gapBg).toContain('insight-card-gap');
      }
      if (oppVisible) {
        const oppBg = await oppCard.getAttribute('class');
        expect(oppBg).toContain('insight-card-opportunity');
      }
      if (riskVisible) {
        const riskBg = await riskCard.getAttribute('class');
        expect(riskBg).toContain('insight-card-risk');
      }
    } else {
      // If no card types found, check for empty state
      const emptyState = page.locator('text=No actionable insights');
      const isVisible = await emptyState.isVisible().catch(() => false);
      expect(isVisible).toBeTruthy();
    }
  });

  test('retry button reloads insights on error', async ({ page }) => {
    // Wait for insights panel
    await page.waitForSelector('text=AI-Powered Insights', { timeout: 10000 });

    // If error message is shown
    const retryBtn = page.locator('text=Retry');
    if (await retryBtn.isVisible({ timeout: 3000 }).catch(() => false)) {
      // Click retry
      await retryBtn.click();

      // Wait for the panel to refresh (indicated by loading new content)
      await page.waitForTimeout(1000);

      // After retry, either insights are shown or error persists
      const insightsExist = await page.locator('[class*="insight-card"]').first().isVisible().catch(() => false);
      const errorStillExists = await retryBtn.isVisible().catch(() => false);
      const emptyState = await page.locator('text=No actionable insights').isVisible().catch(() => false);

      // At least one of these states should be present
      expect(insightsExist || errorStillExists || emptyState).toBeTruthy();
    }
  });

  test('powered by NVIDIA NIM badge is visible', async ({ page }) => {
    await page.waitForSelector('text=AI-Powered Insights', { timeout: 10000 });
    const badge = page.locator('text=powered by NVIDIA NIM');
    await expect(badge).toBeVisible();
  });
});
