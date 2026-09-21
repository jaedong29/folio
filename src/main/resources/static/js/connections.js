/* Provider snapshots stay separate from the manually maintained transaction ledger. */
const connectedState = { overview: null, timer: null, generation: 0, loading: false };
const connectionLabels = { TOSS: '토스증권', BINANCE_SPOT: '바이낸스 현물' };
const connectionErrors = {
  AUTH_OR_IP_REJECTED: '키 권한과 허용 IP를 확인해주세요.',
  READ_ONLY_KEY_REQUIRED: '조회 전용 키가 필요합니다. 주문·출금·이체 권한을 모두 꺼주세요.',
  PROVIDER_RATE_LIMITED: '조회 요청이 많아 잠시 기다리고 있습니다.',
  PROVIDER_UNAVAILABLE: '연결한 곳에서 정보를 받아오지 못했습니다.',
  INVALID_PROVIDER_RESPONSE: '응답을 확인하지 못해 이전 자산을 유지하고 있습니다.',
  NO_SUPPORTED_ACCOUNT: '조회할 수 있는 종합매매 계좌가 없습니다.',
  MULTIPLE_ACCOUNTS_UNSUPPORTED: '여러 계좌 선택은 아직 지원하지 않습니다.',
  SYNC_FAILED: '갱신하지 못했습니다. 연결 설정을 확인해주세요.',
};

function stopConnected() {
  clearTimeout(connectedState.timer);
  connectedState.timer = null;
  connectedState.generation++;
  connectedState.loading = false;
  connectedState.fastPollDeadline = 0;
  $('refresh-btn').classList.remove('is-loading');
}

const connectionBusy = c => c.status === 'QUEUED' || c.status === 'SYNCING';

function clearConnectedSession() {
  stopConnected();
  connectedState.overview = null;
  document.getElementById('connected-content').replaceChildren();
  document.getElementById('connection-form').reset();
  document.getElementById('connection-dialog').close();
}

function showConnected(pushHistory = true) {
  stopNewsSummaryPolling();
  ['overview', 'analysis', 'activity', 'news'].forEach(p => $(p + '-page').hidden = true);
  $('connected-page').hidden = false;
  setPrimaryNavigation('connected');
  if (pushHistory && location.hash !== '#connected') history.pushState({ view: 'connected' }, '', '#connected');
  renderConnected();
  loadConnected();
}

async function loadConnected() {
  if (connectedState.loading || !token() || $('connected-page').hidden) return;
  clearTimeout(connectedState.timer);
  const generation = connectedState.generation;
  connectedState.loading = true;
  let anyBusy = false;
  try {
    const overview = await api('/api/connections');
    if (generation !== connectedState.generation) return;
    connectedState.overview = overview;
    $('connected-error').textContent = '';
    renderConnected();
    anyBusy = overview.connections.some(connectionBusy);
    $('refresh-btn').disabled = anyBusy;
    $('refresh-btn').classList.toggle('is-loading', anyBusy);
    $('last-refresh-label').textContent = anyBusy ? '동기화 중…' : '연결 상태 확인됨';
  } catch (e) {
    if (generation !== connectedState.generation) return;
    $('connected-error').textContent = e.message;
    $('refresh-btn').classList.remove('is-loading');
    $('last-refresh-label').textContent = '연결 상태 확인 실패';
  } finally {
    if (generation === connectedState.generation) {
      connectedState.loading = false;
      if (token() && !$('connected-page').hidden) {
        const fast = anyBusy && Date.now() < (connectedState.fastPollDeadline || 0);
        connectedState.timer = setTimeout(loadConnected, fast ? 2000 : 10000);
      }
    }
  }
}

function renderConnected() {
  const d = connectedState.overview;
  if (!d) return;
  const esc = escapeHtml;
  const money = v => esc(privateText(won(v)));
  const signedAmount = (v, currency, max = 2) => {
    const n = Number(v);
    return (n >= 0 ? '+' : '−') + num(Math.abs(n), max) + ' ' + currency;
  };
  const pnlText = h => h.providerProfitLoss == null ? '손익 -' : esc(privateText(signedAmount(h.providerProfitLoss, h.currency)));
  const pnlClass = h => privateSignClass(h.providerProfitLoss);
  const items = d.connections.flatMap(c => c.holdings.map(h => ({ ...h, connection: c })));
  const pnlCount = items.filter(h => h.providerProfitLoss != null).length;
  $('connection-add').disabled = !d.enabled;
  const cards = d.connections.map(c => {
    const busy = connectionBusy(c);
    const status = c.status === 'ERROR' ? '갱신 실패' : busy ? '불러오는 중' : c.stale ? '이전 조회값' : '연결됨';
    return `<article class="connection-card"><div class="connection-card-top"><strong>${esc(connectionLabels[c.provider])}</strong>
      <span class="connection-badge ${c.stale ? 'connection-warning' : ''}">${status}</span></div>
      <p>${esc(c.lastSyncedAt ? '마지막 성공 · ' + timeAgo(c.lastSyncedAt) : '첫 조회를 기다리고 있습니다')}</p>
      ${c.errorCode ? `<p class="connection-error">${esc(connectionErrors[c.errorCode] || '연결을 확인해주세요.')}</p>` : ''}
      <div class="connection-actions"><button class="btn-ghost sm" data-sync="${c.id}" ${busy || !d.enabled ? 'disabled' : ''}>새로고침</button>
      <button class="btn-ghost sm" data-reconnect="${esc(c.provider)}" ${!d.enabled ? 'disabled' : ''}>키 변경</button>
      <button class="btn-ghost sm" data-disconnect="${c.id}">연결 해제</button></div></article>`;
  }).join('');
  const allocations = d.allocation.map(a => `<div class="connected-allocation-item"><span>${esc({STOCK:'주식', CRYPTO:'현물 코인', CASH:'USDT'}[a.category] || a.category)}</span>
    <strong>${money(a.knownValueKRW)}</strong><small>${a.percentage == null ? '비중 확인 전' : esc(num(a.percentage, 1)) + '%'}</small>
    <div class="connected-bar"><i style="width:${Math.max(0, Math.min(100, Number(a.percentage || 0)))}%"></i></div></div>`).join('');
  const rows = items.map(h => {
    const fx = h.exchangeRate == null ? '환율 확인 필요' : h.fxStale ? '이전 환율 적용' : '';
    const valueNote = fx || (h.connection.stale ? '이전 조회값' : '');
    const lockedLabel = h.lockedQuantity != null && Number(h.lockedQuantity) > 0
      ? `<small>주문 중 ${esc(privateText(num(h.lockedQuantity)))}</small>` : '';
    return `<div class="row portfolio-row">
      <div class="portfolio-asset">
        ${assetIcon(h.category, h.symbol)}
        <div class="asset-name-block">
          <div class="asset-name-line">
            <span class="asset-name">${esc(h.name)}</span>
            <span class="asset-symbol">${esc(h.symbol)}</span>
          </div>
          <div class="asset-market">${esc(connectionLabels[h.connection.provider])}</div>
        </div>
      </div>
      <div class="portfolio-position">
        <div class="position-price">${h.price == null ? '시세 확인 필요' : esc(num(h.price, 4) + ' ' + h.currency)}</div>
        <div class="position-quantity">${esc(privateText(num(h.quantity)))}${lockedLabel}</div>
      </div>
      <div class="portfolio-value">
        <strong>${money(h.valuationKRW)}</strong>
        ${valueNote ? `<small>${esc(valueNote)}</small>` : ''}
        <small class="${pnlClass(h)}">${pnlText(h)}</small>
        ${h.providerProfitLoss != null ? '<small>제공처 기준 · 비용 차감 전</small>' : ''}
      </div>
    </div>`;
  }).join('');
  $('connected-content').innerHTML = `
    ${!d.enabled ? '<p class="connection-notice">계좌 연결을 준비하고 있습니다. 현재는 연결 기능이 비활성화되어 있습니다.</p>' : ''}
    ${!d.connections.length ? `<div class="connected-empty"><span class="eyebrow">한 번 연결하고, 한곳에서 확인하세요</span>
      <h2>주식과 현물 코인의<br>현재 가치를 모아보세요.</h2><p>계좌를 연결하면 보유 자산을 자동으로 가져옵니다.<br>토스증권 국내·미국 주식 / 바이낸스 현물·USDT</p>
      <button class="btn-primary" data-connect="true" ${!d.enabled ? 'disabled' : ''}>첫 계좌 연결하기</button></div>` : `
    <section class="connected-hero"><div><p class="eyebrow">연결된 투자자산 평가금액</p><div class="connected-total">${money(d.totalValueKRW)}</div>
      <p>${d.totalValueKRW == null ? `확인된 금액 ${money(d.knownValueKRW)} · 평가 불가 ${d.unvaluedHoldingCount}종목 · 첫 조회 대기 ${d.pendingConnectionCount}곳` : `${items.length}개 보유 자산 · ${d.connections.length}곳 연결`}</p>
      ${d.stale ? '<p class="connection-warning">최신 조회가 안 된 연결 또는 환율이 있습니다. 마지막 확인값이 포함됩니다.</p>' : ''}</div>
      <button class="btn-ghost" data-privacy="true">${state.privacyHidden ? '금액 표시' : '금액 숨기기'}</button></section>
    <div class="connected-allocation">${allocations}</div>
    <section class="connected-positions"><div class="connection-section-head"><h2>보유 자산</h2><span>평가손익 제공 ${pnlCount}/${items.length}종목</span></div>
      <div class="connected-table-wrap">
        <div class="asset-table-head" aria-hidden="true"><span>자산 / 계좌</span><span>조회 가격 · 보유량</span><span>원화 평가금액</span></div>
        <div class="list">${rows}</div>
      </div>
      ${!items.length ? '<p class="empty-row">조회가 완료되면 이곳에 보유 자산이 표시됩니다. 조회 완료된 빈 계좌는 0원입니다.</p>' : ''}
    </section>`}
    <section class="connected-connections"><div class="connection-section-head"><h2>연결한 곳</h2><span>자동 갱신 · 갱신 주기는 서버 설정 기준</span></div><div class="connection-cards">${cards || '<p class="connection-help">아직 연결한 계좌가 없습니다.</p>'}</div></section>
    <p class="connection-scope">집계 범위: 토스증권 보유 주식과 바이낸스 현물 지갑. 토스 예수금·채권, 바이낸스 선물·Earn·대출, 직접 기록한 자산은 이 합계에 포함되지 않습니다.
    평가손익은 제공처가 확인한 원래 통화 기준이며, 전체 투자 수익률이나 실현손익을 뜻하지 않습니다. 바이낸스 잔고만으로 매입 원가를 추정하지 않습니다.</p>`;
}

async function syncConnected(id) {
  try {
    await api(`/api/connections/${id}/sync`, {method:'POST'});
    connectedState.fastPollDeadline = Date.now() + 25000;
    await loadConnected();
    return true;
  } catch (e) { toast(e.message); return false; }
}

async function refreshConnected() {
  const connections = connectedState.overview?.connections || [];
  if (!connections.length) return loadConnected();
  const button = $('refresh-btn');
  button.disabled = true;
  button.classList.add('is-loading');
  $('last-refresh-label').textContent = '동기화 중…';
  try {
    let accepted = 0;
    for (const c of connections) if (await syncConnected(c.id)) accepted++;
    if (accepted > 0) toast(`${accepted}곳에 갱신을 요청했습니다`);
  } finally { await loadConnected(); }
}

function updateConnectionHelp() {
  const binance = $('connection-provider').value === 'BINANCE_SPOT';
  $('connection-key-label').textContent = binance ? 'API Key (HMAC)' : 'Client ID';
  $('connection-secret-label').textContent = binance ? 'Secret Key' : 'Client Secret';
  $('connection-help').textContent = binance
    ? '바이낸스 API Management에서 읽기만 허용한 HMAC 키를 발급하세요. 주문·출금·이체 권한은 모두 꺼야 연결됩니다. 현물 지갑의 코인과 USDT를 조회합니다.'
    : '토스증권 웹의 설정 → Open API에서 전용 키를 발급하고 Folio 서버 IP를 허용 목록에 등록하세요. 종합매매 계좌의 국내·미국 보유 주식을 조회합니다. 예수금은 포함되지 않습니다.';
}

function openConnectionForm(provider) {
  if (!connectedState.overview?.enabled) return;
  // Prevent credentials from being submitted over an ordinary remote HTTP origin.
  if (!window.isSecureContext) { toast('계좌 연결은 HTTPS 주소에서 이용해주세요.'); return; }
  $('connection-form').reset();
  $('connection-provider').disabled = Boolean(provider);
  if (provider) $('connection-provider').value = provider;
  $('connection-form-error').textContent = '';
  updateConnectionHelp();
  $('connection-dialog').showModal();
}

document.getElementById('nav-connected').addEventListener('click', () => showConnected());
document.getElementById('connection-add').addEventListener('click', () => openConnectionForm());
document.getElementById('connection-close').addEventListener('click', () => $('connection-dialog').close());
document.getElementById('connection-dialog').addEventListener('close', () => $('connection-form').reset());
document.getElementById('connection-provider').addEventListener('change', updateConnectionHelp);
document.getElementById('connected-content').addEventListener('click', async event => {
  const button = event.target.closest('button'); if (!button) return;
  if (button.dataset.connect) openConnectionForm();
  if (button.dataset.reconnect) openConnectionForm(button.dataset.reconnect);
  if (button.dataset.privacy) $('privacy-toggle').click();
  if (button.dataset.sync) { button.disabled = true; await syncConnected(button.dataset.sync); }
  if (button.dataset.disconnect && confirm('연결을 해제하고 Folio에 저장한 키와 조회 자산을 삭제할까요?')) {
    try {
      await api(`/api/connections/${button.dataset.disconnect}`, {method:'DELETE'});
      await loadConnected();
      toast('Folio 연결을 삭제했습니다. 제공처에서도 API 키를 폐기해주세요.');
    }
    catch (e) { toast(e.message); }
  }
});
document.getElementById('connection-form').addEventListener('submit', async event => {
  event.preventDefault();
  const save = $('connection-save'); save.disabled = true;
  $('connection-form-error').textContent = '';
  try {
    if (!window.isSecureContext) throw new Error('계좌 연결은 HTTPS 주소에서 이용해주세요.');
    await api('/api/connections/' + $('connection-provider').value, {method:'PUT', body:JSON.stringify({
      key:$('connection-key').value.trim(), secret:$('connection-secret').value.trim(),
    })});
    $('connection-dialog').close();
    await loadConnected();
    toast('연결 정보를 저장했습니다. 첫 조회 결과를 기다려주세요.');
  } catch (e) { $('connection-form-error').textContent = e.message; }
  finally {
    $('connection-key').value = '';
    $('connection-secret').value = '';
    save.disabled = false;
  }
});
