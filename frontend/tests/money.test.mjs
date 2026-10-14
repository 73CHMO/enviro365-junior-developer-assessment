import test from 'node:test';
import assert from 'node:assert/strict';
import { toCents, decimal, currency, maximum, validateWithdrawal, parseApiJson, dateBounds, filteredHistory } from '../money.js';

test('money preserves cents beyond JavaScript safe integer precision', () => {
  const amount = toCents('99999999999999999.99');
  assert.equal(amount, 9999999999999999999n);
  assert.equal(decimal(amount), '99999999999999999.99');
  assert.match(currency(amount), /\.99$/);
});

test('API decimal tokens remain exact before floating-point conversion', () => {
  const parsed = parseApiJson('{"balance":99999999999999999.99,"amount":0.01,"id":1}');
  assert.equal(parsed.balance, '99999999999999999.99');
  assert.equal(parsed.amount, '0.01');
  assert.equal(parsed.id, 1);
});

test('fractional cents, exponent notation and excessive integer digits are rejected', () => {
  for (const value of ['0.001', '1e5', '-1', '', '100000000000000000.00', 'NaN']) {
    assert.throws(() => toCents(value));
  }
});

test('withdrawal limit never rounds upward', () => {
  assert.equal(maximum(toCents('0.03')), 2n);
  const product = { type: 'SAVINGS', balance: '100.00' };
  assert.equal(validateWithdrawal(product, 30, '90.00'), '');
  assert.match(validateWithdrawal(product, 30, '90.01'), /90%/);
  assert.match(validateWithdrawal(product, 30, '100.01'), /available balance/);
  assert.match(validateWithdrawal(product, 30, '0'), /at least/);
});

test('retirement age rule is strictly greater than 65', () => {
  const product = { type: 'RETIREMENT', balance: '100.00' };
  assert.match(validateWithdrawal(product, 64, '1'), /older than 65/);
  assert.match(validateWithdrawal(product, 65, '1'), /older than 65/);
  assert.equal(validateWithdrawal(product, 66, '1'), '');
});

test('date filters cover the full day including fractional seconds', () => {
  const bounds = dateBounds('2026-10-14', '2026-10-14');
  const history = [
    { productId: 1, createdAt: '2026-10-14T00:00:00', amount: '1.00' },
    { productId: 1, createdAt: '2026-10-14T23:59:59.999999', amount: '2.00' },
    { productId: 1, createdAt: '2026-10-15T00:00:00', amount: '3.00' },
  ];
  assert.equal(filteredHistory(history, '', bounds.from, bounds.to).length, 2);
  assert.throws(() => dateBounds('2026-10-15', '2026-10-14'));
  assert.deepEqual(dateBounds('', ''), { from: '', to: '' });
});

test('one-sided date filters preserve investor activity order', () => {
  const history = [
    { productId: 2, createdAt: '2026-10-15T12:00:00' },
    { productId: 1, createdAt: '2026-10-14T12:00:00' },
  ];
  assert.deepEqual(filteredHistory(history, '', '2026-10-15T00:00:00', ''), [history[0]]);
  assert.deepEqual(filteredHistory(history, '1', '', '2026-10-14T23:59:59.999999999'), [history[1]]);
});
