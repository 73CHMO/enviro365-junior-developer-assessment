import { test, expect } from '@playwright/test';
import { toCents, currency } from '../../money.js';
import { mkdir, readFile } from 'node:fs/promises';
import { luhn } from '../../identity.js';

async function signIn(page, email = 'alice@example.com') {
  await page.goto('/');
  await expect(page.locator('#auth-status')).not.toBeVisible();
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill('Enviro365!Demo2026');
  await page.locator('#login-submit').click();
}

async function protectedJson(page, path) {
  return page.evaluate(async url => {
    const { sessionRequest } = await import('/auth.js');
    const response = await sessionRequest(url);
    if (!response.ok) throw new Error('Protected API check failed');
    return response.json();
  }, path);
}

test('live portfolio, validated withdrawal, refreshed history and filtered CSV', async ({ page }) => {
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  await signIn(page);
  await expect(page.locator('#connection')).toHaveText('Connected');
  const portfolio = await protectedJson(page, '/api/investors/1/portfolio');
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
  const history = await protectedJson(page, '/api/investors/1/withdrawals');
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
  await signIn(page, 'thabo@example.com');
  await expect(page.locator('#connection')).toHaveText('Connected');
  await page.getByRole('link', { name: 'Withdrawals', exact: true }).click();
  const portfolio = await protectedJson(page, '/api/investors/2/portfolio');
  const retirement = portfolio.products.find(item => item.type === 'RETIREMENT');
  await page.locator('#product-picker').selectOption(String(retirement.id));
  await expect(page.locator('#eligibility-note')).toContainText('older than 65');
  await expect(page.locator('#review-withdrawal')).toBeDisabled();
  await expect(page.locator('#use-maximum')).toBeDisabled();
});

test('mobile layout fits the viewport and provides accessible navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page);
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
  await signIn(page);
  await expect(page.locator('#connection')).toHaveText('Unavailable');
  await expect(page.locator('#global-error')).toContainText('Use Refresh');
  await expect(page.locator('#content')).not.toBeVisible();
  await page.unroute('**/api/investors/**');
  await page.locator('#refresh').click();
  await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(page.locator('#global-error')).not.toBeVisible();
});

test('CSV service errors stay visible instead of disappearing during button reset', async ({ page }) => {
  await signIn(page);
  await expect(page.locator('#connection')).toHaveText('Connected');
  await page.getByRole('link', { name: 'Statements', exact: true }).click();
  await page.route('**/withdrawals/export*', route => route.fulfill({
    status: 500, contentType: 'application/json', body: '{"error":"Statement service unavailable"}',
  }));
  await page.locator('#download-csv').click();
  await expect(page.locator('#statement-error')).toHaveText('Statement service unavailable');
  await expect(page.locator('#download-csv')).toBeEnabled();
});

test('registration masks references, restores sign-in, protects ownership and revokes on sign-out', async ({ page, context }) => {
  await page.goto('/');
  await expect(page.locator('#auth-status')).not.toBeVisible();
  await mkdir('screenshots', { recursive: true });
  await page.screenshot({ path: 'screenshots/login-desktop.png', fullPage: true });
  await page.locator('#register-tab').click();
  await expect(page.locator('#register-id')).toHaveAttribute('type', 'password');
  await expect(page.locator('#register-card')).toHaveAttribute('type', 'password');
  const month = String(1 + Math.floor(Math.random() * 12)).padStart(2, '0');
  const day = String(1 + Math.floor(Math.random() * 28)).padStart(2, '0');
  const birthDate = '1990-' + month + '-' + day;
  const base = '90' + month + day + String(Math.floor(Math.random() * 10000)).padStart(4, '0') + '08';
  const id = Array.from({ length: 10 }, (_, digit) => base + digit).find(luhn);
  const email = 'test-' + Date.now() + '@example.com';
  await page.locator('#register-firstname').fill('Test');
  await page.locator('#register-lastname').fill('Investor');
  await page.locator('#register-email').fill(email);
  await page.locator('#register-id').fill(id);
  await expect(page.locator('#register-dob')).toHaveValue(birthDate);
  await page.locator('#register-card').fill('4242424242424241');
  await page.locator('#register-password').fill('Unique!Passphrase2026');
  await page.locator('#register-confirm').fill('Unique!Passphrase2026');
  await page.locator('#register-submit').click();
  await expect(page.locator('#auth-error')).toContainText('Luhn');
  await page.locator('#register-card').fill('4242424242424242');
  await page.screenshot({ path: 'screenshots/register-desktop.png', fullPage: true });
  const registration = page.waitForResponse(response => response.url().endsWith('/auth/register'));
  await page.locator('#register-submit').click();
  const response = await registration;
  expect(response.status()).toBe(201);
  const result = await response.json();
  expect(result.user.maskedIdNumber).toBe('*********' + id.slice(-4));
  expect(result.user.maskedCardNumber).toBe('•••• •••• •••• 4242');
  expect(JSON.stringify(result.user)).not.toContain(id);
  expect(JSON.stringify(result.user)).not.toContain('4242424242424242');
  await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(page.locator('#product-count')).toHaveText('0');
  await expect(page.locator('#register-id')).toHaveValue('');
  await expect(page.locator('#register-card')).toHaveValue('');
  await page.locator('#profile-button').click();
  await expect(page.locator('#profile-id')).toHaveText('*********' + id.slice(-4));
  await expect(page.locator('#profile-card')).toHaveText('•••• •••• •••• 4242');
  await page.getByRole('button', { name: 'Close account details' }).click();
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
  const cookies = await context.cookies();
  const refresh = cookies.find(cookie => cookie.name === 'enviro-refresh');
  expect(refresh.httpOnly).toBe(true);
  expect(refresh.sameSite).toBe('Strict');
  expect(cookies.some(cookie => cookie.name === 'JSESSIONID')).toBe(false);
  expect((await page.request.get('/api/investors/' + result.user.investorId + '/portfolio')).status()).toBe(401);
  expect((await page.request.post('/api/auth/refresh')).status()).toBe(403);
  const forbidden = await page.evaluate(async () => {
    const { sessionRequest } = await import('/auth.js');
    return (await sessionRequest('/api/investors/1/portfolio')).status;
  });
  expect(forbidden).toBe(403);
  await page.reload();
  await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(page.locator('#investor-picker')).toHaveValue(String(result.user.investorId));
  await page.locator('#logout').click();
  await expect(page.locator('#auth-shell')).toBeVisible();
  await page.reload();
  await expect(page.locator('#auth-status')).not.toBeVisible();
  await expect(page.locator('#workspace')).not.toBeVisible();
  await page.locator('#login-email').fill(email);
  await page.locator('#login-password').fill('Unique!Passphrase2026');
  await page.locator('#login-submit').click();
  await expect(page.locator('#connection')).toHaveText('Connected');
});

test('two tabs coordinate refresh rotation without revoking a legitimate account', async ({ page, context }) => {
  await signIn(page);
  await expect(page.locator('#connection')).toHaveText('Connected');
  const second = await context.newPage();
  await second.goto('/');
  await expect(second.locator('#connection')).toHaveText('Connected');
  await Promise.all([page.reload(), second.reload()]);
  await expect(page.locator('#connection')).toHaveText('Connected');
  await expect(second.locator('#connection')).toHaveText('Connected');
  await page.locator('#logout').click();
  await expect(second.locator('#auth-shell')).toBeVisible();
  await second.close();
});

test('sign-in failure is generic and the mobile registration layout fits', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.locator('#auth-status')).not.toBeVisible();
  await page.locator('#login-email').fill('alice@example.com');
  await page.locator('#login-password').fill('Incorrect!Password2026');
  await page.locator('#login-submit').click();
  await expect(page.locator('#auth-error')).toHaveText('Email or password is incorrect');
  await page.locator('#register-tab').click();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'screenshots/register-mobile.png', fullPage: true });
});
