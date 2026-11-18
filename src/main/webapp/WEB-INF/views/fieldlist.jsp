<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="현장 확인 리스트"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="field"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<div class="page">
  <div class="page-head"><h1>현장 확인 리스트</h1></div>
  

  <div class="page-head fl-search-row" style="margin-bottom:0">
    <div class="tabs" style="margin-bottom:0;border:0">
      <a href="${ctx}/fieldlist.do?tab=doing" class="${tab == 'doing' ? 'active' : ''}">진행중 <b>${doingCnt}</b></a>
      <a href="${ctx}/fieldlist.do?tab=done" class="${tab == 'done' ? 'active' : ''}">완료됨 <b>${doneCnt}</b></a>
    </div>
    <div class="right">
      <form method="get" action="${ctx}/fieldlist.do" class="search-wrap">
        <input type="hidden" name="tab" value="${tab}">
        <input class="field" name="q" value="<c:out value='${q}'/>" placeholder="리스트 이름 검색">
        <button type="submit" title="검색">⌕</button>
      </form>
      <a class="btn btn-primary" href="${ctx}/fieldedit.do?listId=0">+ 새 현장확인 리스트</a>
    </div>
  </div>
  <div class="fl-divider" style="border-bottom:1px solid var(--line);margin:0 0 6px"></div>

  <div class="fl-head"><span>리스트 이름</span><span>건물</span><span>확인 진행</span><span></span></div>

  <c:forEach var="f" items="${list}">
    <div class="fl-card" data-id="${f.surveyId}">
      <div class="fl-row">
        <div>
          <div class="t"><c:out value="${f.title}"/></div>
          <div class="s">
            <c:if test="${not empty f.emdNm}"><span class="badge"><c:out value="${f.guName}"/> <c:out value="${f.emdNm}"/></span></c:if>
            <c:choose>
              <c:when test="${f.itemCnt > 0}"><span><c:out value="${f.firstAddr}"/><c:if test="${f.itemCnt > 1}"> 외 ${f.itemCnt - 1}동</c:if></span></c:when>
              <c:otherwise><span class="muted">담긴 건물이 없습니다</span></c:otherwise>
            </c:choose>
          </div>
        </div>
        <div class="n">${f.itemCnt}동</div>
        <div class="prog"><i><b style="width:${f.progressPct}%"></b></i><span>${f.doneCnt}/${f.itemCnt}</span></div>
        <div class="fl-actions">
          <a class="btn btn-primary btn-sm" href="${ctx}/fieldedit.do?listId=${f.surveyId}">수정</a>
          <a class="btn btn-line btn-sm" href="${ctx}/fielddetail.do?listId=${f.surveyId}">상세보기</a>
          <button type="button" class="caret" title="펼치기">⌄</button>
        </div>
      </div>
      <div class="fl-body">
        <%-- [약도 1] 왼쪽 = 건물 목록, 오른쪽 = 약도 --%>
        <div class="fl-body-grid">
        <div class="fl-items">
        <div class="cap">건물 ${f.itemCnt}동 · 동선 순서</div>
        <c:forEach var="it" items="${f.items}">
          <%-- [약도 2] 약도에 찍을 좌표 · 번호 · 확인 여부 --%>
          <div class="fl-item" data-lat="${it.lat}" data-lng="${it.lng}" data-order="${it.visitOrder}" data-checked="${it.checked}" data-title="<c:out value='${it.title}'/>">
            <span class="num ${it.checked ? 'done' : ''}">${it.checked ? '✓' : it.visitOrder}</span>
            <div class="ti"><b><c:out value="${it.title}"/></b><span><c:out value="${it.addr}"/></span></div>
            <span class="sc">${it.score}</span>
            <span class="badge ${it.checked ? 'done' : 'doing'}">${it.checked ? '확인 완료' : '미확인'}</span>
          </div>
        </c:forEach>
        <c:if test="${empty f.items}"><div class="muted" style="padding:8px 10px">"수정"에서 보관함 건물을 담으세요.</div></c:if>
        </div>
        <%-- [약도 3] 약도 자리 (카드를 처음 펼칠 때 그린다) --%>
        <c:if test="${not empty f.items}">
          <div class="fl-map">
            <div class="nmap"></div>
            <div class="fl-map-zoom"><button type="button" class="zin" title="확대">+</button><button type="button" class="zout" title="축소">−</button></div>
          </div>
        </c:if>
        </div>
        <div class="fl-body-foot">
          <c:choose>
            <c:when test="${f.doneCnt > 0}"><button type="button" class="fl-del" disabled title="현장 확인 기록이 있는 리스트는 건물 확인 이력을 지키기 위해 삭제할 수 없습니다">리스트 삭제</button></c:when>
            <c:otherwise><button type="button" class="fl-del" data-id="${f.surveyId}" data-title="<c:out value='${f.title}'/>">리스트 삭제</button></c:otherwise>
          </c:choose>
        </div>
      </div>
    </div>
  </c:forEach>

  <c:if test="${empty list}"><div class="empty">${tab == 'done' ? '완료된 리스트가 없습니다.' : '진행 중인 리스트가 없습니다.'}</div></c:if>
  <a class="fl-new" href="${ctx}/fieldedit.do?listId=0" title="새 현장확인 리스트"><span>+</span></a>
  
  <%-- 달력 스타일 : resources/css/calendar.css (다른 달력 페이지에서도 이 한 줄로 같이 씀) --%>
  <link rel="stylesheet" href="${ctx}/resources/css/pages_calendar.css">

  <%-- 이번 주 달력 : 위 리스트의 제목 줄을 끌어다 날짜 칸에 놓으면 그 날 일정으로 들어간다 --%>

  <div id="flCalendar" class="fl-calendar"></div>
  <c:if test="${not empty msg}"><div class="flash"><c:out value="${msg}"/></div></c:if>
  
</div>

<form id="flDeleteForm" method="post" action="${ctx}/fielddelete.do" style="display:none"><input type="hidden" name="listId" id="flDeleteId"><input type="hidden" name="tab" value="${tab}"></form>

<%-- 약도 5 --%>
<script>
  window.navermap_authFailure = function () { document.querySelectorAll('.fl-map .nmap').forEach(function (el) { el.innerHTML = '<div class="map-error"><b>네이버 지도 인증 실패</b></div>'; }); };
</script>
<script src="https://oapi.map.naver.com/openapi/v3/maps.js?ncpKeyId=<c:out value='${naverClientId}'/>"></script>
<script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.15/index.global.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@fullcalendar/core@6.1.15/locales/ko.global.min.js"></script>
<script src="${ctx}/resources/js/common.js"></script>
<script>
  $(function () {
    /* 카드 머리 어디를 눌러도 펼침. 하나를 펼치면 나머지는 접는다 (버튼·링크는 제외) */
    $('.fl-row').on('click', function (e) {
      if ($(e.target).closest('a, button:not(.caret)').length) return;
      var $card = $(this).closest('.fl-card'), open = !$card.hasClass('open');
      $('.fl-card').removeClass('open');
      $card.toggleClass('open', open);
    });
    $('.fl-card').first().addClass('open');

    /* [약도 4] 펼친 카드의 약도 : 번호 핀 + 방문 순서대로 점선. 카드마다 처음 펼칠 때 한 번만 만든다
       (접혀 있는 동안은 크기가 0 이라 그때 만들면 지도가 깨진다) */
    function drawMiniMap($card) {
      if (!(window.naver && naver.maps && naver.maps.Map)) return;
      var $box = $card.find('.fl-map');
      if (!$box.length) return;
      if ($box.data('map')) { naver.maps.Event.trigger($box.data('map'), 'resize'); return; }
      var pts = [];
      $card.find('.fl-item').each(function () {
        var lat = parseFloat($(this).data('lat')), lng = parseFloat($(this).data('lng'));
        if (!isNaN(lat) && !isNaN(lng)) pts.push({ lat: lat, lng: lng, order: $(this).data('order'), checked: String($(this).data('checked')) === 'true', title: $(this).data('title') });
      });
      if (!pts.length) { $box.hide(); return; }
      var map = new naver.maps.Map($box.find('.nmap')[0], {
        center: new naver.maps.LatLng(pts[0].lat, pts[0].lng), zoom: 16,
        zoomControl: false, mapDataControl: false, scaleControl: false, logoControlOptions: { position: naver.maps.Position.BOTTOM_LEFT }
      });
      $box.data('map', map);
      var path = pts.map(function (p, i) {
        var ll = new naver.maps.LatLng(p.lat, p.lng);
        new naver.maps.Marker({ position: ll, map: map, zIndex: 100 + i, title: p.title,
          icon: { content: '<div class="mk-inv num-pin' + (p.checked ? ' done' : '') + '">' + (p.checked ? '✓' : p.order) + '</div>', anchor: new naver.maps.Point(14, 14) } });
        return ll;
      });
      if (path.length > 1) {
        new naver.maps.Polyline({ map: map, path: path, strokeColor: '#4a8fe0', strokeWeight: 3, strokeStyle: 'shortdash', strokeOpacity: 0.95, zIndex: 50 });
        var b = new naver.maps.LatLngBounds(path[0], path[0]);
        path.forEach(function (p) { b.extend(p); });
        map.fitBounds(b, { top: 30, right: 30, bottom: 30, left: 30 });
      }
      $box.find('.zin').on('click', function (e) { e.stopPropagation(); map.setZoom(map.getZoom() + 1, true); });
      $box.find('.zout').on('click', function (e) { e.stopPropagation(); map.setZoom(map.getZoom() - 1, true); });
    }
    drawMiniMap($('.fl-card.open').first());
    $('.fl-row').on('click', function () {
      var $card = $(this).closest('.fl-card');
      setTimeout(function () { if ($card.hasClass('open')) drawMiniMap($card); }, 0);
    });
    /* [추가 3] 이번 주 달력
       - 보이는 주의 일정 : GET  api/field/schedule.do?start&end  → [{id, title, start, surveyId}]
       - 리스트를 끌어다 놓기 : POST api/field/schedule/add.do    (surveyId, date)
       - 일정 끌어서 옮기기   : POST api/field/schedule/move.do   (scheduleId, date)
       - × 로 빼기           : POST api/field/schedule/delete.do (scheduleId) */
    var calEl = document.getElementById('flCalendar');
    if (calEl && window.FullCalendar) {
      function ymd(d) { return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2); }   /* 2026-11-17 */
      function failMsg(xhr, def) { return (xhr && xhr.responseJSON && xhr.responseJSON.msg) || def; }

      var calendar = new FullCalendar.Calendar(calEl, {
        initialView: 'dayGridWeek',
        locale: 'ko',
        height: 'auto',
        headerToolbar: { left: 'title', center: '', right: 'today prev,next' },
        dayMaxEvents: 3,                       /* 하루에 3개 넘으면 "+n" 으로 접기 */
        droppable: true,                       /* 밖(리스트)에서 끌어온 것을 받는다 */
        editable: true,                        /* 일정을 끌어서 다른 날로 */
        eventDurationEditable: false,          /* 여러 날로 늘리기는 안 함 */
        events: {
          url: '${ctx}/api/field/schedule.do',
          method: 'GET',
          failure: function () { Common.toast('달력 일정을 불러오지 못했습니다.'); }
        },

        /* 일정 막대 오른쪽에 × (빼기) */
        eventDidMount: function (info) {
          var x = document.createElement('span');
          x.className = 'fc-x'; x.title = '이 날짜에서 빼기'; x.textContent = '×';
          info.el.appendChild(x);
        },

        /* × 를 누르면 빼기, 나머지를 누르면 그 리스트 상세보기로 */
        eventClick: function (info) {
          info.jsEvent.preventDefault();
          if ($(info.jsEvent.target).hasClass('fc-x')) {
            if (!confirm('"' + info.event.title + '" 을(를) ' + ymd(info.event.start) + ' 에서 뺄까요?')) return;
            Common.api('POST', 'api/field/schedule/delete.do', { scheduleId: info.event.id }).done(function (res) {
              if (res && res.ok) info.event.remove(); else Common.toast((res && res.msg) || '일정을 빼지 못했습니다.');
            }).fail(function (xhr) { Common.toast(failMsg(xhr, '일정을 빼지 못했습니다.')); });
            return;
          }
          var sid = info.event.extendedProps.surveyId;
          if (sid) location.href = '${ctx}/fielddetail.do?listId=' + sid;
        },

        /* 리스트를 끌어다 놓음 → 저장. 저장되면 서버 기준으로 다시 그림 (id 를 받으려고) */
        eventReceive: function (info) {
          var ev = info.event;
          Common.api('POST', 'api/field/schedule/add.do', { surveyId: ev.extendedProps.surveyId, date: ymd(ev.start) }).done(function (res) {
            ev.remove();
            if (res && res.ok) { calendar.refetchEvents(); Common.toast('"' + ev.title + '" 을(를) ' + ymd(ev.start) + ' 에 넣었습니다.'); }
            else Common.toast((res && res.msg) || '일정을 넣지 못했습니다.');
          }).fail(function (xhr) { ev.remove(); Common.toast(failMsg(xhr, '일정을 넣지 못했습니다.')); });
        },

        /* 일정을 다른 날로 옮김 → 저장. 실패하면 제자리로 */
        eventDrop: function (info) {
          Common.api('POST', 'api/field/schedule/move.do', { scheduleId: info.event.id, date: ymd(info.event.start) }).done(function (res) {
            if (!res || !res.ok) { info.revert(); Common.toast((res && res.msg) || '일정을 옮기지 못했습니다.'); }
          }).fail(function (xhr) { info.revert(); Common.toast(failMsg(xhr, '일정을 옮기지 못했습니다.')); });
        }
      });
      calendar.render();

      /* 리스트 카드의 제목 줄(.fl-row)을 달력으로 끌 수 있게. 살짝 움직여야 끌기로 보므로 클릭(펼치기)은 그대로 */
      new FullCalendar.Draggable(document.querySelector('.page'), {
        itemSelector: '.fl-card .fl-row',
        minDistance: 8,
        eventData: function (el) {
          var $card = $(el).closest('.fl-card');
          return {
            title: $.trim($card.find('.t').first().text()),
            extendedProps: { surveyId: $card.data('id') },
            create: true
          };
        }
      });
    }

    /* 리스트 삭제 */
    $('.fl-del[data-id]').on('click', function () {
      if (!confirm('"' + $(this).data('title') + '" 리스트를 삭제할까요?\n담긴 건물은 보관함에 그대로 남습니다.')) return;
      $('#flDeleteId').val($(this).data('id'));
      $('#flDeleteForm').trigger('submit');
    });
  });
</script>
</body>
</html>
 