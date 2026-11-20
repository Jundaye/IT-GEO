package itgeo.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpSession;
import itgeo.common.BatchException;
import itgeo.service.DataUpdateService;
import itgeo.to.MemberTO;

/**
 * 데이터 업데이트 JSON API (서울시청 계정 전용. 화면은 DataUpdateController)
 *  api/dataupdate/run.do     POST  시작              ?full
 *  api/dataupdate/job.do     GET   진행 상황          ?id
 *  api/dataupdate/latest.do  GET   마지막 실행 결과
 * 배치 서버 호출은 DataUpdateService. 호출이 실패하면 상태 코드는 그대로 두고 {"status":"error","message":...} 로 돌려준다.
 */
@RestController
public class DataUpdateRESTController {

	@Autowired
	DataUpdateService dataUpdateService;

	private void checkCity(HttpSession session) {
		dataUpdateService.assertCity((MemberTO) session.getAttribute("loginMember"));
	}

	@PostMapping(value = "api/dataupdate/run.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> run(@RequestParam(value = "full", defaultValue = "false") boolean full, HttpSession session) {
		checkCity(session);
		return dataUpdateService.run(full);
	}

	@GetMapping(value = "api/dataupdate/job.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> job(@RequestParam("id") String id, HttpSession session) {
		checkCity(session);
		return dataUpdateService.job(id);
	}

	@GetMapping(value = "api/dataupdate/latest.do", produces = "application/json;charset=UTF-8")
	public Map<String, Object> latest(HttpSession session) {
		checkCity(session);
		return dataUpdateService.latest();
	}

	/** 배치 서버 호출 실패 : HTML 오류 페이지 대신 JSON (상태 코드는 원래 것) */
	@ExceptionHandler(BatchException.class)
	public ResponseEntity<Map<String, Object>> batchError(BatchException e) {
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("status", "error");
		body.put("message", e.getMessage());
		return ResponseEntity.status(e.getStatus()).body(body);
	}
}
