package itgeo.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.servlet.http.HttpSession;
import itgeo.service.FieldService;
import itgeo.to.FieldCheckTO;
import itgeo.to.FieldListTO;
import itgeo.to.MemberTO;

// 현장확인 리스트 (Figma 03 목록 / 03-1 수정 / 03-2 상세)
@Controller
public class FieldController {

	@Value("${app.naver.client-id}")
	String naverClientId;

	@Autowired
	FieldService fieldService;

	// GET fieldlist.do?tab=doing|done&q&open=listId
	// 현장 확인 리스트 + 상세(지도 · 경로 · 소요시간 · 확인 완료)를 한 화면에. open = 처음 펼칠 리스트 (없으면 첫 리스트)
	@GetMapping("fieldlist.do")
	public String list(@RequestParam(value = "tab", required = false) String tabParam,
			@RequestParam(value = "q", required = false) String q,
			@RequestParam(value = "open", required = false) Long open, HttpSession session, Model model) {
		long userId = userId(session);
		String gu = userGu(session);
		String tab = "done".equals(tabParam) ? "done" : "doing";
		List<FieldListTO> list = fieldService.getFieldList(userId, gu, tab, q, true);
		/* open 으로 왔는데 (예전 상세 주소 · 확인 완료 · 저장 후) 이 탭에 없고 탭을 직접 고르지 않았으면 다른 탭에서 찾는다 */
		if (open != null && tabParam == null && list.stream().noneMatch(f -> f.getSurveyId() == open)) {
			String other = "done".equals(tab) ? "doing" : "done";
			List<FieldListTO> o = fieldService.getFieldList(userId, gu, other, q, true);
			if (o.stream().anyMatch(f -> f.getSurveyId() == open)) { tab = other; list = o; }
		}
		model.addAttribute("tab", tab);
		model.addAttribute("q", q);
		model.addAttribute("openId", open);
		model.addAttribute("list", list);
		model.addAttribute("doingCnt", fieldService.getFieldList(userId, gu, "doing", null, false).size());
		model.addAttribute("doneCnt", fieldService.getFieldList(userId, gu, "done", null, false).size());
		model.addAttribute("naverClientId", naverClientId);
		return "fieldlist";
	}

	// GET calendar.do : 업무 캘린더 (진행중 + 완료 리스트 정보를 달력 화면에 넘긴다)
	@GetMapping("calendar.do")
	public String calendar(HttpSession session, Model model) {
		long userId = userId(session);
		String gu = userGu(session);
		List<Object> lists = new ArrayList<>();
		lists.addAll(fieldService.getFieldList(userId, gu, "doing", null, true));
		lists.addAll(fieldService.getFieldList(userId, gu, "done", null, true));
		model.addAttribute("lists", lists);
		return "calendar";
	}

	// GET fieldedit.do?listId (0 = 새 리스트)
	@GetMapping("fieldedit.do")
	public String editForm(@RequestParam(value = "listId", defaultValue = "0") long listId, HttpSession session, Model model) {
		long userId = userId(session);
		FieldListTO field = listId > 0 ? fieldService.getFieldDetail(listId, userId, userGu(session)) : null;
		if (field == null) {
			field = new FieldListTO();
			field.setTitle("");
			field.setSggCd(userGu(session));        // 구청 계정 : 새 리스트도 소속 구 (출발지 기본값)
		}
		model.addAttribute("field", field);
		model.addAttribute("pickList", fieldService.getPickList(userId, userGu(session)));
		model.addAttribute("naverClientId", naverClientId);
		return "fieldedit";
	}

	// POST fielddelete.do : listId → redirect:fieldlist.do (확인 기록이 있는 리스트는 남긴다)
	@PostMapping("fielddelete.do")
	public String delete(@RequestParam("listId") long listId, @RequestParam(value = "tab", defaultValue = "doing") String tab,
			HttpSession session, RedirectAttributes ra) {
		int r = fieldService.deleteField(userId(session), userGu(session), listId);
		ra.addFlashAttribute("msg", r > 0 ? "리스트를 삭제했습니다."
				: r < 0 ? "현장 확인 기록이 있는 리스트는 삭제할 수 없습니다. (건물 확인 이력을 지키기 위해)" : "리스트를 찾을 수 없습니다.");
		return "redirect:fieldlist.do?tab=" + ("done".equals(tab) ? "done" : "doing");
	}

	// POST fieldedit.do : listId, title, moveMode(walk/transit/car), factorIds(순서대로) → redirect:fieldlist.do?open=
	@PostMapping("fieldedit.do")
	public String edit(@RequestParam(value = "listId", defaultValue = "0") long listId,
			@RequestParam(value = "title", required = false) String title,   // 비우면 "새 리스트N" (FieldService.nextAutoTitle)
			@RequestParam(value = "moveMode", required = false) String moveMode,
			@RequestParam(value = "factorIds", required = false) List<Long> factorIds,
			HttpSession session, RedirectAttributes ra) {
		long id;
		try {
			id = fieldService.saveField(userId(session), userGu(session), listId, title, moveMode, factorIds);
		} catch (IllegalArgumentException e) {                  // 한 리스트 = 한 구
			ra.addFlashAttribute("msg", e.getMessage());
			return "redirect:fieldedit.do?listId=" + listId;
		}
		if (id == 0) return "redirect:fieldlist.do";
		ra.addFlashAttribute("msg", "변경 내용을 저장했습니다.");
		return "redirect:fieldlist.do?open=" + id;
	}

	// POST fieldadditems.do : listId(0이면 새 리스트), factorIds → redirect:fieldedit.do
	@PostMapping("fieldadditems.do")
	public String addItems(@RequestParam(value = "listId", defaultValue = "0") long listId,
			@RequestParam(value = "factorIds", required = false) List<Long> factorIds,
			HttpSession session, RedirectAttributes ra) {
		if (factorIds == null || factorIds.isEmpty()) {
			ra.addFlashAttribute("msg", "선택한 건물이 없습니다.");
			return "redirect:archive.do";
		}
		long id;
		try {
			id = fieldService.addItems(userId(session), userGu(session), listId, factorIds);
		} catch (IllegalArgumentException e) {                  // 한 리스트 = 한 구
			ra.addFlashAttribute("msg", e.getMessage());
			return "redirect:archive.do";
		}
		if (id == 0) return "redirect:archive.do";
		ra.addFlashAttribute("msg", factorIds.size() + "건을 현장확인 리스트에 담았습니다.");
		return "redirect:fieldedit.do?listId=" + id;
	}

	// GET fielddetail.do?listId : 상세는 현장 확인 리스트 화면에 합쳐졌다. 예전 주소(대시보드 [상세보기] 등)는 그 리스트를 펼친 목록으로 보낸다
	@GetMapping("fielddetail.do")
	public String detail(@RequestParam("listId") long listId) {
		return "redirect:fieldlist.do?open=" + listId;
	}

	// POST fieldcheck.do : planId, listId, result, content → 확인 완료 (원래 앱 기능, 웹 버튼으로 대체)
	@PostMapping("fieldcheck.do")
	public String check(@RequestParam("planId") long planId, @RequestParam("listId") long listId,
			@RequestParam(value = "result", defaultValue = "MET") String result,
			@RequestParam(value = "content", required = false) String content,
			HttpSession session, RedirectAttributes ra) {
		FieldCheckTO c = new FieldCheckTO();
		c.setPlanId(planId);
		c.setSurveyId(listId);
		c.setUserId(userId(session));
		c.setResult(result);
		if (content != null && !content.isBlank()) c.setContent(content.trim());
		ra.addFlashAttribute("msg", fieldService.checkItem(c) ? "확인 완료로 처리했습니다." : "처리하지 못했습니다.");
		return "redirect:fieldlist.do?open=" + listId;
	}

	private static long userId(HttpSession session) {
		return ((MemberTO) session.getAttribute("loginMember")).getUserId();
	}

	/** 구청 계정이면 소속 구 코드, 서울시청이면 null (= 전체) */
	private static String userGu(HttpSession session) {
		MemberTO m = (MemberTO) session.getAttribute("loginMember");
		return m.isGuUser() ? m.getSggCd() : null;
	}
}
