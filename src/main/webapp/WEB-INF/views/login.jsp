<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%--
  로그인 (2026-11-27) : reference/design_ITGEO/IT-GEO_인트로_로그인_시안 (1).html 을 그대로 옮김.
  왼쪽 = 시간 흐름에 따라 바뀌는 동네 장면(인트로, js/login.js), 오른쪽 = 로그인 패널. 라이트 전용 화면이라 공통 head.jspf(테마 · common.css)를 쓰지 않는다.
  POST login.do (LoginController) : 실패하면 msg · loginId 를 다시 보여 준다. 로고 = resources/img/itgeo-logo-light.png
--%>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!doctype html>
<html lang="ko"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>로그인 | ITGEO</title>
<link rel="stylesheet" href="https://fonts.googleapis.com/css2?family=Noto+Sans+KR:wght@400;500;700&family=IBM+Plex+Mono:wght@500&display=swap">
<link rel="stylesheet" href="${ctx}/resources/css/login.css">
</head>
<body>
<div class="app">
  <section class="scene" id="scene" aria-label="시간 흐름에 따라 바뀌는 동네 장면">
    <canvas id="cv"></canvas>
    <div class="cap">
      <h1 id="headline">지도 위에서 찾고,<br>현장에서 확인합니다</h1>
    </div>
    <button class="replay" id="replay" type="button">인트로 다시 보기</button>
    <div class="hud" id="hud">
      <div class="clock" id="clock">14:20<small>오후</small></div>
      <input id="time" type="range" min="0" max="24" step="0.0833" aria-label="시간 이동">
      <button class="chip" id="play" type="button" aria-pressed="false">▶ 하루 흐름</button>
      <div class="chips" role="group" aria-label="날씨">
        <span class="wx" id="wx">기상청 연동 · 시연용</span>
        <button class="chip" data-w="clear" aria-pressed="true" type="button">맑음</button>
        <button class="chip" data-w="cloud" aria-pressed="false" type="button">흐림</button>
        <button class="chip" data-w="rain" aria-pressed="false" type="button">비</button>
        <button class="chip" data-w="snow" aria-pressed="false" type="button">눈</button>
      </div>
      <div class="hint">장면을 좌우로 끌면 시간이 흐릅니다. 돋보기를 올리면 분석 결과가 보입니다.</div>
    </div>
    <div class="done" id="done" hidden>
      <h2>취약시설 분석 화면으로 이동합니다</h2>
      <p>로그인 정보를 확인하고 있습니다.</p>
      <button id="back" type="button">처음으로</button>
    </div>
  </section>

  <aside class="panel">
  <div class="brand">
    <img class="logo" src="${ctx}/resources/img/itgeo-logo-light.png" alt="IT-GEO">

    <p class="sys">공간정보 기반 주거환경 취약요인 분석 및<br>가구 현장조사 지원 시스템</p>
  </div>
  <div class="card">
    <h2>로그인</h2>
    <p class="desc">사원번호와 비밀번호를 입력해 주세요.</p>
    <form id="login" action="${ctx}/login.do" method="post" novalidate>
      <c:if test="${not empty msg}"><p class="srv-msg" role="alert"><c:out value="${msg}"/></p></c:if>
      <label class="f" for="loginId">사원번호 (아이디)</label>
      <input class="input" id="loginId" name="loginId" value="<c:out value='${loginId}'/>" placeholder="아이디 입력" autocomplete="username">
      <div class="msg" id="idMsg"></div>
      <label class="f" for="pw">비밀번호</label>
      <input class="input" id="pw" name="pw" type="password" placeholder="비밀번호 입력" autocomplete="current-password">
      <div class="msg" id="pwMsg"></div>
      <div class="row">
        <label class="chk"><input type="checkbox" id="saveId">사원번호 저장</label>
        <a href="#" onclick="return false">비밀번호 찾기</a>
      </div>
      <button class="btn" id="go" type="submit">로그인</button>
    </form>
    <ul class="notice">
      <li>비밀번호 5회 연속 오류 시 계정 잠금 → 본인 인증 후 해제</li>
      <li>30분 동안 입력이 없으면 자동 로그아웃</li>
    </ul>
  </div>
  <div class="foot">본 서비스는 내부 분석 지원용 시스템입니다.</div>
</aside>
</div>

<script src="${ctx}/resources/js/login.js"></script>
</body></html>
