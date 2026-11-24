package itgeo.controller;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import itgeo.common.BadRequestException;
import itgeo.service.FieldService;
import itgeo.service.RouteService;
import itgeo.service.ScheduleService;
import itgeo.to.MemberTO;
import itgeo.to.ScheduleEventTO;
import itgeo.to.WalkPathTO;

// 현장확인 JSON API (myabweb EmpRESTController 패턴)
@RestController
public class FieldRESTController {

	@Autowired
	FieldService fieldService;

	@Autowired
	RouteService routeService;

	@Autowired
	ScheduleService scheduleService;

	// POST api/field/walk.do?lngs=..&lats=..&mode=walk|transit|car (출발점부터 방문 순서대로) : 현장확인 동선 경로 (TMAP). appKey 는 서버에만
	@PostMapping(value = "api/field/walk.do", produces = "application/json;charset=UTF-8")
	public WalkPathTO walk(@RequestParam("lngs") double[] lngs, @RequestParam("lats") double[] lats,
			@RequestParam(value = "mode", defaultValue = "walk") String mode) {
		return routeService.walkPath(lngs, lats, mode);
	}

	// POST api/field/mode.do?listId&moveMode(walk|transit|car) : 03-2 소요시간 카드에서 이동수단 바꾸면 저장
	@PostMapping(value = "api/field/mode.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> mode(@RequestParam("listId") long listId, @RequestParam("moveMode") String moveMode, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		boolean ok = fieldService.updateMoveMode(m.getUserId(), m.isGuUser() ? m.getSggCd() : null, listId, moveMode);
		Map<String, Object> out = new HashMap<>();
		out.put("ok", ok);
		out.put("moveMode", FieldService.moveModeOf(moveMode));
		return out;
	}

	// GET api/field/schedule.do?start&end : 현장확인 리스트 이번 주 달력 (풀캘린더가 보이는 범위를 붙여서 부름). 내 일정만
	@GetMapping(value = "api/field/schedule.do", produces = "application/json;charset=UTF-8")
	public List<ScheduleEventTO> schedule(@RequestParam("start") String start, @RequestParam("end") String end, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		return scheduleService.myEvents(m.getUserId(), start, end);
	}

	// POST api/field/schedule/add.do?surveyId&date(yyyy-MM-dd) : 리스트를 달력 날짜에 끌어다 놓음
	@PostMapping(value = "api/field/schedule/add.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> scheduleAdd(@RequestParam("surveyId") long surveyId, @RequestParam("date") String date, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		return result(() -> scheduleService.add(m.getUserId(), m.isGuUser() ? m.getSggCd() : null, surveyId, date));
	}

	// POST api/field/schedule/move.do?scheduleId&date : 달력에서 일정을 다른 날로 끌어 옮김
	@PostMapping(value = "api/field/schedule/move.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> scheduleMove(@RequestParam("scheduleId") long scheduleId, @RequestParam("date") String date, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		return result(() -> scheduleService.move(m.getUserId(), scheduleId, date));
	}

	// POST api/field/schedule/delete.do?scheduleId : 달력에서 × 로 뺌
	@PostMapping(value = "api/field/schedule/delete.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> scheduleDelete(@RequestParam("scheduleId") long scheduleId, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		return result(() -> scheduleService.delete(m.getUserId(), scheduleId));
	}

	// GET api/field/recommend.do?gu&perDay&exceptBldIds=.. : 하루치 방문 묶음 추천 (보관함 안에서, K-means + 우선순위)
	//  exceptBldIds = 지금 편집 중인 리스트에 이미 담긴 건물. 실패하면 {ok:false, msg}
	@GetMapping(value = "api/field/recommend.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> recommend(@RequestParam(value = "gu", required = false) String gu,
			@RequestParam(value = "perDay", defaultValue = "6") int perDay,
			@RequestParam(value = "exceptBldIds", required = false) String[] exceptBldIds, HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		try {
			Map<String, Object> out = fieldService.recommend(m.getUserId(), m.isGuUser() ? m.getSggCd() : null, gu, perDay,
					exceptBldIds == null ? null : new HashSet<>(Arrays.asList(exceptBldIds)));
			out.put("ok", true);
			return out;
		} catch (IllegalArgumentException e) {
			Map<String, Object> out = new HashMap<>();
			out.put("ok", false);
			out.put("msg", e.getMessage());
			return out;
		}
	}

	// 달력 저장 응답 : 성공 {ok:true}, 막힘 {ok:false, msg:"이유"}
	private Map<String, Object> result(Runnable job) {
		Map<String, Object> out = new HashMap<>();
		try {
			job.run();
			out.put("ok", true);
		} catch (BadRequestException e) {
			out.put("ok", false);
			out.put("msg", e.getMessage());
		}
		return out;
	}
}
