import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = dirname(fileURLToPath(import.meta.url));
const port = Number(process.env.PORT || 4200);
const files = new Map([
  ['/', ['index.html', 'text/html; charset=utf-8']],
  ['/index.html', ['index.html', 'text/html; charset=utf-8']],
  ['/styles.css', ['styles.css', 'text/css; charset=utf-8']],
  ['/app.js', ['app.js', 'text/javascript; charset=utf-8']],
  ['/money.js', ['money.js', 'text/javascript; charset=utf-8']],
  ['/favicon.svg', ['favicon.svg', 'image/svg+xml']],
]);
const server = createServer(async (request, response) => {
  const requestUrl = new URL(request.url, 'http://localhost');
  const path = requestUrl.pathname;
  if (path.startsWith('/api/')) {
    if (!['GET', 'POST', 'OPTIONS'].includes(request.method)) {
      response.writeHead(405).end();
      return;
    }
    try {
      const chunks = [];
      let size = 0;
      for await (const chunk of request) {
        size += chunk.length;
        if (size > 16384) {
          response.writeHead(413).end('Request too large');
          return;
        }
        chunks.push(chunk);
      }
      const upstream = await fetch(new URL(path + requestUrl.search, 'http://127.0.0.1:8080'), {
        method: request.method,
        headers: { Accept: request.headers.accept || '*/*',
          ...(request.headers['content-type'] ? { 'Content-Type': request.headers['content-type'] } : {}) },
        body: request.method === 'POST' ? Buffer.concat(chunks) : undefined,
        signal: AbortSignal.timeout(15000),
      });
      const headers = {};
      for (const key of ['content-type', 'content-disposition', 'allow']) {
        if (upstream.headers.has(key)) headers[key] = upstream.headers.get(key);
      }
      response.writeHead(upstream.status, { ...headers, 'Cache-Control': 'no-store' });
      response.end(Buffer.from(await upstream.arrayBuffer()));
    } catch {
      response.writeHead(502, { 'Content-Type': 'application/json' });
      response.end(JSON.stringify({ error: 'The investment service is unavailable. Please start the backend on port 8080 and try again.', details: {} }));
    }
    return;
  }
  if (!['GET', 'HEAD'].includes(request.method)) {
    response.writeHead(405).end();
    return;
  }
  const entry = files.get(path);
  if (!entry) {
    response.writeHead(404).end('Page not found');
    return;
  }
  try {
    const content = await readFile(join(root, entry[0]));
    response.writeHead(200, {
      'Content-Type': entry[1],
      'Cache-Control': 'no-cache',
      'X-Content-Type-Options': 'nosniff',
      'Referrer-Policy': 'same-origin',
      'Content-Security-Policy': "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'",
    });
    response.end(request.method === 'HEAD' ? undefined : content);
  } catch {
    response.writeHead(500).end('Unable to load the application');
  }
});
server.listen(port, '127.0.0.1', () => console.log('Enviro365 frontend: http://localhost:' + port));
