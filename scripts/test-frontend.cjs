// Regression checks execute the actual shipped scripts with a minimal DOM.
// No browser, dependencies, secrets, or network calls are required.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const { webcrypto, createHash } = require('node:crypto');
const path = require('node:path');

function client() {
  const elements = new Map(), storage = new Map();
  const element = id => {
    if (!elements.has(id)) elements.set(id, {
      hidden: true, disabled: false, value: '', innerHTML: '', textContent: '', dataset: {},
      classList: { add() {}, remove() {}, toggle() {} },
      handlers: {}, addEventListener(type, handler) { this.handlers[type] = handler; },
      setAttribute() {}, focus() {}, reset() {}, close() {},
      replaceChildren() { this.innerHTML = ''; }, querySelectorAll() { return []; },
    });
    return elements.get(id);
  };
  const context = vm.createContext({
    console, URL, Intl, Uint8Array, crypto: { getRandomValues: a => webcrypto.getRandomValues(a) },
    document: { getElementById: element, addEventListener() {}, querySelectorAll: () => [] },
    localStorage: { getItem: k => storage.get(k) ?? null, setItem: (k,v) => storage.set(k,v), removeItem: k => storage.delete(k) },
    window: { addEventListener() {}, scrollTo() {}, isSecureContext: true },
    location: { hash: '' }, history: { pushState() {} },
    setTimeout: () => 1, clearTimeout() {}, fetch: () => { throw new Error('Unexpected network call'); },
  });
  const run = source => vm.runInContext(source, context);
  for (const name of ['connections.js', 'app.js']) run(fs.readFileSync(path.join(__dirname, '../src/main/resources/static/js', name), 'utf8'));
  return { run, element, storage, context };
}

test('transaction request IDs remain unique when randomUUID is unavailable', () => {
  const { run } = client();
  const ids = run('Array.from({length:100}, newRequestId)');
  assert.equal(new Set(ids).size, 100);
  for (const id of ids) assert.match(id, /^[\da-f]{8}-[\da-f]{4}-4[\da-f]{3}-[89ab][\da-f]{3}-[\da-f]{12}$/);
});

test('manual asset names are escaped in content and HTML attributes', () => {
  const { run, element } = client();
  run(`renderCash([{id:1, currency:'KRW', name:'\"><img src=x onerror=alert(1)>', quantity:1, valuationKRW:1}])`);
  const html = element('cash-list').innerHTML;
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&quot;&gt;&lt;img'));
});

test('connected holdings escape provider names and distinguish missing cost', () => {
  const { run, element } = client();
  run(`connectedState.overview = {enabled:true, totalValueKRW:null, knownValueKRW:0,
    unvaluedHoldingCount:1, pendingConnectionCount:0, stale:false, allocation:[],
    connections:[{id:1,provider:'BINANCE_SPOT',status:'READY',lastSyncedAt:new Date().toISOString(),
      holdings:[{symbol:'BTC',name:'<img src=x onerror=alert(1)>',currency:'USDT',quantity:1,price:null,valuationKRW:null}]}]};
    renderConnected();`);
  const html = element('connected-content').innerHTML;
  assert.ok(!html.includes('<img'));
  assert.ok(html.includes('&lt;img'));
  assert.ok(html.includes('손익 -'));
  assert.ok(html.includes('시세 확인 필요'));
  assert.ok(html.includes('평가 불가 1종목'));
});

test('direct manual page navigation makes its initially hidden section visible', () => {
  const { run, element } = client();
  run("location.hash='#manual'; refresh = () => {}; enterApp();");
  assert.equal(element('overview-page').hidden, false);
  assert.equal(element('connected-page').hidden, true);
});

test('late token refresh cannot restore a logged-out session', async () => {
  const { run, context, storage } = client();
  storage.set('asset-dashboard.refresh-token', 'old-refresh');
  let resolve;
  context.fetch = () => new Promise(r => { resolve = r; });
  const pending = run('refreshAccessToken()');
  // Do not make a separate logout HTTP request in this fixture.
  storage.delete('asset-dashboard.refresh-token');
  run("$('cash-list').innerHTML='previous-user-balance'; $('dashboard-body').hidden=false;");
  run('logout()');
  resolve({ok:true,json:async () => ({accessToken:'late-token',refreshToken:'late-refresh',nickname:'old-user'})});
  assert.equal(await pending, false);
  assert.equal(storage.get('asset-dashboard.token'), undefined);
  assert.equal(run("$('cash-list').innerHTML"), '');
  assert.equal(run("$('dashboard-body').hidden"), true);
});

test('failed refresh requests never display a success notification', async () => {
  const { run, context } = client();
  context.messages = [];
  run("connectedState.overview={connections:[{id:1}]}; api=async()=>{throw new Error('갱신 제한');}; toast=m=>messages.push(m);");
  await run('refreshConnected()');
  assert.deepEqual(context.messages, ['갱신 제한']);
});

test('all executable scripts are local and the chart integrity matches its shipped bytes', () => {
  const root = path.join(__dirname, '../src/main/resources/static');
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const scripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/g)];
  assert.ok(scripts.length > 0);
  for (const [, attributes, body] of scripts) {
    assert.match(attributes, /src="\/js\/[^"<>]+"/);
    assert.equal(body.trim(), '');
  }
  const chart = scripts.find(([, attributes]) => attributes.includes('/vendor/chart.'));
  const src = chart[1].match(/src="([^"]+)"/)[1];
  const sri = chart[1].match(/integrity="([^"]+)"/)[1];
  assert.equal(sri, 'sha384-' + createHash('sha384').update(fs.readFileSync(path.join(root, src))).digest('base64'));
});

test('failed credential submission clears both secret input fields', async () => {
  const { run, element } = client();
  element('connection-key').value = 'fixture-key';
  element('connection-secret').value = 'fixture-secret';
  run("api=async()=>{throw new Error('연결 실패');}");
  await element('connection-form').handlers.submit({preventDefault() {}});
  assert.equal(element('connection-key').value, '');
  assert.equal(element('connection-secret').value, '');
  assert.equal(element('connection-save').disabled, false);
});
