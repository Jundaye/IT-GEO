/*
 * 데이터 업데이트 모달 (include/dataupdate-modal.jspf, 헤더의 [데이터 업데이트] 버튼, 서울시청 계정만)
 *  - 예전 dataupdate.jsp 의 로직(run.do POST → 1초마다 job.do 폴링, showMode · showSteps · showReport)을 여기로 옮겼다.
 *    dataupdate.do 화면도 이 파일을 쓴다 (window.DU_AUTO_OPEN = true 로 모달을 바로 연다) → 로직은 한 곳
 *  - 상태 3가지 : 대기(마지막 실행 요약 + 시작 버튼) / 실행 중(진행률 · 단계 · 변경 사항 피드) / 완료(결과 한 줄 · 숫자 카드 · 요약 표)
 *  - 백그라운드 : 실행 중에 닫아도 폴링은 계속. jobId 를 sessionStorage wf.duJob 에 두어 다른 화면으로 가도 이어진다.
 *    그동안 헤더 버튼 = "데이터 업데이트 46%" + 아래 진행 막대. 끝나면 버튼을 되돌린다 (헤더의 데이터 기준 시각 표시는 없앴다)
 *  - 배치 서버가 아직 안 주는 값 (지시문 E-4) : feed · report.diff. 없으면 그 칸만 빠진다
 *      feed 없음 → log 의 단계 완료를 "확인" 태그로 쌓는다 / report.diff 없음 → 새로 위험 · 내려감 카드와 [지도에서 보기] 를 숨긴다
 *  - 이 스크립트는 common.js 보다 먼저 읽히므로 Common 은 $(function) 안에서만 쓴다. alert 는 쓰지 않는다 (오류는 모달 안 빨간 문구)
 */
$(function () {
  var $m = $('#duModal');
  if (!$m.length) return;
  var esc = Common.esc, num = Common.num;
  var CHECK = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
  var OK_ICO = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M8 12.5l3 3 5-6"/></svg>';
  var NG_ICO = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="9"/><path d="M12 7.5v5.5M12 16.4v.1"/></svg>';
  var KIND = { check: '확인', change: '변경', up: '위험↑', down: '위험↓' };

  /* sessionStorage (막혀 있어도 화면은 돌아가게) */
  function ssGet(k) { try { return sessionStorage.getItem(k); } catch (e) { return null; } }
  function ssSet(k, v) { try { sessionStorage.setItem(k, v); } catch (e) {} }
  function ssDel(k) { try { sessionStorage.removeItem(k); } catch (e) {} }
  function ssJson(k) { try { return JSON.parse(ssGet(k) || '{}') || {}; } catch (e) { return {}; } }

  var jobId = ssGet('wf.duJob');      // 실행 중인 작업 (없으면 null)
  var timer = null;                   // 다음 조회 예약 (setTimeout)
  var polling = false;                // 조회를 이어 가는 중
  var finished = false;               // finish() 는 작업 하나에 한 번만
  var failN = 0;                      // 조회 연속 실패 횟수
  var lost = false;                   // 연속 FAIL_MAX 회 실패 → 조회를 멈춤 (jobId · wf.duJob 은 남겨 두어 새로고침하면 이어서 본다)
  var FAIL_MAX = 5;
  var LOST_MSG = '연결이 끊겼습니다. 새로고침하면 다시 확인합니다';
  var last = null;                    // 마지막으로 받은 작업 상태
  var view = 'idle';                  // idle | run | done
  var feedN = 0;                      // 피드에 이미 그린 줄 수
  var needReload = false;             // DB 가 바뀌었다 → 닫을 때 지도 다시 불러오기
  var onMap = /\/map\.do$/.test(location.pathname);

  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function hms(t) { var d = new Date(Number(t)); return pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds()); }
  function durText(sec) { sec = Math.round(sec); return sec >= 60 ? Math.floor(sec / 60) + '분 ' + (sec % 60) + '초' : sec + '초'; }
  function pctOf(s) { return Math.max(0, Math.min(100, Math.round((Number(s.progress) || 0) * 100))); }
  function showErr(msg) { $('#duErr').text(msg || '').prop('hidden', !msg); }

  /* ---------- 실행 방식 한 줄 (예전 showMode 문구 그대로) ---------- */
  function renderMode(s) {
    var tag = '', t = '';
    if (s.mode === 'partial') { tag = '부분 업데이트'; t = (s.changed || []).join(' · ') + ' 변경 감지 → 해당 단계만 다시 계산 (나머지 ' + (s.skipped || []).length + '단계는 변경 없음)'; }
    else if (s.mode === 'full') { tag = '전체 다시 계산'; t = '12단계 전체 다시 계산'; }
    else if (s.mode === 'none') { tag = '변경 없음'; t = '바뀐 원천 파일이 없습니다. 전부 다시 계산하려면 [전체 다시 계산]을 누르세요.'; }
    $('#duModeTag').text(tag); $('#duModeTxt').text(t);
    $('#duMode').prop('hidden', !tag);
  }

  /* ---------- 진행 단계 (예전 showSteps) : 끝난 단계 ✓ + 걸린 초, 현재 단계 스피너, 건너뛴 단계 흐리게 ---------- */
  function renderSteps(s) {
    var h = '';
    $.each(s.log || [], function (_, x) { h += '<li class="done">' + CHECK + '<span>' + esc(x.step) + '</span><em>' + esc(x.sec) + '초</em></li>'; });
    if (s.status === 'running' && s.step) h += '<li class="cur"><i class="du-spin"></i><span>' + esc(s.step) + '</span><em>진행 중</em></li>';
    /* 남은 단계 목록(steps_all)은 배치 서버가 주면 "대기" 로 보인다. 지금은 주지 않아 끝난 단계 · 현재 단계만 */
    var seen = {};
    $.each(s.log || [], function (_, x) { seen[x.step] = 1; });
    $.each(s.steps_all || [], function (_, n) { if (!seen[n] && n !== s.step && s.status === 'running') h += '<li class="wait"><i class="du-wait"></i><span>' + esc(n) + '</span><em>대기</em></li>'; });
    if (s.mode === 'partial') $.each(s.skipped || [], function (_, n) { h += '<li class="skip"><i class="du-dash">–</i><span>' + esc(n) + '</span><em>변경 없음</em></li>'; });
    $('#duSteps').html(h);
  }

  /* ---------- 변경 사항 피드 : 시각 · [태그] · 문구 가 아래로 쌓인다 ---------- */
  function feedOf(s) {
    if ($.isArray(s.feed)) return s.feed;                       // 배치 서버가 주면 그대로
    /* 없으면 단계 완료를 "확인" 으로 대신. 시각은 이 화면이 그 단계를 처음 본 때 (sessionStorage 에 기억 → 다른 화면에서도 같은 시각) */
    var seen = ssJson('wf.duFeedT'), dirty = false, out = [];
    function at(key) { if (!seen[key]) { seen[key] = hms(Date.now()); dirty = true; } return seen[key]; }
    if (s.mode === 'partial' && (s.changed || []).length) out.push({ time: at('#changed'), kind: 'check', text: '원천 파일 확인 · ' + s.changed.join(', ') + ' 바뀜' });
    if (s.mode === 'partial' && (s.skipped || []).length) out.push({ time: at('#skipped'), kind: 'check', text: s.skipped.join(' · ') + ' 변경 없음 → 건너뜀' });
    $.each(s.log || [], function (i, x) { out.push({ time: at(i + ':' + x.step), kind: 'check', text: x.step + ' 완료 (' + x.sec + '초)' }); });
    if (dirty) ssSet('wf.duFeedT', JSON.stringify(seen));
    return out;
  }
  function renderFeed(s, reset) {
    var list = feedOf(s), $f = $('#duFeed');
    if (reset || list.length < feedN) { $f.empty(); feedN = 0; }
    for (var i = feedN; i < list.length; i++) {
      var x = list[i], k = KIND[x.kind] ? x.kind : 'check';
      var $row = $('<div class="du-feed-row new"></div>')
        .append($('<time></time>').text(x.time || ''))
        .append($('<span class="du-tag"></span>').addClass(k).text(KIND[k]))
        .append($('<span class="du-feed-txt"></span>').text(x.text || ''));
      $f.append($row);
      (function ($r) { setTimeout(function () { $r.removeClass('new'); }, reset ? 0 : 1200); })($row);   // 새 줄은 잠깐 강조
    }
    if (list.length !== feedN) $f.scrollTop($f[0].scrollHeight);
    feedN = list.length;
  }

  /* ---------- 결과 한 줄 (예전 showReport 의 result 판정 그대로) → { kind: ok | gray | fail, title, text } ---------- */
  function resultOf(s) {
    var r = s.report || {}, u = r.db_update || {};
    var nRule = (r.rules || []).length, pass = nRule ? '검증 ' + nRule + '개 규칙 모두 통과했습니다.' : '검증을 통과했습니다.';
    if (s.status === 'error') return { kind: 'fail', title: '오류', text: s.error || '작업이 중간에 멈췄습니다.' };
    if (r.mode === 'none') return { kind: 'gray', title: '변경 없음', text: r.note || '바뀐 원천 파일이 없습니다.' };
    if (r.mode === 'partial') {
      if (u.updated) return { kind: 'ok', title: 'DB 반영 완료', text: '건물 ' + num(u.changed_rows) + '동 정보가 바뀌었고, ' + pass };
      if (r.ok) return { kind: 'gray', title: '계산만 함', text: 'DB는 그대로입니다.' + (u.changed_rows != null ? ' 반영하면 ' + num(u.changed_rows) + '동이 바뀝니다.' : '') };
      return { kind: 'fail', title: '반영 안 함', text: u.reason || '아래 검증 항목을 확인하세요.' };
    }
    if (r.db_swap && r.db_swap.swapped) return { kind: 'ok', title: 'DB 반영 완료', text: pass };
    if (r.ok) return { kind: 'gray', title: '검증 통과', text: '새 파일을 저장했고, DB는 그대로입니다.' };
    return { kind: 'fail', title: '반영 안 함', text: '아래 검증 항목을 확인하세요.' };
  }
  function failedRules(s) { return ((s.report || {}).rules || []).filter(function (x) { return !x.ok; }); }
  function dbChanged(s) { var r = s.report || {}; return !!((r.db_update && r.db_update.updated) || (r.db_swap && r.db_swap.swapped)); }

  /* ---------- 화면 3가지 ---------- */
  function setView(v) {
    view = v;
    $m.find('.du-modal').toggleClass('done', v === 'done').removeClass('ok fail');
    $('#duIdle').prop('hidden', v !== 'idle');
    $('#duRun').prop('hidden', v === 'idle');
    $('#duFull, #duStart').prop('hidden', v !== 'idle');
    $('#duBg').prop('hidden', v !== 'run');
    $('#duClose').prop('hidden', v !== 'done');
    if (v !== 'done') { $('#duView, #duResult, #duCards, #duSum').prop('hidden', true); $('#duFeed').prop('hidden', false); }
    $('#duNote').text(v === 'run' ? '창을 닫아도 업데이트는 계속됩니다. 헤더 버튼에 진행률이 표시돼요.' : '');
  }

  /* 대기 : 마지막 실행 결과 요약 (latest.do). 서버 응답에 실행 시각이 없어 결과 한 줄만 보인다 */
  function renderIdle(s) {
    setView('idle');
    var $l = $('#duLast').removeClass('ok fail');
    if (s && (s.report || s.status === 'error')) {
      var res = resultOf(s);
      $l.addClass(res.kind === 'gray' ? '' : res.kind).html('<small>마지막 실행</small><b>' + esc(res.title) + '</b> · ' + esc(res.text));
    } else $l.html('<small>마지막 실행</small>아직 실행한 기록이 없습니다. [업데이트 시작]을 누르면 바뀐 원천 파일만 다시 계산합니다.');
  }

  /* 실행 중 */
  function renderRun(s, reset) {
    if (view !== 'run') { setView('run'); reset = true; }
    var done = (s.log || []).length, total = s.total || (s.mode === 'full' ? 12 : 0), n = total ? Math.min(done + 1, total) : done + 1;
    $('#duStepNo').text(total ? n + ' / ' + total + '단계' : n + '단계');
    $('#duStepNm').text((s.step || '준비') + ' 중');
    $('#duPct').text(pctOf(s) + '%');
    $('#duBar').css('width', pctOf(s) + '%');
    $('#duRightTitle').text('변경 사항'); $('#duRightSub').text('실시간 · 아래로 쌓임');
    renderMode(s); renderSteps(s); renderFeed(s, reset);
  }

  /* 완료 (성공 · 계산만 함 · 실패 · 오류) */
  function renderDone(s) {
    setView('done');
    var r = s.report || {}, res = resultOf(s), diff = r.diff || null, u = r.db_update || {};
    var t0 = ssGet('wf.duT0'), t1 = ssGet('wf.duT1');
    var sec = (s.log || []).reduce(function (a, x) { return a + (Number(x.sec) || 0); }, 0);
    var when = [(t0 && t1) ? hms(t0) + ' ~ ' + hms(t1) : '', sec ? durText(sec) : ''].filter(Boolean).join(' · ');
    $m.find('.du-modal').addClass(res.kind === 'fail' ? 'fail' : 'ok');
    $('#duStepNo').text(res.kind === 'fail' ? '업데이트 실패' : '업데이트 완료');
    $('#duStepNm').text(when);
    $('#duPct').text(res.kind === 'fail' ? pctOf(s) + '%' : '100%');
    $('#duBar').css('width', res.kind === 'fail' ? pctOf(s) + '%' : '100%');
    renderMode(s); renderSteps(s);

    /* 결과 한 줄 박스 + 실패한 검증 규칙 */
    var ng = failedRules(s);
    var h = (res.kind === 'ok' ? OK_ICO : NG_ICO) + '<div><b>' + esc(res.title) + '</b> · ' + esc(res.text);
    if (ng.length) h += '<ul>' + ng.map(function (x) { return '<li>' + esc(x.rule) + (x.detail ? ' — ' + esc(x.detail) : '') + '</li>'; }).join('') + '</ul>';
    $('#duResult').attr('class', 'du-result ' + res.kind).html(h + '</div>').prop('hidden', false);

    /* 숫자 카드 : 값이 없는 카드는 뺀다 (diff 는 배치 서버 연동 뒤) */
    var cards = [];
    var changed = diff && diff.changedBld != null ? diff.changedBld : u.changed_rows;
    if (changed != null) cards.push({ v: num(changed), k: '정보가 바뀐 건물' });
    if (diff && diff.upCnt != null) cards.push({ v: '+' + num(diff.upCnt), k: '새로 기준점(80점) 이상', c: 'up' });
    if (diff && diff.downCnt != null) cards.push({ v: '−' + num(diff.downCnt), k: '기준점 미만으로 내려감', c: 'down' });
    if (r.rules) cards.push({ v: num(ng.length), k: '검증 실패', c: ng.length ? 'up' : '' });
    $('#duCards').html(cards.map(function (c) { return '<div class="du-card"><b class="' + (c.c || '') + '">' + c.v + '</b><span>' + c.k + '</span></div>'; }).join('')).prop('hidden', !cards.length);

    /* 변경 사항 요약 표 : diff.items(항목 · 바뀐 내용 · 건물 수). 없으면 예전 화면처럼 report.summary · 더미 점검을 그대로. 둘 다 없으면 피드를 그대로 둔다 */
    var rows = '';
    if (diff && $.isArray(diff.items)) {
      rows = '<tr><th>항목</th><th>바뀐 내용</th><th class="n">건물 수</th></tr>'
        + diff.items.map(function (x) { return '<tr><td>' + esc(x.name) + '</td><td>' + esc(x.desc) + '</td><td class="n">' + (x.cnt != null ? num(x.cnt) : '–') + '</td></tr>'; }).join('');
    } else {
      $.each(r.summary || {}, function (k, v) { rows += '<tr><td>' + esc(k) + '</td><td class="n">' + num(v) + '</td></tr>'; });
      $.each((r.dummy || {}).issues || {}, function (k, v) { rows += '<tr><td>더미 · ' + esc(k) + '</td><td class="n">' + esc(v) + '</td></tr>'; });
      if (rows) rows = '<tr><th>항목</th><th class="n">값</th></tr>' + rows;
    }
    if (rows && s.mode === 'partial' && (s.skipped || []).length) rows += '<tr class="skip"><td>' + esc(s.skipped.join(' · ')) + '</td><td' + (diff && diff.items ? '' : ' class="n"') + '>변경 없음 · 건너뜀</td>' + (diff && diff.items ? '<td class="n">–</td>' : '') + '</tr>';
    $('#duSum').html(rows).prop('hidden', !rows);
    $('#duFeed').prop('hidden', !!rows);
    if (rows) { $('#duRightTitle').text('변경 사항 요약'); $('#duRightSub').text('항목별'); }
    else { $('#duRightTitle').text('변경 사항'); $('#duRightSub').text(''); renderFeed(s, true); }

    var up = diff && Number(diff.upCnt) > 0 ? Number(diff.upCnt) : 0;
    $('#duViewTxt').text('새로 위험해진 ' + num(up) + '동 지도에서 보기');
    $('#duView').prop('hidden', !up);
    $('#duNote').text(dbChanged(s) && onMap ? '지도는 닫으면 새 데이터로 다시 불러옵니다.' : '');
  }

  /* ---------- 헤더 버튼 : 실행 중이면 "데이터 업데이트 46%" + 아래 진행 막대 ---------- */
  function headerRun(s) {
    var p = s ? pctOf(s) : 0;
    $('#btnDataUpdate').addClass('running');
    $('#duBtnText').text('데이터 업데이트 ' + p + '%');
    $('#duHbar').css('width', p + '%');
  }
  function headerIdle() {
    $('#btnDataUpdate').removeClass('running');
    $('#duBtnText').text('데이터 업데이트');
    $('#duHbar').css('width', 0);
  }

  /* ---------- 폴링 : 응답을 받은 뒤 1초 뒤에 다음 조회 (체인). 앞 요청이 끝나기 전에 다음 요청을 보내지 않으므로
       늦게 온 '실행 중' 응답이 '완료' 화면을 되돌리는 일이 없다 (리뷰 3) ---------- */
  function errMsg(x, def) { var j = (x && x.responseJSON) || {}; return j.message || j.msg || def; }
  function stopPoll() { clearTimeout(timer); timer = null; polling = false; }
  function next() { clearTimeout(timer); timer = setTimeout(poll, 1000); }
  function poll() {
    if (!jobId || !polling) return;
    var my = jobId;
    $.ajax({ url: Common.url('api/dataupdate/job.do'), data: { id: my }, dataType: 'json' }).done(function (s) {
      if (my !== jobId || !polling) return;                   // 그 사이 멈췄거나 다른 작업
      failN = 0;
      last = s;
      if (s.status === 'running') { headerRun(s); if ($m.hasClass('open')) renderRun(s); next(); return; }
      finish(s);
    }).fail(function (x) {
      if (my !== jobId || !polling) return;
      /* 404 = 배치 서버가 다시 켜져 그 작업 기록이 없어졌다 (다시 물어도 같음) → 이 작업 추적은 끝낸다 */
      if (x.status === 404) {
        stopPoll(); jobId = null; ssDel('wf.duJob'); headerIdle();
        if ($m.hasClass('open')) { renderIdle(null); $('#duLast').text('진행 상황을 이어서 볼 수 없습니다.'); showErr(errMsg(x, '작업 기록이 없습니다.')); }
        return;
      }
      /* 그 밖의 실패(배치 서버 잠깐 꺼짐 · 응답 늦음 · 네트워크)는 다시 묻는다. 연속 FAIL_MAX 회면 멈추고 안내만 (리뷰 4).
         작업은 배치 서버에서 계속 돌고 있을 수 있으므로 jobId · wf.duJob 은 지우지 않는다 → 새로고침하면 다시 이어서 본다 */
      failN++;
      if (failN < FAIL_MAX) { next(); return; }
      stopPoll(); lost = true;
      if ($m.hasClass('open')) showErr(LOST_MSG); else Common.toast('데이터 업데이트 : ' + LOST_MSG);
    });
  }
  function startPoll() { stopPoll(); polling = true; finished = false; failN = 0; lost = false; headerRun(last); poll(); }
  function finish(s) {
    if (finished) return;                                     // 한 번만 (리뷰 3)
    finished = true;
    console.log('[dataupdate] finish · ' + s.status);
    stopPoll();
    jobId = null; ssDel('wf.duJob'); ssSet('wf.duT1', String(Date.now()));
    headerIdle();
    if (dbChanged(s)) needReload = true;
    if ($m.hasClass('open')) renderDone(s);
    else ssSet('wf.duDone', '1');                            // 닫혀 있었으면 다음에 열 때 완료 화면
  }

  /* ---------- 시작 (예전 start) ---------- */
  function start(full) {
    var $btns = $('#duFull, #duStart').prop('disabled', true);
    showErr('');
    $.ajax({ url: Common.url('api/dataupdate/run.do'), method: 'POST', data: { full: full }, dataType: 'json' }).done(function (r) {
      jobId = r.jobId;
      ssSet('wf.duJob', jobId); ssSet('wf.duT0', String(Date.now())); ssDel('wf.duT1'); ssDel('wf.duFeedT'); ssDel('wf.duDone');
      last = { status: 'running', step: '준비', progress: 0 };
      renderRun(last, true);
      startPoll();
    }).fail(function (x) {                                    // 409 이미 실행 중 · 503 배치 서버 꺼짐 · 403 구청 계정
      showErr(errMsg(x, '시작하지 못했습니다. (' + x.status + ')'));
    }).always(function () { $btns.prop('disabled', false); });
  }

  /* ---------- 열기 · 닫기 ---------- */
  function open() {
    showErr('');
    $m.addClass('open');
    if (jobId) { renderRun(last || { status: 'running', step: '준비', progress: 0 }, true); if (lost) showErr(LOST_MSG); return; }
    setView('idle'); $('#duLast').removeClass('ok fail').text('마지막 실행 결과를 확인하는 중…');
    $.ajax({ url: Common.url('api/dataupdate/latest.do'), dataType: 'json' }).done(function (s) {
      if (jobId || !$m.hasClass('open')) return;
      if (s && s.status === 'running') { $('#duLast').html('<small>지금</small>다른 창에서 시작한 업데이트가 실행 중입니다. 끝난 뒤 다시 열어 주세요.'); return; }
      if (ssGet('wf.duDone') && s && (s.report || s.status === 'error')) renderDone(s);   // 백그라운드에서 끝난 작업
      else renderIdle(s);
    }).fail(function (x) { renderIdle(null); showErr(errMsg(x, '마지막 실행 결과를 읽지 못했습니다.')); });
  }
  function close() {
    $m.removeClass('open');
    if (view === 'done') {
      ssDel('wf.duDone');
      /* 지도 화면이면 현재 조건으로 지도 · 결과를 다시 (분석 전이면 구 요약만). map-main.js 가 window.WF_MAP_RELOAD 를 내놓는다 */
      if (needReload && window.WF_MAP_RELOAD) window.WF_MAP_RELOAD();
      needReload = false;
    }
  }

  $(document).on('click', '#btnDataUpdate', open);
  $('#duX, #duBg, #duClose').on('click', close);             // 실행 중의 × = 백그라운드로 닫기
  $m.on('mousedown', function (e) { if (e.target === this) close(); });
  $(document).on('keydown', function (e) { if (e.key === 'Escape' && $m.hasClass('open')) close(); });
  $('#duStart').on('click', function () { start(false); });
  $('#duFull').on('click', function () { start(true); });
  /* 새로 위험해진 건물만 지도에서 : 우선 쿼리 파라미터로만 넘긴다 (map.do?changed=up). 서버 필터는 배치 서버가 diff 를 주면 연결 */
  $('#duView').on('click', function () {
    ssDel('wf.duDone');
    location.href = Common.url('map.do?changed=up');
  });

  /* 다른 화면에서 시작한 작업이 있으면 이어서 폴링 */
  if (jobId) startPoll();
  if (window.DU_AUTO_OPEN) open();                           // dataupdate.do 화면
});
