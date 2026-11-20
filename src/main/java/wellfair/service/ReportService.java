package wellfair.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import wellfair.to.DashboardTO;
import wellfair.to.MemberTO;
import wellfair.to.ReportFactsTO;
import wellfair.to.WeatherTO;

/**
 * 대시보드 "보고서 만들기" : 월간 현황 보고서.
 *  - 1~4번 숫자(ReportFactsTO)는 시스템이 대시보드와 같은 기준으로 채운다. AI 는 숫자를 만들거나 고치지 않는다.
 *  - 5번 종합 의견만 Claude(LLM)가 그 숫자를 읽고 3~4문장으로 쓴다.
 *  - {"facts":…, "opinion":[…]} 를 monthly_report 에 저장 → "지난 보고서 보기" 때 같은 양식으로 다시 그린다.
 * 키 = app.properties 의 app.llm.api-key (비어 있으면 환경 변수 ANTHROPIC_API_KEY). 키가 없거나 호출이 실패해도 숫자 보고서는 만든다.
 * 거절(refusal) 시 서버가 다른 모델로 다시 시도하도록 fallbacks: "default" 를 켜 둔다.
 */
@Service
public class ReportService {

	private static final Logger log = LoggerFactory.getLogger(ReportService.class);
	/** 계산용 getter(remain · schedPct …)도 JSON 에 들어가므로 다시 읽을 때는 모르는 항목을 무시 */
	private static final ObjectMapper JSON = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
	private static final String MODEL = "claude-opus-5-5";
	private static final int MAX_LINES = 5;

	private static final String SYSTEM = "당신은 서울시 구청 복지정책과 주무관이 쓰는 '월간 고위험 가구 현장 확인 현황 보고'(구청 내부 보고 문서)의 "
			+ "마지막 항목인 '종합 의견'만 쓰는 도우미입니다.\n"
			+ "규칙:\n"
			+ "- 주어진 숫자만 사용하고, 없는 사실이나 숫자를 지어내지 마세요. 비율 · 차이처럼 주어진 숫자로 바로 계산되는 값은 써도 됩니다.\n"
			+ "- 공공기관 내부 보고서의 개조식으로 쓰세요. 각 줄은 명사형 종결(~함, ~임, ~필요, ~예정)로 끝내고, '~습니다' · '~해요' 체는 쓰지 마세요.\n"
			+ "  예) - 11월 현장 확인 44곳으로 전월 대비 6곳 증가함\n"
			+ "      - 가리봉동 확인율 18%(9/49)로 가장 낮아 별도 방문 계획 수립 필요\n"
			+ "- 3~4줄, 줄 맨 앞에 '- ' 를 붙이고 한 줄에 한 가지 내용만 쓰세요. 제목 · 머리말 · 맺음말 · 마크다운 강조는 쓰지 마세요.\n"
			+ "- 첫 줄은 이번 달 실적 요약, 다음 줄들은 남은 고위험 가구가 몰린 동 · 확인율이 낮은 동 · 남은 일정에 대한 다음 달 조치 계획으로 쓰세요.\n"
			+ "- 기상 특보가 주어졌을 때만 방문 시 유의 사항을 한 줄 넣을 수 있습니다.";

	@Value("${app.llm.api-key:}")
	String apiKey;

	@Autowired
	DashboardService dashboardService;

	@Autowired
	WeatherService weatherService;

	private volatile AnthropicClient client;

	/** → {ok, reportId, ym, month, createdAt, facts, opinion[], aiOk, msg?} */
	public Map<String, Object> writeMonthlyReport(MemberTO member) {
		ReportFactsTO f = dashboardService.getReportFacts(member, null);
		try {
			WeatherTO w = weatherService.getWeather(member.isGuUser() ? member.getSggCd() : null);
			if (w != null && w.getWarnings() != null) f.setWarnings(w.getWarnings());
		} catch (RuntimeException e) {
			log.debug("특보 조회 실패 (보고서는 계속) : {}", e.toString());
		}

		List<String> opinion = new ArrayList<>();
		String aiMsg = askOpinion(f, opinion);

		Map<String, Object> saved = new HashMap<>();
		saved.put("facts", f);
		saved.put("opinion", opinion);
		long id;
		try {
			id = dashboardService.addReport(member, f.getYm(), JSON.writeValueAsString(saved));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException(e);
		}
		DashboardTO.ReportRefTO ref = dashboardService.getLastReport(member);

		Map<String, Object> out = new HashMap<>(saved);
		out.put("ok", true);
		out.put("reportId", id);
		out.put("ym", f.getYm());
		out.put("month", f.getMonth());
		out.put("createdAt", ref != null && ref.getReportId() == id ? ref.getCreatedAt() : null);
		out.put("aiOk", aiMsg == null);
		if (aiMsg != null) out.put("msg", aiMsg);
		return out;
	}

	/** 저장한 보고서 다시 보기 → {ok, reportId, facts, opinion[]} (예전 글자만 저장된 보고서는 facts 없이 opinion 에 본문) */
	public Map<String, Object> readReport(MemberTO member, long reportId) {
		String content = dashboardService.getReportContent(member, reportId);
		if (content == null) return Map.of("ok", false, "msg", "보고서를 찾을 수 없습니다.");
		Map<String, Object> out = new HashMap<>();
		out.put("ok", true);
		out.put("reportId", reportId);
		try {
			JsonNode n = JSON.readTree(content);
			if (n != null && n.has("facts")) {
				out.put("facts", JSON.treeToValue(n.get("facts"), ReportFactsTO.class));
				List<String> op = new ArrayList<>();
				n.path("opinion").forEach(x -> op.add(x.asText()));
				out.put("opinion", op);
				return out;
			}
		} catch (JsonProcessingException | IllegalArgumentException e) {
			// 예전 형식 (글자만)
		}
		out.put("facts", null);
		out.put("opinion", List.of(content));
		return out;
	}

	/** 종합 의견을 opinion 에 채운다. 실패하면 화면에 보여 줄 이유를 돌려준다 (성공 = null) */
	private String askOpinion(ReportFactsTO f, List<String> opinion) {
		AnthropicClient c = client();
		if (c == null) return "LLM API 키(app.llm.api-key)가 설정되지 않아 종합 의견은 비워 둠";
		MessageCreateParams params = MessageCreateParams.builder()
				.model(MODEL)
				.maxTokens(4000L)
				.outputConfig(OutputConfig.builder().effort(OutputConfig.Effort.MEDIUM).build())
				.system(SYSTEM)
				.addUserMessage(facts(f))
				.putAdditionalHeader("anthropic-beta", "server-side-fallback-2026-07-01")
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
				.build();
		try {
			Message res = c.messages().create(params);
			if (res.stopReason().map(StopReason.REFUSAL::equals).orElse(false)) {
				return "종합 의견을 만들지 못했습니다. 다시 시도해 주세요.";
			}
			StringBuilder sb = new StringBuilder();
			res.content().forEach(b -> b.text().ifPresent(t -> sb.append(t.text())));
			for (String line : sb.toString().split("\\r?\\n")) {
				String t = line.replaceFirst("^\\s*(?:[-*•·]|\\d+[.)])\\s*", "").replace("**", "").trim();
				if (!t.isEmpty() && opinion.size() < MAX_LINES) opinion.add(t);
			}
			return opinion.isEmpty() ? "종합 의견이 비어 있습니다. 다시 시도해 주세요." : null;
		} catch (RateLimitException e) {
			log.warn("LLM 요청 한도 : {}", e.getMessage());
			return "요청이 많아 종합 의견을 만들지 못했습니다. 잠시 후 다시 시도해 주세요.";
		} catch (AnthropicServiceException e) {
			log.warn("LLM 호출 실패 : {} {}", e.statusCode(), e.getMessage());
			return "종합 의견 작성 서비스 연결에 실패했습니다. (" + e.statusCode() + ")";
		} catch (RuntimeException e) {
			log.warn("LLM 호출 실패 : {}", e.toString());
			return "종합 의견 작성 서비스 연결에 실패했습니다.";
		}
	}

	/** LLM 에 넘길 숫자 (보고서 1~4번과 같은 값) */
	private static String facts(ReportFactsTO f) {
		StringBuilder sb = new StringBuilder();
		sb.append("[보고] ").append(f.getYear()).append("년 ").append(f.getMonth()).append("월 · ").append(f.getArea())
				.append(" · 기간 ").append(f.getPeriodFrom()).append(" ~ ").append(f.getPeriodTo()).append('\n');
		sb.append("[1. 고위험 가구 확인 (누적)] 전체 ").append(f.getHighTotal()).append("가구, 확인 ").append(f.getHighChecked())
				.append("가구, 남은 곳 ").append(f.getHighRemain()).append("가구, 확인율 ").append(f.getHighPct())
				.append("% (이번 달 1일 기준 ").append(f.getPrevPct()).append("%)\n");
		sb.append("[2. 남은 고위험 가구 많은 동 (남은 곳 / 확인 / 전체)]\n");
		for (DashboardTO.DongRiskTO r : f.getTopDong()) {
			sb.append("- ").append(r.getEmdNm()).append(" : ").append(r.getRemain()).append(" / ").append(r.getChecked())
					.append(" / ").append(r.getTotal()).append('\n');
		}
		sb.append("[3. 월별 현장 확인 완료 (건물 수)]\n");
		for (DashboardTO.MonthStatTO s : f.getMonthly()) {
			sb.append("- ").append(s.getYm()).append(" : ").append(s.getChecked()).append('\n');
		}
		sb.append("[4. 이번 달 방문 일정] 전체 ").append(f.getSchedTotal()).append("건, 진행 ").append(f.getSchedDone())
				.append("건, 남은 일정 ").append(f.getSchedRemain()).append("건\n");
		if (f.getWarnings() != null && !f.getWarnings().isEmpty()) {
			sb.append("[참고 : 현재 기상 특보] ").append(String.join(", ", f.getWarnings())).append('\n');
		}
		sb.append("\n위 숫자로 '5. 종합 의견'만 써 주세요.");
		return sb.toString();
	}

	private AnthropicClient client() {
		if (client != null) return client;
		synchronized (this) {
			if (client != null) return client;
			String k = apiKey != null && !apiKey.isBlank() ? apiKey.trim() : System.getenv("ANTHROPIC_API_KEY");
			if (k == null || k.isBlank()) return null;
			client = AnthropicOkHttpClient.builder().apiKey(k).build();
			return client;
		}
	}
}
