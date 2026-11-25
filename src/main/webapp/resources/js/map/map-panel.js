/*
 * 오른쪽 "건물 상세 정보" 패널 (include/building-panel.jspf, Figma 02-2)
 *  - 점수 구성 · 건축물대장 · 시설까지의 거리(가까운 시설) · 거주 취약 가구(집계) · 메모 · 보관함 저장
 *  - [개인 정보 보기] → 취약 가구 정보 모달 (Figma 02-2-1) : 순위·가점(툴팁 = 내역) · 성만 보이는 세대주 · 층수 · 연령대 · 가구 구성 · 취약 유형 · 복지 연계
 *      순서는 서버(mapper-resident getResidentList)가 정한 "가장 취약한 순" 그대로. 여기서 다시 정렬하지 않는다 (12단계)
 *  - [보관함에 저장] (13단계) : 안 담김 "보관함에 저장" / 담겼고 조사 전 "보관함에 저장됨"(누르면 안내 + 보관함에서 보기) / 조사 이력 있음 "다시 저장 (점수 갱신)"
 */
window.MapPanel = (function () {
  var esc = Common.esc;
  var C = window.MAP_CONFIG;   // 모델 B 표시 (MODEL_B · dispScore)
  var getCriteria = null, onArchived = null;
  var current = null;        // BuildingDetailTO

  /* 묶음 한 줄 (피그마 02-2) : "주거 공간 (반지하 · 옥탑)" · 막대 · "+45". 막대 = 받은 점수 ÷ 가장 큰 묶음 가중치 */
  /* 2차(11-17 K) 펼침형 : [꺾쇠] 묶음 이름 · 요약 · 막대 · +점수. 묶음 줄을 누르면 아래에 요인별 표 (요인 | 이 건물 | 기준 | 충족).
     서버가 factors 를 주지 않으면(옛 응답 등) 예전처럼 desc 를 괄호로 붙인 한 줄로 그린다 */
  var CHEV = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M9 5l7 7-7 7"/></svg>';
  var CHECK = '<svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12.5l4.5 4.5L19 7.5"/></svg>';
  function scoreRow(it, maxW, open) {
    var w = maxW ? Math.round(Math.min(it.points, maxW) / maxW * 100) : 0;
    if (it.factors && it.factors.length) {
      var rows = it.factors.map(function (f) {
        var cls = !f.on ? 'off' : f.hit ? '' : 'miss';
        return '<tr class="' + cls + '"><td>' + esc(f.name) + '</td><td>' + esc(f.value) + '</td><td>' + esc(f.criterion) + '</td>'
          + '<td class="c">' + (!f.on ? '제외' : f.hit ? CHECK : '–') + '</td></tr>';
      }).join('');
      return '<div class="sc-item' + (open ? ' open' : '') + (it.hit ? '' : ' miss') + '">'
        + '<button type="button" class="sc-head" aria-expanded="' + !!open + '" title="가중치 ' + it.weight + '">' + CHEV
        + '<span class="sc-label">' + esc(it.label) + '</span><span class="sc-sum">' + esc(it.summary || '') + '</span>'
        + '<span class="sc-bar"><i style="width:' + w + '%"></i></span>'
        + '<b class="sc-pt">' + (it.pointsText ? (it.hit ? it.pointsText : (it.weight ? '0' : '-')) : (it.hit ? '+' + it.points : (it.weight ? '0' : '-'))) + '</b></button>'
        + '<table class="sc-detail"><thead><tr><th>요인</th><th>이 건물</th><th>기준</th><th class="c">충족</th></tr></thead><tbody>' + rows + '</tbody></table></div>';
    }
    return '<div class="sc-row' + (it.hit ? '' : ' miss') + '" title="가중치 ' + it.weight + ' · ' + esc(it.desc) + '">'
      + '<span class="sc-label">' + esc(it.label) + ' <small>(' + esc(it.desc) + ')</small></span>'
      + '<span class="sc-bar"><i style="width:' + w + '%"></i></span>'
      + '<b class="sc-pt">' + (it.hit ? '+' + it.points : (it.weight ? '0' : '-')) + '</b></div>';
  }

  function infoRow(k, v) { return '<tr><th>' + esc(k) + '</th><td>' + esc(v == null || v === '' ? '-' : v) + '</td></tr>'; }

  function distText(m) { return m == null ? '-' : m >= 1000 ? (m / 1000).toFixed(1) + 'km' : Math.round(m) + 'm'; }
  function welfRow(k, nm, dist) {
    return '<div class="welf-row"><span class="welf-k">' + esc(k) + '</span><span class="welf-nm">' + esc(nm || '-') + '</span><b class="welf-d">' + distText(dist) + '</b></div>';
  }

  function titleOf(b) {
    return b.bldNm ? Common.shortAddr(b.addr) + ' ' + b.bldNm : Common.shortAddr(b.addr) + (b.useNm ? ' ' + b.useNm : '');
  }

  /* 아래 [조사 이력 보기] : 현장 방문 기록이 있으면 05 건물 확인 이력으로 가는 링크, 없으면 막아 둔다 */
  function setHistory(href) {
    var $h = $('#dHistory').toggleClass('disabled', !href).attr('aria-disabled', String(!href)).attr('title', href ? '' : '아직 현장 조사 기록이 없습니다');
    if (href) $h.attr('href', href); else $h.removeAttr('href');
  }

  function renderInfo(d) {
    var b = d.building;
    $('#dTitle').text(titleOf(b));
    $('#dAddr').text(b.addr);
    /* 점수는 #dScoreTotal 한 곳 (기본 B = 환산 점수, 폭우 = 폭우 시나리오 기준 우선확인 점수, 한파 = 기본 B 점수 + #dScenNote 현장확인 표시).
       예전 #dScore 참조는 화면에 없는 요소라 지웠다 (2026-11-25) */
    renderScen(d);
    $('#dChecked').toggle(!!b.checkedAt).text('현장 확인됨 ' + (b.checkedAt || '').slice(5));
    /* 조사 이력 보기 : 현장 방문 기록이 있을 때만 */
    setHistory(b.visitCnt > 0 ? Common.url('archivedetail.do?bldId=' + encodeURIComponent(String(b.bldId))) : null);
    var maxW = d.scoreItems.reduce(function (m, it) { return Math.max(m, it.weight || 0); }, 0);
    /* 처음에는 모두 접어 둔다. 묶음 줄을 눌러야 요인별 표가 펼쳐진다 */
    $('#dScoreItems').html(d.scoreItems.map(function (it) { return scoreRow(it, maxW, false); }).join(''));
    if (!C.MODEL_B) $('#dScoreTotal').html('합계 <b>' + b.score + '</b>점');
    else if (b.scoreTarget === false) $('#dScoreTotal').html('세대수가 없는 건물이라 점수 모델 B 분석 대상이 아닙니다 (우선확인 후보 · 집계 · 순위에서 제외)');
    /* 점수 아래 작은 설명 문장은 ⓘ 안으로 (2026-11-25, 문구 그대로) */
    else if (d.scenario === 'rain') $('#dScoreTotal').html('폭우 시나리오 기준 우선확인 점수 <b>' + b.displayScore + '</b>점 · 계산용 원점수 <b>' + Number(b.rawScore).toFixed(2) + '</b>점 (영역별 기여 점수의 합)'
      + C.tip('폭우 고정 기준 거처 30 · 건물 상태 20 · 침수 기록 40 · 접근성 10. 3개 구 폭우 점수 300번째를 80점, 최고를 100점으로 맞춘 값' + (b.candidate ? ' · 80점 이상 = 폭우 우선확인 후보' : '') + '. 침수 피해 확률이 아닙니다.', '점수 설명'));
    else $('#dScoreTotal').html('환산 점수 <b>' + b.displayScore + '</b>점 · 계산용 원점수 <b>' + Number(b.rawScore).toFixed(2) + '</b>점 (영역별 기여 점수의 합)'
      + C.tip('환산 점수 = 3개 구 300번째 계산 점수를 80점, 최고 점수를 100점으로 맞춘 값' + (b.candidate ? ' · 80점 이상 = 우선확인 후보' : ''), '점수 설명'));

    var floors = (b.grndFlr != null ? '지상 ' + b.grndFlr + '층' : '') + (b.ugrndFlr ? ' / 지하 ' + b.ugrndFlr + '층' : '');
    if (b.ugrndUnits > 0) floors += (floors ? ' · ' : '') + '지하 전유 ' + b.ugrndUnits + '호';   // 공동주택 전유부 기준 (점수와 무관)
    var use = (b.useNm || '미상') + (b.strctNm ? ' (' + b.strctNm + ')' : '');                      // 구조는 용도 뒤 괄호로
    var apr = b.useaprDay && b.useaprDay.length === 8 ? b.useaprDay.slice(0, 4) + '.' + b.useaprDay.slice(4, 6) + '.' + b.useaprDay.slice(6) + (b.bldAge != null ? ' (' + b.bldAge + '년)' : '') : '자료 없음';
    /* 건축물대장에 있는 값만 */
    $('#dInfo').html(
      infoRow('주용도', use) + infoRow('층수', floors || '-') + infoRow('사용승인', apr)
      + infoRow('위반건축물', b.violYn === '1' ? '해당' : b.violYn === '0' ? '없음' : '대장 미연계')
      + infoRow('세대수', b.units != null ? b.units + '세대' : '-')
    );
    /* 시설까지의 거리 : 가장 가까운 시설까지 거리 */
    $('#dWelf').html(welfRow('복지시설', b.welfNm, b.welfDist) + welfRow('병원', b.hospNm, b.hospDist) + welfRow('정류장', b.busNm, b.busDist));

    /* 거주 취약 가구 (02-2) : 세대 수 + 많은 유형 3개 */
    var rc = d.residents || {};
    /* 한 줄 (2026-11-17) : "취약 가구 3세대 · 75세 이상 2 · 심한 장애 1 · …" 넘치면 … 로 줄고, 전체 문장은 title 로 */
    var types = rc.vulnLine || rc.vulnTypes || (rc.hhCnt ? '취약 유형 없음' : '거주 가구 자료 없음');
    $('#dVulnCnt').text('취약 가구 ' + (rc.vulnHh || 0) + '세대');
    $('#dVulnTypes').text(' · ' + types);
    $('#dVulnLine').attr('title', '취약 가구 ' + (rc.vulnHh || 0) + '세대 · ' + types + (rc.hhCnt ? ' (전체 ' + rc.hhCnt + '가구 ' + rc.mbrCnt + '명)' : ''));
    $('#btnResidents').prop('disabled', !rc.hhCnt);
    $('#dMemo').val('');
    $('#btnArchive').prop('disabled', C.MODEL_B && b.scoreTarget === false).text(C.MODEL_B && b.scoreTarget === false ? '분석 대상 아님 (저장 불가)' : archiveLabel(b));
  }
  /* 기상 시나리오 안내 (2026-11-24) : 응답의 scenario 기준으로 그린다 (요청할 때 기준과 섞이지 않게)
       폭우 = 침수 기록 연도 수 · 지하층 보유 여부, 한파 = 사용승인 연도 · 현장확인 사유 (B 점수 · 순위는 그대로) */
  function renderScen(d) {
    var b = d.building, $n = $('#dScenNote');
    if (!C.MODEL_B || !d.scenario || d.scenario === 'base' || b.scoreTarget === false) { $n.prop('hidden', true).empty(); return; }
    var year = /^\d{4}/.test(b.useaprDay || '') ? Number(String(b.useaprDay).slice(0, 4)) : null;
    /* 2026-11-25 : 한 줄만 보이고 나머지 안내 문장은 그 줄 끝 ⓘ 안 (문구 그대로) */
    if (d.scenario === 'rain') {
      $n.html('<b>폭우 기준 점수</b> · 침수 기록 <b>' + esc(b.floodCnt) + '개 연도</b> · 지하층 <b>' + (b.ugrndRes === 1 ? '있음' : '없음') + '</b>'
        + C.tip('<b>폭우 시나리오</b> · 침수 기록 ' + esc(b.floodCnt) + '개 연도 · 지하층 보유 주택 ' + (b.ugrndRes === 1 ? '있음' : '없음') + '<br>'
          + '경사는 폭우 점수에 넣지 않습니다 (경사 ' + Number(b.slopeDeg || 0).toFixed(1) + '°). 실제 침수 · 지하 거주 여부는 현장에서 확인이 필요합니다.')).prop('hidden', false);
      return;
    }
    var ym = d.coldYearMax || C.COLD_YEAR_MAX, head, more;
    var COLD_COMMON = '한파는 새 점수식이 아니라 기본 B 점수 · 순위 그대로에 추가 확인 표시만 붙입니다.';
    if (b.coldCheck === 'Y') { head = '<b class="cold-y">한파 현장확인 대상</b> · ' + (ym + 1) + '년 이전 사용승인 (' + year + '년)'; more = esc(C.COLD_NOTE); }
    else if (b.coldCheck === 'U') { head = '<b>사용승인일 확인 불가</b>'; more = '한파 확인 불가 · 사용승인일이 없어 대상 여부를 판단할 수 없습니다 (기본 B 우선확인 후보)'; }
    else if (b.candidate) { head = '<b>' + (ym + 1) + '년 이후 사용승인</b> (' + (year || '-') + '년)'; more = '한파 현장확인 해당 없음 · 사용승인 ' + (year || '-') + '년 (' + (ym + 1) + '년 이후 사용승인)'; }
    else { head = '<b>한파 표시 대상 아님</b>'; more = '한파 현장확인은 기본 B 우선확인 후보 중에서만 봅니다' + (year ? ' (사용승인 ' + year + '년)' : ''); }
    $n.html(head + C.tip(more + '<br>' + COLD_COMMON)).prop('hidden', false);
  }

  /* 저장 버튼 문구 (13단계) : building.do 의 archiveCnt(내 보관함) · visitCnt(방문 기록) 로 */
  function archiveLabel(b) {
    if (!(b.archiveCnt > 0)) return '보관함에 저장';
    return b.visitCnt > 0 ? '다시 저장 (점수 갱신)' : '보관함에 저장됨';
  }

  /* ---------- 취약 가구 정보 모달 (02-2-1) ---------- */
  function ageBand(age) { return age == null ? '-' : age < 10 ? '10세 미만' : (Math.floor(age / 10) * 10) + '대'; }
  function household(r) {
    var t = r.hhType || '';
    if (t === '1인') return '1인 가구';
    return t + (r.hhMbrCnt ? ' · ' + r.hhMbrCnt + '인' : '');
  }
  /* 복지 연계 = 원본 값 그대로 (5-1). 임의 규칙("상담 필요" 등) 없음
   *  - 가구원 중 지원연계(support_type: 공적급여 / 민간자원 / 기타서비스)가 있으면 "{값} 연계"
   *  - 없고 처리결과(detect_result: 조치완료 / 단순상담 / 미처리 / 요청접수 / 미지원 …)가 있으면 그 값
   *  - 둘 다 없으면 "이력 없음" */
  function welfare(r) {
    if (r.supportType) return { c: 'ok', t: r.supportType + ' 연계' };
    if (r.detectResult) return { c: 'blue', t: r.detectResult };
    return { c: 'none', t: '이력 없음' };
  }
  /* 한 줄 = 한 가구. 맨 앞 = 순위(서버 순서의 몇 번째) + 가점, 가점에 올리면 내역 툴팁 ("고위험고립 30 · 지하층 10 · 기초연금 3", ResidentService) */
  function rmRow(r, i) {
    var w = welfare(r);
    var tags = (r.tags || []).map(function (t) { return '<span class="tag">' + esc(t) + '</span>'; }).join('')
      + (r.benefits || []).map(function (t) { return '<span class="tag benefit" title="수급 확인용 (이미 복지 체계 안)">' + esc(t) + '</span>'; }).join('');
    return '<tr><td class="rm-rank"><b>' + (i + 1) + '</b><small title="' + esc(r.priorityText || '') + '">' + r.priority + '점</small></td>'
      + '<td class="rm-flr" title="' + esc(r.resFlr || '') + '">' + esc(r.resFlr || '-') + '</td><td>' + esc(r.headNm) + '</td><td>' + ageBand(r.maxAge) + '</td>'
      /* 가구 구성 · 복지 연계는 넘치면 … 로 끝나므로 전체 값을 title 로. 취약 유형 칸만 칩 단위로 줄바꿈 (2차 M) */
      + '<td title="' + esc(household(r)) + '">' + esc(household(r)) + '</td>'
      + '<td class="rm-tagcell">' + (tags ? '<div class="rm-tags">' + tags + '</div>' : '<span class="muted">-</span>') + '</td>'
      + '<td title="' + esc(w.t) + '"><i class="rm-dot ' + w.c + '"></i>' + esc(w.t) + '</td></tr>';
  }
  function pad(n) { return (n < 10 ? '0' : '') + n; }
  function openResidents() {
    if (!current) return;
    var b = current.building, now = new Date();
    $('#rmSub').text(titleOf(b) + ' · ' + b.addr);
    $('#rmViewer').text(($('#resModal').data('viewer') || '').trim() + ' · ' + now.getFullYear() + '-' + pad(now.getMonth() + 1) + '-' + pad(now.getDate()) + ' ' + pad(now.getHours()) + ':' + pad(now.getMinutes()));
    $('#rmRows').html('<tr><td colspan="7" class="rs-empty">불러오는 중…</td></tr>');
    $('#resModal').addClass('open');
    MapApi.residents(b.bldId).done(function (list) {
      var vuln = list.filter(function (r) { return r.priority >= 10; });   // 취약 가구 = 가구 가점 10 이상 (5-1). 순서는 서버 그대로
      $('#rmRows').html(vuln.length ? vuln.map(function (r, i) { return rmRow(r, i); }).join('') : '<tr><td colspan="7" class="rs-empty">취약 유형에 해당하는 가구가 없습니다. (전체 ' + list.length + '가구)</td></tr>');
    }).fail(function () {
      $('#rmRows').html('<tr><td colspan="7" class="rs-empty">가구 정보를 볼 수 없습니다.</td></tr>');
    });
  }
  function closeResidents() { $('#resModal').removeClass('open'); }

  var showSeq = 0;
  function show(bldId) {
    current = null;
    var my = ++showSeq;                                 // 시나리오를 바꾼 뒤 늦게 온 옛 응답은 버린다 (2026-11-24)
    $('#panelRight').addClass('open');
    $('#resultView').hide(); $('#detailView').show();
    $('#dTitle').text('불러오는 중…'); $('#dAddr').text(''); setHistory(null); $('#dScoreItems, #dInfo, #dWelf').empty();
    $('#dScenNote').prop('hidden', true).empty();
    MapApi.building($.extend({}, getCriteria(), { bldId: bldId })).done(function (d) {
      if (my !== showSeq) return;
      if (!d || !d.building) { Common.toast('건물 정보를 찾을 수 없습니다.'); return; }
      current = d;
      renderInfo(d);
      if (window.Bld3D) Bld3D.show(d);                   // 상세 패널 왼쪽 건물 3D 입체보기 : 안 보이는 채로 미리 그린다 (map-3d.js)
    }).fail(function () {                               // 403(관할 밖) 등 : 안내는 Common.api 가 띄운다
      $('#dTitle').text('건물 정보를 볼 수 없습니다');
      $('#btnArchive, #btnResidents').prop('disabled', true);
    });
  }

  function init(opts) {
    getCriteria = opts.getCriteria;
    onArchived = opts.onArchived;

    /* 취약 가구 모달을 오른쪽 패널 밖(body 맨 끝)으로 옮긴다 (2차 Q). 패널이 transform · z-index 로 자기만의 겹침 영역을 만들어서,
       패널 안에 두면 패널 밖의 여닫기 버튼이 모달 위로 올라온다. 이벤트는 id 로 걸려 있어 그대로 동작한다 */
    $('#resModal').appendTo('body');

    /* 점수 구성 : 묶음 줄을 누르면 요인별 표를 펼치고 접는다 (여러 개를 같이 펼칠 수 있다) */
    $('#dScoreItems').on('click', '.sc-head', function () {
      var open = $(this).closest('.sc-item').toggleClass('open').hasClass('open');
      $(this).attr('aria-expanded', String(open));
    });

    $('#btnResidents').on('click', openResidents);
    $('#rmClose, #rmClose2').on('click', closeResidents);
    $('#resModal').on('click', function (e) { if (e.target === this) closeResidents(); });
    $(document).on('keydown', function (e) { if (e.key === 'Escape') closeResidents(); });

    /* 보관함 저장 (13단계). 판정은 서버(ArchiveService.addArchive) :
     *   DUP     = 이미 저장돼 있고 조사 전 → 저장 안 함. 안내 모달(Common.dialog) [보관함에서 보기] = 보관함 목록 새 탭 / [닫기]
     *   UPDATED = 조사 이력 있음 → 새 점수로 다시 저장 (묻지 않음)
     *   ADDED   = 처음 저장 */
    $('#btnArchive').on('click', function () {
      if (!current) return;
      var $b = $(this).prop('disabled', true), bld = current.building, bldId = bld.bldId;
      MapApi.archiveAdd(bldId, current.building.sggCd, C.dispScore(current.building), getCriteria()).done(function (res) {
        if (res && res.ok === false) {
          if (res.code === 'DUP') {
            var info = res.savedAt ? esc(res.savedAt) + ' 저장 · ' + esc(res.savedScore) + '점' : esc(res.msg || '');
            Common.dialog({
              title: '이미 저장된 건물입니다',
              html: '<div class="dlg-info"><span class="t">' + esc(titleOf(bld)) + '</span><span class="s">' + info + '</span></div>'
                + '아직 현장 조사 기록이 없어 다시 저장하지 않았어요.<br>보관함에서 볼까요?',
              okText: '보관함에서 보기', cancelText: '닫기'
            }).done(function (go) {
              /* 확인 클릭 직후라 팝업 차단에 걸리지 않는다 (사용자 동작 안에서 open) */
              if (go) window.open(Common.url('archive.do'), '_blank');   // 보관함 목록 (2026-11-22, 건물 확인 이력 대신)
            });
            return;
          }
          Common.toast(res.msg || '저장할 수 없습니다.');
          return;
        }
        current.building.archiveCnt = 1;
        $b.text(archiveLabel(current.building));
        if (onArchived) onArchived(res.statusCount);
        savedDialog(bld, res);
      }).always(function () { $b.prop('disabled', false); });
    });

    /* 오른쪽 패널 폭 : 왼쪽 끝 손잡이를 끌면 360~640px (기본 400), itgeo.rpW 에 기억 */
    Common.resizer({ handle: '#rpGrip', cssVar: '--rp-w', key: 'itgeo.rpW', min: 360, max: 640, def: 400, sign: -1 });

    /* 3D 보기 (#btn3d) : 클릭 · 로딩 표시는 map-3d.js (Bld3D) 가 맡는다. 목록으로 돌아가거나 패널을 닫으면 3D 도 닫는다 */
    $('#dHistory').on('click', function (e) { if ($(this).hasClass('disabled')) e.preventDefault(); });
    $('#detailBack').on('click', function () { $('#detailView').hide(); $('#resultView').show(); if (window.Bld3D) Bld3D.hide(); });
    $('#detailClose, #resultClose').on('click', function () { $('#panelRight').removeClass('open'); if (window.Bld3D) Bld3D.hide(); });   /* 오른쪽 패널도 지도 위에 덮으므로 resize 없음 */
  }

  /* 저장 완료 안내 (2026-11-22) : 오른쪽 아래 버튼 글자만 바뀌어 잘 안 보이므로 모달로 알린다.
       건물 · 저장일 · 점수 · 어떤 가중치(기본/폭우/폭염/한파/사용자 설정)로 저장했는지 + [보관함에서 보기]
       [다시 보지 않기] 를 체크하고 닫으면 itgeo.archiveDlgOff = 1 → 다음부터는 예전처럼 아래 알림(toast)만 */
  var DLG_OFF_KEY = 'itgeo.archiveDlgOff';
  function scenLabel(c) {
    if (c && c.modified) return '사용자 설정';
    var key = (c && c.scenario) || 'base';
    var hit = (window.MAP_CONFIG && MAP_CONFIG.SCENARIOS || []).filter(function (x) { return x.key === key; })[0];
    return hit ? hit.name : '기본';
  }
  function savedDialog(bld, res) {
    var off = false;
    try { off = localStorage.getItem(DLG_OFF_KEY) === '1'; } catch (e) {}
    var msg = res.msg || '보관함에 저장했습니다.';
    if (off) { Common.toast(msg); return; }
    var d = new Date(), pad = function (n) { return (n < 10 ? '0' : '') + n; };
    var day = d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate());
    var bldId = bld.bldId;
    Common.dialog({
      title: res.code === 'UPDATED' ? '점수를 갱신해 다시 저장했습니다' : '보관함에 저장했습니다',
      html: '<div class="dlg-info"><span class="t">' + esc(titleOf(bld)) + '</span>'
        + '<span class="s">' + day + ' 저장 · ' + (C.MODEL_B ? (current && current.scenario === 'rain' ? '폭우 환산 ' : '환산 ') : '') + esc(C.dispScore(bld)) + '점 · ' + esc(scenLabel(getCriteria ? getCriteria() : null)) + '</span></div>'
        + '<label class="dlg-off"><input type="checkbox" class="dlg-off-chk"> 다시 보지 않기</label>',
      okText: '보관함에서 보기', cancelText: '닫기'
    }).done(function (go) {
      if (go) window.open(Common.url('archive.do'), '_blank');   // 보관함 목록 (2026-11-22, 건물 확인 이력 대신)
    });
    /* 체크하는 순간 기억 (닫기 · 보관함에서 보기 · Esc 어느 쪽으로 닫아도 적용) */
    $('.dlg-bg').last().on('change', '.dlg-off-chk', function () {
      try { if (this.checked) localStorage.setItem(DLG_OFF_KEY, '1'); else localStorage.removeItem(DLG_OFF_KEY); } catch (e) {}
    });
  }

  /* 여닫기 버튼으로 패널을 다시 열었을 때 : 보던 건물 상세가 있으면 3D 를 다시 준비한다 (닫을 때 Bld3D.hide 로 정리했으므로) */
  function refresh3d() {
    if (current && window.Bld3D && $('#detailView').is(':visible')) Bld3D.show(current);
  }

  return { init: init, show: show, refresh3d: refresh3d };
})();
