<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="업무 캘린더"/>
<c:set var="pageCss" value="pages"/>
<c:set var="pageCss2" value="workcal"/>
<c:set var="activeMenu" value="calendar"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%@ include file="/WEB-INF/views/include/header.jspf" %>

<%-- 달력 공용 스타일 (현장 확인 리스트 달력과 같은 파일). 이 페이지 전용 workcal.css 는 위 pageCss2 로 head 에서 불러옴 --%>
<link rel="stylesheet" href="${ctx}/resources/css/pages_calendar.css">

<div class="page">
  <%-- 제목 줄 : 업무 캘린더 · ‹ 2026년 11월 › · 오늘 ········ 범례 · 이번 달 리스트 n개 --%>
  <div class="wc-head">
    <h1>업무 캘린더</h1>
    <button type="button" class="wc-nav" id="wcPrev" title="이전 달">‹</button>
    <b class="wc-title" id="wcTitle"></b>
    <button type="button" class="wc-nav" id="wcNext" title="다음 달">›</button>
    <button type="button" class="btn btn-line btn-sm" id="wcToday">오늘</button>
    <div class="wc-legend">
      <span><i class="st-todo"></i>조사 전</span>
      <span><i class="st-doing"></i>조사 중</span>
      <span><i class="st-done"></i>확인 완료</span>
      <em>이번 달 리스트 <b id="wcCount">0</b>개</em>
    </div>
  </div>

  <div class="wc-grid">
    <%-- 왼쪽 : 한 달 달력 (현장 확인 리스트 화면에서 날짜에 넣은 일정이 그대로 보인다) --%>
    <div id="wcCalendar" class="fl-calendar wc-calendar"></div>

    <%-- 오른쪽 : 고른 날짜의 방문 리스트 → 리스트를 고르면 그 리스트의 건물 --%>
    <aside class="wc-side">
      <div class="wc-side-head"><h3 id="wcDay"></h3><small id="wcDaySub"></small></div>
      <div id="wcLists"></div>
      <div class="wc-bld" id="wcBld"></div>
    </aside>
  </div>
</div>

<%-- 리스트 정보 (이름 · 건물 수 · 완료 수 · 건물들). 화면에는 안 보이고 스크립트가 읽는다 --%>
<div id="wcData" style="display:none">
  <c:forEach var="f" items="${lists}">
    <div class="d-list" data-id="${f.surveyId}" data-title="<c:out value='${f.title}'/>" data-item-cnt="${f.itemCnt}" data-done-cnt="${f.doneCnt}">
      <c:forEach var="it" items="${f.items}">
        <i data-title="<c:out value='${it.title}'/>" data-addr="<c:out value='${it.addr}'/>" data-score="${it.score}" data-checked="${it.checked}" data-order="${it.visitOrder}"></i>
      </c:forEach>
    </div>
  </c:forEach>
</div>

<script src="https://cdn.jsdelivr.net/npm/fullcalendar@6.1.15/index.global.min.js"></script>
<script src="https://cdn.jsdelivr.net/npm/@fullcalendar/core@6.1.15/locales/ko.global.min.js"></script>
<script src="${ctx}/resources/js/common.js"></script>
<script>
  $(function () {
    /* ---------- 리스트 정보 읽기 : LISTS[surveyId] = {title, itemCnt, doneCnt, status, items[]} ---------- */
    var LISTS = {};
    $('#wcData .d-list').each(function () {
      var $l = $(this), item = Number($l.data('itemCnt')) || 0, done = Number($l.data('doneCnt')) || 0;
      LISTS[$l.data('id')] = {
        title: String($l.data('title')), itemCnt: item, doneCnt: done,
        status: done === 0 ? 'todo' : (done >= item ? 'done' : 'doing'),
        items: $l.find('i').map(function () {
          var $i = $(this);
          return { title: String($i.data('title')), addr: String($i.data('addr') || ''), score: $i.data('score'), checked: String($i.data('checked')) === 'true' };
        }).get()
      };
    });
    var ST_NAME = { todo: '조사 전', doing: '조사 중', done: '확인 완료' };
    var WEEK = ['일', '월', '화', '수', '목', '금', '토'];
    function ymd(d) { return d.getFullYear() + '-' + ('0' + (d.getMonth() + 1)).slice(-2) + '-' + ('0' + d.getDate()).slice(-2); }
    function listOf(ev) { return LISTS[ev.extendedProps.surveyId] || { title: ev.title, itemCnt: 0, doneCnt: 0, status: 'todo', items: [] }; }

    var selDate = ymd(new Date()), selEventId = null;

    /* ---------- 달력 ---------- */
    var calendar = new FullCalendar.Calendar(document.getElementById('wcCalendar'), {
      initialView: 'dayGridMonth',
      locale: 'ko',
      headerToolbar: false,                  /* 제목 · 이전/다음은 위 wc-head 에서 */
      height: 'auto',
      fixedWeekCount: false,                 /* 그 달에 필요한 주만 */
      dayMaxEvents: 3,
      moreLinkContent: function (a) { return '+' + a.num + ' 더보기'; },
      dayCellContent: function (a) { return a.date.getDate(); },   /* "1일" 대신 "1" */
      editable: true,                        /* 일정을 끌어 다른 날로 */
      eventDurationEditable: false,
      events: {
        url: '${ctx}/api/field/schedule.do',
        method: 'GET',
        failure: function () { Common.toast('달력 일정을 불러오지 못했습니다.'); }
      },
      /* 상태(조사 전 · 조사 중 · 확인 완료)별 색 */
      eventDidMount: function (info) { info.el.classList.add('st-' + listOf(info.event).status); },
      eventsSet: function () { renderSide(); countMonth(); },
      datesSet: function () { $('#wcTitle').text(calendar.view.title); countMonth(); },
      dateClick: function (info) { selDate = info.dateStr; selEventId = null; renderSide(); },
      eventClick: function (info) {
        info.jsEvent.preventDefault();
        selDate = ymd(info.event.start); selEventId = info.event.id; renderSide();
      },
      dayCellClassNames: function (a) { return ymd(a.date) === selDate ? ['wc-sel'] : []; },
      eventDrop: function (info) {
        Common.api('POST', 'api/field/schedule/move.do', { scheduleId: info.event.id, date: ymd(info.event.start) }).done(function (res) {
          if (!res || !res.ok) { info.revert(); Common.toast((res && res.msg) || '일정을 옮기지 못했습니다.'); return; }
          selDate = ymd(info.event.start); selEventId = info.event.id; renderSide();
        }).fail(function () { info.revert(); Common.toast('일정을 옮기지 못했습니다.'); });
      }
    });
    calendar.render();
    $('#wcPrev').on('click', function () { calendar.prev(); });
    $('#wcNext').on('click', function () { calendar.next(); });
    $('#wcToday').on('click', function () { calendar.today(); selDate = ymd(new Date()); selEventId = null; renderSide(); });

    /* 이번 달(보고 있는 달)에 들어 있는 리스트 수 */
    function countMonth() {
      var m = calendar.getDate().getMonth(), y = calendar.getDate().getFullYear();
      var n = calendar.getEvents().filter(function (e) { return e.start.getMonth() === m && e.start.getFullYear() === y; }).length;
      $('#wcCount').text(n);
    }

    /* ---------- 오른쪽 패널 ---------- */
    function renderSide() {
      /* 고른 날짜 칸 테두리 */
      $('#wcCalendar .fc-daygrid-day').removeClass('wc-sel').filter('[data-date="' + selDate + '"]').addClass('wc-sel');

      var d = new Date(selDate + 'T00:00:00');
      var evs = calendar.getEvents().filter(function (e) { return ymd(e.start) === selDate; });
      $('#wcDay').text((d.getMonth() + 1) + '월 ' + d.getDate() + '일 (' + WEEK[d.getDay()] + ')');
      $('#wcDaySub').text('방문 리스트 ' + evs.length + '개');

      var $lists = $('#wcLists').empty();
      if (!evs.length) $lists.append($('<div class="wc-empty">').text('이 날짜에 넣은 리스트가 없습니다.'));
      if (selEventId && !evs.some(function (e) { return e.id === selEventId; })) selEventId = null;
      if (!selEventId && evs.length) selEventId = evs[0].id;

      evs.forEach(function (ev) {
        var L = listOf(ev);
        var $row = $('<div class="wc-row">').toggleClass('on', ev.id === selEventId).attr('data-id', ev.id);
        $row.append($('<i class="dot">').addClass('st-' + L.status));
        $row.append($('<div class="tx">').append($('<b>').text(L.title)).append($('<small>').text(L.itemCnt + '개 건물 · ' + L.doneCnt + '개 완료')));
        $row.append($('<span class="st-badge">').addClass('st-' + L.status).text(ST_NAME[L.status]));
        var $act = $('<div class="acts">');
        $act.append($('<a class="btn btn-line btn-sm">').attr('href', '${ctx}/fieldedit.do?listId=' + ev.extendedProps.surveyId).text('수정'));
        $act.append($('<button type="button" class="btn btn-line btn-sm wc-del">').text('삭제'));
        $row.append($act);
        $lists.append($row);
      });

      /* 고른 리스트의 건물 */
      var $bld = $('#wcBld').empty();
      var sel = evs.filter(function (e) { return e.id === selEventId; })[0];
      if (!sel) return;
      var L = listOf(sel);
      $bld.append($('<div class="cap">').text(L.title + ' · 건물 ' + L.items.length + '곳'));
      L.items.forEach(function (it) {
        $bld.append($('<div class="wc-b">')
          .append($('<div class="tx">').append($('<b>').text(it.title)).append($('<small>').text((it.addr ? it.addr + ' · ' : '') + (it.checked ? '확인 완료' : '조사 전'))))
          .append($('<div class="sc">').append($('<b>').text(it.score)).append($('<small>').text('취약도'))));
      });
      if (!L.items.length) $bld.append($('<div class="wc-empty">').text('담긴 건물이 없습니다.'));
    }

    /* 리스트 고르기 · 삭제(이 날짜에서 빼기, 리스트 자체는 남음) */
    $('#wcLists').on('click', '.wc-row', function (e) {
      if ($(e.target).closest('a, button').length) return;
      selEventId = String($(this).data('id')); renderSide();
    });
    $('#wcLists').on('click', '.wc-del', function () {
      var ev = calendar.getEventById(String($(this).closest('.wc-row').data('id')));
      if (!ev || !confirm('"' + ev.title + '" 을(를) ' + selDate + ' 일정에서 뺄까요?\n리스트 자체는 지워지지 않습니다.')) return;
      Common.api('POST', 'api/field/schedule/delete.do', { scheduleId: ev.id }).done(function (res) {
        if (res && res.ok) { ev.remove(); selEventId = null; renderSide(); countMonth(); }
        else Common.toast((res && res.msg) || '일정을 빼지 못했습니다.');
      }).fail(function () { Common.toast('일정을 빼지 못했습니다.'); });
    });
  });
</script>
</body>
</html>
 