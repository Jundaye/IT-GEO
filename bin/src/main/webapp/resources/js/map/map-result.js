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
    var col = scoreColor(b.score);
    var title = (b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : ''));
    return '<div class="rs-row" data-id="' + esc(b.bldId) + '" data-lat="' + b.lat + '" data-lng="' + b.lng + '">'
      + '<span class="rs-rank">' + b.rank + '</span>'
      + '<div class="rs-body"><div class="rs-title">' + esc(title) + '</div>'
      + '<div class="rs-sub">' + esc(b.factorText || '해당 요인 없음') + (b.vulnHhCnt ? '<small class="rs-hh">취약 가구 ' + b.vulnHhCnt + '</small>' : '') + '</div></div>'
      /* 검색(q)일 때만 기준 미만 건물도 온다 (15단계 C-9) → 점수 옆에 회색 "기준 미만" */
      + '<div class="rs-score"><b style="color:' + col + '">' + b.score + '</b>' + (b.score < C.SCORE_MIN ? '<em class="rs-under">기준 미만</em>' : '')
      + '<span class="rs-track"><i style="width:' + Math.min(100, b.score) + '%;background:' + col + '"></i></span></div>'
      + '</div>';
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

  function load(reset) {
    /* 새로 받기(reset)는 앞 요청이 도는 중이어도 무시하지 않는다 : seq 를 올려 새 요청을 보내고, 늦게 온 앞 응답은 아래 my !== seq 로 버린다.
       (예전에는 분석 중에 다른 시나리오를 누르면 새 요청이 무시되고 이전 결과가 남았다) "목록 더 불러오기" 만 loading 으로 막는다 */
    if (loading && !reset) return;
    if (reset) { page = 0; totalPages = 0; $('#resultList').empty(); }
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

  return { init: init, load: load, highlight: highlight };
})();
