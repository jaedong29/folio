/*
 * Asset Dashboard — 프레임워크 없는 순수 JS 클라이언트.
 *
 * 이 파일은 백엔드 API 만으로 PRD 5장의 화면이 실제로 그려지는지 보여주기 위한 것이다.
 * Mock 데이터는 한 줄도 없다 — 모든 숫자는 /api/dashboard 와 /api/portfolio 응답이다.
 */

const API = '';
const TOKEN_KEY = 'asset-dashboard.token';
const REFRESH_TOKEN_KEY = 'asset-dashboard.refresh-token';
const NICK_KEY = 'asset-dashboard.nickname';
const PRIVACY_KEY = 'asset-dashboard.privacy-hidden';

const $ = (id) => document.getElementById(id);
const state = {
  portfolioType: '',
  portfolio: [],
  assets: [],
  dashboard: null,
  chart: null,
  detailChart: null,
  analysisChart: null,
  analysisHistory: null,
  analysisPortfolio: [],
  analysisDays: 1,
  activityTransactions: [],
  activityFilter: '',
  activitySearch: '',
  newsScope: 'ALL',
  newsCategory: '',
  newsQuery: '',
  newsRefreshTimer: null,
  newsSummaryTimer: null,
  detailAssetId: null,
  refreshPromise: null,
  tokenRefreshPromise: null,
  sessionGeneration: 0,
  krSecurities: null,
  krSecuritiesPromise: null,
  privacyHidden: localStorage.getItem(PRIVACY_KEY) === 'true',
  mobilePriceAssetIds: new Set(),
};

/* ── HTTP ──────────────────────────────────────── */

function token() { return localStorage.getItem(TOKEN_KEY); }
function refreshTokenValue() { return localStorage.getItem(REFRESH_TOKEN_KEY); }

function storeSession(res) {
  localStorage.setItem(TOKEN_KEY, res.accessToken);
  localStorage.setItem(REFRESH_TOKEN_KEY, res.refreshToken);
  localStorage.setItem(NICK_KEY, res.nickname);
}

// Access Token은 짧게 살고(기본 30분) Refresh Token으로 조용히 갱신한다. 동시에 여러 요청이
// 401을 받아도 재발급은 한 번만 진행하도록 진행 중인 Promise를 공유한다.
async function refreshAccessToken() {
  if (state.tokenRefreshPromise) return state.tokenRefreshPromise;
  const rt = refreshTokenValue();
  if (!rt) return false;
  const generation = state.sessionGeneration;

  state.tokenRefreshPromise = (async () => {
    try {
      const res = await fetch(API + '/api/auth/refresh', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ refreshToken: rt }),
      });
      if (!res.ok) return false;
      const session = await res.json();
      if (generation !== state.sessionGeneration || refreshTokenValue() !== rt) return false;
      storeSession(session);
      return true;
    } catch {
      return false;
    } finally {
      if (generation === state.sessionGeneration) state.tokenRefreshPromise = null;
    }
  })();
  return state.tokenRefreshPromise;
}

async function api(path, options = {}, retriedAfterRefresh = false) {
  const generation = state.sessionGeneration;
  const headers = { 'Content-Type': 'application/json', ...(options.headers || {}) };
  const t = token();
  if (t) headers.Authorization = `Bearer ${t}`;

  const res = await fetch(API + path, { ...options, headers });
  if (generation !== state.sessionGeneration) throw new Error('로그인 상태가 변경되었습니다.');

  if (res.status === 401 && !retriedAfterRefresh && refreshTokenValue()) {
    if (await refreshAccessToken()) return api(path, options, true);
  }

  const text = await res.text();
  if (generation !== state.sessionGeneration) throw new Error('로그인 상태가 변경되었습니다.');
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
const wonRate = (v) => v == null ? '-'
  : '₩' + Number(v).toLocaleString('ko-KR', { minimumFractionDigits: 0, maximumFractionDigits: 2 });
const num = (v, max = 8) => v == null ? '-'
  : Number(v).toLocaleString('ko-KR', { maximumFractionDigits: max });
const pct = (v) => v == null ? '-' : (Number(v) >= 0 ? '+' : '') + Number(v).toFixed(2) + '%';
const signClass = (v) => v == null ? '' : (Number(v) > 0 ? 'up' : Number(v) < 0 ? 'down' : '');
const privateText = (value) => state.privacyHidden ? '••••••' : value;
const privateSignClass = (value) => state.privacyHidden ? '' : signClass(value);
const escapeHtml = (value) => String(value ?? '')
  .replaceAll('&', '&amp;')
  .replaceAll('<', '&lt;')
  .replaceAll('>', '&gt;')
  .replaceAll('"', '&quot;')
  .replaceAll("'", '&#039;');

function newRequestId() {
  if (typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const bytes = crypto.getRandomValues(new Uint8Array(16));
  bytes[6] = (bytes[6] & 15) | 64;
  bytes[8] = (bytes[8] & 63) | 128;
  const hex = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`;
}

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

let checkedSignupEmail = '';

function setAuthMode(mode) {
  const signup = mode === 'signup';
  $('login-form').hidden = signup;
  $('signup-form').hidden = !signup;
  $('auth-login-tab').classList.toggle('active', !signup);
  $('auth-signup-tab').classList.toggle('active', signup);
  $('auth-login-tab').setAttribute('aria-selected', String(!signup));
  $('auth-signup-tab').setAttribute('aria-selected', String(signup));
  $('login-error').textContent = '';
  $('signup-error').textContent = '';
  setTimeout(() => (signup ? $('signup-email') : $('login-email')).focus(), 0);
}

function setAuthFieldStatus(id, message, status = '') {
  const element = $(id);
  element.textContent = message;
  element.className = `auth-field-status${status ? ` ${status}` : ''}`;
}

function normalizedSignupEmail() {
  return $('signup-email').value.trim().toLowerCase();
}

function updateSignupValidation() {
  const password = $('signup-password').value;
  const confirmation = $('signup-password-confirm').value;
  const passwordValid = password.length >= 8;
  const confirmationValid = confirmation.length > 0 && confirmation === password;
  const emailVerified = checkedSignupEmail && checkedSignupEmail === normalizedSignupEmail();

  if (password) {
    setAuthFieldStatus(
      'signup-password-status',
      passwordValid ? '사용 가능한 비밀번호입니다.' : `비밀번호가 ${8 - password.length}자 더 필요합니다.`,
      passwordValid ? 'success' : 'invalid',
    );
  } else {
    setAuthFieldStatus('signup-password-status', '8자 이상이면 사용할 수 있습니다.');
  }

  if (confirmation) {
    setAuthFieldStatus(
      'signup-confirm-status',
      confirmationValid ? '비밀번호가 일치합니다.' : '비밀번호가 일치하지 않습니다.',
      confirmationValid ? 'success' : 'invalid',
    );
  } else {
    setAuthFieldStatus('signup-confirm-status', '같은 비밀번호를 한 번 더 입력해주세요.');
  }

  $('signup-submit').disabled = !(
    emailVerified
    && $('signup-nickname').value.trim()
    && passwordValid
    && confirmationValid
  );
}

$('login-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  $('login-error').textContent = '';
  try {
    const res = await api('/api/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email: $('login-email').value, password: $('login-password').value }),
    });
    storeSession(res);
    enterApp();
  } catch (err) {
    $('login-error').textContent = err.message;
  }
});

$('auth-login-tab').addEventListener('click', () => setAuthMode('login'));
$('auth-signup-tab').addEventListener('click', () => setAuthMode('signup'));
$('open-signup-btn').addEventListener('click', () => setAuthMode('signup'));
$('open-login-btn').addEventListener('click', () => setAuthMode('login'));

$('signup-email').addEventListener('input', () => {
  checkedSignupEmail = '';
  setAuthFieldStatus('signup-email-status', '변경한 이메일은 다시 중복확인해주세요.');
  updateSignupValidation();
});
$('signup-password').addEventListener('input', updateSignupValidation);
$('signup-password-confirm').addEventListener('input', updateSignupValidation);
$('signup-nickname').addEventListener('input', updateSignupValidation);

$('check-email-btn').addEventListener('click', async () => {
  const emailInput = $('signup-email');
  const email = normalizedSignupEmail();
  $('signup-error').textContent = '';
  if (!email || !emailInput.checkValidity()) {
    checkedSignupEmail = '';
    setAuthFieldStatus('signup-email-status', '올바른 이메일 주소를 입력해주세요.', 'invalid');
    updateSignupValidation();
    return;
  }

  const button = $('check-email-btn');
  button.disabled = true;
  button.textContent = '확인 중';
  try {
    const result = await api(`/api/auth/email-availability?email=${encodeURIComponent(email)}`);
    checkedSignupEmail = result.available ? result.email : '';
    setAuthFieldStatus(
      'signup-email-status',
      result.message,
      result.available ? 'success' : 'invalid',
    );
  } catch (err) {
    checkedSignupEmail = '';
    setAuthFieldStatus('signup-email-status', err.message, 'invalid');
  } finally {
    button.disabled = false;
    button.textContent = '중복확인';
    updateSignupValidation();
  }
});

$('signup-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  $('signup-error').textContent = '';
  const email = normalizedSignupEmail();
  if (checkedSignupEmail !== email) {
    $('signup-error').textContent = '이메일 중복확인을 먼저 완료해주세요.';
    return;
  }
  if ($('signup-password').value !== $('signup-password-confirm').value) {
    $('signup-error').textContent = '비밀번호 확인이 일치하지 않습니다.';
    return;
  }

  const submitButton = $('signup-submit');
  submitButton.disabled = true;
  submitButton.textContent = '계정을 만드는 중…';
  try {
    await api('/api/auth/signup', {
      method: 'POST',
      body: JSON.stringify({
        email,
        password: $('signup-password').value,
        nickname: $('signup-nickname').value.trim(),
      }),
    });
    $('login-email').value = email;
    $('login-password').value = '';
    $('signup-form').reset();
    checkedSignupEmail = '';
    updateSignupValidation();
    setAuthMode('login');
    toast('회원가입이 완료되었습니다. 이제 로그인해주세요.');
  } catch (err) {
    $('signup-error').textContent = err.message;
    if (err.message.includes('이메일')) {
      checkedSignupEmail = '';
      setAuthFieldStatus('signup-email-status', err.message, 'invalid');
    }
  } finally {
    submitButton.textContent = '회원가입 완료하기';
    updateSignupValidation();
  }
});

$('logout-btn').addEventListener('click', logout);

function logout() {
  state.sessionGeneration++;
  state.tokenRefreshPromise = null;
  state.refreshPromise = null;
  state.dashboard = null;
  state.assets = [];
  state.portfolio = [];
  state.analysisHistory = null;
  state.analysisPortfolio = [];
  state.activityTransactions = [];
  state.detailAssetId = null;
  $('dashboard-body').hidden = true;
  ['cash-list', 'portfolio-list', 'activity-history-list'].forEach(id => $(id).replaceChildren());
  $('drawer-backdrop').hidden = true;
  $('modal-backdrop').hidden = true;
  $('refresh-btn').disabled = false;
  clearConnectedSession();
  clearTimeout(state.newsRefreshTimer);
  state.newsRefreshTimer = null;
  clearTimeout(state.newsSummaryTimer);
  state.newsSummaryTimer = null;
  const rt = refreshTokenValue();
  if (rt) {
    // 서버의 Refresh Token도 실제로 폐기한다. 실패해도 로컬 로그아웃 자체는 막지 않는다.
    fetch(API + '/api/auth/logout', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ refreshToken: rt }),
    }).catch(() => {});
  }
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
  localStorage.removeItem(NICK_KEY);
  $('app-view').hidden = true;
  $('login-view').hidden = false;
  setAuthMode('login');
}

function enterApp() {
  $('login-view').hidden = true;
  $('app-view').hidden = false;
  const nickname = localStorage.getItem(NICK_KEY) || '사용자';
  $('user-nickname').textContent = nickname;
  $('user-avatar').textContent = nickname.trim().charAt(0).toUpperCase() || 'U';
  $('page-date').textContent = new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'long', day: 'numeric', weekday: 'long',
  }).format(new Date());
  syncPrivacyControl();
  if (!location.hash || location.hash === '#connected') showConnected(false);
  else if (location.hash === '#manual') showOverview();
  else { $('connected-page').hidden = true; refresh(); }
}

/* ── 렌더링 ────────────────────────────────────── */

$('refresh-btn').addEventListener('click', () => $('connected-page').hidden ? refresh(true) : refreshConnected());
$('privacy-toggle').addEventListener('click', () => {
  state.privacyHidden = !state.privacyHidden;
  localStorage.setItem(PRIVACY_KEY, String(state.privacyHidden));
  syncPrivacyControl();
  renderPrivacySensitiveViews();
  toast(state.privacyHidden ? '개인 자산 금액을 숨겼습니다' : '개인 자산 금액을 표시합니다');
});

function syncPrivacyControl() {
  const button = $('privacy-toggle');
  const hidden = state.privacyHidden;
  button.setAttribute('aria-pressed', String(hidden));
  button.setAttribute('aria-label', hidden ? '개인 자산 금액 표시하기' : '개인 자산 금액 숨기기');
  button.title = hidden ? '개인 자산 금액 표시하기' : '개인 자산 금액 숨기기';
  button.innerHTML = hidden
    ? '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="m3 3 18 18M10.6 6.2A10.5 10.5 0 0 1 12 6c6 0 9.5 6 9.5 6a15.8 15.8 0 0 1-2.1 2.8M6.5 6.5C3.8 8.3 2.5 12 2.5 12s3.5 6 9.5 6c1.4 0 2.6-.3 3.7-.7M9.9 9.9a3 3 0 0 0 4.2 4.2"/></svg>'
    : '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6S2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>';
}

function renderPrivacySensitiveViews() {
  renderConnected();
  if (!state.dashboard) return;
  renderDashboard(state.dashboard, state.assets);
  renderPortfolio(state.portfolio);
  if (!$('analysis-page').hidden) {
    renderAnalysisSummary();
    renderAnalysisChart();
    renderAnalysisComposition();
    renderAnalysisRanking();
  }
  if (!$('activity-page').hidden) renderActivityLedger();
}

function refresh(notify = false) {
  if (state.refreshPromise) return state.refreshPromise;

  const generation = state.sessionGeneration;
  const refreshButton = $('refresh-btn');
  refreshButton.disabled = true;
  refreshButton.classList.add('is-loading');
  $('last-refresh-label').textContent = '동기화 중…';

  state.refreshPromise = (async () => {
    try {
      // Dashboard가 시세를 반영하는 유일한 선행 요청이 되게 한다.
      // Dashboard와 Portfolio를 동시에 호출하면 두 요청이 같은 Asset을 갱신하며 @Version 충돌을 만들 수 있다.
      const dashboard = await api(`/api/dashboard${notify ? '?force=true' : ''}`);
      const [portfolio, assets] = await Promise.all([
        api('/api/portfolio' + (state.portfolioType ? `?type=${state.portfolioType}` : '')),
        api('/api/assets'),
      ]);
      if (generation !== state.sessionGeneration) return;
      state.dashboard = dashboard;
      state.assets = assets;
      state.portfolio = portfolio;
      renderDashboard(dashboard, assets);
      renderPortfolio(portfolio);
      if (location.hash === '#analysis' && $('analysis-page').hidden) {
        await showAnalysis(false);
      } else if (location.hash === '#activity' && $('activity-page').hidden) {
        await showActivity(false);
      } else if (location.hash === '#news' && $('news-page').hidden) {
        await showNews(false);
      } else if (!$('analysis-page').hidden) {
        await loadAnalysisData();
      } else if (!$('activity-page').hidden) {
        await loadActivityData();
      } else if (!$('news-page').hidden) {
        await loadNewsData();
      }
      $('last-refresh-label').textContent = `대시보드 ${timeAgo(new Date().toISOString())}`;
      if (notify) toast('최신 시세로 갱신했습니다');
    } catch (err) {
      if (generation !== state.sessionGeneration) return;
      $('last-refresh-label').textContent = '동기화 실패';
      toast(err.message);
    } finally {
      if (generation !== state.sessionGeneration) return;
      refreshButton.disabled = false;
      refreshButton.classList.remove('is-loading');
      state.refreshPromise = null;
    }
  })();

  return state.refreshPromise;
}

function renderDashboard(d, assets) {
  // 가입 시 자동 준비되는 KRW/USD/USDT 0원 계좌도 사용자에게 보여준다.
  // 투자 자산이 아직 없어도 빈 화면으로 돌려보내지 않고, 준비된 지갑에서 바로 시작하게 한다.
  const hasDashboardAccounts = d.hasAssets || assets.length > 0;
  $('empty-state').hidden = hasDashboardAccounts;
  $('dashboard-body').hidden = !hasDashboardAccounts;
  if (!hasDashboardAccounts) return;

  $('total-asset').textContent = privateText(won(d.totalAssetKRW));
  $('stale-badge').hidden = !d.priceStale;
  $('exchange-rate-badge').hidden = !d.exchangeRateMissing;
  renderSummary(d.investmentSummary, d.cashSummary);
  renderMarketRates(d.marketRates);
  renderDailyPnl(d.dailyPnl);

  renderAllocation(d.allocation);
  renderCash(assets.filter((a) => a.type === 'CASH' || a.type === 'BANK'));
  renderRecent(d.recentTransactions);
}

/** 총자산 아래의 구성·손익 요약을 렌더링한다. */
function renderSummary(investment, cash) {
  $('investment-total').textContent = privateText(won(investment?.valuationKRW));
  $('cash-total').textContent = privateText(won(cash?.valuationKRW));

  const unrealized = $('unrealized-total');
  unrealized.textContent = privateText(signedWon(investment?.unrealizedPnl));
  unrealized.className = privateSignClass(investment?.unrealizedPnl);
  $('unrealized-rate').textContent = investment?.unrealizedPnlRate == null
    ? '평단 입력 시 계산'
    : privateText(`${pct(investment.unrealizedPnlRate)} · 미실현`);

  const realized = $('realized-total');
  realized.textContent = privateText(signedWon(investment?.realizedPnl));
  realized.className = privateSignClass(investment?.realizedPnl);
}

function renderDailyPnl(daily) {
  const value = $('daily-pnl');
  if (!daily?.available) {
    value.textContent = '-';
    value.className = '';
    $('daily-baseline').textContent = daily?.unavailableReason || '오늘 손익 기준을 준비하고 있습니다';
    return;
  }
  value.textContent = privateText(`${signedWon(daily.amountKRW)} (${pct(daily.rate)})`);
  value.className = privateSignClass(daily.amountKRW);
  $('daily-baseline').textContent = `${fmtDate(daily.baselineAt)} 기준 · 외부 입출금 반영`;
}

function renderMarketRates(rates) {
  renderMarketRate('usd', rates?.usdKrw, rates?.usdSource, rates?.usdUpdatedAt);
  renderMarketRate('usdt', rates?.usdtKrw, rates?.usdtSource, rates?.usdtUpdatedAt);
}

function renderMarketRate(prefix, rate, source, updatedAt) {
  $(`${prefix}-krw-rate`).textContent = rate == null ? '-' : wonRate(rate);
  $(`${prefix}-krw-source`).textContent = rate == null
    ? '현재 조회값이 없습니다'
    : updatedAt
      ? `${source} · ${timeAgo(updatedAt)}`
      : `${source} · 마지막 저장값`;
}

const TYPE_COLOR = { CRYPTO: '#f7b84b', STOCK: '#6d8cff', BANK: '#3ddc97', CASH: '#b98cff' };
const TYPE_LABEL = { CRYPTO: '암호화폐', STOCK: '주식', BANK: '은행', CASH: '현금' };

function renderAllocation(allocation) {
  const labels = allocation.map((a) => a.type);
  const values = allocation.map((a) => Number(a.valuationKRW));
  const colors = labels.map((t) => TYPE_COLOR[t] || '#8b95a5');

  $('allocation-legend').innerHTML = allocation.map((a, i) => `
    <li>
      <span class="dot" style="background:${colors[i]}"></span>
      <span class="lg-name">${TYPE_LABEL[a.type] || a.type}</span>
      <span class="lg-val">${Number(a.ratio).toFixed(1)}%</span>
    </li>`).join('');
  $('allocation-count').textContent = allocation.length;

  const ctx = $('allocation-chart');
  if (state.chart) state.chart.destroy();
  state.chart = new Chart(ctx, {
    type: 'doughnut',
    data: { labels, datasets: [{ data: values, backgroundColor: colors, borderWidth: 0 }] },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      cutout: '73%',
      animation: { duration: 550 },
      plugins: {
        legend: { display: false },
        tooltip: {
          callbacks: { label: (c) => ` ${TYPE_LABEL[c.label] || c.label}  ${privateText(won(c.raw))}` },
        },
      },
      elements: { arc: { borderRadius: 5, spacing: 3 } },
    },
  });
}

function renderPortfolio(items) {
  const list = $('portfolio-list');
  $('portfolio-count').textContent = items.length;
  if (!items.length) {
    list.innerHTML = '<div class="empty-row">해당 조건의 투자 자산이 없습니다</div>';
    return;
  }
  list.innerHTML = items.map((a) => {
    const metadata = state.assets.find((asset) => asset.id === a.assetId);
    const trust = priceTrustLabel(metadata || a);
    const mobilePriceVisible = state.mobilePriceAssetIds.has(a.assetId);
    const currentPriceLabel = a.currentPrice == null ? '현재가 없음' : `${num(a.currentPrice, 4)} ${a.currency}`;
    const positionLabel = `${quantityLabel(a)} · ${a.costBasisMissing
      ? '평단 미입력'
      : `평단 ${num(a.avgPriceOriginal ?? a.avgPrice, 4)} ${a.avgPriceOriginal != null ? a.currency : 'KRW'}`}`;
    return `
    <div class="row portfolio-row" data-asset-id="${a.assetId}" data-asset-name="${escapeHtml(a.name)}">
      <div class="portfolio-asset">
        ${assetIcon(a.type, a.displaySymbol || a.symbol)}
        <div class="asset-name-block">
          <div class="asset-name-line">
            <span class="asset-name">${escapeHtml(a.name)}</span>
            <span class="asset-symbol">${a.displaySymbol || a.symbol}</span>
          </div>
          <div class="asset-market">
            ${a.marketLabel}${trust ? ` · <span class="data-trust">${trust}</span>` : ''}
            ${a.priceStale ? `<span class="pill pill-stale">${timeAgo(a.priceUpdatedAt)}</span>` : ''}
            ${a.exchangeRateMissing ? '<span class="pill pill-stale">환율 필요</span>' : ''}
          </div>
          <button class="mobile-price-toggle" type="button" data-mobile-price-id="${a.assetId}" aria-expanded="${mobilePriceVisible}">
            <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M2.5 12s3.5-6 9.5-6 9.5 6 9.5 6-3.5 6-9.5 6S2.5 12 2.5 12Z"/><circle cx="12" cy="12" r="2.5"/></svg>
            <span>${mobilePriceVisible ? escapeHtml(currentPriceLabel) : '현재가 보기'}</span>
          </button>
        </div>
      </div>
      <div class="portfolio-position">
        <div class="position-price">${a.currentPrice == null ? '현재가 -' : escapeHtml(currentPriceLabel)}</div>
        <div class="position-quantity">${escapeHtml(privateText(positionLabel))}</div>
      </div>
      <div class="portfolio-value">
        ${a.exchangeRateMissing
          ? '<strong class="rate-missing">환율 입력 필요</strong>'
          : `<strong>${privateText(won(a.valuationKRW))}</strong>
             <small class="${privateSignClass(a.unrealizedPnl)}">
               ${a.unrealizedPnl == null
                 ? '손익 -'
                 : privateText(`${signedWon(a.unrealizedPnl)} (${pct(a.unrealizedPnlRate)})`)}
             </small>`}
      </div>
    </div>`;
  }).join('');
}

function renderCash(assets) {
  const list = $('cash-list');
  if (!assets.length) {
    list.innerHTML = '<div class="empty-row">등록된 현금/은행 자산이 없습니다</div>';
    return;
  }
  const currencyOrder = { KRW: 0, USD: 1, USDT: 2 };
  const ordered = [...assets].sort((a, b) => {
    if (a.defaultSettlementAsset !== b.defaultSettlementAsset) return a.defaultSettlementAsset ? -1 : 1;
    return (currencyOrder[a.currency] ?? 9) - (currencyOrder[b.currency] ?? 9);
  });
  list.innerHTML = ordered.map((a) => `
    <div class="currency-card" data-asset-id="${a.id}" data-asset-name="${escapeHtml(a.name)}">
      ${currencyIcon(a.currency)}
      <div class="currency-copy">
        <strong>${escapeHtml(a.currency)}${a.defaultSettlementAsset ? ' <span class="pill pill-market">기본</span>' : ''}</strong>
        <small>${escapeHtml(a.name)}</small>
      </div>
      <div class="currency-value">
        <strong>${escapeHtml(privateText(`${num(a.quantity, a.currency === 'KRW' ? 0 : 4)} ${a.currency}`))}</strong>
        <small class="${a.exchangeRateMissing ? 'rate-missing' : ''}">${a.exchangeRateMissing ? '환율 입력 필요' : privateText(won(a.valuationKRW))}</small>
      </div>
    </div>`).join('');
}

function renderRecent(transactions) {
  const list = $('recent-list');
  if (!transactions.length) {
    list.innerHTML = '<div class="empty-row">아직 거래 내역이 없습니다</div>';
    return;
  }
  list.innerHTML = transactions.map((t) => `
    <div class="row activity-row" data-asset-id="${t.assetId}" data-asset-name="${escapeHtml(t.assetName)}">
      <div class="activity-main">
        <span class="activity-icon ${isInboundTransaction(t.type) ? 'activity-icon-in' : 'activity-icon-out'}">${isInboundTransaction(t.type) ? '↙' : '↗'}</span>
        <div class="activity-copy">
          <strong>${escapeHtml(t.assetName)} · ${transactionLabel(t.type)}</strong>
          <small>${fmtDate(t.tradedAt)} · ${t.symbol}</small>
        </div>
      </div>
      <div class="activity-value">
        <strong class="${state.privacyHidden ? '' : isInboundTransaction(t.type) ? 'up' : 'down'}">${privateText(`${isInboundTransaction(t.type) ? '+' : '−'}${num(t.quantity)}`)}</strong>
        ${t.price != null ? `<small>${privateText(`@ ${num(t.price, 2)}`)}</small>` : '<small>잔액 변동</small>'}
      </div>
    </div>`).join('');
}

/* ── Asset Analysis ────────────────────────────── */

$('brand-home').addEventListener('click', () => {
  if (location.hash) {
    history.replaceState(null, '', location.pathname + location.search);
  }
  showConnected();
});

$('nav-overview').addEventListener('click', () => {
  history.pushState({ view: 'overview' }, '', '#manual');
  showOverview();
});
$('nav-news').addEventListener('click', () => showNews(true));

$('open-analysis').addEventListener('click', () => showAnalysis(true));
$('open-activity').addEventListener('click', () => showActivity(true));
$('analysis-back').addEventListener('click', () => {
  if (history.state?.view === 'analysis') {
    history.back();
  } else {
    history.replaceState(null, '', location.pathname + location.search);
    showOverview();
  }
});
$('activity-back').addEventListener('click', () => {
  if (history.state?.view === 'activity') {
    history.back();
  } else {
    history.replaceState(null, '', location.pathname + location.search);
    showOverview();
  }
});

$('analysis-period-tabs').addEventListener('click', (event) => {
  const tab = event.target.closest('.tab[data-days]');
  if (!tab) return;
  $('analysis-period-tabs').querySelectorAll('.tab').forEach((item) => item.classList.remove('active'));
  tab.classList.add('active');
  state.analysisDays = Number(tab.dataset.days);
  renderAnalysisChart();
});

window.addEventListener('popstate', () => {
  if (!location.hash || location.hash === '#connected') showConnected(false);
  else if (location.hash === '#analysis') showAnalysis(false);
  else if (location.hash === '#activity') showActivity(false);
  else if (location.hash === '#news') showNews(false);
  else showOverview();
});

function setPrimaryNavigation(page) {
  $("nav-connected").classList.toggle("active", page === "connected");
  if (page !== "connected") { $("connected-page").hidden = true; stopConnected(); }
  $('nav-overview').classList.toggle('active', page === 'overview');
  $('nav-news').classList.toggle('active', page === 'news');
  $('nav-current').textContent = page === 'connected' ? '연결 자산' : page === 'news'
    ? 'News'
    : page === 'analysis'
      ? 'Analysis'
      : page === 'activity'
        ? 'Activity'
        : 'Overview';
}

async function showAnalysis(pushHistory) {
  stopNewsSummaryPolling();
  if (!state.dashboard) {
    toast('대시보드를 먼저 불러오고 있습니다');
    return;
  }
  $('overview-page').hidden = true;
  $('activity-page').hidden = true;
  $('news-page').hidden = true;
  $('analysis-page').hidden = false;
  setPrimaryNavigation('analysis');
  window.scrollTo({ top: 0, behavior: 'smooth' });
  renderAnalysisSummary();

  if (pushHistory && location.hash !== '#analysis') {
    history.pushState({ view: 'analysis' }, '', '#analysis');
  }
  await loadAnalysisData();
}

function showOverview() {
  stopNewsSummaryPolling();
  $('analysis-page').hidden = true;
  $('activity-page').hidden = true;
  $('news-page').hidden = true;
  $('overview-page').hidden = false;
  setPrimaryNavigation('overview');
  if (!state.dashboard) refresh();
  window.scrollTo({ top: 0, behavior: 'smooth' });
}

async function showActivity(pushHistory) {
  stopNewsSummaryPolling();
  if (!state.assets.length) {
    toast('대시보드를 먼저 불러오고 있습니다');
    return;
  }
  $('overview-page').hidden = true;
  $('analysis-page').hidden = true;
  $('news-page').hidden = true;
  $('activity-page').hidden = false;
  setPrimaryNavigation('activity');
  window.scrollTo({ top: 0, behavior: 'smooth' });
  if (pushHistory && location.hash !== '#activity') {
    history.pushState({ view: 'activity' }, '', '#activity');
  }
  await loadActivityData();
}

async function showNews(pushHistory) {
  $('overview-page').hidden = true;
  $('analysis-page').hidden = true;
  $('activity-page').hidden = true;
  $('news-page').hidden = false;
  setPrimaryNavigation('news');
  window.scrollTo({ top: 0, behavior: 'smooth' });
  if (pushHistory && location.hash !== '#news') {
    history.pushState({ view: 'news' }, '', '#news');
  }
  await loadNewsData();
}

$('news-scope-tabs').addEventListener('click', async (event) => {
  const tab = event.target.closest('.tab[data-scope]');
  if (!tab) return;
  state.newsScope = tab.dataset.scope;
  $('news-scope-tabs').querySelectorAll('.tab').forEach((item) => item.classList.remove('active'));
  tab.classList.add('active');
  await loadNewsData();
});

$('news-category-tabs').addEventListener('click', async (event) => {
  const tab = event.target.closest('.news-category[data-category]');
  if (!tab) return;
  state.newsCategory = tab.dataset.category;
  $('news-category-tabs').querySelectorAll('.news-category').forEach((item) => item.classList.remove('active'));
  tab.classList.add('active');
  await loadNewsData();
});

$('news-search-form').addEventListener('submit', async (event) => {
  event.preventDefault();
  state.newsQuery = $('news-search').value.trim();
  if (state.newsQuery && state.newsQuery.length < 2) {
    toast('검색어는 2자 이상 입력해주세요');
    return;
  }
  await loadNewsData();
});

$('news-search').addEventListener('search', async () => {
  state.newsQuery = $('news-search').value.trim();
  await loadNewsData();
});

$('news-refresh-btn').addEventListener('click', requestNewsRefresh);

async function loadNewsData() {
  clearTimeout(state.newsSummaryTimer);
  state.newsSummaryTimer = null;
  $('news-feed-list').innerHTML = '<div class="empty-row">뉴스 피드를 불러오는 중…</div>';
  const params = new URLSearchParams({ scope: state.newsScope, limit: '30' });
  if (state.newsCategory) params.set('category', state.newsCategory);
  if (state.newsQuery) params.set('query', state.newsQuery);
  try {
    const [feed, sources] = await Promise.all([
      api(`/api/news?${params}`),
      api('/api/news/sources'),
    ]);
    renderNewsSourceStatus(sources[0]);
    renderNewsFeed(feed);
    if (feed.summaryEnabled && feed.pendingSummaryCount > 0 && location.hash === '#news') {
      state.newsSummaryTimer = setTimeout(loadNewsData, 3000);
    }
  } catch (error) {
    $('news-feed-list').innerHTML = `<div class="empty-row">${escapeHtml(error.message)}</div>`;
    $('news-source-status').textContent = '뉴스 상태를 확인하지 못했습니다.';
  }
}

function renderNewsSourceStatus(source) {
  if (!source) {
    $('news-source-name').textContent = '등록된 공식 출처가 없습니다';
    $('news-source-status').textContent = '출처 Adapter를 먼저 등록해야 합니다.';
    return;
  }
  $('news-source-name').textContent = source.displayName;
  const labels = {
    PENDING: '갱신 요청이 대기 중입니다.',
    RUNNING: '공식자료를 백그라운드에서 수집하고 있습니다.',
    COMPLETED: `${timeAgo(source.lastCompletedAt)} 갱신 완료`,
    FAILED: `최근 갱신 실패 · ${source.lastErrorCode || 'SOURCE_UNAVAILABLE'}`,
  };
  $('news-source-status').textContent = source.lastStatus
    ? labels[source.lastStatus]
    : '아직 수집하지 않았습니다. 공식자료 갱신을 눌러 시작하세요.';
}

function renderNewsFeed(feed) {
  const items = feed.items || [];
  $('news-result-count').textContent = `${items.length.toLocaleString('ko-KR')}건`;
  if (!items.length) {
    const message = feed.scope === 'PORTFOLIO' && !(feed.portfolioSymbols || []).length
      ? '활성 투자자산이 없습니다. 자산을 등록하면 관련 뉴스만 모아볼 수 있습니다.'
      : feed.scope === 'PORTFOLIO'
        ? `${(feed.portfolioSymbols || []).join(', ')} 관련 저장 자료가 아직 없습니다.`
        : '저장된 공용 자료가 없습니다. 공식자료 갱신을 눌러 첫 자료를 수집해보세요.';
    $('news-feed-list').innerHTML = `<div class="news-empty"><strong>표시할 뉴스가 없습니다</strong><span>${escapeHtml(message)}</span></div>`;
    return;
  }
  $('news-feed-list').innerHTML = items.map((item) => {
    const symbolTags = (item.symbols || []).map((symbol) => `<span class="news-symbol">${escapeHtml(symbol)}</span>`).join('');
    const topics = (item.topics || []).slice(0, 3).map((topic) => `<span>${escapeHtml(newsTopicLabel(topic))}</span>`).join('');
    const hasSummary = item.summaryStatus === 'COMPLETED' && item.summaryKo;
    const summaryLabels = {
      COMPLETED: 'AI 요약',
      RUNNING: 'AI 요약 중',
      PENDING: 'AI 요약 대기',
      FAILED: '원문 표시',
    };
    const summaryLabel = !feed.summaryEnabled && !hasSummary
      ? '원문 표시'
      : summaryLabels[item.summaryStatus] || '원문 표시';
    const usage = item.summaryLatencyMs == null
      ? ''
      : ` · ${Number(item.summaryLatencyMs).toLocaleString('ko-KR')}ms · ${Number(item.summaryInputTokens || 0).toLocaleString('ko-KR')} in / ${Number(item.summaryOutputTokens || 0).toLocaleString('ko-KR')} out`;
    const summaryTitle = hasSummary
      ? `${item.summaryModel || 'model'} · ${item.summaryPromptVersion || 'prompt'}${usage}`
      : item.summaryErrorCode
        ? `AI 요약 실패: ${item.summaryErrorCode}${usage} · 공식 원문 일부를 표시합니다.`
        : '요약을 사용할 수 없어 공식 원문 일부를 표시합니다.';
    const primaryText = hasSummary
      ? item.summaryKo
      : item.excerpt || '원문에서 세부 내용을 확인할 수 있습니다.';
    return `
      <article class="news-item-card">
        <div class="news-item-meta">
          <span class="news-trust ${item.trust === 'VERIFIED_OFFICIAL' ? 'verified' : ''}">${item.trust === 'VERIFIED_OFFICIAL' ? '공식 확인' : '출처 메타데이터'}</span>
          ${symbolTags}
          <span class="news-summary-status news-summary-${String(item.summaryStatus || 'FAILED').toLowerCase()}" title="${escapeHtml(summaryTitle)}">${summaryLabel}</span>
          <time>${escapeHtml(formatNewsDate(item.publishedAt))}</time>
        </div>
        <h2><a href="${escapeHtml(item.sourceUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(item.title)}</a></h2>
        <p class="news-summary-copy">${escapeHtml(primaryText)}</p>
        ${hasSummary ? `<p class="news-significance"><span>기술적 의미</span>${escapeHtml(item.significanceKo)}</p>` : ''}
        <p class="news-price-boundary"><span>가격 직접 영향</span>이 자료만으로는 확인할 수 없습니다.</p>
        ${hasSummary && item.excerpt ? `
          <details class="news-original">
            <summary>공식 원문 일부 보기</summary>
            <p>${escapeHtml(item.excerpt)}</p>
          </details>` : ''}
        <div class="news-item-footer">
          <strong>${escapeHtml(item.publisher)}</strong>
          <div class="news-topics">${topics}</div>
          <a href="${escapeHtml(item.sourceUrl)}" target="_blank" rel="noopener noreferrer">원문 보기 ↗</a>
        </div>
      </article>`;
  }).join('');
}

function stopNewsSummaryPolling() {
  clearTimeout(state.newsSummaryTimer);
  state.newsSummaryTimer = null;
}

function newsTopicLabel(topic) {
  const labels = {
    DEVELOPMENT: '개발',
    RELEASE: '릴리스',
    SECURITY: '보안',
    NETWORK_UPGRADE: '네트워크 업그레이드',
  };
  return labels[topic] || topic;
}

function formatNewsDate(iso) {
  if (!iso) return '발표 시각 미확인';
  return new Intl.DateTimeFormat('ko-KR', {
    year: 'numeric', month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit',
  }).format(new Date(iso));
}

async function requestNewsRefresh() {
  const button = $('news-refresh-btn');
  button.disabled = true;
  button.classList.add('is-loading');
  button.querySelector('span').textContent = '갱신 요청 중…';
  try {
    const job = await api('/api/news/refresh', { method: 'POST' });
    if (job.status === 'COMPLETED') {
      toast(job.reused ? '최신 공용 자료를 재사용했습니다' : '공식자료 갱신이 완료되었습니다');
      await loadNewsData();
      return;
    }
    toast(job.reused ? '이미 진행 중인 갱신 작업을 확인합니다' : '백그라운드 갱신을 시작했습니다');
    pollNewsRefresh(job.jobId, 0);
  } catch (error) {
    toast(error.message);
  } finally {
    button.disabled = false;
    button.classList.remove('is-loading');
    button.querySelector('span').textContent = '공식자료 갱신';
  }
}

async function pollNewsRefresh(jobId, attempt) {
  clearTimeout(state.newsRefreshTimer);
  if (attempt >= 20) {
    $('news-source-status').textContent = '갱신이 계속 진행 중입니다. 잠시 후 다시 확인해주세요.';
    return;
  }
  try {
    const job = await api(`/api/news/refresh-jobs/${encodeURIComponent(jobId)}`);
    if (job.status === 'COMPLETED') {
      toast(`공식자료 ${job.insertedCount}건 추가 · ${job.updatedCount}건 갱신`);
      await loadNewsData();
      return;
    }
    if (job.status === 'FAILED') {
      toast(`공식자료 갱신 실패 · ${job.errorCode || 'SOURCE_UNAVAILABLE'}`);
      await loadNewsData();
      return;
    }
    $('news-source-status').textContent = job.status === 'RUNNING'
      ? '공식자료를 백그라운드에서 수집하고 있습니다.'
      : '갱신 작업이 대기 중입니다.';
  } catch (error) {
    toast(error.message);
    return;
  }
  state.newsRefreshTimer = setTimeout(() => pollNewsRefresh(jobId, attempt + 1), 1000);
}

async function loadActivityData() {
  $('activity-history-list').innerHTML = '<div class="empty-row">거래 내역을 불러오는 중…</div>';
  try {
    const results = await Promise.all(state.assets.map(async (asset) => {
      const transactions = await api(`/api/assets/${asset.id}/transactions`);
      return transactions.map((transaction) => ({
        ...transaction,
        assetId: asset.id,
        assetName: asset.name,
        assetType: asset.type,
        symbol: asset.displaySymbol || asset.symbol,
        currency: asset.currency,
      }));
    }));
    state.activityTransactions = results.flat()
      .sort((a, b) => new Date(b.tradedAt) - new Date(a.tradedAt) || b.id - a.id);
    renderActivityLedger();
  } catch (error) {
    $('activity-history-list').innerHTML = `<div class="empty-row">${escapeHtml(error.message)}</div>`;
  }
}

$('activity-filter-tabs').addEventListener('click', (event) => {
  const tab = event.target.closest('.tab[data-filter]');
  if (!tab) return;
  $('activity-filter-tabs').querySelectorAll('.tab').forEach((item) => item.classList.remove('active'));
  tab.classList.add('active');
  state.activityFilter = tab.dataset.filter;
  renderActivityLedger();
});

$('activity-search').addEventListener('input', (event) => {
  state.activitySearch = event.target.value.trim().toLowerCase();
  renderActivityLedger();
});

function renderActivityLedger() {
  const all = state.activityTransactions;
  const search = state.activitySearch;
  const filtered = all.filter((transaction) => {
    if (state.activityFilter && transaction.type !== state.activityFilter) return false;
    if (!search) return true;
    return [transaction.assetName, transaction.symbol, transaction.memo, transaction.type]
      .some((value) => String(value || '').toLowerCase().includes(search));
  });

  $('activity-total-count').textContent = `${all.length.toLocaleString('ko-KR')}건`;
  $('activity-asset-count').textContent = `${new Set(all.map((item) => item.assetId)).size}개`;
  $('activity-last-date').textContent = all.length ? fmtDate(all[0].tradedAt) : '-';

  $('activity-history-list').innerHTML = filtered.length ? filtered.map((transaction) => {
    const settlement = state.assets.find((asset) => asset.id === transaction.settlementAssetId);
    const inbound = isInboundTransaction(transaction.type);
    const quantity = privateText(`${inbound ? '+' : '−'}${num(transaction.quantity)} ${transaction.symbol}`);
    const settlementLabel = transaction.settlementAmount != null && settlement
      ? privateText(`${transaction.type === 'SELL' ? '+' : '−'}${num(transaction.settlementAmount, 4)} ${settlement.currency}`)
      : transaction.type === 'DEPOSIT' ? '외부 입금' : transaction.type === 'WITHDRAW' ? '외부 출금' : '-';
    return `
      <div class="ledger-row" data-asset-id="${transaction.assetId}" data-asset-name="${escapeHtml(transaction.assetName)}">
        <div class="ledger-asset">
          ${transaction.assetType === 'CASH' || transaction.assetType === 'BANK'
            ? currencyIcon(transaction.currency)
            : assetIcon(transaction.assetType, transaction.symbol)}
          <div class="ledger-copy">
            <strong><span class="ledger-type ledger-type-${transaction.type}">${transactionLabel(transaction.type)}</span>${escapeHtml(transaction.assetName)}</strong>
            <small>${transaction.symbol}${transaction.memo ? ` · ${escapeHtml(transaction.memo)}` : ''}</small>
          </div>
        </div>
        <div class="ledger-date"><strong>${fmtDate(transaction.tradedAt)}</strong><small>거래 시점</small></div>
        <div class="ledger-amount"><strong class="${state.privacyHidden ? '' : inbound ? 'up' : 'down'}">${escapeHtml(quantity)}</strong><small>${transaction.price == null ? '잔액 변동' : escapeHtml(privateText(`@ ${num(transaction.price, 4)} ${transaction.currency}`))}</small></div>
        <div class="ledger-settlement"><strong>${escapeHtml(settlementLabel)}</strong><small>${escapeHtml(settlement?.name || 'Portfolio 외부')}</small></div>
      </div>`;
  }).join('') : '<div class="empty-row">조건에 맞는 거래 내역이 없습니다</div>';
}

async function loadAnalysisData() {
  $('analysis-history-status').textContent = 'Snapshot을 불러오는 중…';
  try {
    const [historyResponse, portfolio] = await Promise.all([
      api('/api/dashboard/history?days=90'),
      api('/api/portfolio'),
    ]);
    state.analysisHistory = historyResponse;
    state.analysisPortfolio = portfolio;
    renderAnalysisSummary();
    renderAnalysisChart();
    renderAnalysisComposition();
    renderAnalysisRanking();
    $('analysis-demo-badge').hidden = !historyResponse.demoData;
  } catch (error) {
    $('analysis-history-status').textContent = '분석 데이터를 불러오지 못했습니다';
    toast(error.message);
  }
}

function renderAnalysisSummary() {
  const dashboard = state.dashboard;
  const daily = dashboard?.dailyPnl;
  const dailyValue = $('analysis-daily-pnl');
  dailyValue.textContent = daily?.available
    ? privateText(`${signedWon(daily.amountKRW)} (${pct(daily.rate)})`)
    : '-';
  dailyValue.className = daily?.available ? privateSignClass(daily.amountKRW) : '';
  $('analysis-daily-caption').textContent = daily?.available
    ? `${fmtDate(daily.baselineAt)} 기준 · 외부 입출금 반영`
    : (daily?.unavailableReason || '기준을 준비하고 있습니다');
  $('analysis-current-total').textContent = privateText(won(dashboard?.totalAssetKRW));

  const unrealized = dashboard?.investmentSummary?.unrealizedPnl;
  $('analysis-unrealized-pnl').textContent = privateText(signedWon(unrealized));
  $('analysis-unrealized-pnl').className = privateSignClass(unrealized);
  $('analysis-unrealized-rate').textContent = dashboard?.investmentSummary?.unrealizedPnlRate == null
    ? '평단 입력 시 계산'
    : privateText(`${pct(dashboard.investmentSummary.unrealizedPnlRate)} · 미실현`);

  const realized = dashboard?.investmentSummary?.realizedPnl;
  $('analysis-realized-pnl').textContent = privateText(signedWon(realized));
  $('analysis-realized-pnl').className = privateSignClass(realized);
}

function renderAnalysisChart() {
  const points = analysisPoints(state.analysisDays);
  const empty = $('analysis-chart-empty');
  const canvas = $('analysis-value-chart');
  if (state.analysisChart) state.analysisChart.destroy();

  if (!points.length) {
    canvas.hidden = true;
    empty.hidden = false;
    $('analysis-period-change').textContent = '-';
    $('analysis-history-status').textContent = '아직 저장된 Snapshot이 없습니다';
    return;
  }

  canvas.hidden = false;
  empty.hidden = true;
  const values = points.map((point) => Number(point.valueKRW));
  const change = values.at(-1) - values[0];
  const changeRate = values[0] === 0 ? null : change / values[0] * 100;
  const changeElement = $('analysis-period-change');
  changeElement.textContent = privateText(`${signedWon(change)}${changeRate == null ? '' : ` (${pct(changeRate)})`}`);
  changeElement.className = privateSignClass(change);

  const snapshotCount = points.filter((point) => !point.current).length;
  $('analysis-history-status').textContent = snapshotCount
    ? `${snapshotCount}개 Snapshot · 현재값 포함`
    : '현재값만 확보됨 · Snapshot 수집 중';

  const first = values[0];
  const last = values.at(-1);
  const color = last >= first ? '#3ddc97' : '#ff647c';
  const context = canvas.getContext('2d');
  const fill = context.createLinearGradient(0, 0, 0, 330);
  fill.addColorStop(0, color + '32');
  fill.addColorStop(1, color + '00');

  state.analysisChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels: points.map((point) => analysisPointLabel(point, state.analysisDays)),
      datasets: [{
        data: values,
        borderColor: color,
        backgroundColor: fill,
        borderWidth: 2.2,
        pointRadius: points.length <= 8 ? 3 : 0,
        pointHoverRadius: 5,
        pointBackgroundColor: color,
        pointBorderColor: '#121720',
        pointBorderWidth: 2,
        fill: true,
        tension: .36,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      interaction: { intersect: false, mode: 'index' },
      plugins: {
        legend: { display: false },
        tooltip: {
          displayColors: false,
          callbacks: { label: (context) => privateText(won(context.parsed.y)) },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { display: false },
          ticks: { color: '#697486', maxTicksLimit: 8, font: { size: 10 } },
        },
        y: {
          position: 'right',
          grid: { color: 'rgba(255,255,255,.055)' },
          border: { display: false },
          ticks: {
            color: '#697486',
            maxTicksLimit: 5,
            font: { size: 10 },
            callback: (value) => state.privacyHidden ? '•••' : compactWon(value),
          },
        },
      },
    },
  });
}

function analysisPoints(days) {
  const dashboard = state.dashboard;
  const cutoff = new Date();
  cutoff.setDate(cutoff.getDate() - (days - 1));
  cutoff.setHours(0, 0, 0, 0);
  let points = (state.analysisHistory?.points || [])
    .filter((point) => new Date(`${point.date}T00:00:00`) >= cutoff)
    .map((point) => ({ ...point, current: false }));

  if (days === 1 && dashboard?.dailyPnl?.available) {
    points = [{
      date: dashboard.dailyPnl.baselineAt.slice(0, 10),
      capturedAt: dashboard.dailyPnl.baselineAt,
      valueKRW: dashboard.dailyPnl.baselineValueKRW,
      current: false,
    }];
  }
  if (dashboard?.totalAssetKRW != null) {
    points.push({
      date: new Date().toISOString().slice(0, 10),
      capturedAt: new Date().toISOString(),
      valueKRW: dashboard.totalAssetKRW,
      current: true,
    });
  }

  const unique = new Map();
  points.forEach((point) => unique.set(point.capturedAt, point));
  return [...unique.values()].sort((a, b) => new Date(a.capturedAt) - new Date(b.capturedAt));
}

function analysisPointLabel(point, days) {
  if (point.current) return '현재';
  if (days === 1) return `기준 ${fmtDate(point.capturedAt).split(' ')[1]}`;
  const date = new Date(`${point.date}T00:00:00`);
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function compactWon(value) {
  const numeric = Number(value);
  if (Math.abs(numeric) >= 100000000) {
    const units = (numeric / 100000000).toFixed(3).replace(/\.?0+$/, '');
    return `₩${units}억`;
  }
  if (Math.abs(numeric) >= 10000) return `₩${Math.round(numeric / 10000).toLocaleString('ko-KR')}만`;
  return won(numeric);
}

function renderAnalysisComposition() {
  const assets = state.assets
    .filter((asset) => asset.valuationKRW != null && Number(asset.valuationKRW) > 0)
    .sort((a, b) => Number(b.valuationKRW) - Number(a.valuationKRW));
  const total = assets.reduce((sum, asset) => sum + Number(asset.valuationKRW), 0);
  $('analysis-composition').innerHTML = assets.length ? assets.map((asset) => {
    const ratio = total === 0 ? 0 : Number(asset.valuationKRW) / total * 100;
    return `
      <div class="composition-row" data-asset-id="${asset.id}">
        <div class="composition-asset">
          ${assetIcon(asset.type, asset.displaySymbol || asset.symbol)}
          <div class="composition-copy">
            <strong>${escapeHtml(asset.name)}</strong>
            <small>${asset.displaySymbol || asset.symbol} · ${TYPE_LABEL[asset.type] || asset.type}</small>
          </div>
        </div>
        <div class="composition-bar"><i style="width:${Math.max(ratio, 1)}%"></i></div>
        <div class="composition-value"><strong>${privateText(won(asset.valuationKRW))}</strong><small>${ratio.toFixed(1)}%</small></div>
      </div>`;
  }).join('') : '<div class="empty-row">평가 가능한 자산이 없습니다</div>';
}

function renderAnalysisRanking() {
  const positions = state.analysisPortfolio
    .filter((asset) => asset.unrealizedPnl != null)
    .sort((a, b) => Number(b.unrealizedPnl) - Number(a.unrealizedPnl));
  $('analysis-ranking').innerHTML = positions.length ? positions.map((asset, index) => `
    <div class="ranking-row" data-asset-id="${asset.assetId}">
      <span class="ranking-number">${index + 1}</span>
      <div class="ranking-copy">
        <strong>${escapeHtml(asset.name)}</strong>
        <small>${asset.displaySymbol || asset.symbol} · ${privateText(won(asset.valuationKRW))}</small>
      </div>
      <div class="ranking-value">
        <strong class="${privateSignClass(asset.unrealizedPnl)}">${privateText(signedWon(asset.unrealizedPnl))}</strong>
        <small class="${privateSignClass(asset.unrealizedPnlRate)}">${privateText(pct(asset.unrealizedPnlRate))}</small>
      </div>
    </div>`).join('') : '<div class="empty-row">평단이 입력된 Position이 없습니다</div>';
}

function assetIcon(type, symbol) {
  const normalized = String(symbol || '').toUpperCase();
  if (normalized === 'BTC') return '<span class="asset-icon asset-icon-btc">₿</span>';
  if (normalized === 'ETH') return '<span class="asset-icon asset-icon-eth">Ξ</span>';
  const label = normalized.replace(/[^A-Z0-9가-힣]/g, '').slice(0, 2) || '•';
  return `<span class="asset-icon asset-icon-${String(type).toLowerCase()}">${label}</span>`;
}

function currencyIcon(currency) {
  const normalized = String(currency || '').toUpperCase();
  const labels = { KRW: '₩', USD: '$', USDT: '₮' };
  const css = labels[normalized] ? normalized.toLowerCase() : 'other';
  return `<span class="currency-icon currency-${css}">${labels[normalized] || normalized.slice(0, 1)}</span>`;
}

function isInboundTransaction(type) { return type === 'BUY' || type === 'DEPOSIT'; }

function transactionLabel(type) {
  return { BUY: '매수', SELL: '매도', DEPOSIT: '입금', WITHDRAW: '출금' }[type] || type;
}

function priceSourceLabel(source) {
  return { API: 'API 자동', MANUAL: '직접 입력', CSV: 'CSV 입력', MYDATA: 'MyData', WEB3: '지갑 연동' }[source] || '';
}

function priceTrustLabel(asset) {
  if (!asset?.currentPrice) return '';
  const source = priceSourceLabel(asset.source);
  const updated = asset.priceUpdatedAt ? timeAgo(asset.priceUpdatedAt) : '갱신 시각 없음';
  return [source, updated].filter(Boolean).join(' · ');
}

function quantityLabel(asset) {
  const unit = asset.type === 'STOCK' ? '주' : asset.type === 'CRYPTO' ? (asset.displaySymbol || asset.symbol) : '원';
  return `${num(asset.quantity)}${unit}`;
}

function renderPreview(previewId, result) {
  const preview = $(previewId);
  if (!preview) return;
  if (!result) {
    preview.innerHTML = `
      <div class="transaction-preview-head"><strong>저장 후 예상</strong><span>LIVE PREVIEW</span></div>
      <div class="preview-placeholder">수량과 단가를 입력하면 자산 변화를 미리 보여드려요.</div>`;
    return;
  }
  preview.innerHTML = `
    <div class="transaction-preview-head"><strong>저장 후 예상</strong><span>LIVE PREVIEW</span></div>
    <div class="transaction-preview-body">
      ${result.items.map((item) => `
        <div class="preview-item"><span>${escapeHtml(item.label)}</span><strong class="${item.className || ''}">${escapeHtml(item.value)}</strong></div>`).join('')}
    </div>
    ${result.warning ? `<p class="preview-warning">${escapeHtml(result.warning)}</p>` : ''}
    <p class="preview-note">예상값이며 저장 후 서버의 평단·실현손익 계산이 최종 반영됩니다.</p>`;
}

function updateTradePreview({ asset, side, quantity, price, exchangeRate, settlement, previewId, submitId }) {
  const qty = Number(quantity);
  const unitPrice = Number(price);
  const fx = Number(exchangeRate);
  const submit = $(submitId);
  if (!asset || !settlement || !(qty > 0) || !(unitPrice > 0) || !(fx > 0)) {
    renderPreview(previewId, null);
    if (submit) submit.disabled = true;
    return false;
  }

  const selling = side === 'sell';
  const currentQuantity = Number(asset.quantity || 0);
  const currentSettlement = Number(settlement.quantity || 0);
  const settlementAmount = qty * unitPrice;
  const quantityAfter = currentQuantity + (selling ? -qty : qty);
  const settlementAfter = currentSettlement + (selling ? settlementAmount : -settlementAmount);
  const tradeValueKrw = settlementAmount * fx;
  let warning = '';
  if (selling && qty > currentQuantity) {
    warning = `보유 수량 ${num(currentQuantity)} ${asset.displaySymbol || asset.symbol}보다 많이 매도할 수 없습니다.`;
  } else if (!selling && settlementAmount > currentSettlement) {
    warning = `${settlement.name} 잔액이 ${num(settlementAmount - currentSettlement, 4)} ${settlement.currency} 부족합니다.`;
  }

  let resultLabel;
  let resultValue;
  let resultClass = '';
  if (selling) {
    const avgPrice = asset.avgPrice == null ? null : Number(asset.avgPrice);
    const realized = avgPrice == null ? null : (unitPrice * fx - avgPrice) * qty;
    resultLabel = '이번 거래 예상 손익';
    resultValue = realized == null ? '평단 입력 필요' : signedWon(realized);
    resultClass = realized == null ? '' : signClass(realized);
  } else {
    const avgPrice = asset.avgPrice == null ? null : Number(asset.avgPrice);
    const averageAfter = currentQuantity > 0 && avgPrice == null
      ? null
      : ((avgPrice || 0) * currentQuantity + tradeValueKrw) / quantityAfter;
    resultLabel = '예상 평균 매수가';
    resultValue = averageAfter == null ? '기존 평단 입력 필요' : `≈ ${won(averageAfter)}`;
  }

  renderPreview(previewId, {
    items: [
      {
        label: `${asset.name} 보유량`,
        value: `${num(currentQuantity)} → ${num(Math.max(quantityAfter, 0))} ${asset.displaySymbol || asset.symbol}`,
      },
      {
        label: `${settlement.name} 잔액`,
        value: `${num(currentSettlement, 4)} → ${num(Math.max(settlementAfter, 0), 4)} ${settlement.currency}`,
      },
      { label: resultLabel, value: resultValue, className: resultClass },
      { label: '거래금액 원화 환산', value: won(tradeValueKrw) },
    ],
    warning,
  });
  if (submit) submit.disabled = Boolean(warning);
  return !warning;
}

function updateCashPreview({ asset, side, quantity, exchangeRate, previewId, submitId }) {
  const amount = Number(quantity);
  const fx = asset?.currency === 'KRW' ? 1 : Number(exchangeRate);
  const submit = $(submitId);
  if (!asset || !(amount > 0) || !(fx > 0)) {
    renderPreview(previewId, null);
    if (submit) submit.disabled = true;
    return false;
  }
  const withdrawing = side === 'withdraw';
  const current = Number(asset.quantity || 0);
  const after = current + (withdrawing ? -amount : amount);
  const warning = withdrawing && amount > current
    ? `현재 잔액보다 ${num(amount - current, asset.currency === 'KRW' ? 0 : 4)} ${asset.currency} 많이 출금할 수 없습니다.`
    : '';
  renderPreview(previewId, {
    items: [
      { label: `${asset.name} 현재 잔액`, value: `${num(current, asset.currency === 'KRW' ? 0 : 4)} ${asset.currency}` },
      { label: '저장 후 잔액', value: `${num(Math.max(after, 0), asset.currency === 'KRW' ? 0 : 4)} ${asset.currency}` },
      { label: withdrawing ? '출금 금액' : '입금 금액', value: `${withdrawing ? '−' : '+'}${num(amount, asset.currency === 'KRW' ? 0 : 4)} ${asset.currency}`, className: withdrawing ? 'down' : 'up' },
      { label: '원화 환산', value: won(amount * fx) },
    ],
    warning,
  });
  if (submit) submit.disabled = Boolean(warning);
  return !warning;
}

/* ── 자산 상세 드로어 (조회 + 정정) ────────────────
 *
 * FAB(+)는 "새로 만드는" 동선이라 이미 있는 것을 고칠 수 없다. 수정은 대상에서 출발해야 하므로,
 * 자산 행을 누르면 열리는 이 드로어에 정정 수단을 모았다.
 */

document.addEventListener('click', async (e) => {
  const mobilePriceButton = e.target.closest('[data-mobile-price-id]');
  if (mobilePriceButton) {
    e.preventDefault();
    e.stopPropagation();
    const assetId = Number(mobilePriceButton.dataset.mobilePriceId);
    if (state.mobilePriceAssetIds.has(assetId)) state.mobilePriceAssetIds.delete(assetId);
    else state.mobilePriceAssetIds.add(assetId);
    renderPortfolio(state.portfolio);
    return;
  }
  const row = e.target.closest('[data-asset-id]');
  if (!row) return;
  openAssetDetail(Number(row.dataset.assetId));
});

async function openAssetDetail(assetId) {
  state.detailAssetId = assetId;
  $('drawer-body').innerHTML = '<div class="empty-row">불러오는 중…</div>';
  $('drawer-backdrop').hidden = false;
  document.querySelector('.asset-drawer').scrollTop = 0;
  await renderAssetDetail();
}

async function renderAssetDetail() {
  const assetId = state.detailAssetId;
  try {
    const asset = await api(`/api/assets/${assetId}`);
    const isInvestment = asset.type === 'STOCK' || asset.type === 'CRYPTO';
    const [history, priceHistory, evidenceResult] = await Promise.all([
      api(`/api/assets/${assetId}/transactions`),
      isInvestment
        ? api(`/api/assets/${assetId}/price-history`).catch(() => ({ available: false, points: [] }))
        : Promise.resolve(null),
      isInvestment
        ? api(`/api/assets/${assetId}/evidence-documents`)
            .then((items) => ({ items, error: null }))
            .catch((error) => ({ items: [], error: error.message }))
        : Promise.resolve({ items: [], error: null }),
    ]);
    const evidenceDocuments = evidenceResult.items;
    $('drawer-title').innerHTML = `${escapeHtml(asset.name)} <span class="row-sym">· ${asset.displaySymbol || asset.symbol}</span>`;

    const valuationLabel = asset.exchangeRateMissing ? '환율 입력 필요' : won(asset.valuationKRW);
    const pnlLabel = asset.unrealizedPnl == null
      ? '-'
      : `${signedWon(asset.unrealizedPnl)} (${pct(asset.unrealizedPnlRate)})`;
    const averageLabel = asset.costBasisMissing
      ? '-'
      : `${num(asset.avgPriceOriginal ?? asset.avgPrice, 4)} ${asset.avgPriceOriginal != null ? asset.currency : 'KRW'}`;
    $('drawer-body').innerHTML = `
      <div class="position-hero">
        <span>${asset.marketLabel}</span>
        <strong class="${asset.exchangeRateMissing ? 'rate-missing' : ''}">${valuationLabel}</strong>
        ${isInvestment ? `<em class="${signClass(asset.unrealizedPnl)}">${pnlLabel}</em>` : ''}
      </div>

      <div class="detail-stats">
        <div><span>보유 수량</span><strong>${num(asset.quantity)} ${isInvestment ? (asset.type === 'STOCK' ? '주' : asset.displaySymbol) : asset.currency}</strong></div>
        ${isInvestment ? `<div><span>평균 매수가</span><strong>${averageLabel}</strong></div>` : ''}
        ${isInvestment ? `<div><span>현재 시장가격</span><strong>${asset.currentPrice == null ? '-' : `${num(asset.currentPrice, 4)} ${asset.currency}`}</strong></div>` : ''}
        ${asset.currency !== 'KRW' ? `<div><span>현재 환율</span><strong>${asset.exchangeRateMissing ? '입력 필요' : num(asset.exchangeRate, 2) + ' KRW'}</strong></div>` : ''}
        ${isInvestment ? `<div><span>실현손익</span><strong class="${asset.costBasisMissing ? '' : signClass(asset.realizedPnl)}">${asset.costBasisMissing ? '-' : signedWon(asset.realizedPnl)}</strong></div>` : ''}
        ${isInvestment ? `<div><span>시세 출처</span><strong>${priceSourceLabel(asset.source) || '확인 필요'} · ${asset.marketLabel}</strong></div>` : ''}
        ${isInvestment ? `<div><span>마지막 갱신</span><strong>${asset.priceUpdatedAt ? `${timeAgo(asset.priceUpdatedAt).replace(' 기준', '')} · ${fmtDate(asset.priceUpdatedAt).split(' ')[1]}` : '-'}</strong></div>` : ''}
      </div>

      ${isInvestment ? `
        <div class="asset-price-chart">
          <div class="asset-price-chart-head">
            <strong>최근 시장가격</strong>
            <span>${priceHistory?.source || '7일 · 일봉'}${priceHistory?.stale ? ' · 마지막 조회값' : ''}</span>
          </div>
          ${priceHistory?.available
            ? '<canvas id="asset-price-chart"></canvas>'
            : '<div class="asset-chart-empty">가격 차트를 불러오지 못했습니다</div>'}
        </div>` : ''}

      <div class="detail-actions">
        ${isInvestment
          ? '<button class="chip chip-primary" data-act="trade" data-side="buy">매수</button>'
              + '<button class="chip chip-primary" data-act="trade" data-side="sell">매도</button>'
          : '<button class="chip chip-primary" data-act="cash" data-side="deposit">입금</button>'
              + '<button class="chip chip-primary" data-act="cash" data-side="withdraw">출금</button>'}
        ${isInvestment ? '<button class="chip" data-act="quantity">수량 정정</button>' : ''}
        <button class="chip" data-act="rename">이름 수정</button>
        ${isInvestment ? '<button class="chip" data-act="refresh">시세 새로고침</button>' : ''}
        ${isInvestment ? '<button class="chip" data-act="price">시세 입력</button>' : ''}
        ${isInvestment ? '<button class="chip" data-act="evidence">근거 자료 추가</button>' : ''}
        ${isInvestment ? '<button class="chip chip-agent" data-act="agent">AI 근거 분석</button>' : ''}
        ${asset.currency !== 'KRW' ? `<button class="chip ${asset.exchangeRateMissing ? 'chip-primary' : ''}" data-act="fx">${asset.exchangeRateMissing ? '현재 환율 입력' : '환율 수정'}</button>` : ''}
        ${asset.defaultSettlementAsset ? '' : '<button class="chip chip-danger" data-act="remove">자산 삭제</button>'}
      </div>

      ${isInvestment ? '<section id="agent-panel" class="agent-panel" hidden></section>' : ''}

      ${isInvestment ? `
        <h4 class="detail-heading">근거 자료 <em class="hint">${evidenceResult.error ? '확인 불가' : `공식자료·뉴스·메모 ${evidenceDocuments.length}건`}</em></h4>
        <div class="evidence-list">
          ${evidenceResult.error
            ? `<div class="empty-row">자료를 불러오지 못했습니다 · ${escapeHtml(evidenceResult.error)}</div>`
            : evidenceDocuments.length ? evidenceDocuments.map((document) => `
            <div class="evidence-item">
              <div class="evidence-item-main">
                <div class="evidence-meta">
                  <span>${evidenceSourceLabel(document.sourceType)}</span>
                  <em>${document.publishedAt ? fmtDate(document.publishedAt) : '발표 시각 미입력'}</em>
                </div>
                ${document.sourceUrl
                  ? `<a href="${escapeHtml(document.sourceUrl)}" target="_blank" rel="noopener noreferrer">${escapeHtml(document.title)}</a>`
                  : `<strong>${escapeHtml(document.title)}</strong>`}
                <p>${escapeHtml(document.publisher || '사용자 메모')} · ${evidenceTrustLabel(document.trust)}</p>
              </div>
              <button class="btn-icon btn-evidence-del" data-evidence-id="${document.id}" title="이 자료 삭제">🗑</button>
            </div>`).join('') : '<div class="empty-row">등록된 근거 자료가 없습니다</div>'}
        </div>` : ''}

      <h4 class="detail-heading">거래 내역 <em class="hint">잘못 입력한 거래를 지우면 평단가·실현손익이 다시 계산됩니다</em></h4>
      <div class="list">
        ${history.length ? history.map((t) => `
          <div class="row" style="cursor:default">
            <div class="row-main">
              <div class="row-name"><span class="pill pill-${t.type}">${t.type}</span></div>
              <div class="row-sub">${fmtDate(t.tradedAt)}${t.memo ? ' · ' + escapeHtml(t.memo) : ''}</div>
            </div>
            <div class="row-right">
              <div class="row-value">${num(t.quantity)}</div>
              ${t.price != null ? `<div class="row-delta">${num(t.price, 2)} × ${num(t.exchangeRate, 2)}</div>` : ''}
            </div>
            <button class="btn-icon btn-del" data-tx-id="${t.id}" title="이 거래 삭제">🗑</button>
          </div>`).join('') : '<div class="empty-row">거래 내역이 없습니다</div>'}
      </div>
      <p id="drawer-error" class="error"></p>`;

    if (priceHistory?.available) renderAssetPriceChart(priceHistory);
    bindDetailActions(asset);
  } catch (err) {
    $('drawer-body').innerHTML = `<div class="empty-row">${escapeHtml(err.message)}</div>`;
  }
}

function renderAssetPriceChart(history) {
  const canvas = $('asset-price-chart');
  if (!canvas) return;
  if (state.detailChart) state.detailChart.destroy();

  const points = history.points || [];
  const first = Number(points[0]?.price || 0);
  const last = Number(points[points.length - 1]?.price || 0);
  const color = last >= first ? '#22c55e' : '#ef4444';
  state.detailChart = new Chart(canvas, {
    type: 'line',
    data: {
      labels: points.map((point) => {
        const date = new Date(point.timestamp);
        return `${date.getMonth() + 1}/${date.getDate()}`;
      }),
      datasets: [{
        data: points.map((point) => Number(point.price)),
        borderColor: color,
        backgroundColor: color + '18',
        borderWidth: 2,
        pointRadius: 2,
        pointHoverRadius: 4,
        fill: true,
        tension: .25,
      }],
    },
    options: {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: { callbacks: { label: (ctx) => `${num(ctx.parsed.y, 4)} ${history.currency}` } },
      },
      scales: {
        x: { grid: { display: false }, ticks: { color: '#7f8da3', maxTicksLimit: 7 } },
        y: {
          position: 'right',
          grid: { color: 'rgba(255,255,255,.05)' },
          ticks: { color: '#7f8da3', callback: (value) => num(value, 2) },
        },
      },
    },
  });
}

function bindDetailActions(asset) {
  const err = (message) => { $('drawer-error').textContent = message; };

  $('drawer-body').querySelectorAll('.chip').forEach((button) => {
    button.addEventListener('click', (event) => {
      event.stopPropagation();
      const action = button.dataset.act;
      if (action === 'trade') {
        return openInlineTransactionForm(asset, 'trade', button.dataset.side);
      }
      if (action === 'cash') {
        return openInlineTransactionForm(asset, 'cash', button.dataset.side);
      }
      if (action === 'quantity') return openQuantityCorrectionForm(asset);
      if (action === 'rename') return editField(asset, 'name');
      if (action === 'refresh') return refreshSingleAsset(asset, button);
      if (action === 'price') return editField(asset, 'price');
      if (action === 'evidence') return openEvidenceDocumentForm(asset);
      if (action === 'agent') return openFinancialAgentForm(asset);
      if (action === 'fx') return editField(asset, 'fx');
      if (action === 'remove') return removeAsset(asset);
    });
  });

  $('drawer-body').querySelectorAll('.btn-del').forEach((button) => {
    button.addEventListener('click', async () => {
      if (!confirm('이 거래를 삭제할까요? 남은 거래로 평단가·실현손익이 다시 계산됩니다.')) return;
      err('');
      button.disabled = true;
      try {
        await api(`/api/assets/${asset.id}/transactions/${button.dataset.txId}`, { method: 'DELETE' });
        toast('거래를 삭제하고 다시 계산했습니다');
        await renderAssetDetail();
        await refresh();
      } catch (e) {
        button.disabled = false;
        err(e.message);
      }
    });
  });

  $('drawer-body').querySelectorAll('.btn-evidence-del').forEach((button) => {
    button.addEventListener('click', async () => {
      if (!confirm('이 근거 자료를 삭제할까요?')) return;
      err('');
      button.disabled = true;
      try {
        await api(`/api/assets/${asset.id}/evidence-documents/${button.dataset.evidenceId}`, {
          method: 'DELETE',
        });
        toast('근거 자료를 삭제했습니다');
        await renderAssetDetail();
      } catch (error) {
        button.disabled = false;
        err(error.message);
      }
    });
  });
}

/* ── 금융 Evidence Agent ─────────────────────────
 *
 * 답변은 현재 화면에만 보여주며 서버 Trace에는 질문/답변 원문 대신 해시만 남는다.
 * 모델은 설명만 만들고 계산값과 결론은 getAssetEvidence Tool의 구조화 응답을 따른다.
 */

function openFinancialAgentForm(asset) {
  const panel = $('agent-panel');
  if (!panel) return;

  panel.hidden = false;
  panel.innerHTML = `
    <div class="agent-panel-head">
      <div>
        <span class="agent-kicker">FOLIO EVIDENCE AGENT</span>
        <h4>${escapeHtml(asset.displaySymbol || asset.symbol)} 근거 분석</h4>
      </div>
      <span class="agent-readonly">읽기 전용</span>
    </div>
    <p class="agent-guide">등록된 자산과 계산 근거만 사용합니다. 근거가 부족하면 확인 불가로 답합니다.</p>
    <textarea id="agent-question" class="agent-question" rows="3" maxlength="500"
      placeholder="예: 왜 현재 원화 평가금액을 계산할 수 없어?">이 자산의 현재 평가금액과 계산 근거를 설명해줘.</textarea>
    <div class="agent-actions">
      <button id="agent-close" class="btn-ghost sm" type="button">닫기</button>
      <button id="agent-submit" class="btn-primary agent-submit" type="button">근거로 답변하기</button>
    </div>
    <p id="agent-error" class="error" aria-live="polite"></p>
    <div id="agent-result"></div>`;

  $('agent-close').addEventListener('click', () => {
    panel.hidden = true;
    panel.innerHTML = '';
  });
  $('agent-submit').addEventListener('click', () => runFinancialAgent(asset));
  $('agent-question').focus();
}

async function runFinancialAgent(asset) {
  const question = $('agent-question').value.trim();
  const button = $('agent-submit');
  const error = $('agent-error');
  const result = $('agent-result');

  error.textContent = '';
  if (!question) {
    error.textContent = '질문을 입력해주세요.';
    return;
  }

  button.disabled = true;
  button.textContent = '근거 확인 중…';
  result.innerHTML = '<div class="agent-loading">NIM이 Tool을 선택하고 서버 근거를 확인하고 있습니다…</div>';
  try {
    const response = await api(`/api/ai/agent/assets/${asset.id}/ask`, {
      method: 'POST',
      body: JSON.stringify({ question }),
    });
    renderFinancialAgentResult(response);
  } catch (e) {
    result.innerHTML = '';
    error.textContent = e.message;
  } finally {
    button.disabled = false;
    button.textContent = '근거로 답변하기';
  }
}

function renderFinancialAgentResult(response) {
  const result = $('agent-result');
  if (!result) return;
  const conclusion = response.conclusion || 'UNAVAILABLE';
  const conclusionLabel = {
    CONFIRMED: '답변 근거 확인',
    PARTIAL: '답변 근거 일부 확인',
    UNAVAILABLE: '답변 근거 없음',
  }[conclusion] || conclusion;
  const intentLabel = {
    ASSET_CALCULATION: '자산 계산',
    PRICE_TREND: '최근 가격 방향',
    SYMBOL_NEWS: '공용 뉴스·공식자료',
  }[response.intent] || response.intent || '질문 분석';
  const references = response.evidenceReferenceIds || [];
  const tokens = response.inputTokens == null || response.outputTokens == null
    ? '토큰 확인 불가'
    : `${response.inputTokens} in · ${response.outputTokens} out`;

  result.innerHTML = `
    <article class="agent-answer">
      <div class="agent-answer-head">
        <span class="agent-conclusion agent-conclusion-${escapeHtml(conclusion.toLowerCase())}">${escapeHtml(conclusionLabel)}</span>
        <span>${escapeHtml(intentLabel)} · ${escapeHtml(response.model || 'model unknown')}</span>
      </div>
      <p>${escapeHtml(response.answer)}</p>
      <div class="agent-references">
        <strong>사용한 근거 ID</strong>
        ${references.length
          ? `<ul>${references.map((reference) => `<li>${escapeHtml(reference)}</li>`).join('')}</ul>`
          : '<span>표시할 근거 ID가 없습니다.</span>'}
      </div>
      <div class="agent-run-meta">
        <span>${Number(response.latencyMs || 0).toLocaleString('ko-KR')} ms</span>
        <span>${escapeHtml(tokens)}</span>
        <span>${escapeHtml((response.toolsUsed || []).join(', ') || 'Tool 없음')}</span>
        <button id="agent-trace-open" class="agent-trace-button" type="button">실행 Trace 보기</button>
      </div>
      <div id="agent-trace" class="agent-trace" hidden></div>
    </article>`;

  $('agent-trace-open').addEventListener('click', (event) => {
    loadFinancialAgentTrace(response.traceId, event.currentTarget);
  });
}

async function loadFinancialAgentTrace(traceId, button) {
  const container = $('agent-trace');
  if (!container) return;
  if (!container.hidden) {
    container.hidden = true;
    button.textContent = '실행 Trace 보기';
    return;
  }

  button.disabled = true;
  button.textContent = 'Trace 불러오는 중…';
  try {
    const trace = await api(`/api/ai/traces/${encodeURIComponent(traceId)}`);
    container.innerHTML = `
      <div class="agent-trace-summary">
        <span>${escapeHtml(trace.status)}</span>
        <span>원문 질문 저장 ${trace.rawQuestionStored ? '됨' : '안 됨'}</span>
        <span>원문 답변 저장 ${trace.rawAnswerStored ? '됨' : '안 됨'}</span>
      </div>
      <ol class="trace-tree">${renderAgentTraceNodes(trace.steps || [])}</ol>`;
    container.hidden = false;
    button.textContent = '실행 Trace 닫기';
  } catch (e) {
    container.innerHTML = `<p class="error">${escapeHtml(e.message)}</p>`;
    container.hidden = false;
    button.textContent = 'Trace 다시 시도';
  } finally {
    button.disabled = false;
  }
}

function renderAgentTraceNodes(nodes) {
  return nodes.map((node) => `
    <li>
      <div class="trace-node">
        <span class="trace-type">${escapeHtml(node.type)}</span>
        <strong>${escapeHtml(node.name)}</strong>
        <em class="trace-status-${escapeHtml(String(node.status).toLowerCase())}">${escapeHtml(node.status)}</em>
        <small>${Number(node.latencyMs || 0).toLocaleString('ko-KR')} ms</small>
      </div>
      ${(node.referenceIds || []).length
        ? `<div class="trace-references">${node.referenceIds.map((id) => `<code>${escapeHtml(id)}</code>`).join('')}</div>`
        : ''}
      ${(node.children || []).length ? `<ol>${renderAgentTraceNodes(node.children)}</ol>` : ''}
    </li>`).join('');
}

function evidenceSourceLabel(sourceType) {
  if (sourceType === 'OFFICIAL') return '공식자료';
  if (sourceType === 'NEWS') return '뉴스';
  return '사용자 메모';
}

function evidenceTrustLabel(trust) {
  if (trust === 'VERIFIED_OFFICIAL') return '검증된 공식 출처';
  if (trust === 'USER_ASSERTED_OFFICIAL') return '사용자 지정 공식자료';
  if (trust === 'USER_ASSERTED_NEWS') return '사용자 지정 뉴스';
  return '사용자 작성';
}

/** 등록 자산에 외부 자료를 수동으로 붙여넣는다. 서버가 URL을 자동 수집하지는 않는다. */
function openEvidenceDocumentForm(asset) {
  $('drawer-title').textContent = `${asset.name} — 근거 자료 추가`;
  document.querySelector('.asset-drawer').scrollTop = 0;
  $('drawer-body').innerHTML = `
    <div class="inline-form">
      <p class="form-notice">
        원문은 AI 지시가 아닌 인용 자료로만 저장됩니다. 뉴스가 가격 변동의 직접 원인이라고 자동 확정하지 않습니다.
      </p>
      <label>자료 종류
        <select id="evidence-source-type">
          <option value="OFFICIAL">공식자료</option>
          <option value="NEWS">뉴스</option>
          <option value="USER_NOTE">사용자 메모</option>
        </select>
      </label>
      <label>제목<input id="evidence-title" maxlength="200" placeholder="예: 2026년 2분기 실적 발표" /></label>
      <label>발행처 <span class="hint" id="evidence-publisher-hint">(필수)</span>
        <input id="evidence-publisher" maxlength="120" placeholder="예: Apple Investor Relations" />
      </label>
      <label>원문 URL <span class="hint" id="evidence-url-hint">(필수)</span>
        <input id="evidence-url" type="url" maxlength="2048" placeholder="https://..." />
      </label>
      <label>발표 시각 <span class="hint" id="evidence-time-hint">(필수)</span>
        <input id="evidence-published-at" type="datetime-local" />
      </label>
      <label>원문 또는 메모
        <textarea id="evidence-content" rows="10" maxlength="50000" placeholder="분석에 사용할 원문을 붙여넣으세요."></textarea>
        <small class="field-hint">같은 본문은 중복 등록되지 않습니다.</small>
      </label>
      <div class="inline-form-actions">
        <button class="btn-ghost sm" id="evidence-cancel">취소</button>
        <button class="btn-primary" id="evidence-submit">자료 저장</button>
      </div>
      <p id="drawer-error" class="error"></p>
    </div>`;

  const sourceType = $('evidence-source-type');
  const syncRequiredHints = () => {
    const optional = sourceType.value === 'USER_NOTE';
    ['evidence-publisher-hint', 'evidence-url-hint', 'evidence-time-hint'].forEach((id) => {
      $(id).textContent = optional ? '(선택)' : '(필수)';
    });
  };
  sourceType.addEventListener('change', syncRequiredHints);
  $('evidence-cancel').addEventListener('click', () => renderAssetDetail());
  $('evidence-submit').addEventListener('click', async () => {
    const button = $('evidence-submit');
    $('drawer-error').textContent = '';
    button.disabled = true;
    try {
      const publishedValue = $('evidence-published-at').value;
      await api(`/api/assets/${asset.id}/evidence-documents`, {
        method: 'POST',
        body: JSON.stringify({
          sourceType: sourceType.value,
          title: $('evidence-title').value,
          publisher: $('evidence-publisher').value || null,
          sourceUrl: $('evidence-url').value || null,
          publishedAt: publishedValue ? new Date(publishedValue).toISOString() : null,
          content: $('evidence-content').value,
        }),
      });
      toast(`${asset.displaySymbol || asset.symbol} 근거 자료를 저장했습니다`);
      await renderAssetDetail();
    } catch (error) {
      $('drawer-error').textContent = error.message;
      button.disabled = false;
    }
  });
  syncRequiredHints();
  $('evidence-title').focus();
}

/** 최초 등록 수량 오입력을 실제 현재 보유 수량 기준으로 바로잡는다. */
function openQuantityCorrectionForm(asset) {
  const unit = asset.type === 'STOCK' ? '주' : (asset.displaySymbol || asset.symbol);
  const current = Number(asset.quantity || 0);
  $('drawer-title').textContent = `${asset.name} — 보유 수량 정정`;
  document.querySelector('.asset-drawer').scrollTop = 0;
  $('drawer-body').innerHTML = `
    <div class="inline-form">
      <p class="form-notice">
        최초 등록 수량을 잘못 입력한 경우에만 사용하세요. 매도 거래나 대기자금 이동은 만들지 않습니다.<br />
        잘못 입력한 매수·매도는 아래 거래 내역에서 해당 거래를 삭제해야 합니다.
      </p>
      <label>현재 기록된 수량
        <input value="${num(current)} ${unit}" readonly />
      </label>
      <label>실제 보유 수량
        <input id="detail-correction-quantity" type="number" step="any" min="0" value="${asset.quantity}" />
        <small class="field-hint">빼려는 수량이 아니라 정정 후 최종 수량을 입력하세요. 예: 14.7</small>
      </label>
      <div id="detail-correction-preview" class="transaction-preview" aria-live="polite"></div>
      <div class="inline-form-actions">
        <button class="btn-ghost sm" id="detail-correction-cancel">취소</button>
        <button class="btn-primary" id="detail-correction-submit" disabled>수량 정정</button>
      </div>
      <p id="drawer-error" class="error"></p>
    </div>`;

  const input = $('detail-correction-quantity');
  const submitButton = $('detail-correction-submit');
  const syncPreview = () => {
    const raw = input.value.trim();
    const corrected = Number(raw);
    const valid = raw !== '' && Number.isFinite(corrected) && corrected >= 0;
    const difference = valid ? corrected - current : 0;
    submitButton.disabled = !valid || difference === 0;

    if (!valid) {
      $('detail-correction-preview').innerHTML = `
        <div class="transaction-preview-head"><strong>정정 후 예상</strong><span>NO TRADE</span></div>
        <div class="preview-placeholder">0 이상의 실제 보유 수량을 입력해주세요.</div>`;
      return;
    }

    const differenceLabel = difference === 0
      ? '변경 없음'
      : `${difference > 0 ? '+' : '−'}${num(Math.abs(difference))} ${unit}`;
    $('detail-correction-preview').innerHTML = `
      <div class="transaction-preview-head"><strong>정정 후 예상</strong><span>NO TRADE</span></div>
      <div class="transaction-preview-body">
        <div class="preview-item"><span>현재 기록</span><strong>${num(current)} ${unit}</strong></div>
        <div class="preview-item"><span>정정 후</span><strong>${num(corrected)} ${unit}</strong></div>
        <div class="preview-item"><span>수량 차이</span><strong class="${signClass(difference)}">${differenceLabel}</strong></div>
        <div class="preview-item"><span>신규 매도·정산</span><strong>발생하지 않음</strong></div>
      </div>
      ${difference === 0 ? '<p class="preview-warning">현재 기록과 같은 수량입니다.</p>' : ''}
      <p class="preview-note">기존 거래가 있다면 새 최초 수량을 기준으로 평단가·실현손익이 다시 계산됩니다.</p>`;
  };

  input.addEventListener('input', syncPreview);
  $('detail-correction-cancel').addEventListener('click', () => renderAssetDetail());
  submitButton.addEventListener('click', async () => {
    $('drawer-error').textContent = '';
    submitButton.disabled = true;
    try {
      await api(`/api/assets/${asset.id}/quantity`, {
        method: 'PATCH',
        body: JSON.stringify({ quantity: Number(input.value) }),
      });
      toast(`${asset.name} 보유 수량을 정정했습니다`);
      await refresh();
      await renderAssetDetail();
    } catch (error) {
      $('drawer-error').textContent = error.message;
      syncPreview();
    }
  });
  syncPreview();
  input.focus();
  input.select();
}

async function refreshSingleAsset(asset, button) {
  const original = button.textContent;
  button.disabled = true;
  button.textContent = '갱신 중…';
  $('drawer-error').textContent = '';
  try {
    await api(`/api/assets/${asset.id}/refresh`, { method: 'POST' });
    await refresh();
    await renderAssetDetail();
    toast(`${asset.name} 시세를 갱신했습니다`);
  } catch (error) {
    button.disabled = false;
    button.textContent = original;
    $('drawer-error').textContent = error.message;
  }
}

/**
 * 자산 상세 화면 안에서 해당 자산의 거래를 바로 입력한다.
 *
 * @param asset 거래 대상 자산
 * @param kind 투자 거래인지 현금성 거래인지
 * @param initialSide 처음 선택할 거래 방향
 */
function openInlineTransactionForm(asset, kind, initialSide) {
  const isInvestment = kind === 'trade';
  const settlementAssets = isInvestment
    ? state.assets.filter((item) => (item.type === 'CASH' || item.type === 'BANK') && item.currency === asset.currency)
    : [];
  const settlementOptions = settlementAssets
    .map((item) => `<option value="${item.id}">${escapeHtml(item.name)} · ${num(item.quantity)} ${item.currency}</option>`)
    .join('');
  $('drawer-title').textContent = `${asset.name} — ${isInvestment ? '거래 기록' : '잔액 조정'}`;
  document.querySelector('.asset-drawer').scrollTop = 0;
  $('drawer-body').innerHTML = `
    <div class="inline-form">
      <label>거래
        <select id="detail-tx-side">
          ${isInvestment
            ? `<option value="buy" ${initialSide === 'buy' ? 'selected' : ''}>매수</option>
               <option value="sell" ${initialSide === 'sell' ? 'selected' : ''}>매도</option>`
            : `<option value="deposit" ${initialSide === 'deposit' ? 'selected' : ''}>입금</option>
               <option value="withdraw" ${initialSide === 'withdraw' ? 'selected' : ''}>출금</option>`}
        </select>
      </label>
      <label>${isInvestment ? '수량' : '금액'}
        <input id="detail-tx-qty" type="number" step="any" placeholder="${isInvestment ? '0.1' : '1000000'}" />
      </label>
      ${isInvestment
        ? `<label><span id="detail-settlement-label">${initialSide === 'sell' ? '매도대금 받을 곳' : '매수대금 출금할 곳'}</span>
             <select id="detail-tx-settlement" ${settlementOptions ? '' : 'disabled'}>
               ${settlementOptions || '<option>같은 통화의 대기자금이 없습니다</option>'}
             </select>
             <small class="field-hint" id="detail-settlement-hint">${initialSide === 'sell' ? `매도대금이 ${asset.currency} 대기자금에 즉시 입금됩니다.` : `매수대금이 ${asset.currency} 대기자금에서 즉시 차감됩니다.`}</small>
           </label>
           ${settlementOptions ? '' : `<p class="form-notice">${asset.currency} 기본 대기자금을 준비하지 못했습니다. 화면을 새로고침해주세요.</p>`}
           <label>단가 <span class="hint">(${asset.currency} 기준)</span>
             <input id="detail-tx-price" type="number" step="any" value="${asset.currentPrice ?? ''}" placeholder="현재 시장가격" />
           </label>
           <label>거래 시점 환율 <span class="hint" id="detail-tx-fx-hint"></span>
             <input id="detail-tx-fx" type="number" step="any" placeholder="${asset.currency === 'KRW' ? '1' : '예: 1415'}" />
           </label>`
        : asset.currency !== 'KRW'
          ? `<label>거래 시점 환율 <span class="hint" id="detail-tx-fx-hint"></span>
               <input id="detail-tx-fx" type="number" step="any" placeholder="예: 1415" />
             </label>`
          : ''}
      <label>거래 시점<input id="detail-tx-date" type="datetime-local" value="${today()}" /></label>
      <label>메모 <span class="hint">(선택)</span><input id="detail-tx-memo" /></label>
      <div id="detail-tx-preview" class="transaction-preview" aria-live="polite"></div>
      <div class="inline-form-actions">
        <button class="btn-ghost sm" id="detail-tx-cancel">취소</button>
        <button class="btn-primary" id="detail-tx-submit" disabled>기록</button>
      </div>
      <p id="drawer-error" class="error"></p>
    </div>`;

  $('detail-tx-cancel').addEventListener('click', () => renderAssetDetail());
  const detailFx = $('detail-tx-fx');
  const detailFxHint = $('detail-tx-fx-hint');
  const detailDate = $('detail-tx-date');
  if (detailFx) {
    syncExchangeRateField({
      input: detailFx,
      hint: detailFxHint,
      asset,
      dateValue: detailDate.value,
      reset: true,
    });
    detailFx.addEventListener('input', () => {
      markExchangeRateManual(detailFx, detailFxHint, asset, detailDate.value);
    });
  }
  const syncInlinePreview = () => {
    const side = $('detail-tx-side').value;
    if (isInvestment) {
      const selling = $('detail-tx-side').value === 'sell';
      $('detail-settlement-label').textContent = selling ? '매도대금 받을 곳' : '매수대금 출금할 곳';
      $('detail-settlement-hint').textContent = selling
        ? `매도대금이 ${asset.currency} 대기자금에 즉시 입금됩니다.`
        : `매수대금이 ${asset.currency} 대기자금에서 즉시 차감됩니다.`;
      const settlement = state.assets.find((item) => String(item.id) === $('detail-tx-settlement').value);
      updateTradePreview({
        asset,
        side,
        quantity: $('detail-tx-qty').value,
        price: $('detail-tx-price').value,
        exchangeRate: exchangeRateForPreview(asset, detailFx),
        settlement,
        previewId: 'detail-tx-preview',
        submitId: 'detail-tx-submit',
      });
    } else {
      updateCashPreview({
        asset,
        side,
        quantity: $('detail-tx-qty').value,
        exchangeRate: exchangeRateForPreview(asset, detailFx),
        previewId: 'detail-tx-preview',
        submitId: 'detail-tx-submit',
      });
    }
  };
  ['detail-tx-side', 'detail-tx-qty', 'detail-tx-settlement', 'detail-tx-price', 'detail-tx-fx']
    .forEach((id) => {
      const element = $(id);
      if (!element) return;
      element.addEventListener(element.tagName === 'SELECT' ? 'change' : 'input', syncInlinePreview);
    });
  detailDate.addEventListener('change', () => {
    if (detailFx) {
      syncExchangeRateField({
        input: detailFx,
        hint: detailFxHint,
        asset,
        dateValue: detailDate.value,
      });
    }
    syncInlinePreview();
  });
  syncInlinePreview();

  const detailTxIdempotencyKey = newRequestId();
  $('detail-tx-submit').addEventListener('click', async () => {
    const submitButton = $('detail-tx-submit');
    const error = $('drawer-error');
    error.textContent = '';
    submitButton.disabled = true;
    try {
      const side = $('detail-tx-side').value;
      const body = {
        quantity: Number($('detail-tx-qty').value),
        memo: $('detail-tx-memo').value || null,
        tradedAt: $('detail-tx-date').value + ':00',
      };
      if (isInvestment) {
        body.price = Number($('detail-tx-price').value);
        body.settlementAssetId = Number($('detail-tx-settlement').value);
      }
      Object.assign(body, exchangeRatePayload(asset, detailFx));
      await api(`/api/assets/${asset.id}/transactions/${side}`, {
        method: 'POST',
        headers: { 'Idempotency-Key': detailTxIdempotencyKey },
        body: JSON.stringify(body),
      });
      toast('거래를 기록했습니다');
      await refresh();
      await renderAssetDetail();
    } catch (e) {
      error.textContent = e.message;
      submitButton.disabled = false;
    }
  });
}

const EDIT_SPEC = {
  name: { label: '표시 이름', path: '', field: 'name', value: (a) => a.name, cast: (v) => v },
  price: { label: '현재가 (원래 통화 기준)', path: '/price', field: 'currentPrice',
           value: (a) => a.currentPrice ?? '', cast: Number },
  fx: { label: '현재 환율 (원/통화)', path: '/exchange-rate', field: 'exchangeRate',
        value: (a) => a.exchangeRateMissing ? '' : a.exchangeRate, cast: Number },
};

/** 자산의 한 필드를 수정한다. 세 API 가 형태만 다르고 흐름이 같아 하나로 묶었다. */
function editField(asset, kind) {
  const spec = EDIT_SPEC[kind];
  const promptLabel = kind === 'fx' && asset.exchangeRateMissing
    ? `${asset.name} — ${spec.label}\n현재 USD/KRW 등 실제 현재 환율을 입력하세요. 예: 1400`
    : `${asset.name} — ${spec.label}`;
  const input = prompt(promptLabel, spec.value(asset));
  if (input === null || input.trim() === '') return;

  (async () => {
    try {
      await api(`/api/assets/${asset.id}${spec.path}`, {
        method: 'PATCH',
        body: JSON.stringify({ [spec.field]: spec.cast(input.trim()) }),
      });
      toast('수정했습니다');
      await renderAssetDetail();
      await refresh();
    } catch (e) {
      $('drawer-error').textContent = e.message;
    }
  })();
}

async function removeAsset(asset) {
  // 삭제는 Soft Delete 라 거래 내역과 실현손익이 보존되고, 같은 심볼을 다시 등록하면 그대로 복구된다.
  // 사용자가 "초기화"로 오해하지 않도록 미리 알린다.
  const ok = confirm(
    `'${asset.name}'을(를) 목록에서 제거할까요?\n\n`
    + '거래 내역과 실현손익은 보존됩니다. 같은 심볼을 다시 등록하면 이 자산이 그대로 복구됩니다.\n'
    + '숫자를 되돌리려면 자산 삭제가 아니라 거래 삭제를 사용하세요.');
  if (!ok) return;
  try {
    await api(`/api/assets/${asset.id}`, { method: 'DELETE' });
    $('drawer-backdrop').hidden = true;
    toast('자산을 삭제했습니다');
    await refresh();
  } catch (e) {
    $('drawer-error').textContent = e.message;
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
$('empty-add-btn').addEventListener('click', openChoice);
$('account-btn').addEventListener('click', openAccountModal);
$('modal-close').addEventListener('click', closeModal);
modal.addEventListener('click', (e) => { if (e.target === modal) closeModal(); });

function closeModal() { modal.hidden = true; $('modal-error').textContent = ''; }

function openAccountModal() {
  $('modal-title').textContent = '계정 관리';
  $('modal-body').innerHTML = `
    <div class="form-notice">계정 데이터는 현재 로그인한 사용자에게만 연결됩니다.</div>
    <form id="password-form" class="inline-form">
      <h4 class="detail-heading">비밀번호 변경</h4>
      <div class="form-field"><label>현재 비밀번호<input id="account-current-password" type="password" autocomplete="current-password" required></label></div>
      <div class="form-field"><label>새 비밀번호<input id="account-new-password" type="password" minlength="8" autocomplete="new-password" required></label><span class="field-hint">8자 이상 입력해주세요.</span></div>
      <div class="inline-form-actions"><button class="btn-primary" type="submit">비밀번호 변경</button></div>
      <p id="password-form-error" class="error" aria-live="polite"></p>
    </form>
    <div class="account-danger">
      <h4 class="detail-heading">회원 탈퇴</h4>
      <p>탈퇴하면 자산·거래·분석 Snapshot이 즉시 영구 삭제됩니다. 먼저 필요한 데이터를 별도로 보관해주세요.</p>
      <form id="delete-account-form" class="inline-form">
        <div class="form-field"><label>현재 비밀번호<input id="delete-account-password" type="password" autocomplete="current-password" required></label></div>
        <div class="form-field"><label>확인 문구<input id="delete-account-confirmation" type="text" placeholder="DELETE" autocomplete="off" required></label></div>
        <div class="inline-form-actions"><button class="btn-primary btn-danger-solid" type="submit">계정 영구 삭제</button></div>
        <p id="delete-account-error" class="error" aria-live="polite"></p>
      </form>
    </div>`;
  modal.hidden = false;

  $('password-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const error = $('password-form-error');
    error.textContent = '';
    try {
      await api('/api/auth/password', {
        method: 'PATCH',
        body: JSON.stringify({
          currentPassword: $('account-current-password').value,
          newPassword: $('account-new-password').value,
        }),
      });
      $('password-form').reset();
      toast('비밀번호를 변경했습니다');
    } catch (err) {
      error.textContent = err.message;
    }
  });

  $('delete-account-form').addEventListener('submit', async (event) => {
    event.preventDefault();
    const error = $('delete-account-error');
    error.textContent = '';
    if ($('delete-account-confirmation').value.trim() !== 'DELETE') {
      error.textContent = '탈퇴하려면 DELETE를 정확히 입력해주세요.';
      return;
    }
    if (!window.confirm('계정과 모든 자산·거래 데이터를 영구 삭제할까요?')) return;
    try {
      await api('/api/auth/account', {
        method: 'DELETE',
        body: JSON.stringify({
          password: $('delete-account-password').value,
          confirmation: $('delete-account-confirmation').value.trim(),
        }),
      });
      closeModal();
      logout();
      toast('계정을 삭제했습니다');
    } catch (err) {
      error.textContent = err.message;
    }
  });
}

function openChoice() {
  $('modal-title').textContent = '무엇을 하시겠어요?';
  $('modal-body').innerHTML = `
    <button class="choice" data-action="asset">자산 등록<small>현재 보유량부터 간단히 등록합니다</small></button>
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

const today = () => {
  const now = new Date();
  const local = new Date(now.getTime() - now.getTimezoneOffset() * 60000);
  return local.toISOString().slice(0, 16);
};

function isHistoricalTransactionDate(value) {
  return Boolean(value) && value.slice(0, 10) < today().slice(0, 10);
}

function syncExchangeRateField({ input, hint, asset, dateValue, reset = false }) {
  if (!input || !asset) return;
  const isKrw = asset.currency === 'KRW';
  if (reset || !input.dataset.exchangeRateMode) input.dataset.exchangeRateMode = 'AUTO';

  if (isKrw) {
    input.value = '1';
    input.readOnly = true;
    input.dataset.exchangeRateMode = 'AUTO';
    if (hint) hint.textContent = '(원화 자산은 1로 고정)';
    return;
  }

  input.readOnly = false;
  if (input.dataset.exchangeRateMode === 'MANUAL') {
    if (hint) hint.textContent = isHistoricalTransactionDate(dateValue)
      ? '(수동 입력 · 거래 당시 환율)'
      : '(수동 입력 · 현재값 대신 이 환율 사용)';
    return;
  }

  if (isHistoricalTransactionDate(dateValue)) {
    input.value = '';
    if (hint) hint.textContent = '(과거 거래 · 당시 환율을 직접 입력해주세요)';
    return;
  }

  input.value = asset.exchangeRate ?? '';
  if (hint) hint.textContent = asset.exchangeRate == null
    ? '(자동 환율 없음 · 직접 입력해주세요)'
    : '(현재 환율 자동 적용 · 직접 수정하면 수동 입력)';
}

function markExchangeRateManual(input, hint, asset, dateValue) {
  if (!input || !asset || asset.currency === 'KRW') return;
  input.dataset.exchangeRateMode = 'MANUAL';
  if (hint) hint.textContent = isHistoricalTransactionDate(dateValue)
    ? '(수동 입력 · 거래 당시 환율)'
    : '(수동 입력 · 현재값 대신 이 환율 사용)';
}

function exchangeRatePayload(asset, input) {
  if (!asset || asset.currency === 'KRW') return { exchangeRateMode: 'AUTO' };
  const mode = input?.dataset.exchangeRateMode || 'AUTO';
  if (mode === 'MANUAL') {
    return {
      exchangeRateMode: 'MANUAL',
      exchangeRate: input?.value ? Number(input.value) : null,
    };
  }
  return { exchangeRateMode: 'AUTO' };
}

function exchangeRateForPreview(asset, input) {
  if (!asset) return 0;
  if (asset.currency === 'KRW') return 1;
  return Number(input?.value || 0);
}

async function loadKoreanSecurities() {
  if (state.krSecurities) return state.krSecurities;
  if (state.krSecuritiesPromise) return state.krSecuritiesPromise;

  // 정적 카탈로그도 기본 보안 정책(anyRequest.authenticated)에 포함되므로 공통 API 함수로 토큰을 보낸다.
  state.krSecuritiesPromise = api('/data/krx-listed-securities.json')
    .then((payload) => {
      state.krSecurities = (payload.securities || []).map((security) => ({
        ...security,
        searchName: normalizeSecurityQuery(security.name),
        searchCode: String(security.code || '').trim(),
      }));
      return state.krSecurities;
    })
    .catch((error) => {
      state.krSecuritiesPromise = null;
      throw error;
    });
  return state.krSecuritiesPromise;
}

function normalizeSecurityQuery(value) {
  return String(value || '')
    .normalize('NFKC')
    .replace(/\s+/g, '')
    .toUpperCase()
    .replaceAll('에스케이', 'SK');
}

function searchKoreanSecurities(items, query) {
  const normalized = normalizeSecurityQuery(query);
  if (!normalized) return [];

  return items
    .map((security) => {
      let score = null;
      if (security.searchCode === normalized) score = 0;
      else if (security.searchName === normalized) score = 1;
      else if (security.searchName.startsWith(normalized)) score = 2;
      else if (security.searchCode.startsWith(normalized)) score = 3;
      else if (security.searchName.includes(normalized)) score = 4;
      return score == null ? null : { security, score };
    })
    .filter(Boolean)
    .sort((left, right) => left.score - right.score
      || left.security.name.localeCompare(right.security.name, 'ko-KR')
      || left.security.code.localeCompare(right.security.code))
    .slice(0, 20)
    .map((match) => match.security);
}

function renderSecuritySearchMessage(container, message) {
  container.replaceChildren();
  const empty = document.createElement('div');
  empty.className = 'security-result-empty';
  empty.textContent = message;
  container.appendChild(empty);
  container.hidden = false;
}

function renderSecuritySearchResults(container, securities, onSelect) {
  container.replaceChildren();
  if (!securities.length) {
    renderSecuritySearchMessage(container, '일치하는 국내 상장종목이 없습니다. 종목코드로 직접 입력할 수 있습니다.');
    return;
  }

  securities.forEach((security) => {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'security-result';
    button.setAttribute('role', 'option');

    const mark = document.createElement('span');
    mark.className = 'security-result-mark';
    mark.textContent = security.name.slice(0, 2);

    const copy = document.createElement('span');
    copy.className = 'security-result-copy';
    const name = document.createElement('strong');
    name.textContent = security.name;
    const meta = document.createElement('small');
    meta.textContent = `${security.code} · ${security.market}`;
    copy.append(name, meta);

    const action = document.createElement('span');
    action.className = 'security-result-action';
    action.textContent = '선택';
    button.append(mark, copy, action);
    button.addEventListener('click', () => onSelect(security));
    container.appendChild(button);
  });
  container.hidden = false;
}

function openAssetForm() {
  modal.hidden = false;
  $('modal-title').textContent = '자산 등록';
  $('modal-body').innerHTML = `
    <label>자산 종류
      <select id="f-type">
        <option value="CRYPTO">암호화폐</option>
        <option value="STOCK">주식</option>
        <option value="BANK">은행·증권계좌 대기자금</option>
      </select>
    </label>
    <label id="f-market-field" hidden>시장
      <select id="f-market">
        <option value="KOSPI">KOSPI · 코스피</option>
        <option value="KOSDAQ">KOSDAQ · 코스닥</option>
        <option value="OVERSEAS">해외주식</option>
      </select>
    </label>
    <div id="f-symbol-field" class="form-field">
      <label for="f-symbol">심볼 또는 종목코드</label>
      <input id="f-symbol" placeholder="BTC" autocomplete="off" />
      <div id="f-security-results" class="security-results" role="listbox" hidden></div>
      <small class="field-hint" id="f-symbol-hint">예: BTC, ETH</small>
      <small class="symbol-preview" id="f-provider-preview"></small>
    </div>
    <label>표시 이름 <span class="hint">(선택 — 비우면 심볼을 사용)</span>
      <input id="f-name" placeholder="비트코인" />
    </label>
    <label>기준 통화
      <select id="f-currency">
        <option value="KRW">KRW · 원화</option>
        <option value="USD">USD · 달러</option>
        <option value="USDT">USDT · 테더</option>
      </select>
    </label>
    <label><span id="f-quantity-label">현재 보유 수량</span>
      <input id="f-quantity" type="number" step="any" min="0" placeholder="예: 14.7" />
      <small class="field-hint">과거 거래를 복원하지 않고 현재 Position에서 시작합니다.</small>
    </label>
    <label id="f-average-field">평균 매수가 <span class="hint">(선택 · 모르면 비워두세요)</span>
      <input id="f-average-price" type="text" inputmode="decimal" autocomplete="off" placeholder="예: 60k 또는 60,000" />
      <small class="field-hint" id="f-average-hint">USDT 기준 평단입니다. k·쉼표 입력도 가능합니다.</small>
    </label>
    <label id="f-average-fx-field">평균 매입 당시 환율 <span class="hint">(평단 입력 시 필수)</span>
      <input id="f-average-exchange-rate" type="text" inputmode="decimal" autocomplete="off" placeholder="예: 1,380" />
      <small class="field-hint">누적 손익의 KRW 원가를 계산할 때만 사용합니다.</small>
    </label>
    <div id="f-opening-preview" class="transaction-preview" aria-live="polite" hidden></div>
    <button class="btn-primary" id="f-submit">등록</button>`;

  let previousCurrency = null;
  let selectedDomesticSecurity = null;

  const parseOpeningNumber = (raw, label, allowSuffix = false) => {
    const normalized = String(raw ?? '').trim().replaceAll(',', '');
    if (!normalized) return null;
    const pattern = allowSuffix
      ? /^(\d+(?:\.\d+)?|\.\d+)([kKmM])?$/
      : /^(\d+(?:\.\d+)?|\.\d+)$/;
    const match = normalized.match(pattern);
    if (!match) throw new Error(`${label} 형식을 확인해주세요.`);
    const multiplier = !match[2] ? 1 : match[2].toLowerCase() === 'k' ? 1_000 : 1_000_000;
    const value = Number(match[1]) * multiplier;
    if (!Number.isFinite(value) || value <= 0) throw new Error(`${label}은 0보다 커야 합니다.`);
    return value;
  };

  const updateOpeningPreview = () => {
    const preview = $('f-opening-preview');
    const rawAverage = $('f-average-price').value;
    if (!rawAverage.trim()) {
      preview.hidden = true;
      preview.textContent = '';
      return;
    }

    try {
      const quantity = Number($('f-quantity').value || 0);
      const averagePrice = parseOpeningNumber(rawAverage, '평균 매수가', true);
      const currency = $('f-currency').value;
      const exchangeRate = currency === 'KRW'
        ? 1
        : parseOpeningNumber($('f-average-exchange-rate').value, '평균 매입 당시 환율');
      if (quantity <= 0) {
        preview.hidden = false;
        preview.textContent = '평단을 저장하려면 현재 보유 수량도 입력해주세요.';
        return;
      }
      if (currency !== 'KRW' && exchangeRate == null) {
        preview.hidden = false;
        preview.textContent = '평단을 원화 원가로 계산하려면 평균 매입 당시 환율을 입력해주세요.';
        return;
      }
      const krwAverage = averagePrice * exchangeRate;
      preview.hidden = false;
      preview.textContent = `${num(quantity, 8)} × 평단 ${num(averagePrice, 4)} ${currency} · 원화 기준 평단 ${won(krwAverage)}로 저장됩니다.`;
    } catch (error) {
      preview.hidden = false;
      preview.textContent = error.message;
    }
  };

  const hideSecurityResults = () => {
    $('f-security-results').hidden = true;
    $('f-security-results').replaceChildren();
  };

  const updateSecurityResults = async () => {
    const type = $('f-type').value;
    const market = $('f-market').value;
    const query = $('f-symbol').value.trim();
    const resultContainer = $('f-security-results');
    if (type !== 'STOCK' || market === 'OVERSEAS' || !query || selectedDomesticSecurity) {
      hideSecurityResults();
      return;
    }

    renderSecuritySearchMessage(resultContainer, '국내 상장종목을 찾는 중…');
    try {
      const securities = await loadKoreanSecurities();
      if ($('f-security-results') !== resultContainer) return;
      renderSecuritySearchResults(
        resultContainer,
        searchKoreanSecurities(securities, query),
        (security) => {
          selectedDomesticSecurity = security;
          $('f-symbol').value = security.code;
          $('f-market').value = security.market;
          $('f-name').value = security.name;
          hideSecurityResults();
          syncAssetForm();
          $('f-quantity').focus();
        },
      );
    } catch (error) {
      if ($('f-security-results') === resultContainer) {
        renderSecuritySearchMessage(resultContainer, `${error.message} 종목코드로 직접 입력해주세요.`);
      }
    }
  };

  const syncAssetForm = () => {
    const type = $('f-type').value;
    const isStock = type === 'STOCK';
    const isInvestment = type === 'STOCK' || type === 'CRYPTO';
    const market = $('f-market').value;
    const input = $('f-symbol').value.trim().toUpperCase();
    const fixedCurrency = type === 'CRYPTO' ? 'USDT' : type === 'STOCK' && market === 'OVERSEAS' ? 'USD' : 'KRW';

    $('f-market-field').hidden = !isStock;
    $('f-symbol-field').hidden = !isInvestment;
    $('f-currency').disabled = isInvestment;
    if (isInvestment) $('f-currency').value = fixedCurrency;
    const currency = $('f-currency').value;
    if (currency !== previousCurrency) {
      $('f-average-exchange-rate').value = currency === 'KRW' ? '1' : '';
      previousCurrency = currency;
    }
    $('f-symbol').value = isInvestment ? $('f-symbol').value.toUpperCase() : currency;
    $('f-name').placeholder = type === 'BANK' ? `${currency} 증권계좌` : type === 'CASH' ? `${currency} 투자 대기자금` : type === 'STOCK' ? '엔비디아' : '비트코인';
    $('f-quantity-label').textContent = isInvestment ? '현재 보유 수량' : `현재 ${currency} 잔액`;
    $('f-average-field').hidden = !isInvestment;
    $('f-average-fx-field').hidden = !isInvestment || currency === 'KRW' || !$('f-average-price').value;
    $('f-average-hint').textContent = `${currency} 기준 평단입니다. 60k·60,000처럼 입력할 수 있습니다.`;
    updateOpeningPreview();

    if (type === 'STOCK') {
      $('f-symbol').placeholder = market === 'OVERSEAS' ? 'NVDA' : 'SK하이닉스 또는 000660';
      $('f-symbol-hint').textContent = market === 'OVERSEAS'
        ? '미국주식 티커만 입력하세요. 예: NVDA'
        : '종목명 또는 6자리 코드를 검색한 뒤 선택하세요. 직접 입력도 가능합니다.';
    } else if (type === 'CRYPTO') {
      $('f-symbol').placeholder = 'BTC';
      $('f-symbol-hint').textContent = '코인 심볼만 입력하세요. 예: BTC, ETH, ZEC';
    }

    let providerPreview = '';
    if (selectedDomesticSecurity) {
      const suffix = selectedDomesticSecurity.market === 'KOSPI' ? '.KS' : '.KQ';
      providerPreview = `${selectedDomesticSecurity.name} · ${selectedDomesticSecurity.code}${suffix}로 시세를 조회합니다.`;
    } else if (isInvestment && input) {
      providerPreview = type === 'CRYPTO'
        ? `${input}/USDT · Binance Spot 시세를 자동 조회합니다.`
        : `${market} 시장 시세를 자동 조회합니다.`;
    }
    $('f-provider-preview').textContent = providerPreview;
  };

  $('f-type').addEventListener('change', () => {
    selectedDomesticSecurity = null;
    if ($('f-type').value === 'BANK') {
      $('f-currency').value = 'KRW';
    }
    syncAssetForm();
    updateSecurityResults();
  });
  $('f-market').addEventListener('change', () => {
    if (selectedDomesticSecurity?.market !== $('f-market').value) selectedDomesticSecurity = null;
    syncAssetForm();
    updateSecurityResults();
  });
  $('f-currency').addEventListener('change', syncAssetForm);
  $('f-symbol').addEventListener('input', () => {
    if (selectedDomesticSecurity && $('f-symbol').value.trim() !== selectedDomesticSecurity.code) {
      if ($('f-name').value.trim() === selectedDomesticSecurity.name) $('f-name').value = '';
      selectedDomesticSecurity = null;
    }
    syncAssetForm();
    updateSecurityResults();
  });
  $('f-symbol').addEventListener('keydown', (event) => {
    if (event.key === 'Escape') hideSecurityResults();
  });
  $('f-average-price').addEventListener('input', syncAssetForm);
  $('f-average-exchange-rate').addEventListener('input', updateOpeningPreview);
  $('f-quantity').addEventListener('input', updateOpeningPreview);
  syncAssetForm();
  loadKoreanSecurities().catch(() => {});

  $('f-submit').addEventListener('click', () => submit(async () => {
    const type = $('f-type').value;
    let symbol = $('f-symbol').value.trim();
    let market = type === 'STOCK' ? $('f-market').value : null;
    let name = $('f-name').value.trim();
    if (type === 'STOCK' && market !== 'OVERSEAS' && !selectedDomesticSecurity) {
      const query = normalizeSecurityQuery(symbol);
      const exact = (await loadKoreanSecurities()).find((security) =>
        security.searchCode === query || security.searchName === query);
      if (exact) {
        symbol = exact.code;
        market = exact.market;
        if (!name) name = exact.name;
      }
    }
    if (type === 'BANK' && !name) {
      throw new Error('은행·증권계좌는 표시 이름을 입력해주세요.');
    }
    const quantity = $('f-quantity').value ? Number($('f-quantity').value) : 0;
    const averagePrice = parseOpeningNumber($('f-average-price').value, '평균 매수가', true);
    const averageExchangeRate = averagePrice == null
      ? null
      : $('f-currency').value === 'KRW'
        ? 1
        : parseOpeningNumber($('f-average-exchange-rate').value, '평균 매입 당시 환율');
    if (averagePrice != null && quantity <= 0) {
      throw new Error('평균 매수가를 저장하려면 현재 보유 수량도 입력해주세요.');
    }
    if (averagePrice != null && averageExchangeRate == null) {
      throw new Error('외화 평단을 저장하려면 평균 매입 당시 환율도 입력해주세요.');
    }
    const created = await api('/api/assets', {
      method: 'POST',
      body: JSON.stringify({
        type,
        symbol,
        market,
        name,
        currency: $('f-currency').value,
        quantity,
        averagePrice,
        averageExchangeRate,
      }),
    });
    const averageConfirmation = created.avgPriceOriginal != null
      ? ` · 평단 ${num(created.avgPriceOriginal, 4)} ${created.currency}`
      : ' · 평단 미입력';
    toast(`${created.name} 등록 완료${averageConfirmation}`);
  }));
}

function assetOptions(filter) {
  return state.assets.filter(filter)
    .map((a) => `<option value="${a.id}">${escapeHtml(a.name)} (${a.displaySymbol || a.symbol})</option>`).join('');
}

function openTradeForm() {
  const options = assetOptions((a) => a.type === 'STOCK' || a.type === 'CRYPTO');
  if (!options) {
    $('modal-error').textContent = '먼저 STOCK 또는 CRYPTO 자산을 등록해주세요.';
    return;
  }
  $('drawer-backdrop').hidden = true;
  modal.hidden = false;
  $('modal-title').textContent = '매수 · 매도';
  $('modal-body').innerHTML = `
    <label>자산<select id="f-asset">${options}</select></label>
    <label>거래<select id="f-side"><option value="buy">매수</option><option value="sell">매도</option></select></label>
    <label><span id="f-settlement-label">매수대금 출금할 곳</span>
      <select id="f-settlement"></select>
      <small class="field-hint" id="f-settlement-hint">같은 통화의 대기자금에서 매수대금을 차감하고 매도대금을 입금합니다.</small>
    </label>
    <label>수량<input id="f-qty" type="number" step="any" placeholder="0.1" /></label>
    <label>단가 <span class="hint">(원래 통화 기준)</span><input id="f-price" type="number" step="any" placeholder="40000" /></label>
    <label>거래 당시 환율 <span class="hint" id="f-fx-hint">(원/통화)</span><input id="f-fx" type="number" step="any" /></label>
    <label>거래 시점<input id="f-date" type="datetime-local" value="${today()}" /></label>
    <label>메모 <span class="hint">(선택)</span><input id="f-memo" /></label>
    <div id="f-preview" class="transaction-preview" aria-live="polite"></div>
    <button class="btn-primary" id="f-submit" disabled>기록</button>`;

  const syncMainTradePreview = () => {
    const selected = state.assets.find((a) => String(a.id) === $('f-asset').value);
    const settlement = state.assets.find((a) => String(a.id) === $('f-settlement').value);
    updateTradePreview({
      asset: selected,
      side: $('f-side').value,
      quantity: $('f-qty').value,
      price: $('f-price').value,
      exchangeRate: $('f-fx').value,
      settlement,
      previewId: 'f-preview',
      submitId: 'f-submit',
    });
  };

  const syncTradeFields = (assetChanged = false) => {
    const selected = state.assets.find((a) => String(a.id) === $('f-asset').value);
    const fxInput = $('f-fx');
    if (assetChanged) {
      $('f-price').value = selected?.currentPrice ?? '';
    }
    fxInput.placeholder = selected?.currency === 'KRW' ? '1' : '예: 1520';
    syncExchangeRateField({
      input: fxInput,
      hint: $('f-fx-hint'),
      asset: selected,
      dateValue: $('f-date').value,
      reset: assetChanged,
    });

    if (assetChanged || !$('f-settlement').options.length) {
      const settlements = state.assets.filter((a) =>
        (a.type === 'CASH' || a.type === 'BANK') && a.currency === selected?.currency);
      $('f-settlement').innerHTML = settlements.length
        ? settlements.map((a) => `<option value="${a.id}">${escapeHtml(a.name)} · ${num(a.quantity)} ${a.currency}</option>`).join('')
        : '<option value="">같은 통화의 대기자금이 없습니다</option>';
      $('f-settlement').disabled = !settlements.length;
    }
    const selling = $('f-side').value === 'sell';
    const hasSettlement = Boolean($('f-settlement').value);
    $('f-settlement-hint').textContent = hasSettlement
      ? selling
        ? `매도대금이 ${selected?.currency || ''} 대기자금에 즉시 입금됩니다.`
        : `매수대금이 ${selected?.currency || ''} 대기자금에서 즉시 차감됩니다.`
      : `${selected?.currency || ''} 기본 대기자금을 준비하지 못했습니다. 화면을 새로고침해주세요.`;
    $('f-settlement-label').textContent = selling ? '매도대금 받을 곳' : '매수대금 출금할 곳';
    syncMainTradePreview();
  };
  $('f-asset').addEventListener('change', () => syncTradeFields(true));
  $('f-side').addEventListener('change', () => syncTradeFields(false));
  $('f-settlement').addEventListener('change', syncMainTradePreview);
  ['f-qty', 'f-price'].forEach((id) => $(id).addEventListener('input', syncMainTradePreview));
  $('f-fx').addEventListener('input', () => {
    const selected = state.assets.find((a) => String(a.id) === $('f-asset').value);
    markExchangeRateManual($('f-fx'), $('f-fx-hint'), selected, $('f-date').value);
    syncMainTradePreview();
  });
  $('f-date').addEventListener('change', () => syncTradeFields(false));
  syncTradeFields(true);

  const tradeIdempotencyKey = newRequestId();
  $('f-submit').addEventListener('click', () => submit(async () => {
    const id = $('f-asset').value;
    const selected = state.assets.find((a) => String(a.id) === id);
    await api(`/api/assets/${id}/transactions/${$('f-side').value}`, {
      method: 'POST',
      headers: { 'Idempotency-Key': tradeIdempotencyKey },
      body: JSON.stringify({
        quantity: Number($('f-qty').value),
        price: Number($('f-price').value),
        ...exchangeRatePayload(selected, $('f-fx')),
        settlementAssetId: Number($('f-settlement').value),
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
  $('drawer-backdrop').hidden = true;
  modal.hidden = false;
  $('modal-title').textContent = '입금 · 출금';
  $('modal-body').innerHTML = `
    <label>자산<select id="f-asset">${options}</select></label>
    <label>거래<select id="f-side"><option value="deposit">입금</option><option value="withdraw">출금</option></select></label>
    <label>금액<input id="f-qty" type="number" step="any" placeholder="1000000" /></label>
    <label id="f-cash-fx-field">거래 당시 환율 <span class="hint" id="f-fx-hint"></span>
      <input id="f-fx" type="number" step="any" placeholder="예: 1415" />
    </label>
    <label>거래 시점<input id="f-date" type="datetime-local" value="${today()}" /></label>
    <label>메모 <span class="hint">(선택)</span><input id="f-memo" placeholder="월급 입금" /></label>
    <div id="f-preview" class="transaction-preview" aria-live="polite"></div>
    <button class="btn-primary" id="f-submit" disabled>기록</button>`;

  const syncCashPreview = () => {
    const selected = state.assets.find((a) => String(a.id) === $('f-asset').value);
    updateCashPreview({
      asset: selected,
      side: $('f-side').value,
      quantity: $('f-qty').value,
      exchangeRate: exchangeRateForPreview(selected, $('f-fx')),
      previewId: 'f-preview',
      submitId: 'f-submit',
    });
  };

  const syncCashFields = (assetChanged = false) => {
    const selected = state.assets.find((a) => String(a.id) === $('f-asset').value);
    $('f-cash-fx-field').hidden = selected?.currency === 'KRW';
    syncExchangeRateField({
      input: $('f-fx'),
      hint: $('f-fx-hint'),
      asset: selected,
      dateValue: $('f-date').value,
      reset: assetChanged,
    });
    syncCashPreview();
  };

  $('f-asset').addEventListener('change', () => syncCashFields(true));
  $('f-side').addEventListener('change', syncCashPreview);
  $('f-qty').addEventListener('input', syncCashPreview);
  $('f-fx').addEventListener('input', () => {
    const selected = state.assets.find((a) => String(a.id) === $('f-asset').value);
    markExchangeRateManual($('f-fx'), $('f-fx-hint'), selected, $('f-date').value);
    syncCashPreview();
  });
  $('f-date').addEventListener('change', () => syncCashFields(false));
  syncCashFields(true);

  const cashIdempotencyKey = newRequestId();
  $('f-submit').addEventListener('click', () => submit(async () => {
    const id = $('f-asset').value;
    const selected = state.assets.find((a) => String(a.id) === id);
    await api(`/api/assets/${id}/transactions/${$('f-side').value}`, {
      method: 'POST',
      headers: { 'Idempotency-Key': cashIdempotencyKey },
      body: JSON.stringify({
        quantity: Number($('f-qty').value),
        ...exchangeRatePayload(selected, $('f-fx')),
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
