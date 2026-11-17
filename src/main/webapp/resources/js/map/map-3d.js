/*
 * 건물 상세 패널 왼쪽 "건물 3D 입체보기" (브이월드 WebGL 3D)
 *  - map-panel.js 가 건물 상세를 받으면 Bld3D.show(d) / 패널을 닫으면 Bld3D.hide()
 *  - 3D 지도는 resources/vworld3d/bld3d-frame.html (iframe).
 *    건물 핀이 보이는 확대 단계에 처음 들어와 1초 가만히 있으면 엔진만 미리 켜 두고(그리기는 멈춘 채, map-main.js → Bld3D.warmup),
 *    실제 그리기는 마커를 누른 순간부터. 화면 열 때 바로 켜 두고 계속 그리게 하면 네이버 지도 확대가 버벅인다
 *  - 상세 패널을 닫으면 3D 그리기를 멈춘다(pause). 다시 건물을 고르면 이어서 그린다
 *  - 마커를 누르면 상자는 안 보이게(.pre : visibility hidden) 둔 채 뒤에서 미리 그리고,
 *    상세 패널의 [3D 입체보기] 버튼(#btn3d)을 눌러야 보인다. 다 그려지면 버튼이 "준비됨" 으로 바뀐다
 *  - 층별 색 : 거주 가구(api/map/residents.do) 의 거주 층(resFlr) · 가구 가점(priority ≥ 10 = 취약) 으로
 *      지하층 = 파랑 / 취약 가구 있는 층 = 빨강(위기가구) / 옥탑 · 고시원 = 노랑 / 가구 있음 = 회색(주거) / 자료 없음 = 흰색
 */
window.Bld3D = (function () {
  var CAT = {
    crisis:  { nm: '위기가구',        c: '#e5484d' },
    rooftop: { nm: '옥탑',            c: '#f0b429' },
    gosiwon: { nm: '고시원',          c: '#f0b429' },
    normal:  { nm: '주거',            c: '#8a8a92' },
    empty:   { nm: '거주 자료 없음',  c: '#d4d4d8' },
    base:    { nm: '지하층(반지하)',  c: '#3b82f6' }
  };
  var VULN_MIN = 10;     // 취약 가구 = 가구 가점 10 이상 (map-panel.js 와 같은 기준)
  var $box, frame, ready = false, queued = null, seq = 0, rotating = false, drawn = false;

  function parseFlr(s) {
    if (!s) return null;
    s = String(s).replace(/\s/g, '');
    if (/옥탑/.test(s)) return 'R';
    var m = s.match(/^(?:지하|B)(\d+)/i); if (m) return -parseInt(m[1], 10);
    if (/^(?:반지하|지하)/.test(s)) return -1;
    m = s.match(/^(\d+)/); if (m) return parseInt(m[1], 10) || null;
    return null;
  }

  function buildFloors(b, list) {
    var up = Math.max(1, Math.min(b.grndFlr || 1, 40)), down = Math.min(b.ugrndFlr || 0, 5);
    var per = {}, roof = { hh: 0, vuln: 0 };
    (list || []).forEach(function (r) {
      var f = parseFlr(r.resFlr), o;
      if (f === 'R') o = roof; else if (f == null) return; else o = per[f] || (per[f] = { hh: 0, vuln: 0 });
      o.hh++; if (r.priority >= VULN_MIN) o.vuln++;
      if (typeof f === 'number') { if (f < 0) down = Math.max(down, Math.min(-f, 5)); else up = Math.max(up, Math.min(f, 40)); }
    });
    var gosiwon = b.ptGosiwon > 0 || /고시원|다중생활/.test((b.useNm || '') + (b.spNm || '') + (b.spType || ''));
    var floors = [];
    function add(idx, key, cat, o) {
      var t = CAT[cat];
      floors.push({ idx: idx, key: key, cat: cat, hh: o.hh, vuln: o.vuln, color: t.c, name: t.nm,
        label: key + ' · ' + t.nm + (o.vuln ? ' ' + o.vuln + '가구' : '') });
    }
    for (var i = down; i >= 1; i--) add(-i, 'B' + i, 'base', per[-i] || { hh: 0, vuln: 0 });
    for (var f = 1; f <= up; f++) {
      var o = per[f] || { hh: 0, vuln: 0 };
      add(f, f + 'F', o.vuln ? 'crisis' : gosiwon ? 'gosiwon' : o.hh ? 'normal' : 'empty', o);
    }
    var rooftop = null;
    if (roof.hh || b.ptRooftop > 0) {
      var rc = roof.vuln ? 'crisis' : 'rooftop';
      rooftop = { idx: up + 1, key: '옥탑', cat: rc, hh: roof.hh, vuln: roof.vuln, color: CAT[rc].c, name: CAT[rc].nm,
        label: '옥탑 · ' + CAT[rc].nm + (roof.vuln ? ' ' + roof.vuln + '가구' : '') };
    }
    return { floors: floors, rooftop: rooftop };
  }

  function legend(fl) {
    var rows = fl.floors.slice().reverse();
    if (fl.rooftop) rows.unshift(fl.rooftop);
    return rows.map(function (f) {
      return '<div class="b3-row"><i style="background:' + f.color + '"></i><b>' + Common.esc(f.key) + '</b><span>' + Common.esc(f.name) + '</span>'
        + '<small>' + (f.hh ? '가구 ' + f.hh + (f.vuln ? ' · <em>취약 ' + f.vuln + '</em>' : '') : '-') + '</small></div>';
    }).join('');
  }

  function status(text, err) { $('#b3Status').text(text || '').toggleClass('err', !!err).toggle(!!text); }

  /* 네이버 지도가 움직이는 동안(목록 클릭 → 이동 애니메이션, 드래그, 확대)에는 3D 작업을 미룬다.
     iframe 이 같은 주소(localhost:8080)라 메인 화면과 같은 스레드에서 돌아서, 겹치면 지도 이동이 버벅인다 */
  var stillQ = [], stillTimer = null, isMoving = false;
  function whenStill(fn) { stillQ.push(fn); arm(); }
  function arm() {
    clearTimeout(stillTimer);
    if (isMoving || !stillQ.length) return;
    stillTimer = setTimeout(function () { var q = stillQ; stillQ = []; q.forEach(function (f) { f(); }); }, 300);
  }
  function moving() { isMoving = true; clearTimeout(stillTimer); }
  function idle() { isMoving = false; arm(); }

  function ensureFrame() {
    if (frame) return;
    frame = document.getElementById('b3Frame');
    frame.src = Common.url('resources/vworld3d/bld3d-frame.html');
  }
  function send(msg) {
    if (!ready) { queued = msg; return; }
    frame.contentWindow.postMessage(msg, location.origin);
  }

  function show(d) {
    if (!d || !d.building) return;
    var b = d.building, my = ++seq;
    drawn = false; btn('loading');
    $('#b3Sub').text((b.grndFlr != null ? '지상 ' + b.grndFlr + '층' : '') + (b.ugrndFlr ? ' · 지하 ' + b.ugrndFlr + '층' : ''));
    $('#b3Legend').html('<div class="b3-empty">층별 거주 정보를 불러오는 중…</div>');
    status(ready ? '건물 불러오는 중…' : '브이월드 3D 지도 불러오는 중…');
    var go = function (list) {
      if (my !== seq) return;
      var fl = buildFloors(b, list);
      $('#b3Legend').html(legend(fl));
      send({ type: 'show', visible: !$box.hasClass('pre'), bld: { id: b.bldId, lng: b.lng, lat: b.lat, elevM: b.elevM }, floors: fl.floors, rooftop: fl.rooftop });
    };
    whenStill(function () {                 // 지도 이동이 끝나고 0.3초 조용할 때 시작
      if (my !== seq) return;
      ensureFrame();
      if (ready) send({ type: 'resume' });
      if (d.residents && d.residents.hhCnt) MapApi.residents(b.bldId).done(go).fail(function () { go([]); });
      else go([]);
    });
  }

  /* 상자 상태 : 미리 그리기(.pre, 안 보임) ↔ 열림 */
  function open() { $box.removeClass('pre').prop('hidden', false); $('#btn3d').addClass('on'); }
  function close() { $box.addClass('pre').removeClass('big'); $('#btn3d').removeClass('on'); }
  function btn(st) {
    var $b = $('#btn3d').prop('hidden', false).removeClass('loading ready');
    if (st === 'loading') $b.addClass('loading').html('<i class="b3-spin"></i>3D 준비 중');
    else if (st === 'ready') $b.addClass('ready').text('3D 입체보기');
    else if (st === 'error') $b.text('3D 입체보기 (오류)');
  }
  var warmTimer = null;
  function warmup(inBuildingZoom) {
    clearTimeout(warmTimer);
    if (frame || !inBuildingZoom || !$box) return;
    warmTimer = setTimeout(function () { whenStill(ensureFrame); }, 1000);
  }
  function hide() { seq++; drawn = false; if ($box) close(); $('#btn3d').prop('hidden', true); if (ready) { send({ type: 'clear' }); send({ type: 'pause' }); } }

  function init() {
    $box = $('#bld3d');
    if (!$box.length) return;
    window.addEventListener('message', function (e) {
      if (e.origin !== location.origin || !e.data || e.data.src !== 'bld3d') return;
      var m = e.data;
      if (m.type === 'ready') { ready = true; if (queued) { var q = queued; queued = null; send(q); } }
      else if (m.type === 'status') status(m.text);
      else if (m.type === 'error') { status(m.text, true); btn('error'); }
      else if (m.type === 'drawn') {
        status(''); drawn = true; btn('ready');
        $('#b3Note').text((m.measured ? '실측 높이 ' : '추정 높이 ') + m.height + 'm · 층당 ' + m.floorH + 'm'
          + (m.footprint ? '' : ' · 외곽선 자료 없음(사각형으로 표시)'));
      }
    });
    $('#b3Close').on('click', close);
    $('#btn3d').on('click', function () { if ($box.hasClass('pre')) open(); else close(); });
    $box.addClass('pre').prop('hidden', false);   // 투명 상태로만 둔다. iframe 은 첫 마커 클릭 때 불러온다
    $('#b3Expand').on('click', function () { $box.toggleClass('big'); });
    $('#b3Only').on('click', function () { var on = !$(this).hasClass('on'); $(this).toggleClass('on', on).attr('title', on ? '주변 건물 보이기' : '선택 건물만 보기'); send({ type: 'only', on: on }); });
    $('#b3Rotate').on('click', function () { rotating = !rotating; $(this).toggleClass('on', rotating); send({ type: 'rotate', on: rotating }); });
  }

  $(init);
  return { show: show, hide: hide, warmup: warmup, moving: moving, idle: idle };
})();
