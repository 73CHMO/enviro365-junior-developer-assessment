export function toCents(value) {
  const text = String(value).trim();
  if (!/^\d{1,17}(?:\.\d{1,2})?$/.test(text)) {
    throw new Error('Enter a valid amount with no more than two decimal places.');
  }
  const [whole, fraction = ''] = text.split('.');
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, '0'));
}

export function decimal(cents) {
  return (cents / 100n).toString() + '.' + (cents % 100n).toString().padStart(2, '0');
}

export function currency(cents) {
  return 'R ' + new Intl.NumberFormat('en-ZA', { maximumFractionDigits: 0 })
    .format(cents / 100n) + '.' + (cents % 100n).toString().padStart(2, '0');
}

export function maximum(balance) {
  return balance * 9n / 10n;
}

export function validateWithdrawal(product, age, value) {
  if (!product) return 'Choose an investment to withdraw from.';
  if (product.type === 'RETIREMENT' && age <= 65) {
    return 'Retirement withdrawals are available to investors older than 65.';
  }
  let amount;
  try { amount = toCents(value); } catch (error) { return error.message; }
  if (amount <= 0n) return 'Enter an amount of at least R 0.01.';
  const balance = toCents(product.balance);
  if (amount > balance) return 'This amount exceeds your available balance.';
  if (amount > maximum(balance)) return 'You can withdraw up to 90% of this investment’s current balance.';
  return '';
}

export function parseApiJson(text) {
  // Modern JSON.parse source context preserves financial numbers before floating-point conversion.
  return JSON.parse(text, (key, value, context) =>
    ['balance', 'amount'].includes(key) && typeof value === 'number'
      ? (context?.source || String(value)) : value);
}

export function dateBounds(from, to) {
  if (from && to && from > to) throw new Error('The start date must be on or before the end date.');
  return {
    from: from ? from + 'T00:00:00' : '',
    to: to ? to + 'T23:59:59.999999999' : '',
  };
}

export function filteredHistory(history, productId, from, to) {
  return history.filter(item =>
    (!productId || String(item.productId) === productId) &&
    (!from || item.createdAt >= from) &&
    (!to || item.createdAt <= to));
}
