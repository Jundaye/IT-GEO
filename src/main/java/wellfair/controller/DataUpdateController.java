package wellfair.controller;

import java.util.Collections;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import jakarta.servlet.http.HttpSession;
import wellfair.common.ForbiddenException;
import wellfair.to.MemberTO;

/**
 * 데이터 업데이트 (서울시청 계정 전용).
 *  - 화면  : dataupdate.do
 *  - API   : api/dataupdate/run.do (POST, 시작) · api/dataupdate/job.do?id= (GET, 진행 상황)
 * 실제 작업은 Python 배치 서버(FastAPI, pipeline/api.py)가 한다. 여기는 전달만.
 * .do 로 끝나서 LoginInterceptor 가 로그인 검사를 하고, 구청 계정은 아래 checkCity 에서 403.
 */
@Controller
public class DataUpdateController {

	private static final String PY = "http://localhost:8000";   // 배치 서버 주소
	private final RestTemplate rest = new RestTemplate();

	/** 3개 구 데이터를 한꺼번에 바꾸는 작업이라 서울시청 계정(sggCd 없음)만 */
	private static void checkCity(HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		if (m == null || m.isGuUser()) throw new ForbiddenException("데이터 업데이트는 서울시청 계정만 할 수 있습니다.");
	}

	@GetMapping("dataupdate.do")
	public String page(HttpSession session) {
		checkCity(session);
		return "dataupdate";
	}

	@PostMapping(value = "api/dataupdate/run.do", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public ResponseEntity<?> run(@RequestParam(value = "full", defaultValue = "false") boolean full, HttpSession session) {
		checkCity(session);
		try {
			// full=false : 바뀐 원천만 다시 계산 (침수흔적도만 바뀌면 침수 단계만) / full=true : 12단계 전부
			return ResponseEntity.ok(rest.postForObject(PY + "/pipeline/run?full={full}", null, Map.class, full));
		} catch (HttpClientErrorException.Conflict e) {
			return ResponseEntity.status(409).body(Collections.singletonMap("msg", "이미 실행 중인 작업이 있습니다."));
		} catch (ResourceAccessException e) {
			return ResponseEntity.status(503).body(Collections.singletonMap("msg", "배치 서버(8000)가 꺼져 있습니다."));
		}
	}

	@GetMapping(value = "api/dataupdate/job.do", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public ResponseEntity<?> job(@RequestParam("id") String id, HttpSession session) {
		checkCity(session);
		try {
			// 배치 서버 응답을 그대로 넘긴다 (feed · report.diff 가 생겨도 그대로 지나간다)
			return ResponseEntity.ok(rest.getForObject(PY + "/pipeline/jobs/{id}", Map.class, id));
		} catch (HttpClientErrorException.NotFound e) {       // 배치 서버가 다시 켜져 작업 기록이 사라진 경우
			return ResponseEntity.status(404).body(Collections.singletonMap("msg", "작업 기록이 없습니다. 다시 실행해 주세요."));
		} catch (ResourceAccessException e) {
			return ResponseEntity.status(503).body(Collections.singletonMap("msg", "배치 서버(8000)가 꺼져 있습니다."));
		}
	}

	/** 화면 처음 열 때 마지막 실행 결과 */
	@GetMapping(value = "api/dataupdate/latest.do", produces = "application/json;charset=UTF-8")
	@ResponseBody
	public ResponseEntity<?> latest(HttpSession session) {
		checkCity(session);
		try {
			return ResponseEntity.ok(rest.getForObject(PY + "/pipeline/latest", Map.class));
		} catch (Exception e) {
			return ResponseEntity.ok(Collections.emptyMap());
		}
	}
}
