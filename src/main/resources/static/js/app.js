/*
 * Asset Dashboard — 프레임워크 없는 순수 JS 클라이언트.
 *
 * 이 파일은 백엔드 API 만으로 PRD 5장의 화면이 실제로 그려지는지 보여주기 위한 것이다.
 * Mock 데이터는 한 줄도 없다 — 모든 숫자는 /api/dashboard 와 /api/portfolio 응답이다.
 */

const API = '';
const TOKEN_KEY = 'asset-dashboard.token';
const NICK_KEY = 'asset-dashboard.nickname';

const $ = (id) => document.getElementById(id);
const state = { portfolioType: '', assets: [], chart: null };

/* ── HTTP ──────────────────────────────────────── */

function token() { return localStorage.getItem(TOKEN_KEY); }

async function api(path, options = {}) {
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const t = token();
  if (t) headers.Authorization = `Bearer ${t}`;

  const res = await fetch(API + path, { ...options, headers });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;

  if (!res.ok) {
    if (res.status === 401) logout();
    // 서버가 항상 { status, code, message } 형식으로 내려주므로 그대로 사용자에게 보여줄 수 있다.
    throw new Error(data?.message || `요청 실패 (${res.status})`);
  }
  return data;
}

/* ── 포맷 ──────────────────────────────────────── */

const won = (v) => v == null ? '-' : '₩' + Math.round(Number(v)).toLocaleString('ko-KR');
const num = (v, max = 8) => v == null ? '-'
  : Number(v).toLocaleString('ko-KR', { maximumFractionDigits: max });
const pct = (v) => v == null ? '-' : (Number(v) >= 0 ? '+' : '') + Number(v).toFixed(2) + '%';
const signClass = (v) => v == null ? '' : (Number(v) > 0 ? 'up' : Number(v) < 0 ? 'down' : '');

function signedWon(v) {
  if (v == null) return '-';
  const n = Number(v);
  return (n >= 0 ? '+' : '−') + '₩' + Math.abs(Math.round(n)).toLocaleString('ko-KR');
}

function timeAgo(iso) {
  if (!iso) return '갱신 이력 없음';
  const mins = Math.floor((Date.now() - new Date(iso).getTime()) / 60000);
  if (mins < 1) return '방금 전 기준';
  if (mins < 60) return `${mins}분 전 기준`;
  const hours = Math.floor(mins / 60);
  if (hours < 24) return `${hours}시간 전 기준`;
  return `${Math.floor(hours / 24)}일 전 기준`;
}

function fmtDate(iso) {
  const d = new Date(iso);
  const p = (n) => String(n).padStart(2, '0');
  return `${p(d.getMonth() + 1)}/${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
}

function toast(message) {
  const el = $('toast');
  el.textContent = message;
  el.hidden = false;
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => { el.hidden = true; }, 2600);
}

/* ── 인증 ──────────────────────────────────────── */

$('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('login-error').textContent = '';
  try {
    const res = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: $('login-email').value, password: $('login-password').value }),
    });
    localStorage.setItem(TOKEN_KEY, res.accessToken);
    localStorage.setItem(NICK_KEY, res.nickname);
    enterApp();
  } catch (err) {
    $('login-error').textContent = err.message;
  }
});

$('signup-btn').addEventListener('click', async () => {
  $('login-error').textContent = '';
  const email = $('login-email').value;
  try {
    await api('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({ email, password: $('login-password').value, nickname: email.split('@')[0] }),
    });
    toast('가입 완료. 로그인해주세요.');
  } catch (err) {
    $('login-error').textContent = err.message;
  }
});

$('logout-btn').addEventListener('click', logout);

function logout() {
  // JWT 는 서버가 상태를 저장하지 않으므로, 로그아웃은 클라이언트가 토큰을 지우는 것으로 끝난다(PRD 4-1).
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(NICK_KEY);
  $('app-view').hidden = true;
  $('login-view').hidden = false;
}

function enterApp() {
  $('login-view').hidden = true;
  $('app-view').hidden = false;
  $('user-nickname').textContent = (localStorage.getItem(NICK_KEY) || '') + '님';
  refresh();
}

/* ── 렌더링 ────────────────────────────────────── */

$('refresh-btn').addEventListener('click', () => refresh(true));

async function refresh(notify = false) {
  try {
    const [dashboard, portfolio, assets] = await Promise.all([
      api('/api/dashboard'),
      api('/api/portfolio' + (state.portfolioType ? `?type=${state.portfolioType}` : '')),
      api('/api/assets'),
    ]);
    state.assets = assets;
    renderDashboard(dashboard, assets);
    renderPortfolio(portfolio);
    if (notify) toast('최신 시세로 갱신했습니다');
  } catch (err) {
    toast(err.message);
  }
}

function renderDashboard(d, assets) {
  $('empty-state').hidden = d.hasAssets;
  $('dashboard-body').hidden = !d.hasAssets;
  if (!d.hasAssets) return;

  $('total-asset').textContent = won(d.totalAssetKRW);
  $('stale-badge').hidden = !d.priceStale;

  const s = d.investmentSummary;
  $('inv-valuation').textContent = won(s.valuationKRW);
  $('inv-cost').textContent = won(s.costKRW);

  const unrealized = $('inv-unrealized');
  unrealized.textContent = `${signedWon(s.unrealizedPnl)} (${pct(s.unrealizedPnlRate)})`;
  unrealized.className = signClass(s.unrealizedPnl);

  const realized = $('inv-realized');
  realized.textContent = signedWon(s.realizedPnl);
  realized.className = signClass(s.realizedPnl);

  renderAllocation(d.allocation);
  renderCash(assets.filter((a) => a.type === 'CASH' || a.type === 'BANK'));
  renderRecent(d.recentTransactions);
}

const TYPE_COLOR = { CRYPTO: '#f59e0b', STOCK: '#4c8dff', BANK: '#22c55e', CASH: '#a78bfa' };

function renderAllocation(allocation) {
  const labels = allocation.map((a) => a.type);
  const values = allocation.map((a) => Number(a.valuationKRW));
  const colors = labels.map((t) => TYPE_COLOR[t] || '#8b95a5');

  $('allocation-legend').innerHTML = allocation.map((a, i) => `
    <li>
      <span class="dot" style="background:${colors[i]}"></span>
      <span class="lg-name">${a.type}</span>
      <span class="lg-val">${won(a.valuationKRW)} · ${Number(a.ratio).toFixed(1)}%</span>
    </li>`).join('');

  const ctx = $('allocation-chart');
  if (state.chart) state.chart.destroy();
  state.chart = new Chart(ctx, {
    type: 'doughnut',
    data: { labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }] },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '62%',
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: { label: (c) => ` ${c.label}  ${won(c.raw)}` },
        },
      },
    },
  });
}

function renderPortfolio(items) {
  const list = $('portfolio-list');
  if (!items.length) {
    list.innerHTML = '<div class="empty-row">해당 조건의 투자 자산이 없습니다</div>';
    return;
  }
  list.innerHTML = items.map((a) => `
    <div class="row" data-asset-id="${a.assetId}" data-asset-name="${a.name}">
      <div class="row-main">
        <div class="row-name">
          ${a.name} <span class="row-sym">· ${a.symbol}</span>
          ${a.priceStale ? `<span class="pill pill-stale">${timeAgo(a.priceUpdatedAt)}</span>` : ''}
        </div>
        <div class="row-sub">
          ${num(a.quantity)}개 · 평단 ${won(a.avgPrice)}
          ${a.currentPrice != null ? ` · 현재가 ${num(a.currentPrice, 2)} ${a.currency}` : ''}
        </div>
      </div>
      <div class="row-right">
        <div class="row-value">${won(a.valuationKRW)}</div>
        <div class="row-delta ${signClass(a.unrealizedPnl)}">
          ${signedWon(a.unrealizedPnl)} ${a.unrealizedPnlRate == null ? '(-)' : `(${pct(a.unrealizedPnlRate)})`}
        </div>
      </div>
    </div>`).join('');
}

function renderCash(assets) {
  const list = $('cash-list');
  if (!assets.length) {
    list.innerHTML = '<div class="empty-row">등록된 현금/은행 자산이 없습니다</div>';
    return;
  }
  list.innerHTML = assets.map((a) => `
    <div class="row" data-asset-id="${a.id}" data-asset-name="${a.name}">
      <div class="row-main">
        <div class="row-name">${a.name} <span class="row-sym">· ${a.type}</span></div>
      </div>
      <div class="row-right"><div class="row-value">${won(a.valuationKRW)}</div></div>
    </div>`).join('');
}

function renderRecent(transactions) {
  const list = $('recent-list');
  if (!transactions.length) {
    list.innerHTML = '<div class="empty-row">아직 거래 내역이 없습니다</div>';
    return;
  }
  list.innerHTML = transactions.map((t) => `
    <div class="row" data-asset-id="${t.assetId}" data-asset-name="${t.assetName}">
      <div class="row-main">
        <div class="row-name">
          <span class="pill pill-${t.type}">${t.type}</span> ${t.assetName}
        </div>
        <div class="row-sub">${fmtDate(t.tradedAt)}</div>
      </div>
      <div class="row-right">
        <div class="row-value">${num(t.quantity)}</div>
        ${t.price != null ? `<div class="row-delta">@ ${num(t.price, 2)}</div>` : ''}
      </div>
    </div>`).join('');
}

/* ── 거래 내역 드로어 ──────────────────────────── */

document.addEventListener('click', async (e) => {
  const row = e.target.closest('.row[data-asset-id]');
  if (!row) return;
  openHistory(row.dataset.assetId, row.dataset.assetName);
});

async function openHistory(assetId, assetName) {
  $('drawer-title').textContent = `${assetName} 거래 내역`;
  $('drawer-body').innerHTML = '<div class="empty-row">불러오는 중…</div>';
  $('drawer-backdrop').hidden = false;
  try {
    const list = await api(`/api/assets/${assetId}/transactions`);
    $('drawer-body').innerHTML = list.length
      ? list.map((t) => `
        <div class="row" style="cursor:default">
          <div class="row-main">
            <div class="row-name"><span class="pill pill-${t.type}">${t.type}</span></div>
            <div class="row-sub">${fmtDate(t.tradedAt)}${t.memo ? ' · ' + t.memo : ''}</div>
          </div>
          <div class="row-right">
            <div class="row-value">${num(t.quantity)}</div>
            ${t.price != null ? `<div class="row-delta">${num(t.price, 2)} × ${num(t.exchangeRate, 2)}</div>` : ''}
          </div>
        </div>`).join('')
      : '<div class="empty-row">거래 내역이 없습니다</div>';
  } catch (err) {
    $('drawer-body').innerHTML = `<div class="empty-row">${err.message}</div>`;
  }
}

$('drawer-close').addEventListener('click', () => { $('drawer-backdrop').hidden = true; });
$('drawer-backdrop').addEventListener('click', (e) => {
  if (e.target === $('drawer-backdrop')) $('drawer-backdrop').hidden = true;
});

/* ── 탭 ────────────────────────────────────────── */

$('portfolio-tabs').addEventListener('click', (e) => {
  const tab = e.target.closest('.tab');
  if (!tab) return;
  document.querySelectorAll('#portfolio-tabs .tab').forEach((t) => t.classList.remove('active'));
  tab.classList.add('active');
  state.portfolioType = tab.dataset.type;
  refresh();
});

/* ── FAB 모달 ──────────────────────────────────── */

const modal = $('modal-backdrop');
$('fab').addEventListener('click', openChoice);
$('modal-close').addEventListener('click', closeModal);
modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

function closeModal() { modal.hidden = true; $('modal-error').textContent = ''; }

function openChoice() {
  $('modal-title').textContent = '무엇을 하시겠어요?';
  $('modal-body').innerHTML = `
    <button class="choice" data-action="asset">자산 등록<small>심볼을 입력해 새 자산을 추가합니다</small></button>
    <button class="choice" data-action="trade">매수 · 매도<small>보유한 투자 자산의 거래를 기록합니다</small></button>
    <button class="choice" data-action="cash">입금 · 출금<small>현금/은행 자산의 잔액을 조정합니다</small></button>`;
  $('modal-body').querySelectorAll('.choice').forEach((b) => {
    b.addEventListener('click', () => {
      if (b.dataset.action === 'asset') openAssetForm();
      if (b.dataset.action === 'trade') openTradeForm();
      if (b.dataset.action === 'cash') openCashForm();
    });
  });
  modal.hidden = false;
}

const today = () => new Date().toISOString().slice(0, 16);

function openAssetForm() {
  $('modal-title').textContent = '자산 등록';
  $('modal-body').innerHTML = `
    <label>자산 종류
      <select id="f-type">
        <option value="CRYPTO">CRYPTO</option>
        <option value="STOCK">STOCK</option>
        <option value="BANK">BANK</option>
        <option value="CASH">CASH</option>
      </select>
    </label>
    <label>심볼
      <input id="f-symbol" placeholder="BTC, NVDA, 000660.KS" />
      <small class="field-hint">국내주식은 000660.KS(코스피) / .KQ(코스닥), 해외주식은 티커만. 현금·은행은 통화 코드(KRW).</small>
    </label>
    <label>표시 이름 <span class="hint">(선택 — 비우면 심볼을 사용)</span>
      <input id="f-name" placeholder="비트코인" />
    </label>
    <label>통화<input id="f-currency" value="USDT" /></label>
    <button class="btn-primary" id="f-submit">등록</button>`;

  $('f-type').addEventListener('change', (e) => {
    $('f-currency').value = e.target.value === 'CRYPTO' ? 'USDT' : 'KRW';
  });
  $('f-submit').addEventListener('click', () => submit(async () => {
    const res = await fetch('/api/assets', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token()}` },
      body: JSON.stringify({
        type: $('f-type').value,
        symbol: $('f-symbol').value,
        name: $('f-name').value,
        currency: $('f-currency').value,
      }),
    });
    const data = await res.json();
    if (!res.ok) throw new Error(data.message);
    // 200 은 삭제되었던 자산의 복구, 201 은 신규 등록이다(서버가 상태 코드로 구분해 알려준다).
    toast(res.status === 200 ? '삭제되었던 자산을 복구했습니다' : '자산을 등록했습니다');
  }, false));
}

function assetOptions(filter) {
  return state.assets.filter(filter)
    .map((a) => `<option value="${a.id}">${a.name} (${a.symbol})</option>`).join('');
}

function openTradeForm() {
  const options = assetOptions((a) => a.type === 'STOCK' || a.type === 'CRYPTO');
  if (!options) {
    $('modal-error').textContent = '먼저 STOCK 또는 CRYPTO 자산을 등록해주세요.';
    return;
  }
  $('modal-title').textContent = '매수 · 매도';
  $('modal-body').innerHTML = `
    <label>자산<select id="f-asset">${options}</select></label>
    <label>거래<select id="f-side"><option value="buy">매수</option><option value="sell">매도</option></select></label>
    <label>수량<input id="f-qty" type="number" step="any" placeholder="0.1" /></label>
    <label>단가 <span class="hint">(원래 통화 기준)</span><input id="f-price" type="number" step="any" placeholder="40000" /></label>
    <label>환율 <span class="hint">(국내주식·원화는 1)</span><input id="f-fx" type="number" step="any" value="1" /></label>
    <label>거래 시점<input id="f-date" type="datetime-local" value="${today()}" /></label>
    <label>메모 <span class="hint">(선택)</span><input id="f-memo" /></label>
    <button class="btn-primary" id="f-submit">기록</button>`;

  $('f-submit').addEventListener('click', () => submit(async () => {
    const id = $('f-asset').value;
    await api(`/api/assets/${id}/transactions/${$('f-side').value}`, {
      method: 'POST',
      body: JSON.stringify({
        quantity: Number($('f-qty').value),
        price: Number($('f-price').value),
        exchangeRate: Number($('f-fx').value),
        memo: $('f-memo').value || null,
        tradedAt: $('f-date').value + ':00',
      }),
    });
    toast('거래를 기록했습니다');
  }));
}

function openCashForm() {
  const options = assetOptions((a) => a.type === 'CASH' || a.type === 'BANK');
  if (!options) {
    $('modal-error').textContent = '먼저 CASH 또는 BANK 자산을 등록해주세요.';
    return;
  }
  $('modal-title').textContent = '입금 · 출금';
  $('modal-body').innerHTML = `
    <label>자산<select id="f-asset">${options}</select></label>
    <label>거래<select id="f-side"><option value="deposit">입금</option><option value="withdraw">출금</option></select></label>
    <label>금액<input id="f-qty" type="number" step="any" placeholder="1000000" /></label>
    <label>거래 시점<input id="f-date" type="datetime-local" value="${today()}" /></label>
    <label>메모 <span class="hint">(선택)</span><input id="f-memo" placeholder="월급 입금" /></label>
    <button class="btn-primary" id="f-submit">기록</button>`;

  $('f-submit').addEventListener('click', () => submit(async () => {
    const id = $('f-asset').value;
    await api(`/api/assets/${id}/transactions/${$('f-side').value}`, {
      method: 'POST',
      body: JSON.stringify({
        quantity: Number($('f-qty').value),
        memo: $('f-memo').value || null,
        tradedAt: $('f-date').value + ':00',
      }),
    });
    toast('거래를 기록했습니다');
  }));
}

/** 폼 제출 공통 처리 — 에러는 모달 안에 보여주고, 성공하면 닫고 화면을 갱신한다. */
async function submit(action) {
  $('modal-error').textContent = '';
  try {
    await action();
    closeModal();
    await refresh();
  } catch (err) {
    $('modal-error').textContent = err.message;
  }
}

/* ── 시작 ──────────────────────────────────────── */

if (token()) enterApp();
