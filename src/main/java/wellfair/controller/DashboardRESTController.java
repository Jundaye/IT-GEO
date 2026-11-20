package wellfair.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import wellfair.service.DashboardService;
import wellfair.service.ReportService;
import wellfair.service.WeatherService;
import wellfair.to.DashboardTO;
import wellfair.to.MemberTO;
import wellfair.to.ScheduleTO;
import wellfair.to.WeatherTO;

// 대시보드 JSON API (dashboard.js · common.js 가 부른다). 로그인 검사는 LoginInterceptor (api/ → 401 JSON)
@RestController
public class DashboardRESTController {

	@Autowired
	DashboardService dashboardService;

	@Autowired
	WeatherService weatherService;

	@Autowired
	ReportService reportService;

	private static MemberTO me(HttpSession s) {
		return (MemberTO) s.getAttribute("loginMember");
	}

	/** GET api/dashboard/weather.do : 현재 기온 · 특보 · 기준 시각 (소속 구 격자, 서울시청 계정은 서울) */
	@GetMapping(value = "api/dashboard/weather.do", produces = "application/json;charset=UTF-8")
	public WeatherTO weather(HttpSession session) {
		MemberTO m = me(session);
		return weatherService.getWeather(m.isGuUser() ? m.getSggCd() : null);
	}

	/** GET api/dashboard/schedule.do?date=yyyy-MM-dd : 그날 일정 (읽기 전용) */
	@GetMapping(value = "api/dashboard/schedule.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> schedule(@RequestParam(value = "date", required = false) String date, HttpSession session) {
		List<ScheduleTO> list = dashboardService.getSchedules(me(session), date);
		Map<String, Object> out = new HashMap<>();
		out.put("ok", true);
		out.put("list", list);
		return out;
	}

	/** GET api/dashboard/schedule-marks.do?ym=yyyy-MM : 달력 날짜별 일정 수 */
	@GetMapping(value = "api/dashboard/schedule-marks.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> marks(@RequestParam(value = "ym", required = false) String ym, HttpSession session) {
		List<DashboardTO.DayMarkTO> list = dashboardService.getScheduleMarks(me(session), ym);
		Map<String, Object> out = new HashMap<>();
		out.put("ok", true);
		out.put("list", list);
		return out;
	}

	/** POST api/dashboard/recent.do?menu&url&title&sub : 이어서 보기 기록 (common.js 가 지도 · 현장 확인 리스트 · 보관함 화면을 열 때) */
	@PostMapping(value = "api/dashboard/recent.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> recent(@RequestParam("menu") String menu, @RequestParam("url") String url,
			@RequestParam(value = "title", required = false) String title,
			@RequestParam(value = "sub", required = false) String sub, HttpSession session) {
		dashboardService.saveRecent(me(session), menu, url, title, sub);
		return Map.of("ok", true);
	}

	/** POST api/dashboard/report.do : 이번 달 현황 보고서 (숫자 = 시스템, 종합 의견 = LLM) → {ok, reportId, month, facts, opinion[], aiOk} */
	@PostMapping(value = "api/dashboard/report.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> report(HttpSession session) {
		return reportService.writeMonthlyReport(me(session));
	}

	/** GET api/dashboard/report-view.do?id= : 지난 보고서 다시 보기 (내 보고서만) */
	@GetMapping(value = "api/dashboard/report-view.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> reportView(@RequestParam("id") long id, HttpSession session) {
		return reportService.readReport(me(session), id);
	}
}
