import { toCents, decimal, currency, maximum, validateWithdrawal, parseApiJson, dateBounds, filteredHistory } from './money.js';
import { login, register, logout, restoreSession, sessionRequest, onSessionChange, currentUser } from './auth.js';
import { validIdentity, validCard } from './identity.js';

const $ = id => document.getElementById(id);
const colors = ['#3a8e75', '#b99a63', '#6994ab', '#8c82ad'];
const labels = { RETIREMENT: 'Retirement', DISCRETIONARY: 'Discretionary', SAVINGS: 'Savings', TAX_FREE: 'Tax-free' };
const icons = {
  grid: '<rect x="3" y="3" width="7" height="7" rx="1.5"/><rect x="14" y="3" width="7" height="7" rx="1.5"/><rect x="3" y="14" width="7" height="7" rx="1.5"/><rect x="14" y="14" width="7" height="7" rx="1.5"/>',
  arrow: '<path d="M7 17 17 7M7 7h10v10"/><path d="M4 4v16h16"/>',
  document: '<path d="M14 3H6a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V9Z"/><path d="M14 3v6h6M8 13h8M8 17h5"/>',
  refresh: '<path d="M20 7v5h-5M4 17v-5h5"/><path d="M6 7a7 7 0 0 1 12-1l2 6M4 12l2 6a7 7 0 0 0 12-1"/>',
  wallet: '<path d="M20 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h13a2 2 0 0 0 2-2v-3"/><path d="M3 7h17v9h-6V8"/><path d="M16 12h.01"/>',
  leaf: '<path d="M20 4c0 10-2 15-9 15a7 7 0 0 1-7-7C4 5 11 4 20 4Z"/><path d="m4 21 11-11M9 16h5"/>',
  layers: '<path d="m12 3 10 5-10 5L2 8Zm-10 9 10 5 10-5M2 17l10 5 10-5"/>',
  check: '<path d="m8 12 3 3 5-6"/><circle cx="12" cy="12" r="9"/>',
  chevron: '<path d="m9 5 7 7-7 7"/>',
  download: '<path d="M12 3v12m-5-5 5 5 5-5M4 16v4a1 1 0 0 0 1 1h14a1 1 0 0 0 1-1v-4"/>',
};
function icon(name) {
  return '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">' + (icons[name] || icons.layers) + '</svg>';
}
function hydrateIcons(parent = document) {
  parent.querySelectorAll('[data-icon]').forEach(element => { element.innerHTML = icon(element.dataset.icon); });
}
function escape(value) {
  return String(value).replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' })[char]);
}
const state = { investorId: '1', portfolio: null, history: [], view: 'overview', submitting: false, loading: false, controller: null, pending: null };
let toastTimer;
function toast(message) {
  clearTimeout(toastTimer);
  $('toast-message').textContent = message;
  $('toast').hidden = false;
  toastTimer = setTimeout(() => { $('toast').hidden = true; }, 8000);
}
function notice(element, message) {
  element.textContent = message;
  element.hidden = !message;
}
function selectedProduct() {
  return state.portfolio?.products.find(product => String(product.id) === $('product-picker').value);
}
function eligible(product) {
  return !(product.type === 'RETIREMENT' && state.portfolio.investor.age <= 65);
}
function dateDisplay(value) {
  // API timestamps are local Johannesburg times; the explicit offset avoids the browser's local zone.
  const date = new Date(value + '+02:00');
  return new Intl.DateTimeFormat('en-ZA', { day: '2-digit', month: 'short', year: 'numeric',
    hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Johannesburg' }).format(date);
}
async function api(path, options = {}, signal) {
  let response;
  try {
    response = await sessionRequest('/api/investors/' + state.investorId + path, { ...options,
      signal: signal || AbortSignal.timeout(18000) });
  } catch (error) {
    if (error.name === 'AbortError') throw error;
    throw new Error('We couldn’t reach your investment service. Check your connection and try again.');
  }
  const text = await response.text();
  let body;
  try { body = parseApiJson(text); } catch { throw new Error('The investment service returned an unreadable response. Please try again.'); }
  if (!response.ok) {
    const fields = Object.values(body.details || {});
    throw new Error(fields.length ? fields.join(' ') : body.error || 'Your request could not be completed.');
  }
  return body;
}
function setControls() {
  $('investor-picker').disabled = true;
  $('logout').disabled = state.submitting;
  $('refresh').disabled = state.loading || state.submitting;
  $('confirm-withdrawal').disabled = state.submitting;
  $('cancel-withdrawal').disabled = state.submitting;
  $('close-dialog').disabled = state.submitting;
  $('confirm-withdrawal').textContent = state.submitting ? 'Submitting…' : 'Confirm withdrawal';
  $('product-picker').disabled = state.submitting;
  $('amount').disabled = state.submitting;
  const product = selectedProduct();
  $('review-withdrawal').disabled = state.submitting || !product || !eligible(product);
  $('use-maximum').disabled = state.submitting || !product || !eligible(product) || maximum(toCents(product.balance)) === 0n;
}
async function loadPortfolio() {
  state.controller?.abort();
  const controller = new AbortController();
  state.controller = controller;
  state.loading = true;
  $('loading').hidden = false;
  $('content').hidden = true;
  notice($('global-error'), '');
  $('connection').classList.remove('offline');
  $('connection').innerHTML = '<i></i>Updating';
  setControls();
  try {
    const [portfolio, history] = await Promise.all([
      api('/portfolio', {}, controller.signal), api('/withdrawals', {}, controller.signal),
    ]);
    if (controller.signal.aborted) return;
    state.portfolio = portfolio;
    state.history = history;
    renderPortfolio();
    $('content').hidden = false;
    $('connection').innerHTML = '<i></i>Connected';
    $('last-updated').textContent = 'Updated ' + new Intl.DateTimeFormat('en-ZA', {
      hour: '2-digit', minute: '2-digit', timeZone: 'Africa/Johannesburg' }).format(new Date()) + ' SAST';
  } catch (error) {
    if (controller.signal.aborted) return;
    state.portfolio = null;
    state.history = [];
    $('connection').innerHTML = '<i></i>Unavailable';
    $('connection').classList.add('offline');
    notice($('global-error'), error.message + ' Use Refresh to try again.');
  } finally {
    if (state.controller === controller) {
      state.loading = false;
      $('loading').hidden = true;
      setControls();
    }
  }
}
function renderPortfolio() {
  const { investor, products } = state.portfolio;
  $('avatar').textContent = (investor.firstName[0] + investor.lastName[0]).toUpperCase();
  const total = products.reduce((sum, product) => sum + toCents(product.balance), 0n);
  const limit = products.filter(eligible).reduce((sum, product) => sum + maximum(toCents(product.balance)), 0n);
  $('total-balance').textContent = currency(total);
  $('hero-description').textContent = 'Across ' + products.length + ' investment' + (products.length === 1 ? '' : 's') + ' · ' + investor.firstName + ' ' + investor.lastName;
  $('product-count').textContent = products.length;
  $('eligible-limit').textContent = currency(limit);
  $('withdrawal-count').textContent = state.history.length;
  $('donut-count').textContent = products.length;
  let position = 0;
  const stops = products.map((product, index) => {
    const share = total ? Number(toCents(product.balance) * 10000n / total) / 100 : 0;
    const start = position;
    position = index === products.length - 1 && total ? 100 : position + share;
    return colors[index % colors.length] + ' ' + start + '% ' + position + '%';
  });
  $('allocation-donut').style.background = total ? 'conic-gradient(' + stops.join(',') + ')' : '#e7eded';
  $('allocation-legend').innerHTML = products.length ? products.map((product, index) => {
    const share = total ? Number(toCents(product.balance) * 10000n / total) / 100 : 0;
    return '<div class="legend-row"><span class="legend-dot" style="background:' + colors[index % colors.length] + '"></span><div>' + escape(labels[product.type] || product.type) + '<small>' + escape(currency(toCents(product.balance))) + '</small></div><strong>' + share.toFixed(1) + '%</strong></div>';
  }).join('') : '<p class="muted">No investments yet.</p>';
  $('product-grid').innerHTML = products.length ? products.map((product, index) => {
    const share = total ? Number(toCents(product.balance) * 10000n / total) / 100 : 0;
    const allowed = eligible(product);
    return '<article class="card product-card"><div class="product-top"><span class="product-icon ' + (index % 2 ? 'gold' : '') + '">' + icon(product.type === 'RETIREMENT' ? 'leaf' : 'layers') + '</span><div><h3>' + escape(product.name) + '</h3><p>' + escape(labels[product.type] || product.type) + ' investment</p></div><span class="product-type">ZAR</span></div><p class="product-balance-label">Current balance</p><p class="product-balance">' + escape(currency(toCents(product.balance))) + '</p><div class="product-bar"><span style="width:' + share + '%;background:' + colors[index % colors.length] + '"></span></div><div class="product-share"><span>Share of your portfolio</span><span>' + share.toFixed(1) + '%</span></div><div class="product-footer"><span class="eligibility-tag ' + (allowed ? '' : 'restricted') + '"><i></i>' + (allowed ? 'Available to withdraw' : 'Retirement age restriction') + '</span><button class="text-button" data-product-id="' + product.id + '" ' + (!allowed ? 'disabled' : '') + '>Withdraw ' + icon('chevron') + '</button></div></article>';
  }).join('') : '<article class="card empty-state"><h3>No investments in this portfolio.</h3><p>Your investments will appear here when available.</p></article>';
  const previousProduct = $('product-picker').value;
  $('product-picker').innerHTML = products.map(product => '<option value="' + product.id + '">' + escape(product.name) + (eligible(product) ? '' : ' · Age restricted') + '</option>').join('');
  $('product-picker').value = products.some(product => String(product.id) === previousProduct)
    ? previousProduct : String((products.find(eligible) || products[0])?.id || '');
  $('history-count').textContent = state.history.length + ' REQUEST' + (state.history.length === 1 ? '' : 'S');
  renderTable($('history-body'), $('history-empty'), state.history);
  $('recent-activity').innerHTML = state.history.length ? state.history.slice(0, 3).map(item =>
    '<div class="activity-row"><span class="activity-icon">' + icon('arrow') + '</span><div><h3>' + escape(item.productName) + '</h3><p>Withdrawal completed · ' + escape(dateDisplay(item.createdAt)) + '</p></div><strong>' + escape(currency(toCents(item.amount))) + '</strong></div>').join('')
    : '<div class="empty-state"><span>' + icon('document') + '</span><h3>A fresh start for your activity.</h3><p>Your completed withdrawals will appear here.</p></div>';
  updateWithdrawal();
  updateStatement();
}
function renderTable(body, empty, items) {
  body.innerHTML = items.map(item => '<tr><td>' + escape(item.productName) + '</td><td>' + escape(dateDisplay(item.createdAt)) + '</td><td class="amount-cell">' + escape(currency(toCents(item.amount))) + '</td><td><span class="status-pill">' + escape(item.status === 'COMPLETED' ? 'Completed' : item.status) + '</span></td><td class="reference">WD-' + String(item.id).padStart(5, '0') + '</td></tr>').join('');
  empty.hidden = items.length > 0;
}
function updateWithdrawal(showError = false) {
  const product = selectedProduct();
  $('form-balance').textContent = product ? currency(toCents(product.balance)) : '—';
  $('form-maximum').textContent = product && eligible(product) ? currency(maximum(toCents(product.balance))) : 'Not eligible';
  notice($('eligibility-note'), product && !eligible(product) ? 'This retirement investment requires an investor older than 65. You can select another eligible investment.' : '');
  const value = $('amount').value.trim();
  const error = validateWithdrawal(product, state.portfolio?.investor.age, value);
  notice($('amount-error'), (showError || value) && error ? error : '');
  $('amount').setAttribute('aria-invalid', String(Boolean((showError || value) && error)));
  $('amount').parentElement.classList.toggle('invalid', Boolean((showError || value) && error));
  $('projected-balance').textContent = product && !error ? currency(toCents(product.balance) - toCents(value)) : product ? currency(toCents(product.balance)) : '—';
  setControls();
}
function updateStatement() {
  if (!state.portfolio) return;
  let filtered;
  try {
    const bounds = dateBounds($('date-from').value, $('date-to').value);
    filtered = filteredHistory(state.history, '', bounds.from, bounds.to);
    notice($('statement-error'), '');
    $('download-csv').disabled = false;
  } catch (error) {
    notice($('statement-error'), error.message);
    $('download-csv').disabled = true;
    filtered = [];
  }
  $('statement-count').textContent = filtered.length;
  $('statement-total').textContent = currency(filtered.reduce((sum, item) => sum + toCents(item.amount), 0n));
  renderTable($('statement-body'), $('statement-empty'), filtered);
}
function setView() {
  const view = location.hash.slice(1);
  state.view = ['overview', 'withdrawals', 'statements'].includes(view) ? view : 'overview';
  const headings = {
    overview: ['YOUR FINANCIAL PICTURE', 'A clear view of what’s yours.', 'Your investments, thoughtfully brought together.'],
    withdrawals: ['MOVE FORWARD WITH CONFIDENCE', 'Your next move starts here.', 'A clear, considered approach to your withdrawals.'],
    statements: ['EVERY DETAIL, IN ONE PLACE', 'Your records. Clearly organised.', 'Review your withdrawal activity and download a statement.'],
  };
  const [eyebrow, title, subtitle] = headings[state.view];
  $('page-eyebrow').textContent = eyebrow;
  $('page-title').textContent = title;
  $('page-subtitle').textContent = subtitle;
  $('breadcrumb').textContent = state.view[0].toUpperCase() + state.view.slice(1);
  document.querySelectorAll('.view').forEach(viewElement => { viewElement.hidden = viewElement.id !== state.view + '-view'; });
  document.querySelectorAll('.nav-link').forEach(link => {
    const active = link.dataset.view === state.view;
    link.classList.toggle('active', active);
    if (active) link.setAttribute('aria-current', 'page'); else link.removeAttribute('aria-current');
  });
}
$('refresh').addEventListener('click', loadPortfolio);
$('investor-picker').addEventListener('change', () => {
  state.investorId = $('investor-picker').value;
  state.portfolio = null;
  state.history = [];
  $('amount').value = '';
  $('product-picker').innerHTML = '';
  loadPortfolio();
});
$('product-picker').addEventListener('change', () => { $('amount').value = ''; updateWithdrawal(); });
$('amount').addEventListener('input', () => updateWithdrawal());
$('use-maximum').addEventListener('click', () => {
  const product = selectedProduct();
  if (product && eligible(product)) {
    $('amount').value = decimal(maximum(toCents(product.balance)));
    updateWithdrawal(true);
    $('amount').focus();
  }
});
$('product-grid').addEventListener('click', event => {
  const button = event.target.closest('[data-product-id]');
  if (!button || button.disabled) return;
  $('product-picker').value = button.dataset.productId;
  $('amount').value = '';
  updateWithdrawal();
  location.hash = 'withdrawals';
  requestAnimationFrame(() => $('amount').focus());
});
$('withdrawal-form').addEventListener('submit', event => {
  event.preventDefault();
  if (state.submitting || !state.portfolio) return;
  const product = selectedProduct();
  const error = validateWithdrawal(product, state.portfolio.investor.age, $('amount').value.trim());
  updateWithdrawal(true);
  if (error) { $('amount').focus(); return; }
  const amount = toCents($('amount').value.trim());
  state.pending = { investorId: state.investorId, productId: product.id, amount: decimal(amount) };
  $('confirm-product').textContent = product.name;
  $('confirm-amount').textContent = currency(amount);
  $('confirm-remaining').textContent = currency(toCents(product.balance) - amount);
  notice($('confirm-error'), '');
  $('confirm-dialog').showModal();
});
$('cancel-withdrawal').addEventListener('click', () => { if (!state.submitting) $('confirm-dialog').close(); });
$('confirm-dialog').addEventListener('cancel', event => { if (state.submitting) event.preventDefault(); });
$('confirm-dialog').addEventListener('close', () => { if (!state.submitting) state.pending = null; });
$('confirm-withdrawal').addEventListener('click', async () => {
  if (state.submitting || !state.pending || state.pending.investorId !== state.investorId) return;
  state.submitting = true;
  setControls();
  notice($('confirm-error'), '');
  let accepted = false;
  try {
    const { productId, amount } = state.pending;
    // The validated decimal is serialized as a JSON number without converting it to a JS float.
    await api('/withdrawals', { method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: '{"productId":' + productId + ',"amount":' + amount + '}' });
    accepted = true;
    state.submitting = false;
    state.pending = null;
    $('confirm-dialog').close();
    $('amount').value = '';
    toast('Withdrawal accepted. Your balance and activity are being updated.');
    await loadPortfolio();
  } catch (error) {
    notice($('confirm-error'), error.message + (accepted ? ' Please refresh your portfolio.' : ''));
  } finally {
    state.submitting = false;
    setControls();
  }
});
for (const id of ['date-from', 'date-to']) $(id).addEventListener('change', updateStatement);
$('clear-dates').addEventListener('click', () => { $('date-from').value = ''; $('date-to').value = ''; updateStatement(); });
$('statement-form').addEventListener('submit', async event => {
  event.preventDefault();
  if (!state.portfolio) return;
  let bounds;
  try { bounds = dateBounds($('date-from').value, $('date-to').value); } catch (error) { notice($('statement-error'), error.message); return; }
  const params = new URLSearchParams();
  const statementInvestorId = state.investorId;
  if (bounds.from) params.set('from', bounds.from);
  if (bounds.to) params.set('to', bounds.to);
  $('download-csv').disabled = true;
  try {
    const response = await sessionRequest('/api/investors/' + statementInvestorId + '/withdrawals/export' + (params.size ? '?' + params : ''), { signal: AbortSignal.timeout(18000) });
    if (!response.ok) {
      let body = {};
      try { body = await response.json(); } catch { /* Use the readable fallback. */ }
      throw new Error(body.error || 'Your statement could not be downloaded.');
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement('a');
    anchor.href = url;
    anchor.download = 'enviro365-withdrawals-' + statementInvestorId + '.csv';
    document.body.append(anchor);
    anchor.click();
    anchor.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
    notice($('statement-error'), '');
    toast('Your CSV statement is ready. Check your downloads.');
  } catch (error) {
    notice($('statement-error'), error.message || 'We couldn’t download your statement. Please try again.');
  } finally {
    try {
      dateBounds($('date-from').value, $('date-to').value);
      $('download-csv').disabled = false;
    } catch {
      $('download-csv').disabled = true;
    }
  }
});
$('dismiss-toast').addEventListener('click', () => { $('toast').hidden = true; clearTimeout(toastTimer); });
window.addEventListener('hashchange', setView);
$('copyright-year').textContent = new Date().getFullYear();
hydrateIcons();
setView();
setupAuthentication();
function setupAuthentication() {
  let activeTab = 'login';
  function tab(name) {
    activeTab = name;
    $('login-form').hidden = name !== 'login';
    $('register-form').hidden = name !== 'register';
    $('login-tab').setAttribute('aria-selected', String(name === 'login'));
    $('register-tab').setAttribute('aria-selected', String(name === 'register'));
    $('auth-title').textContent = name === 'login' ? 'Welcome back.' : 'Your next chapter starts here.';
    $('auth-subtitle').textContent = name === 'login' ? 'Sign in to a clear view of your portfolio.' : 'Create an account, with your details protected.';
    notice($('auth-error'), '');
  }
  function busy(pending) {
    for (const id of ['login-submit', 'register-submit', 'demo-login', 'login-tab', 'register-tab']) $(id).disabled = pending;
    $('login-submit').textContent = pending ? 'Signing in…' : 'Sign in securely';
    $('register-submit').textContent = pending ? 'Creating your account…' : 'Create your account';
  }
  onSessionChange(user => {
    if (!user) {
      state.controller?.abort();
      state.portfolio = null;
      state.history = [];
      state.pending = null;
      state.submitting = false;
      if ($('confirm-dialog').open) $('confirm-dialog').close();
      if ($('profile-dialog').open) $('profile-dialog').close();
      $('workspace').hidden = true;
      $('auth-shell').hidden = false;
      $('toast').hidden = true;
      $('content').hidden = true;
      return;
    }
    const changed = state.investorId !== String(user.investorId) || !state.portfolio;
    state.investorId = String(user.investorId);
    $('investor-picker').innerHTML = '<option value="' + user.investorId + '">' + escape(user.firstName + ' ' + user.lastName) + '</option>';
    $('profile-email').textContent = user.maskedEmail;
    $('profile-id').textContent = user.maskedIdNumber || 'Demo account';
    $('profile-card').textContent = user.maskedCardNumber || 'No card reference';
    $('workspace').hidden = false;
    $('auth-shell').hidden = true;
    if (changed) {
      state.pending = null;
      if ($('confirm-dialog').open) $('confirm-dialog').close();
      if ($('profile-dialog').open) $('profile-dialog').close();
      state.portfolio = null;
      $('amount').value = '';
      $('product-picker').innerHTML = '';
      loadPortfolio();
    }
  });
  $('login-tab').addEventListener('click', () => tab('login'));
  $('register-tab').addEventListener('click', () => tab('register'));
  for (const id of ['login-form', 'register-form']) {
    $(id).addEventListener('input', () => notice($('auth-error'), ''));
  }
  for (const id of ['login-tab', 'register-tab']) $(id).addEventListener('keydown', event => {
    if (event.key === 'ArrowLeft' || event.key === 'ArrowRight') {
      event.preventDefault();
      tab(activeTab === 'login' ? 'register' : 'login');
      $(activeTab + '-tab').focus();
    }
  });
  $('login-form').addEventListener('submit', async event => {
    event.preventDefault();
    busy(true);
    notice($('auth-error'), '');
    try {
      await login({ email: $('login-email').value.trim(), password: $('login-password').value });
      $('login-password').value = '';
    } catch (error) { notice($('auth-error'), error.message); }
    finally { $('login-password').value = ''; busy(false); }
  });
  $('demo-login').addEventListener('click', () => {
    $('login-email').value = 'alice@example.com';
    $('login-password').value = 'Enviro365!Demo2026';
    $('login-form').requestSubmit();
  });
  $('register-form').addEventListener('submit', async event => {
    event.preventDefault();
    notice($('auth-error'), '');
    const idNumber = $('register-id').value.trim();
    const birthDate = $('register-dob').value;
    const cardNumber = $('register-card').value.trim();
    const password = $('register-password').value;
    if (!validIdentity(idNumber, birthDate)) {
      notice($('auth-error'), 'Enter a valid South African ID number and matching date of birth.');
      $('register-id').focus();
      return;
    }
    if (!validCard(cardNumber)) {
      notice($('auth-error'), 'The card number failed the Luhn check. Check the digits or leave this optional field empty.');
      $('register-card').focus();
      return;
    }
    if (password.length < 12 || new TextEncoder().encode(password).length > 72 || password !== $('register-confirm').value) {
      notice($('auth-error'), 'Use matching passwords of at least 12 characters and no more than 72 UTF-8 bytes.');
      return;
    }
    busy(true);
    try {
      await register({
        firstName: $('register-firstname').value.trim(), lastName: $('register-lastname').value.trim(),
        email: $('register-email').value.trim(), password, idNumber, dateOfBirth: birthDate,
        cardNumber: cardNumber || null,
      });
      $('register-form').reset();
    } catch (error) {
      notice($('auth-error'), error.message);
    } finally {
      $('register-id').value = '';
      $('register-card').value = '';
      $('register-password').value = '';
      $('register-confirm').value = '';
      busy(false);
    }
  });
  $('profile-button').addEventListener('click', () => { if (currentUser()) $('profile-dialog').showModal(); });
  $('logout').addEventListener('click', async () => {
    $('logout').disabled = true;
    try {
      await logout();
      tab('login');
      $('login-password').value = '';
      $('register-form').reset();
      $('login-email').focus();
    } catch (error) { toast('Sign-out could not be confirmed. Please retry: ' + error.message); }
    finally { $('logout').disabled = false; }
  });
  $('auth-status').hidden = false;
  busy(true);
  restoreSession().finally(() => { $('auth-status').hidden = true; busy(false); });
}
