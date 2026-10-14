import { test, expect } from '@playwright/test';
import { toCents, currency } from '../../money.js';
import { mkdir, readFile } from 'node:fs/promises';

test('live portfolio, validated withdrawal, refreshed history and filtered CSV', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await page.goto('/');
  await expect(page.locator('#connection')).toHaveText('Connected');
  const portfolio = await (await page.request.get('/api/investors/1/portfolio')).json();
  const product = portfolio.products.find(item => item.type === 'DISCRETIONARY');
  await expect(page.locator('#total-balance')).toContainText('R ');
  await expect(page.locator('.product-card')).toHaveCount(portfolio.products.length);
  await mkdir('screenshots', { recursive: true });
  await page.screenshot({ path: 'screenshots/overview-desktop.png', fullPage: true });

  await page.getByRole('link', { name: 'Withdrawals', exact: true }).click();
  await page.locator('#product-picker').selectOption(String(product.id));
  await page.locator('#amount').fill('0.001');
  await page.locator('#review-withdrawal').click();
  await expect(page.locator('#amount-error')).toBeVisible();
  await expect(page.locator('#confirm-dialog')).not.toBeVisible();
  await page.locator('#amount').fill('1.00');
  await page.screenshot({ path: 'screenshots/withdrawals-desktop.png', fullPage: true });
  await page.locator('#review-withdrawal').click();
  await expect(page.locator('#confirm-dialog')).toBeVisible();
  await expect(page.locator('#confirm-amount')).toHaveText('R 1.00');
  const completedRequest = page.waitForResponse(response => response.url().endsWith('/withdrawals') && response.request().method() === 'POST');
  await page.locator('#confirm-withdrawal').click();
  expect((await completedRequest).status()).toBe(201);
  await expect(page.locator('#confirm-dialog')).not.toBeVisible();
  await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(page.locator('#form-balance')).toHaveText(currency(toCents(String(product.balance)) - 100n));
  await expect(page.locator('#history-body tr')).not.toHaveCount(0);
  await expect(page.locator('#toast-message')).toContainText('accepted');
  await page.locator('#dismiss-toast').click();

  await page.getByRole('link', { name: 'Statements', exact: true }).click();
  const history = await (await page.request.get('/api/investors/1/withdrawals')).json();
  const today = history[0].createdAt.slice(0, 10);
  await page.locator('#date-from').fill(today);
  await page.locator('#date-to').fill(today);
  await expect(page.locator('#statement-error')).not.toBeVisible();
  await page.screenshot({ path: 'screenshots/statements-desktop.png', fullPage: true });
  const downloadEvent = page.waitForEvent('download');
  const csvResponseEvent = page.waitForResponse(response => response.url().includes('/withdrawals/export?'));
  await page.locator('#download-csv').click();
  const download = await downloadEvent;
  expect(download.suggestedFilename()).toBe('enviro365-withdrawals-1.csv');
  const csvResponse = await csvResponseEvent;
  expect(csvResponse.status()).toBe(200);
  const downloadedCsv = await readFile(await download.path(), 'utf8');
  expect(downloadedCsv).toContain('id,investorId,productId,productName,amount,createdAt,status');
  expect(downloadedCsv).toContain(',1.00,');
  await page.locator('#date-from').fill('2099-01-02');
  await page.locator('#date-to').fill('2099-01-01');
  await expect(page.locator('#statement-error')).toBeVisible();
  await expect(page.locator('#download-csv')).toBeDisabled();
  await page.locator('#clear-dates').click();
  await expect(page.locator('#download-csv')).toBeEnabled();
  expect(errors).toEqual([]);
});

test('age-restricted investor receives clear feedback and cannot submit retirement withdrawal', async ({ page }) => {
  await page.goto('/#withdrawals');
  await expect(page.locator('#connection')).toHaveText('Connected');
  await page.locator('#investor-picker').selectOption('2');
  await expect(page.locator('#connection')).toHaveText('Connected');
  const portfolio = await (await page.request.get('/api/investors/2/portfolio')).json();
  const retirement = portfolio.products.find(item => item.type === 'RETIREMENT');
  await page.locator('#product-picker').selectOption(String(retirement.id));
  await expect(page.locator('#eligibility-note')).toContainText('older than 65');
  await expect(page.locator('#review-withdrawal')).toBeDisabled();
  await expect(page.locator('#use-maximum')).toBeDisabled();
});

test('mobile layout fits the viewport and provides accessible navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.locator('#connection')).toHaveText('Connected');
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: 'screenshots/overview-mobile.png', fullPage: true });
  await page.getByRole('link', { name: 'Withdrawals', exact: true }).click();
  await expect(page.locator('#withdrawal-form')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.getByRole('link', { name: 'Statements', exact: true }).click();
  await expect(page.locator('#download-csv')).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('service outage shows an actionable error and refresh recovers', async ({ page }) => {
  await page.route('**/api/investors/**', route => route.fulfill({
    status: 502, contentType: 'application/json',
    body: '{"error":"Investment service unavailable","details":{}}',
  }));
  await page.goto('/');
  await expect(page.locator('#connection')).toHaveText('Unavailable');
  await expect(page.locator('#global-error')).toContainText('Use Refresh');
  await expect(page.locator('#content')).not.toBeVisible();
  await page.unroute('**/api/investors/**');
  await page.locator('#refresh').click();
  await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(page.locator('#global-error')).not.toBeVisible();
});

test('CSV service errors stay visible instead of disappearing during button reset', async ({ page }) => {
  await page.goto('/#statements');
  await expect(page.locator('#connection')).toHaveText('Connected');
  await page.route('**/withdrawals/export*', route => route.fulfill({
    status: 500, contentType: 'application/json', body: '{"error":"Statement service unavailable"}',
  }));
  await page.locator('#download-csv').click();
  await expect(page.locator('#statement-error')).toHaveText('Statement service unavailable');
  await expect(page.locator('#download-csv')).toBeEnabled();
});
