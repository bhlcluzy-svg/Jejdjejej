(function () {
  var NS = '__memeOverlay';
  if (window[NS]) { window[NS].toggle(); return; }

  var STORE = 'memeOverlay.v1';
  var state = { x: null, y: null, collapsed: false };
  try { state = Object.assign(state, JSON.parse(localStorage.getItem(STORE) || '{}')); } catch (e) {}
  function save() { try { localStorage.setItem(STORE, JSON.stringify(state)); } catch (e) {} }

  // --- host detection -------------------------------------------------
  function host() {
    var h = location.hostname;
    if (/axiom/.test(h)) return 'axiom';
    if (/gmgn/.test(h)) return 'gmgn';
    if (/dexscreener/.test(h)) return 'dexscreener';
    if (/photon/.test(h)) return 'photon';
    if (/bullx/.test(h)) return 'bullx';
    return 'other';
  }

  // Solana mints are base58, 32-44 chars. EVM is 0x + 40 hex.
  var SOL = /[1-9A-HJ-NP-Za-km-z]{32,44}/;
  var EVM = /0x[a-fA-F0-9]{40}/;

  function detectToken() {
    var url = location.href;
    var m = url.match(EVM) || url.match(SOL);
    if (m) return { addr: m[0], chain: m[0].indexOf('0x') === 0 ? 'evm' : 'sol', src: 'url' };
    var txt = (document.body ? document.body.innerText : '') || '';
    m = txt.match(EVM) || txt.match(SOL);
    if (m) return { addr: m[0], chain: m[0].indexOf('0x') === 0 ? 'evm' : 'sol', src: 'page' };
    return null;
  }

  function short(a) { return a ? a.slice(0, 4) + '…' + a.slice(-4) : '—'; }

  function links(addr) {
    if (!addr) return [];
    return [
      ['Axiom', 'https://axiom.trade/meme/' + addr],
      ['GMGN', 'https://gmgn.ai/sol/token/' + addr],
      ['DexS', 'https://dexscreener.com/solana/' + addr],
      ['Photon', 'https://photon-sol.tinyastro.io/en/lp/' + addr],
      ['Bullx', 'https://bullx.io/terminal?chainId=1399811149&address=' + addr],
      ['Solscan', 'https://solscan.io/token/' + addr]
    ];
  }

  // --- shell ----------------------------------------------------------
  var mount = document.createElement('div');
  mount.id = 'meme-overlay-mount';
  mount.style.cssText = 'position:fixed;z-index:2147483647;top:0;left:0;width:0;height:0;';
  (document.body || document.documentElement).appendChild(mount);
  var root = mount.attachShadow ? mount.attachShadow({ mode: 'open' }) : mount;

  var css = document.createElement('style');
  css.textContent = [
    ':host,*{box-sizing:border-box}',
    '.panel{position:fixed;width:250px;background:rgba(14,16,22,.94);color:#e8ecf4;',
    'font:12px/1.35 -apple-system,BlinkMacSystemFont,"SF Pro Text",system-ui,sans-serif;',
    'border:1px solid #2a3040;border-radius:12px;box-shadow:0 8px 28px rgba(0,0,0,.5);',
    'backdrop-filter:blur(8px);-webkit-backdrop-filter:blur(8px);overflow:hidden;touch-action:none}',
    '.hd{display:flex;align-items:center;gap:6px;padding:8px 9px;background:rgba(255,255,255,.04);',
    'border-bottom:1px solid #232838;cursor:grab;user-select:none;-webkit-user-select:none}',
    '.dot{width:7px;height:7px;border-radius:50%;background:#3ddc84;flex:0 0 auto}',
    '.ttl{flex:1;font-weight:600;letter-spacing:.02em;font-size:11px;text-transform:uppercase;color:#9aa6bd}',
    '.ico{width:22px;height:22px;border:0;border-radius:6px;background:rgba(255,255,255,.06);',
    'color:#cbd4e6;font-size:13px;line-height:1;cursor:pointer;flex:0 0 auto}',
    '.ico:active{background:rgba(255,255,255,.14)}',
    '.bd{padding:9px;display:flex;flex-direction:column;gap:9px}',
    '.collapsed .bd{display:none}',
    '.row{display:flex;align-items:center;gap:6px}',
    '.lbl{font-size:10px;text-transform:uppercase;letter-spacing:.05em;color:#6f7c96}',
    '.ca{flex:1;font-family:ui-monospace,Menlo,monospace;font-size:11px;color:#e8ecf4;',
    'overflow:hidden;text-overflow:ellipsis;white-space:nowrap}',
    '.btn{border:1px solid #2f3648;background:#1a1f2b;color:#dbe3f2;border-radius:7px;',
    'padding:5px 8px;font-size:11px;cursor:pointer;white-space:nowrap}',
    '.btn:active{background:#242b3a}',
    '.grid{display:grid;grid-template-columns:repeat(3,1fr);gap:5px}',
    '.grid a{display:block;text-align:center;text-decoration:none;color:#b9c4da;background:#161b25;',
    'border:1px solid #262d3d;border-radius:7px;padding:6px 2px;font-size:11px}',
    '.empty{color:#6f7c96;font-size:11px;text-align:center;padding:4px 0}'
  ].join('');
  root.appendChild(css);

  var panel = document.createElement('div');
  panel.className = 'panel' + (state.collapsed ? ' collapsed' : '');
  panel.innerHTML =
    '<div class="hd" data-drag>' +
      '<span class="dot"></span>' +
      '<span class="ttl">Overlay</span>' +
      '<button class="ico" data-act="collapse">' + (state.collapsed ? '+' : '–') + '</button>' +
      '<button class="ico" data-act="close">×</button>' +
    '</div>' +
    '<div class="bd">' +
      '<div class="row"><span class="lbl">CA</span><span class="ca" data-ca>—</span>' +
        '<button class="btn" data-act="copy">Copy</button></div>' +
      '<div class="grid" data-links></div>' +
      '<div class="row"><button class="btn" data-act="rescan" style="flex:1">Rescan page</button></div>' +
    '</div>';
  root.appendChild(panel);

  // --- position -------------------------------------------------------
  function place(x, y) {
    var w = panel.offsetWidth || 250, h = panel.offsetHeight || 120;
    x = Math.max(4, Math.min(x, innerWidth - w - 4));
    y = Math.max(4, Math.min(y, innerHeight - h - 4));
    state.x = x; state.y = y;
    panel.style.left = x + 'px'; panel.style.top = y + 'px';
  }
  place(state.x == null ? innerWidth - 258 : state.x, state.y == null ? 70 : state.y);

  var drag = null;
  function pt(e) { var t = e.touches ? e.touches[0] : e; return { x: t.clientX, y: t.clientY }; }
  function down(e) {
    if (e.target.closest && e.target.closest('button')) return;
    var p = pt(e);
    drag = { dx: p.x - panel.offsetLeft, dy: p.y - panel.offsetTop };
    e.preventDefault();
  }
  function move(e) { if (!drag) return; var p = pt(e); place(p.x - drag.dx, p.y - drag.dy); e.preventDefault(); }
  function up() { if (drag) { drag = null; save(); } }
  var hd = panel.querySelector('[data-drag]');
  hd.addEventListener('touchstart', down, { passive: false });
  hd.addEventListener('mousedown', down);
  addEventListener('touchmove', move, { passive: false });
  addEventListener('mousemove', move);
  addEventListener('touchend', up);
  addEventListener('mouseup', up);

  // --- render ---------------------------------------------------------
  var token = null;
  function render() {
    token = detectToken();
    panel.querySelector('[data-ca]').textContent = token ? short(token.addr) : 'not found';
    var g = panel.querySelector('[data-links]');
    g.innerHTML = '';
    if (!token) { g.innerHTML = '<div class="empty" style="grid-column:1/-1">open a token page</div>'; return; }
    links(token.addr).forEach(function (l) {
      var a = document.createElement('a');
      a.textContent = l[0]; a.href = l[1]; a.target = '_blank'; a.rel = 'noopener';
      g.appendChild(a);
    });
  }

  function copy(text, btn) {
    var done = function () { var o = btn.textContent; btn.textContent = '✓'; setTimeout(function () { btn.textContent = o; }, 900); };
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(text).then(done, function () { fallback(text); done(); });
    } else { fallback(text); done(); }
  }
  function fallback(text) {
    var ta = document.createElement('textarea');
    ta.value = text; ta.style.cssText = 'position:fixed;opacity:0';
    document.body.appendChild(ta); ta.select();
    try { document.execCommand('copy'); } catch (e) {}
    document.body.removeChild(ta);
  }

  panel.addEventListener('click', function (e) {
    var b = e.target.closest && e.target.closest('[data-act]');
    if (!b) return;
    var act = b.getAttribute('data-act');
    if (act === 'close') { api.destroy(); }
    else if (act === 'collapse') {
      state.collapsed = !state.collapsed;
      panel.classList.toggle('collapsed', state.collapsed);
      b.textContent = state.collapsed ? '+' : '–';
      save();
    }
    else if (act === 'copy') { if (token) copy(token.addr, b); }
    else if (act === 'rescan') { render(); }
  });

  // SPA route changes (Axiom/GMGN are client-side routed)
  var lastUrl = location.href;
  var tick = setInterval(function () {
    if (location.href !== lastUrl) { lastUrl = location.href; render(); }
  }, 800);

  var api = {
    version: '0.1.0',
    host: host(),
    toggle: function () { mount.style.display = mount.style.display === 'none' ? '' : 'none'; },
    refresh: render,
    destroy: function () {
      clearInterval(tick);
      removeEventListener('touchmove', move); removeEventListener('mousemove', move);
      removeEventListener('touchend', up); removeEventListener('mouseup', up);
      mount.remove(); delete window[NS];
    }
  };
  window[NS] = api;

  render();
})();
