<%@ page language="java" contentType="text/html; charset=UTF-8" pageEncoding="UTF-8"%>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<c:set var="pageTitle" value="데이터 업데이트"/>
<c:set var="pageCss" value="pages"/>
<c:set var="activeMenu" value="dataupdate"/>
<c:set var="ctx" value="${pageContext.request.contextPath}"/>
<!DOCTYPE html>
<html lang="ko">
<%@ include file="/WEB-INF/views/include/head.jspf" %>
<body>
<%-- 2차(11-17) : 데이터 업데이트는 헤더 버튼의 모달로 옮겼다 (include/dataupdate-modal.jspf · js/dataupdate-modal.js).
     이 주소로 직접 들어오면 같은 모달을 바로 연다 → 실행 · 진행 상황 로직은 dataupdate-modal.js 한 곳 --%>
<script>window.DU_AUTO_OPEN = true;</script>
<%@ include file="/WEB-INF/views/include/header.jspf" %>
<div class="page">
  <div class="page-head">
    <h1>데이터 업데이트</h1>
    <span class="sub">원천 파일(건물·건축물대장·침수흔적도 등) 중 바뀐 것만 다시 읽어 건물 데이터를 고칩니다.</span>
    <div class="right">
      <button type="button" class="btn btn-primary" onclick="document.getElementById('btnDataUpdate').click()">데이터 업데이트 열기</button>
    </div>
  </div>
  <p class="muted">업데이트 창을 닫아도 작업은 계속되고, 위쪽 [데이터 업데이트] 버튼에 진행률이 표시됩니다.</p>
</div>
<script src="${ctx}/resources/js/common.js"></script>
</body>
</html>
