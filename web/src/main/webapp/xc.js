"use strict";
(function () {
  var canvas = document.getElementById('screen');
  var typer = document.getElementById('type');
  var gl = canvas.getContext('webgl2', { alpha: false, antialias: false, premultipliedAlpha: false, preserveDrawingBuffer: false });
  var queue = [];
  var clip = '';
  var readyCbs = [], isReady = false;
  var textures = {}, named = {}, nextTex = 1;
  var store = {}, db = null;
  var params = new URLSearchParams(location.search);
  var sockets = {}, nextSocket = 1;
  function toB64(bytes) {
    var s = '', CH = 0x8000;
    for (var i = 0; i < bytes.length; i += CH) s += String.fromCharCode.apply(null, bytes.subarray(i, i + CH));
    return btoa(s);
  }
  function fromB64(b64) {
    var s = atob(b64), out = new Uint8Array(s.length);
    for (var i = 0; i < s.length; i++) out[i] = s.charCodeAt(i);
    return out;
  }

  var KEYS = {
    Space: 32, Quote: 39, Comma: 44, Minus: 45, Period: 46, Slash: 47,
    Digit0: 48, Digit1: 49, Digit2: 50, Digit3: 51, Digit4: 52, Digit5: 53, Digit6: 54, Digit7: 55, Digit8: 56, Digit9: 57,
    Semicolon: 59, Equal: 61, BracketLeft: 91, Backslash: 92, BracketRight: 93, Backquote: 96, IntlBackslash: 162,
    Escape: 256, Enter: 257, Tab: 258, Backspace: 259, Insert: 260, Delete: 261, ArrowRight: 262, ArrowLeft: 263,
    ArrowDown: 264, ArrowUp: 265, PageUp: 266, PageDown: 267, Home: 268, End: 269, CapsLock: 280, ScrollLock: 281,
    NumLock: 282, PrintScreen: 283, Pause: 284,
    Numpad0: 320, Numpad1: 321, Numpad2: 322, Numpad3: 323, Numpad4: 324, Numpad5: 325, Numpad6: 326, Numpad7: 327,
    Numpad8: 328, Numpad9: 329, NumpadDecimal: 330, NumpadDivide: 331, NumpadMultiply: 332, NumpadSubtract: 333,
    NumpadAdd: 334, NumpadEnter: 335, NumpadEqual: 336,
    ShiftLeft: 340, ControlLeft: 341, AltLeft: 342, MetaLeft: 343, ShiftRight: 344, ControlRight: 345, AltRight: 346,
    MetaRight: 347, ContextMenu: 348
  };
  for (var i = 0; i < 26; i++) KEYS['Key' + String.fromCharCode(65 + i)] = 65 + i;
  for (var f = 1; f <= 25; f++) KEYS['F' + f] = 289 + f;

  function mods(e) {
    return (e.shiftKey ? 1 : 0) | (e.ctrlKey ? 2 : 0) | (e.altKey ? 4 : 0) | (e.metaKey ? 8 : 0);
  }
  function glfwMods(e) {
    var m = mods(e);
    if (e.metaKey && /Mac/.test(navigator.platform)) m |= 2;
    return m;
  }
  function dpr() {
    return canvas.clientWidth > 0 && canvas.width > 1 ? canvas.width / canvas.clientWidth : (window.devicePixelRatio || 1);
  }
  function pos(e) {
    var r = canvas.getBoundingClientRect(), k = dpr();
    return [(e.clientX - r.left) * k, (e.clientY - r.top) * k];
  }

  var pendingPaste = null;
  var SENT = '\u200b', keyedBack = false, keyedEnter = false;
  function resetTyper() {
    typer.value = SENT;
    try { typer.setSelectionRange(1, 1); } catch (err) {}
  }
  function soft(e) { return e.key === 'Unidentified' || e.key === 'Process' || e.keyCode === 229; }
  function press(code) { queue.push(['k', code, 0, 0]); queue.push(['u', code, 0, 0]); }
  function keyDown(e) {
    if (soft(e)) return;
    var code = KEYS[e.code];
    if (code === undefined && e.key) code = KEYS[e.key];
    if (code === undefined) code = -1;
    if (code === KEYS.Backspace) keyedBack = true;
    if (code === KEYS.Enter) keyedEnter = true;
    var m = glfwMods(e);
    var ctrl = (m & 2) !== 0;
    if (ctrl && code === 86) {
      pendingPaste = ['k', code, 0, m];
      setTimeout(function () {
        if (!pendingPaste) return;
        var p = pendingPaste; pendingPaste = null;
        if (navigator.clipboard && navigator.clipboard.readText) {
          navigator.clipboard.readText().then(function (t) { clip = t; queue.push(p); }, function () { queue.push(p); });
        } else queue.push(p);
      }, 80);
      return;
    }
    queue.push(['k', code, 0, m]);
    var printable = e.key && e.key.length === 1 && !ctrl && !e.altKey;
    if (!printable && !(ctrl && (code === 67 || code === 88))) e.preventDefault();
    if (ctrl && code !== 67 && code !== 88) e.preventDefault();
  }
  function keyUp(e) {
    if (soft(e)) return;
    var code = KEYS[e.code];
    if (code === undefined && e.key) code = KEYS[e.key];
    queue.push(['u', code === undefined ? -1 : code, 0, glfwMods(e)]);
    keyedBack = keyedEnter = false;
  }

  typer.addEventListener('keydown', keyDown);
  typer.addEventListener('keyup', keyUp);
  function outside(e) {
    if (e.target === typer) return false;
    var t = e.target && e.target.tagName;
    return t !== 'INPUT' && t !== 'TEXTAREA';
  }
  document.addEventListener('keydown', function (e) {
    if (!outside(e)) return;
    keyDown(e);
    var m = glfwMods(e);
    if (e.key && e.key.length === 1 && !(m & 2) && !e.altKey) {
      queue.push(['c', e.key.codePointAt(0)]);
      e.preventDefault();
    }
    if (!coarse) focusTyper();
  });
  document.addEventListener('keyup', function (e) { if (outside(e)) keyUp(e); });
  function typed(t) {
    for (var ch of t) if (ch !== SENT && ch !== '\n' && ch !== '\r') queue.push(['c', ch.codePointAt(0)]);
  }
  typer.addEventListener('input', function (e) {
    if (e.isComposing) return;
    var type = e.inputType || '';
    var t = typer.value;
    resetTyper();
    if (type.indexOf('delete') === 0 && type.indexOf('Backward') > 0) {
      if (!keyedBack) press(KEYS.Backspace);
    } else if (type === 'insertLineBreak' || type === 'insertParagraph') {
      if (!keyedEnter) press(KEYS.Enter);
    } else typed(t);
    keyedBack = keyedEnter = false;
  });
  typer.addEventListener('compositionend', function () {
    var t = typer.value;
    resetTyper();
    typed(t);
  });
  typer.addEventListener('focus', resetTyper);
  function onPaste(e) {
    var t = (e.clipboardData || window.clipboardData).getData('text');
    clip = t == null ? '' : t;
    e.preventDefault();
    if (pendingPaste) { var p = pendingPaste; pendingPaste = null; queue.push(p); }
  }
  typer.addEventListener('paste', onPaste);
  document.addEventListener('paste', function (e) { if (e.target !== typer) onPaste(e); });
  function onCopy(e) {
    if (clip) { e.clipboardData.setData('text/plain', clip); e.preventDefault(); }
  }
  document.addEventListener('copy', onCopy);
  document.addEventListener('cut', onCopy);

  function focusTyper() {
    var a = document.activeElement;
    if (a && a !== typer && a.tagName === 'INPUT') return;
    if (a !== typer) typer.focus({ preventScroll: true });
  }
  canvas.addEventListener('mousedown', function (e) {
    if (Date.now() - lastTouch < 800) { e.preventDefault(); return; }
    touchNow = false;
    var p = pos(e);
    var b = e.button === 1 ? 2 : e.button === 2 ? 1 : e.button;
    queue.push(['d', p[0], p[1], b, glfwMods(e), 0]);
    e.preventDefault();
    focusTyper();
  });
  window.addEventListener('mouseup', function (e) {
    if (Date.now() - lastTouch < 800) return;
    var p = pos(e);
    var b = e.button === 1 ? 2 : e.button === 2 ? 1 : e.button;
    queue.push(['r', p[0], p[1], b, glfwMods(e)]);
  });
  window.addEventListener('mousemove', function (e) {
    if (Date.now() - lastTouch < 800) return;
    var p = pos(e);
    queue.push(['m', p[0], p[1]]);
  });
  canvas.addEventListener('wheel', function (e) {
    var p = pos(e);
    var k = e.deltaMode === 1 ? 1 / 3 : e.deltaMode === 2 ? 3 : 1 / 100;
    var dx = -e.deltaX * k, dy = -e.deltaY * k;
    if (e.shiftKey && dx === 0) { dx = dy; dy = 0; }
    queue.push(['w', p[0], p[1], dx, dy]);
    e.preventDefault();
  }, { passive: false });
  canvas.addEventListener('contextmenu', function (e) { e.preventDefault(); });
  window.addEventListener('blur', function () { queue.push(['b']); });

  var coarse = !!(window.matchMedia && matchMedia('(hover: none) and (pointer: coarse)').matches);
  var touchNow = coarse, lastTouch = 0;
  var fingers = {}, tmode = null, t0 = null, longTimer = 0, prevMid = null, prevDist = 0;
  var textOn = false, textRects = [], textScale = 1;
  var LONG = 450, SLOP = 10;
  function fingerList() { return Object.keys(fingers).map(function (k) { return fingers[k]; }); }
  function inText(p) {
    for (var i = 0; i + 3 < textRects.length; i += 4) {
      var x = textRects[i] * textScale, y = textRects[i + 1] * textScale;
      var w = textRects[i + 2] * textScale, h = textRects[i + 3] * textScale;
      if (p[0] >= x && p[0] < x + w && p[1] >= y && p[1] < y + h) return true;
    }
    return false;
  }
  function twoState() {
    var f = fingerList();
    var mid = [(f[0][0] + f[1][0]) / 2, (f[0][1] + f[1][1]) / 2];
    return [mid, Math.max(1, Math.hypot(f[0][0] - f[1][0], f[0][1] - f[1][1]))];
  }
  function keyboardShown() {
    var vv = window.visualViewport;
    return !!vv && window.innerHeight - vv.height > 100;
  }
  function tapKeyboard(p) {
    if (inText(p)) {
      if (document.activeElement === typer && keyboardShown()) return;
      if (document.activeElement === typer) typer.blur();
      typer.focus({ preventScroll: true });
    } else if (!textOn && document.activeElement === typer) typer.blur();
  }
  canvas.addEventListener('touchstart', function (e) {
    e.preventDefault();
    touchNow = true; lastTouch = Date.now();
    for (var t of e.changedTouches) fingers[t.identifier] = pos(t);
    var n = fingerList().length;
    if (n === 1 && tmode === null) {
      var t1 = e.changedTouches[0], p = fingers[t1.identifier];
      t0 = { id: t1.identifier, x: p[0], y: p[1] };
      tmode = 'wait';
      queue.push(['m', p[0], p[1]]);
      clearTimeout(longTimer);
      longTimer = setTimeout(function () {
        if (tmode !== 'wait') return;
        tmode = 'peek';
        t0.moved = false;
        if (navigator.vibrate) navigator.vibrate(12);
      }, LONG);
    } else if (n === 2 && (tmode === 'wait' || tmode === 'drag' || tmode === 'peek')) {
      clearTimeout(longTimer);
      if (tmode === 'drag') { var q = fingers[t0.id] || [t0.x, t0.y]; queue.push(['r', q[0], q[1], 0, 0]); }
      var tap2 = tmode !== 'drag';
      tmode = 'two';
      var st = twoState(); prevMid = st[0]; prevDist = st[1];
      t0.two = tap2 ? { at: Date.now(), mid: st[0], dist: st[1], moved: false } : null;
    }
  }, { passive: false });
  canvas.addEventListener('touchmove', function (e) {
    e.preventDefault();
    lastTouch = Date.now();
    for (var t of e.changedTouches) if (fingers[t.identifier]) fingers[t.identifier] = pos(t);
    if (tmode === 'wait') {
      var p = fingers[t0.id], k = dpr();
      if (p && Math.hypot(p[0] - t0.x, p[1] - t0.y) > SLOP * k) {
        clearTimeout(longTimer);
        tmode = 'drag';
        queue.push(['g', t0.x, t0.y, p[0] - t0.x, p[1] - t0.y]);
        queue.push(['m', p[0], p[1]]);
      }
    } else if (tmode === 'peek') {
      var pk = fingers[t0.id], kk = dpr();
      if (pk) {
        if (Math.hypot(pk[0] - t0.x, pk[1] - t0.y) > SLOP * kk) t0.moved = true;
        queue.push(['m', pk[0], pk[1]]);
      }
    } else if (tmode === 'drag') {
      var d = fingers[t0.id];
      if (d) queue.push(['m', d[0], d[1]]);
    } else if (tmode === 'two' && fingerList().length >= 2) {
      var st = twoState();
      queue.push(['p', st[0][0], st[0][1], st[0][0] - prevMid[0], st[0][1] - prevMid[1]]);
      if (Math.abs(st[1] / prevDist - 1) > 0.002) queue.push(['z', st[0][0], st[0][1], st[1] / prevDist]);
      prevMid = st[0]; prevDist = st[1];
      var tw = t0 && t0.two;
      if (tw && (Math.hypot(st[0][0] - tw.mid[0], st[0][1] - tw.mid[1]) > SLOP * dpr()
          || Math.abs(st[1] / tw.dist - 1) > 0.15)) tw.moved = true;
    }
  }, { passive: false });
  function touchEnd(e, cancelled) {
    e.preventDefault();
    lastTouch = Date.now();
    for (var t of e.changedTouches) {
      var p = fingers[t.identifier] || pos(t);
      delete fingers[t.identifier];
      if (!t0 || t.identifier !== t0.id) continue;
      if (tmode === 'wait') {
        clearTimeout(longTimer);
        tmode = 'done';
        if (!cancelled) {
          queue.push(['d', t0.x, t0.y, 0, 0, 1]); queue.push(['r', t0.x, t0.y, 0, 0]);
          tapKeyboard([t0.x, t0.y]);
        }
      } else if (tmode === 'drag') {
        tmode = 'done';
        queue.push(['r', p[0], p[1], 0, 0]);
      } else if (tmode === 'peek') {
        tmode = 'done';
        if (!cancelled && !t0.moved) queue.push(['h', t0.x, t0.y]);
        t0.keep = !cancelled;
      }
    }
    if (tmode === 'two' && fingerList().length < 2) tmode = 'done';
    if (fingerList().length === 0) {
      var tw2 = t0 && t0.two;
      if (tw2 && !cancelled && !tw2.moved && Date.now() - tw2.at < 400) {
        queue.push(['d', t0.x, t0.y, 1, 0, 1]); queue.push(['r', t0.x, t0.y, 1, 0]);
        t0.keep = true;
      }
      var keep = t0 && t0.keep;
      tmode = null; t0 = null;
      if (!keep) queue.push(['m', -10000, -10000]);
    }
  }
  canvas.addEventListener('touchend', function (e) { touchEnd(e, false); }, { passive: false });
  canvas.addEventListener('touchcancel', function (e) { touchEnd(e, true); }, { passive: false });

  var VS = '#version 300 es\n' +
    'in vec2 aPos; in vec2 aUv; in uvec4 aColor; uniform vec2 uSize; out vec2 vUv; out vec4 vColor;\n' +
    'void main(){ gl_Position = vec4(aPos.x / uSize.x * 2.0 - 1.0, 1.0 - aPos.y / uSize.y * 2.0, 0.0, 1.0);\n' +
    ' vUv = aUv; vColor = vec4(aColor.b, aColor.g, aColor.r, aColor.a) / 255.0; }';
  var FS = '#version 300 es\nprecision mediump float;\n' +
    'in vec2 vUv; in vec4 vColor; uniform sampler2D uTex; uniform int uTextured; out vec4 o;\n' +
    'void main(){ vec4 c = vColor; if (uTextured == 1) c *= texture(uTex, vUv);' +
    ' else if (uTextured == 2) { vec4 t = texture(uTex, vUv); c = t.a > 0.0 ? vec4(t.rgb / t.a, t.a) * c : vec4(0.0); }' +
    ' if (c.a <= 0.003) discard; o = c; }';
  var prog, buf, ibuf, uSize, uTextured, indexCap = 0;
  function shader(type, src) {
    var s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw new Error(gl.getShaderInfoLog(s));
    return s;
  }
  function setupGl() {
    prog = gl.createProgram();
    gl.attachShader(prog, shader(gl.VERTEX_SHADER, VS));
    gl.attachShader(prog, shader(gl.FRAGMENT_SHADER, FS));
    gl.bindAttribLocation(prog, 0, 'aPos');
    gl.bindAttribLocation(prog, 1, 'aUv');
    gl.bindAttribLocation(prog, 2, 'aColor');
    gl.linkProgram(prog);
    gl.useProgram(prog);
    uSize = gl.getUniformLocation(prog, 'uSize');
    uTextured = gl.getUniformLocation(prog, 'uTextured');
    gl.uniform1i(gl.getUniformLocation(prog, 'uTex'), 0);
    var vao = gl.createVertexArray(); gl.bindVertexArray(vao);
    buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
    gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 2, gl.FLOAT, false, 20, 0);
    gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 2, gl.FLOAT, false, 20, 8);
    gl.enableVertexAttribArray(2); gl.vertexAttribIPointer(2, 4, gl.UNSIGNED_BYTE, 20, 16);
    ibuf = gl.createBuffer(); gl.bindBuffer(gl.ELEMENT_ARRAY_BUFFER, ibuf);
    gl.enable(gl.BLEND);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  }
  function ensureIndices(quads) {
    if (quads <= indexCap) return;
    var cap = Math.max(4096, indexCap * 2); while (cap < quads) cap *= 2;
    var idx = new Uint32Array(cap * 6);
    for (var q = 0; q < cap; q++) {
      var v = q * 4, o = q * 6;
      idx[o] = v; idx[o + 1] = v + 1; idx[o + 2] = v + 2; idx[o + 3] = v; idx[o + 4] = v + 2; idx[o + 5] = v + 3;
    }
    gl.bufferData(gl.ELEMENT_ARRAY_BUFFER, idx, gl.STATIC_DRAW);
    indexCap = cap;
  }
  var exactW = 0, exactH = 0;
  try {
    new ResizeObserver(function (entries) {
      var e = entries[0];
      if (e.devicePixelContentBoxSize) {
        exactW = e.devicePixelContentBoxSize[0].inlineSize;
        exactH = e.devicePixelContentBoxSize[0].blockSize;
      }
    }).observe(canvas, { box: 'device-pixel-content-box' });
  } catch (e) {}
  var shift = 0, caretCss = 0, selNow = '', allNow = '';
  var bar = document.getElementById('textbar');
  function say(msg) {
    var d = document.createElement('div'); d.className = 'toast'; d.textContent = msg;
    document.getElementById('toasts').appendChild(d);
    setTimeout(function () { d.remove(); }, 1600);
  }
  function combo(code) { queue.push(['k', code, 0, 2]); queue.push(['u', code, 0, 2]); }
  function copyOut(t) {
    clip = t;
    if (navigator.clipboard && navigator.clipboard.writeText) {
      navigator.clipboard.writeText(t).then(function () { say('скопировано'); }, function () { say('скопировано только сюда'); });
    } else say('скопировано только сюда');
  }
  function barAct(a) {
    if (a === 'all') combo(65);
    else if (a === 'copy') { var t = selNow || allNow; if (t) copyOut(t); }
    else if (a === 'cut') { if (selNow) { copyOut(selNow); combo(88); } }
    else if (a === 'paste') {
      if (navigator.clipboard && navigator.clipboard.readText) {
        navigator.clipboard.readText().then(function (t) { clip = t || ''; combo(86); },
          function () { if (clip) combo(86); else say('браузер не дал буфер'); });
      } else if (clip) combo(86);
    }
  }
  if (bar) {
    bar.addEventListener('touchstart', function (e) {
      e.preventDefault();
      var b = e.target.closest('button'); if (b) b.classList.add('on');
    }, { passive: false });
    bar.addEventListener('touchend', function (e) {
      e.preventDefault();
      var b = e.target.closest('button');
      bar.querySelectorAll('button').forEach(function (x) { x.classList.remove('on'); });
      if (b) barAct(b.getAttribute('data-a'));
    }, { passive: false });
    bar.addEventListener('mousedown', function (e) { e.preventDefault(); });
    bar.addEventListener('click', function (e) {
      var b = e.target.closest('button'); if (b) barAct(b.getAttribute('data-a'));
    });
  }
  function placeBar() {
    if (!bar) return;
    var show = textOn && touchNow;
    bar.style.display = show ? 'flex' : 'none';
    if (!show) return;
    var top = caretCss - shift - 52;
    if (top < 6) top = caretCss - shift + 30;
    bar.style.top = Math.round(top) + 'px';
  }
  function fitViewport() {
    var vv = window.visualViewport;
    if (!vv) return;
    var bottom = vv.offsetTop + vv.height;
    var want = 0;
    if (textOn && canvas.clientHeight - bottom > 60) want = Math.max(0, Math.round(caretCss + 48 - bottom));
    if (want === shift) return;
    shift = want;
    canvas.style.transform = shift ? 'translateY(' + (-shift) + 'px)' : '';
  }
  window.addEventListener('orientationchange', function () { canvas.style.height = ''; });
  if (window.visualViewport) {
    visualViewport.addEventListener('resize', fitViewport);
    visualViewport.addEventListener('scroll', fitViewport);
    fitViewport();
  }
  function resizeCanvas() {
    var k = window.devicePixelRatio || 1;
    var w = Math.max(1, Math.round(canvas.clientWidth * k));
    var h = Math.max(1, Math.round(canvas.clientHeight * k));
    if (exactW && Math.abs(exactW - w) <= 2 && Math.abs(exactH - h) <= 2) { w = exactW; h = exactH; }
    if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  }
  function uploadTexture(img, mips) {
    var t = gl.createTexture();
    gl.bindTexture(gl.TEXTURE_2D, t);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, !!mips);
    gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
    gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
    if (mips) { gl.generateMipmap(gl.TEXTURE_2D); t.mips = true; t.premul = true; }
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE);
    gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
    var id = nextTex++;
    textures[id] = t;
    return id;
  }
  function loadImage(url) {
    return new Promise(function (ok, bad) {
      var img = new Image(); img.crossOrigin = 'anonymous';
      img.onload = function () { ok(img); }; img.onerror = bad; img.src = url;
    });
  }

  var toastBox = document.getElementById('toasts');
  var sound = { ctx: null, buffer: null, src: null, state: 0, id: '', startAt: 0, offset: 0, playingNow: false, looping: false,
    volume: 1, pitch: 1, index: null };

  window.XC = {
    boot: function (start) {
      var bar = document.querySelector('#bar i'), st = document.getElementById('state');
      function step(p, t) { bar.style.width = (p * 100) + '%'; st.textContent = t || ''; }
      if (!gl) { step(0, 'Браузер не поддерживает WebGL2'); return; }
      setupGl(); resizeCanvas();
      step(0.1, 'хранилище…');
      openDb().then(function () {
        step(0.3, 'шрифт и иконки…');
        var up = { font: 4, items16: 4, items48: 1, particles: 1 };
        var later = { items48: true, particles: true };
        return Promise.all(Object.keys(up).map(function (n) {
          var job = loadAtlas(n, up[n]);
          return later[n] ? null : job;
        }));
        function loadAtlas(n, k) {
          return loadImage('assets/' + n + '.png').then(function (img) {
            var src = img;
            if (k > 1) {
              var c = document.createElement('canvas');
              c.width = img.width * k; c.height = img.height * k;
              var g = c.getContext('2d'); g.imageSmoothingEnabled = false;
              g.drawImage(img, 0, 0, c.width, c.height);
              src = c;
            }
            named[n] = uploadTexture(src, n !== 'particles');
          });
        }
      }).then(function () {
        step(1, '');
        document.getElementById('splash').remove();
        isReady = true;
        start();
        readyCbs.forEach(function (f) { f(); });
        readyCbs = [];
        canvas.focus(); if (!coarse) focusTyper();
      }).catch(function (e) { step(0, 'Ошибка загрузки: ' + e); console.error(e); });
    },
    ready: function (f) { if (isReady) f(); else readyCbs.push(f); },
    nextFrame: function (f) { requestAnimationFrame(function () { f(); }); },
    width: function () { resizeCanvas(); return canvas.width; },
    height: function () { return canvas.height; },
    ratio: function () { return dpr(); },
    param: function (k) { return params.get(k) || ''; },
    render: function (verts, vcount, cmds, ccount, scale) {
      resizeCanvas();
      gl.viewport(0, 0, canvas.width, canvas.height);
      gl.disable(gl.SCISSOR_TEST);
      gl.clearColor(0.08, 0.086, 0.11, 1);
      gl.clear(gl.COLOR_BUFFER_BIT);
      if (vcount === 0) return;
      gl.bindBuffer(gl.ARRAY_BUFFER, buf);
      gl.bufferData(gl.ARRAY_BUFFER, new Uint8Array(verts.buffer, verts.byteOffset, vcount * 20), gl.STREAM_DRAW);
      ensureIndices(vcount / 4);
      gl.uniform2f(uSize, canvas.width / scale, canvas.height / scale);
      for (var i = 0; i < ccount; i++) {
        var o = i * 9;
        var first = cmds[o], count = cmds[o + 1], tex = cmds[o + 2], linear = cmds[o + 3], blend = cmds[o + 4];
        if (cmds[o + 7] >= 0) {
          gl.enable(gl.SCISSOR_TEST);
          var sx = cmds[o + 5] * scale, sy = cmds[o + 6] * scale, sw = cmds[o + 7] * scale, sh = cmds[o + 8] * scale;
          gl.scissor(Math.round(sx), Math.round(canvas.height - sy - sh), Math.max(0, Math.round(sw)), Math.max(0, Math.round(sh)));
        } else gl.disable(gl.SCISSOR_TEST);
        if (blend === 1) gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        else if (blend === 2) gl.blendFunc(gl.ONE, gl.ONE);
        else gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ONE, gl.ONE_MINUS_SRC_ALPHA);
        var tx = tex > 0 ? textures[tex] : null;
        if (tx) {
          gl.uniform1i(uTextured, tx.premul ? 2 : 1);
          gl.bindTexture(gl.TEXTURE_2D, tx);
          gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, linear ? (tx.mips ? gl.LINEAR_MIPMAP_LINEAR : gl.LINEAR) : gl.NEAREST);
          gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, linear ? gl.LINEAR : gl.NEAREST);
        } else gl.uniform1i(uTextured, 0);
        gl.drawElements(gl.TRIANGLES, count / 4 * 6, gl.UNSIGNED_INT, first / 4 * 6 * 4);
      }
    },
    pollEvents: function () { var q = queue; queue = []; return q; },
    cursor: function (css) { if (canvas.style.cursor !== css) canvas.style.cursor = css; },
    setClipboard: function (t) {
      clip = t;
      if (navigator.clipboard && navigator.clipboard.writeText) navigator.clipboard.writeText(t).catch(function () {});
    },
    clipboard: function () { return clip; },
    textFocus: function (on, x, y, rects, scale, sel, all) {
      selNow = sel || ''; allNow = all || '';
      var k = dpr();
      caretCss = y / k;
      typer.style.left = (x / k) + 'px'; typer.style.top = Math.max(0, y / k - shift) + 'px';
      textRects = rects ? rects.split(',').map(Number) : [];
      textScale = scale;
      if (touchNow) {
        if (on && !textOn && document.activeElement !== typer) typer.focus({ preventScroll: true });
        if (!on && textOn && document.activeElement === typer) typer.blur();
        if (on && !textOn) canvas.style.height = canvas.clientHeight + 'px';
        if (!on && textOn) canvas.style.height = '';
      } else if (document.activeElement !== typer) focusTyper();
      textOn = on;
      fitViewport();
      placeBar();
    },
    touch: function () { return touchNow; },
    coarse: function () { return coarse; },
    log: function (level, msg) { (console[level] || console.log).call(console, msg); },
    toast: function (msg) {
      var d = document.createElement('div'); d.className = 'toast'; d.textContent = msg;
      toastBox.appendChild(d);
      setTimeout(function () { d.remove(); }, 4000);
    },
    storedPaths: function () { return Object.keys(store).join('\n'); },
    stored: function (p) { return store[p] || ''; },
    store: function (p, b64) { store[p] = b64; if (db) tx('readwrite').put(b64, p); },
    unstore: function (p) { delete store[p]; if (db) tx('readwrite').delete(p); },
    pickFile: function (accept, cb) {
      pickLocal(accept, cb);
    },
    pickLocalFile: function (accept, cb) {
      var inp = document.createElement('input'); inp.type = 'file'; if (accept) inp.accept = accept;
      inp.onchange = function () {
        var file = inp.files[0]; if (!file) { cb('', ''); return; }
        var r = new FileReader();
        r.onload = function () { var s = r.result; cb(file.name, s.substring(s.indexOf(',') + 1)); };
        r.readAsDataURL(file);
      };
      inp.click();
    },
    saveFile: function (name, b64, mime) {
      var a = document.createElement('a'); a.href = 'data:' + (mime || 'application/octet-stream') + ';base64,' + b64;
      a.download = name; document.body.appendChild(a); a.click(); a.remove();
    },
    texture: function (n) { return named[n] || 0; },
    loadTexture: function (url, cb) {
      loadImage(url).then(function (img) { cb(uploadTexture(img), img.width, img.height); }, function () { cb(0, 0, 0); });
    },
    releaseTexture: function (id) { if (textures[id]) { gl.deleteTexture(textures[id]); delete textures[id]; } },
    fetch: function (url, method, headers, body, timeout, cb) {
      var h = new Headers(), lines = headers ? headers.split('\n') : [];
      for (var i = 0; i + 1 < lines.length; i += 2) if (lines[i]) h.append(lines[i], lines[i + 1]);
      var ctl = new AbortController(), timedOut = false;
      var timer = setTimeout(function () { timedOut = true; ctl.abort(); }, timeout || 60000);
      var opts = { method: method, headers: h, signal: ctl.signal };
      if (body) opts.body = fromB64(body);
      fetch(url, opts).then(function (r) {
        return r.arrayBuffer().then(function (buf) { clearTimeout(timer); cb(r.status, toB64(new Uint8Array(buf)), '', false); });
      }).catch(function (e) {
        clearTimeout(timer);
        cb(0, '', timedOut ? 'timed out' : ('' + (e && e.message || e)), timedOut);
      });
    },
    socket: function (url, cb) {
      var id = nextSocket++, ws;
      try { ws = new WebSocket(url); } catch (e) { setTimeout(function () { cb(id, 'error', '' + e, 0); }, 0); return; }
      sockets[id] = ws;
      ws.onopen = function () { cb(id, 'open', '', 0); };
      ws.onmessage = function (e) { if (typeof e.data === 'string') cb(id, 'text', e.data, 0); };
      ws.onerror = function () { cb(id, 'error', 'связь оборвалась', 0); };
      ws.onclose = function (e) { delete sockets[id]; cb(id, 'close', e.reason || '', e.code); };
    },
    socketSend: function (id, text) { var ws = sockets[id]; if (ws && ws.readyState === 1) ws.send(text); },
    socketClose: function (id, code, reason) { var ws = sockets[id]; if (ws) try { ws.close(code === 1000 ? 1000 : 1000, reason); } catch (e) {} },
    ed: {
      generate: function (cb) {
        crypto.subtle.generateKey({ name: 'Ed25519' }, true, ['sign', 'verify']).then(function (pair) {
          return Promise.all([crypto.subtle.exportKey('pkcs8', pair.privateKey), crypto.subtle.exportKey('spki', pair.publicKey)]);
        }).then(function (k) { cb(toB64(new Uint8Array(k[0])), toB64(new Uint8Array(k[1])), ''); },
                function (e) { cb('', '', 'Ed25519 недоступен: ' + e); });
      },
      sign: function (pkcs8, msg, cb) {
        crypto.subtle.importKey('pkcs8', fromB64(pkcs8), { name: 'Ed25519' }, false, ['sign']).then(function (key) {
          return crypto.subtle.sign({ name: 'Ed25519' }, key, fromB64(msg));
        }).then(function (sig) { cb(toB64(new Uint8Array(sig)), '', ''); },
                function (e) { cb('', '', 'подпись не вышла: ' + e); });
      }
    },
    sound: {
      want: function (id) {
        if (id === sound.id) return;
        XC.sound.stop(); sound.id = id; sound.buffer = null; sound.offset = 0;
        if (!id) { sound.state = 0; return; }
        sound.state = 1;
        var go = function () {
          var list = sound.index[id.replace('minecraft:', '')];
          if (!list || !list.length) { sound.state = 3; return; }
          if (!sound.ctx) sound.ctx = new AudioContext();
          fetch('sounds/' + list[0] + '.ogg').then(function (r) { if (!r.ok) throw 0; return r.arrayBuffer(); })
            .then(function (b) { return sound.ctx.decodeAudioData(b); })
            .then(function (buf) { if (sound.id === id) { sound.buffer = buf; sound.state = 2; } },
                  function () { if (sound.id === id) sound.state = 3; });
        };
        if (sound.index) go();
        else fetch('sounds/index.json').then(function (r) { return r.json(); })
          .then(function (j) { sound.index = j; go(); }, function () { sound.index = {}; sound.state = 3; });
      },
      state: function () { return sound.state; },
      duration: function () { return sound.buffer ? sound.buffer.duration / sound.pitch : 0; },
      position: function () {
        if (!sound.playingNow) return sound.offset;
        var t = sound.offset + (sound.ctx.currentTime - sound.startAt);
        var d = XC.sound.duration();
        if (sound.looping && d > 0) return t % d;
        if (t >= d) { sound.playingNow = false; sound.offset = 0; return d; }
        return t;
      },
      playing: function () { XC.sound.position(); return sound.playingNow; },
      play: function () {
        if (!sound.buffer || sound.playingNow) return;
        if (sound.ctx.state === 'suspended') sound.ctx.resume();
        var s = sound.ctx.createBufferSource(); s.buffer = sound.buffer; s.loop = sound.looping;
        s.playbackRate.value = sound.pitch;
        var g = sound.ctx.createGain(); g.gain.value = sound.volume; s.connect(g); g.connect(sound.ctx.destination);
        s.start(0, sound.offset * sound.pitch); sound.src = s; sound.startAt = sound.ctx.currentTime; sound.playingNow = true;
      },
      pause: function () {
        if (!sound.playingNow) return;
        sound.offset = XC.sound.position(); sound.playingNow = false;
        try { sound.src.stop(); } catch (e) {}
      },
      stop: function () { XC.sound.pause(); sound.offset = 0; },
      seek: function (t) { var p = sound.playingNow; XC.sound.pause(); sound.offset = Math.max(0, t); if (p) XC.sound.play(); },
      setLoop: function (on) { sound.looping = on; if (sound.src) sound.src.loop = on; },
      loop: function () { return sound.looping; },
      mix: function (v, p) {
        var was = sound.playingNow; XC.sound.pause();
        sound.volume = Math.max(0, Math.min(2, v)); sound.pitch = Math.max(0.5, Math.min(2, p || 1));
        if (was) XC.sound.play();
      }
    }
  };

  function pickLocal(accept, cb) { XC.pickLocalFile(accept, cb); }


  function tx(mode) { return db.transaction('files', mode).objectStore('files'); }
  function openDb() {
    return new Promise(function (ok) {
      var req;
      try { req = indexedDB.open('xerocode', 1); } catch (e) { ok(); return; }
      req.onupgradeneeded = function () { req.result.createObjectStore('files'); };
      req.onerror = function () { ok(); };
      req.onsuccess = function () {
        db = req.result;
        var cur = tx('readonly').openCursor();
        cur.onsuccess = function () {
          var c = cur.result;
          if (c) { store[c.key] = c.value; c.continue(); } else ok();
        };
        cur.onerror = function () { ok(); };
      };
    });
  }
})();
