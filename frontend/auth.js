let accessToken = null;
let user = null;
let csrf = null;
let refreshPromise = null;
const listeners = new Set();
const channel = typeof BroadcastChannel !== 'undefined' ? new BroadcastChannel('enviro365-account') : null;

export function onSessionChange(listener) { listeners.add(listener); }
export function currentUser() { return user; }

function setSession(session) {
  accessToken = session?.accessToken || null;
  user = session?.user || null;
  for (const listener of listeners) listener(user);
}

async function securityToken() {
  // Read a current token instead of keeping a stale per-tab CSRF cookie/header pair.
  const response = await fetch('/api/auth/csrf', { credentials: 'same-origin', signal: AbortSignal.timeout(15000) });
  if (!response.ok) throw new Error('Unable to prepare a protected request. Please try again.');
  csrf = await response.json();
  return csrf;
}

async function authPost(path, body) {
  const security = await securityToken();
  const response = await fetch('/api/auth/' + path, {
    method: 'POST', credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json', [security.headerName]: security.token },
    body: JSON.stringify(body || {}), signal: AbortSignal.timeout(18000),
  });
  if (!response.ok) {
    let result = {};
    try { result = await response.json(); } catch { /* No personal details in error fallback. */ }
    throw new Error(Object.values(result.details || {}).join(' ') || result.error || 'Please sign in again.');
  }
  return response.status === 204 ? null : response.json();
}

async function refresh() {
  if (!refreshPromise) {
    const perform = async () => {
      try {
        const session = await authPost('refresh');
        setSession(session);
        return true;
      } catch {
        setSession(null);
        return false;
      }
    };
    // Coordinate refresh-cookie rotation across tabs; concurrent replay would otherwise revoke the account.
    refreshPromise = (navigator.locks ? navigator.locks.request('enviro365-refresh', perform) : perform())
      .finally(() => { refreshPromise = null; });
  }
  return refreshPromise;
}

export async function restoreSession() { return refresh(); }
export async function login(credentials) {
  const session = await authPost('login', credentials);
  setSession(session);
  channel?.postMessage({ type: 'account-changed' });
}
export async function register(details) {
  const session = await authPost('register', details);
  setSession(session);
  channel?.postMessage({ type: 'account-changed' });
}
export async function logout() {
  // No Authorization header here: cookie-backed logout remains available after an access JWT expires.
  await authPost('logout');
  setSession(null);
  channel?.postMessage({ type: 'logout' });
}
export async function sessionRequest(url, options = {}) {
  const request = async () => {
    const headers = new Headers(options.headers);
    if (accessToken) headers.set('Authorization', 'Bearer ' + accessToken);
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes((options.method || 'GET').toUpperCase())) {
      const security = await securityToken();
      headers.set(security.headerName, security.token);
    }
    return fetch(url, { ...options, headers, credentials: 'same-origin' });
  };
  let response = await request();
  if (response.status === 401 && await refresh()) response = await request();
  return response;
}
if (channel) channel.onmessage = async event => {
  if (event.data?.type === 'logout') setSession(null);
  else if (event.data?.type === 'account-changed') await refresh();
};
