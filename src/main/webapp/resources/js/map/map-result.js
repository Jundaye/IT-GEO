/*
 * 오른쪽 "검색 결과" 리스트 (api/map/results.do, PageTO)
 *  - 정렬 · 더 불러오기 · 행 클릭 → onSelect(building)
 *  - 조건(criteria + gu/dong/q)은 getParams() 로 매번 받아 온다
 */
window.MapResult = (function () {
  var esc = Common.esc;
  var C = window.MAP_CONFIG;
  var getParams = null, onSelect = null, getScope = null;
  var busyTimer = null;
  var page = 0, totalPages = 0, loading = false, seq = 0;

  /* 점수 색 = 지도 핀 · 도넛과 같은 구간 색 (MAP_CONFIG.LEVELS → COLORS) */
  function scoreColor(score) {
    for (var i = 0; i < C.LEVELS.length; i++) if (score >= C.LEVELS[i].min) return C.COLORS[C.LEVELS[i].key];
    return C.COLORS[C.LEVELS[C.LEVELS.length - 1].key];
  }

  function row(b) {
    if (C.MODEL_B && b.scoreTarget === false) return rowNonTarget(b);   // 주소 검색으로만 나오는 분석 비대상 건물
    var col = scoreColor(b.score);
    var title = (b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : ''));
    return '<div class="rs-row" data-id="' + esc(b.bldId) + '" data-lat="' + b.lat + '" data-lng="' + b.lng + '">'
      + '<span class="rs-rank">' + b.rank + '</span>'
      + '<div class="rs-body"><div class="rs-title">' + esc(title) + '</div>'
      + '<div class="rs-sub">' + esc(b.factorText || '해당 요인 없음') + (b.vulnHhCnt ? '<small class="rs-hh">취약 가구 ' + b.vulnHhCnt + '</small>' : '') + coldTag(b) + '</div></div>'
      /* 검색(q)일 때만 기준 미만 건물도 온다 (15단계 C-9) → 점수 옆에 회색 "기준 미만" */
      + '<div class="rs-score"><b style="color:' + col + '"' + (C.MODEL_B ? ' title="' + (C.scenNow === 'rain' ? '폭우 시나리오 기준 우선확인 점수(환산)' : '환산 점수') + ' · 계산 점수 ' + b.rawScore + '"' : '') + '>' + C.dispScore(b) + '</b>' + (b.score < C.SCORE_MIN ? '<em class="rs-under">기준 미만</em>' : '')
      + '<span class="rs-track"><i style="width:' + Math.min(100, b.score) + '%;background:' + col + '"></i></span></div>'
      + '</div>';
  }

  /* 한파 (2026-11-24) : 기본 B 후보 중 사용승인 연도 <= 기준 연도 = "한파 현장확인", 사용승인일 없는 후보 = "확인 불가". 한파 응답에만 coldCheck 가 온다 */
  function coldTag(b) {
    if (b.coldCheck === 'Y') return '<small class="rs-cold" title="' + esc(C.COLD_NOTE) + '">한파 현장확인 · ' + esc(String(b.useaprDay || '').slice(0, 4)) + '년 사용승인</small>';
    if (b.coldCheck === 'U') return '<small class="rs-cold unk" title="사용승인일이 없어 한파 현장확인 대상인지 판단할 수 없습니다">한파 확인 불가 · 사용승인일 없음</small>';
    return '';
  }

  /* 모델 B 분석 비대상(세대수 없음) : 건물 정보는 그대로, 점수 자리에 "분석 대상 아님" */
  function rowNonTarget(b) {
    var title = (b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : ''));
    return '<div class="rs-row rs-nontarget" data-id="' + esc(b.bldId) + '" data-lat="' + b.lat + '" data-lng="' + b.lng + '">'
      + '<span class="rs-rank">' + b.rank + '</span>'
      + '<div class="rs-body"><div class="rs-title">' + esc(title) + '</div><div class="rs-sub">세대수가 없는 건물 · 점수 모델 B 분석 비대상</div></div>'
      + '<div class="rs-score"><em class="rs-under">분석 대상 아님</em></div></div>';
  }

  /* 분석 중 표시 (2차 R) : 0.3초 안에 끝나면 깜빡이지 않게 300ms 뒤에 띄운다. 목록 자리에 스켈레톤, 건수는 "– 건", 지도 위 알약은 map-main.js ('wf:busy') */
  function busy(on) {
    clearTimeout(busyTimer);
    if (on) {
      busyTimer = setTimeout(function () {
        $('#resultCount').html('<b>–</b> 건');
        $('#resultList').html('<div class="rs-skel"><i></i><i></i><i></i><i></i></div>');
        $(document).trigger('wf:busy', [true]);
      }, 300);
    } else {
      $('#resultList .rs-skel').remove();
      $(document).trigger('wf:busy', [false]);
    }
  }
  /* 0건 안내 : 범위에 맞게 */
  function emptyHtml(p) {
    if (p.q) return '<p class="rs-empty">"' + esc(p.q) + '" 에 맞는 건물이 없습니다. 주소나 건물명을 다시 확인해 보세요.</p>';
    var sc = getScope ? getScope() : {};
    if (sc.screen) return '<p class="rs-empty"><b>이 화면 안에는 기준에 맞는 건물이 없어요.</b>지도를 넓히거나 다른 분석 기준을 골라 보세요.</p>';
    return '<p class="rs-empty"><b>' + esc(sc.area || '이 범위') + '에는 기준에 맞는 건물이 없어요.</b>다른 분석 기준을 골라 보세요.</p>';
  }

  /* ---------- 모델 B 목록 머리 (2026-11-25 문구 줄이기) ----------
       첫 줄(#resultScope) = 시나리오 · 건수만 ("기본 B · 후보 308동" / "폭우 · 후보 301동" / "한파 현장확인 180동") + ⓘ
       ⓘ 안 = 기준 · 범위(map-main renderScope), 보기 상태, 분석 대상 전체 · 후보 수, 한파 집계 · 안내 (예전 머리 문구 그대로)
       둘째 줄(#resultSplit) = 한파일 때 [한파 현장확인 N | 전체 N] 전환, 조건별 검색을 쓰면 "조건 충족 전체 N동 / 그중 후보 M동" 한 줄
       오른쪽 여닫기 버튼 옆 건수 표시(#rpCount)도 여기서 같이 바꾼다 */
  var scopeHtml = '', lastRes = null, lastP = null;
  function setScope(html) { scopeHtml = html || ''; if (lastRes) renderHeadB(lastRes, lastP); }
  function hasCond(p) {
    var cond = false;
    $.each(p, function (k, v) { if (/^filter/.test(k) && k !== 'filterCand' && v != null && v !== false) cond = true; });
    return cond;
  }
  function headLabel(res, p) {
    var n = Common.num(res.total), sc = res.scenario || 'base';
    var pre = sc === 'rain' ? '폭우' : sc === 'cold' ? '한파' : '기본 B';
    if (p.q) return { line: pre + ' · 검색 ' + n + '건', chip: '검색 ' + n + '건' };
    if (sc === 'cold' && p.coldOnly) return { line: '한파 현장확인 ' + n + '동', chip: '한파 현장확인 ' + n + '동' };
    return { line: pre + ' · ' + (p.filterCand ? '후보 ' : '전체 분석 대상 ') + n + '동',
             chip: (sc === 'rain' ? '폭우 ' : sc === 'cold' ? '한파 ' : '') + (p.filterCand ? '후보 ' : '전체 ') + n + '동' };
  }
  function renderHeadB(res, p) {
    var lb = headLabel(res, p), tip = [];
    if (scopeHtml) tip.push(scopeHtml);
    if (res.candTotal != null && res.matchTotal != null) {
      var cond = hasCond(p);
      tip.push((res.scenario === 'rain' ? '폭우 시나리오 기준 · ' : res.scenario === 'cold' ? '한파 · 기본 B 점수 그대로 · ' : '')
        + (p.filterCand ? '우선확인 후보만 보는 중' : '전체 분석 대상 보는 중'));
      if (!cond) tip.push('분석 대상 전체 <b>' + Common.num(res.matchTotal) + '</b>동 / 그중 ' + (res.scenario === 'rain' ? '폭우 ' : '') + '우선확인 후보 <b>' + Common.num(res.candTotal) + '</b>동');
      if (res.coldTotal != null) {
        var rest = res.candTotal - res.coldTotal - res.coldUnknown;
        tip.push('기본 B 우선확인 후보 ' + Common.num(res.candTotal) + '동 중 <b>한파 현장확인 ' + Common.num(res.coldTotal) + '동</b>'
          + ' (' + esc(C.COLD_LABEL) + ') · 확인 불가 ' + Common.num(res.coldUnknown) + '동 (사용승인일 없음) · 그 밖 ' + Common.num(rest) + '동');
        tip.push(esc(C.COLD_NOTE));
      }
    }
    $('#resultScope').html('<b class="rs-head">' + esc(lb.line) + '</b>' + C.tip(tip.join('<br>'), '목록 기준 설명'));
    $('#rpCount').text(lb.chip).prop('hidden', false);
  }

  /* 모델 B (5차) : "조건 충족 전체 N동 / 그중 우선확인 후보 M동" + 지금 조회 범위. 서버가 matchTotal · candTotal 을 줄 때만 (주소 검색 중에는 없음) */
  function renderSplit(res, p) {
    if (C.MODEL_B) return renderSplitB(res, p);
    var $s = $('#resultSplit');
    if (res.candTotal == null || res.matchTotal == null) { $s.prop('hidden', true).empty(); return; }
    var cond = false;
    $.each(p, function (k, v) { if (/^filter/.test(k) && k !== 'filterCand' && v != null && v !== false) cond = true; });
    var tag = p.filterCand ? '<em class="scope-tag cand">우선확인 후보만 보는 중</em>' : '<em class="scope-tag all">전체 분석 대상 보는 중</em>';
    var scenTag = res.scenario === 'rain' ? '<em class="scope-tag rain">폭우 시나리오 기준</em>' : res.scenario === 'cold' ? '<em class="scope-tag cold">한파 · 기본 B 점수 그대로</em>' : '';
    var html = scenTag + tag + (cond ? '조건 충족 전체' : '분석 대상 전체') + ' <b>' + Common.num(res.matchTotal) + '</b>동 / 그중 ' + (res.scenario === 'rain' ? '폭우 ' : '') + '우선확인 후보 <b>' + Common.num(res.candTotal) + '</b>동';
    /* 한파 : 180동은 새 점수의 상위 180동이 아니라 기본 B 후보 중 추가 확인 조건(사용승인 연도) 충족 건물 */
    if (res.coldTotal != null) {
      var rest = res.candTotal - res.coldTotal - res.coldUnknown, on = !!p.coldOnly;
      html += '<span class="cold-line">기본 B 우선확인 후보 ' + Common.num(res.candTotal) + '동 중 <b>한파 현장확인 ' + Common.num(res.coldTotal) + '동</b>'
        + ' (' + C.COLD_LABEL + ') · 확인 불가 ' + Common.num(res.coldUnknown) + '동 (사용승인일 없음) · 그 밖 ' + Common.num(rest) + '동</span>'
        + '<span class="cold-seg" role="group" aria-label="한파 보기">'
        + '<button type="button" data-coldview="target" class="' + (on ? 'on' : '') + '" aria-pressed="' + on + '">한파 현장확인 ' + Common.num(res.coldTotal) + '동</button>'
        + '<button type="button" data-coldview="all" class="' + (on ? '' : 'on') + '" aria-pressed="' + !on + '">전체 보기' + (p.filterCand ? ' (후보 ' + Common.num(res.candTotal) + '동)' : '') + '</button></span>'
        + '<span class="cold-note">' + esc(C.COLD_NOTE) + '</span>';
    }
    $s.html(html).prop('hidden', false);
  }
  /* 모델 B 둘째 줄 (2026-11-25) : 한파 전환 버튼 · 조건 검색 한 줄만. 나머지 문구는 첫 줄 ⓘ 안 (renderHeadB) */
  function renderSplitB(res, p) {
    var $s = $('#resultSplit'), html = '';
    if (res.candTotal != null && res.matchTotal != null) {
      if (res.coldTotal != null) {
        var on = !!p.coldOnly, all = p.filterCand ? res.candTotal : res.matchTotal;
        html += '<span class="cold-seg" role="group" aria-label="한파 보기">'
          + '<button type="button" data-coldview="target" class="' + (on ? 'on' : '') + '" aria-pressed="' + on + '">한파 현장확인 ' + Common.num(res.coldTotal) + '</button>'
          + '<button type="button" data-coldview="all" class="' + (on ? '' : 'on') + '" aria-pressed="' + !on + '">전체 ' + Common.num(all) + '</button></span>';
      }
      if (hasCond(p)) html += '<span class="cond-line">조건 충족 전체 <b>' + Common.num(res.matchTotal) + '</b>동 / 그중 ' + (res.scenario === 'rain' ? '폭우 ' : '') + '우선확인 후보 <b>' + Common.num(res.candTotal) + '</b>동</span>';
    }
    $s.html(html).prop('hidden', !html);
  }

  function load(reset) {
    /* 새로 받기(reset)는 앞 요청이 도는 중이어도 무시하지 않는다 : seq 를 올려 새 요청을 보내고, 늦게 온 앞 응답은 아래 my !== seq 로 버린다.
       (예전에는 분석 중에 다른 시나리오를 누르면 새 요청이 무시되고 이전 결과가 남았다) "목록 더 불러오기" 만 loading 으로 막는다 */
    if (loading && !reset) return;
    if (reset) { page = 0; totalPages = 0; $('#resultList').empty(); if (C.MODEL_B) lastRes = null; }
    if (totalPages && page >= totalPages) return;
    loading = true;
    var my = ++seq;
    var p = $.extend({}, getParams(), { sort: $('#resultSort').val(), page: page + 1, size: 20 });
    if (reset) { $('#resultMore').hide(); busy(true); }
    else $('#resultMore').text('목록을 불러오는 중').show();
    MapApi.results(p).done(function (res) {
      if (my !== seq) return;
      busy(false);
      page = res.page; totalPages = res.totalPages;
      $('#resultCount').html('<b>' + Common.num(res.total) + '</b> 건');
      if (C.MODEL_B && page === 1) { lastRes = res; lastP = p; renderHeadB(res, p); }
      renderSplit(res, p);
      var html = res.list.map(row).join('');
      if (page === 1 && !res.list.length) html = emptyHtml(p);
      $('#resultList').append(html);
      if (page === 1) $('#resultList').scrollTop(0);
      if (selId) highlight(selId);
      if (page < totalPages) $('#resultMore').text('목록 더 불러오기').show();
      else $('#resultMore').hide();
    }).fail(function () {                             // 서버 오류 · 시간 초과 : 목록 자리에 안내 + [다시 시도] (같은 조건으로 처음부터)
      if (my !== seq) return;
      busy(false);
      if (!reset) { $('#resultMore').text('목록 더 불러오기').show(); return; }
      $('#resultCount').html('<b>–</b> 건');
      $('#resultSplit').prop('hidden', true).empty();
      if (C.MODEL_B) { lastRes = null; $('#rpCount').prop('hidden', true); }
      $('#resultMore').hide();
      $('#resultList').html('<p class="rs-empty"><b>결과를 불러오지 못했어요.</b><button type="button" class="btn btn-line" id="resultRetry">다시 시도</button></p>');
    }).always(function () { if (my === seq) loading = false; });
  }

  /* 선택한 건물 행 강조. 리스트를 다시 받아도(재검색 · 더 불러오기) 그 건물이 있으면 다시 강조된다 */
  var selId = null;
  function highlight(bldId) {
    selId = bldId == null ? null : String(bldId);
    $('#resultList .rs-row').removeClass('sel');
    if (selId) $('#resultList .rs-row').filter('[data-id="' + selId + '"]').addClass('sel');
  }

  function init(opts) {
    getParams = opts.getParams;
    onSelect = opts.onSelect;
    getScope = opts.getScope;
    $('#resultList').on('click', '#resultRetry', function () { load(true); });
    $('#resultSort').on('change', function () { load(true); });
    $('#resultMore').on('click', function () { load(false); });
    $('#resultList').on('click', '.rs-row', function () {
      var $r = $(this);
      highlight($r.data('id'));
      if (onSelect) onSelect({ bldId: String($r.data('id')), lat: Number($r.data('lat')), lng: Number($r.data('lng')) });
    });
    /* 스크롤 끝에 닿으면 자동으로 더 불러오기 */
    $('#resultList').on('scroll', function () {
      if (this.scrollTop + this.clientHeight >= this.scrollHeight - 40) load(false);
    });
  }

  return { init: init, load: load, highlight: highlight, setScope: setScope };
})();
