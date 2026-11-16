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
<%@ include file="/WEB-INF/views/include/header.jspf" %>
<style>
 .du-track{background:var(--line-soft);border-radius:7px;overflow:hidden;max-width:520px;margin:10px 0}
 .du-bar{height:14px;background:var(--primary);width:0;transition:width .4s}
 .du-report td{padding:4px 12px 4px 0;font-size:13px} .du-ng{color:#D93A3A}
 .du-mode{font-size:13px;color:var(--text-2);margin:6px 0}
 .du-steps{list-style:none;padding:0;margin:10px 0 16px;font-size:13px;max-width:520px}
 .du-steps li{display:flex;justify-content:space-between;padding:3px 0;border-bottom:1px solid var(--line-soft)}
 .du-steps .skip{color:var(--text-2)}
</style>
<div class="page">
  <div class="page-head">
    <h1>데이터 업데이트</h1>
    <span class="sub">원천 파일(건물·건축물대장·침수흔적도 등) 중 바뀐 것만 다시 읽어 건물 데이터를 고칩니다.</span>
    <div class="right">
      <button id="btnFull" type="button" class="btn btn-line" title="바뀐 것이 없어도 12단계를 전부 다시 계산합니다 (약 3분)">전체 다시 계산</button>
      <button id="btnUpdate" type="button" class="btn btn-primary">데이터 업데이트</button>
    </div>
  </div>
  <p><span id="step">대기</span> <span id="pct"></span></p>
  <div class="du-track"><div class="du-bar" id="bar"></div></div>
  <p class="du-mode" id="mode"></p>
  <ul class="du-steps" id="steps"></ul>
  <table class="du-report" id="report"></table>
</div>
<script>
var ctx = "${ctx}";
/* 실행 방식 한 줄 : 무엇이 바뀌어서 어디까지 다시 계산하는지 */
function showMode(s) {
  var t = "";
  if (s.mode === "partial") t = (s.changed || []).join(" · ") + " 변경 감지 → 해당 단계만 다시 계산 (나머지 " + (s.skipped || []).length + "단계는 변경 없음)";
  else if (s.mode === "full") t = "12단계 전체 다시 계산";
  else if (s.mode === "none") t = "바뀐 원천 파일이 없습니다. 전부 다시 계산하려면 [전체 다시 계산]을 누르세요.";
  $("#mode").text(t);
}
/* 단계 목록 : 끝난 단계는 걸린 시간, 건너뛴 단계는 "변경 없음" */
function showSteps(s) {
  var ul = $("#steps").empty();
  $.each(s.log || [], function (_, x) { ul.append($("<li>").append($("<span>").text("✓ " + x.step), $("<span>").text(x.sec + "초"))); });
  if (s.status === "running" && s.step) ul.append($("<li>").append($("<span>").text("… " + s.step), $("<span>").text("진행 중")));
  if (s.mode === "partial") $.each(s.skipped || [], function (_, n) { ul.append($("<li class='skip'>").append($("<span>").text("– " + n), $("<span>").text("변경 없음 · 건너뜀"))); });
}
function showReport(s) {
  var r = s.report || {}, t = $("#report").empty();
  function row(k, v, ng) { t.append($("<tr>").append($("<td>").addClass(ng ? "du-ng" : "").text(k), $("<td>").text(v))); }
  if (s.status === "error") { row("오류: " + s.error, "", true); return; }
  $.each(r.summary || {}, function (k, v) { row(k, Number(v).toLocaleString()); });
  $.each((r.dummy || {}).issues || {}, function (k, v) { row("더미 · " + k, v); });
  $.each(r.rules || [], function (_, x) { if (!x.ok) row("검증 실패: " + x.rule, x.detail, true); });
  var result;
  if (r.mode === "none") result = "변경 없음";
  else if (r.mode === "partial") {
    var u = r.db_update || {};
    result = u.updated ? "DB 반영 완료 (" + (u.cols || []).join(", ") + " · " + Number(u.changed_rows).toLocaleString() + "동)"
           : r.ok ? "계산만 함 (DB는 그대로" + (u.changed_rows != null ? " · 반영하면 " + Number(u.changed_rows).toLocaleString() + "동이 바뀜" : "") + ")"
           : "반영 안 함 (" + (u.reason || "위 항목 확인") + ")";
  } else result = r.db_swap && r.db_swap.swapped ? "DB 반영 완료" : r.ok ? "검증 통과 (새 파일 저장, DB는 그대로)" : "반영 안 함 (위 항목 확인)";
  t.append($("<tr>").append($("<td>").append($("<b>").text("결과")), $("<td>").append($("<b>").text(result))));
}
function show(s) { showMode(s); showSteps(s); }
$.get(ctx + "/api/dataupdate/latest.do", function (s) { if (s && (s.report || s.status === "error")) { $("#step").text("마지막 실행: " + s.status); show(s); showReport(s); } });
function start(full) {
  var btns = $("#btnUpdate, #btnFull").prop("disabled", true);
  $("#report, #steps").empty(); $("#mode").text(""); $("#bar").css("width", 0); $("#step").text("시작"); $("#pct").text("");
  $.post(ctx + "/api/dataupdate/run.do", { full: full }).done(function (r) {
    var timer = setInterval(function () {
      $.get(ctx + "/api/dataupdate/job.do", { id: r.jobId }, function (s) {
        $("#step").text(s.step || "준비"); $("#pct").text(Math.round(s.progress * 100) + "%");
        $("#bar").css("width", (s.progress * 100) + "%");
        show(s);
        if (s.status !== "running") { clearInterval(timer); btns.prop("disabled", false); showReport(s); }
      }).fail(function (x) {   // 배치 서버가 중간에 꺼지면 조회를 멈춘다
        clearInterval(timer); btns.prop("disabled", false);
        $("#step").text((x.responseJSON || {}).msg || "진행 상황을 읽지 못했습니다.");
      });
    }, 1000);
  }).fail(function (x) { alert((x.responseJSON || {}).msg || "시작 실패"); btns.prop("disabled", false); });
}
$("#btnUpdate").click(function () { start(false); });
$("#btnFull").click(function () { start(true); });
</script>
</body>
</html>
