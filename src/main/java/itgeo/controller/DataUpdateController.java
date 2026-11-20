package itgeo.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpSession;
import itgeo.service.DataUpdateService;
import itgeo.to.MemberTO;

/**
 * 데이터 업데이트 화면 (서울시청 계정 전용) : dataupdate.do
 * JSON API(api/dataupdate/*.do)는 DataUpdateRESTController, 배치 서버 호출은 DataUpdateService.
 * .do 로 끝나서 LoginInterceptor 가 로그인 검사를 하고, 구청 계정은 assertCity 에서 403.
 */
@Controller
public class DataUpdateController {

	@Autowired
	DataUpdateService dataUpdateService;

	@GetMapping("dataupdate.do")
	public String page(HttpSession session) {
		dataUpdateService.assertCity((MemberTO) session.getAttribute("loginMember"));
		return "dataupdate";
	}
}
