package itgeo.service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * LLM 호출 (OpenAI 호환 chat/completions). AI 현장 확인보고서 · 대시보드 월간 보고서가 쓴다.
 * 설정 (app.properties, 값은 각자 환경에 맞게)
 *   app.llm.base-url    예) https://api.openai.com/v1  (끝의 /chat/completions 는 붙이지 않는다)
 *   app.llm.api-key     Bearer 키 (로컬 서버처럼 키가 필요 없으면 비워 둔다)
 *   app.llm.model       모델 이름
 *   app.llm.timeout-sec 응답 대기 초 (기본 90)
 *   app.llm.vision      현장 사진도 같이 보낼지 (기본 true). 모델이 이미지를 못 받으면 글자만 다시 보낸다
 *   app.llm.max-photos  보고서 한 번에 보낼 사진 수 (기본 8, 최근 기록 사진부터)
 * 개인정보 보호 : 보내기 전에 전화번호 · 주민등록번호 모양의 숫자를 가린다 (mask).
 */
@Service
public class LlmService {

	private static final Logger log = LoggerFactory.getLogger(LlmService.class);
	private static final ObjectMapper JSON = new ObjectMapper();

	private static final Pattern PHONE = Pattern.compile("(01[016789]|0\\d{1,2})[-.\\s]?\\d{3,4}[-.\\s]?\\d{4}");
	private static final Pattern RRN = Pattern.compile("\\d{6}[-\\s]?[1-8]\\d{6}");

	@Value("${app.llm.base-url:}")
	String baseUrl;

	@Value("${app.llm.api-key:}")
	String apiKey;

	@Value("${app.llm.model:}")
	String model;

	@Value("${app.llm.timeout-sec:90}")
	int timeoutSec;

	@Value("${app.llm.vision:true}")
	boolean vision;

	@Value("${app.llm.max-photos:8}")
	int maxPhotos;

	/** LLM 에 같이 보낼 사진 1장. label = 프롬프트에서 부를 이름 ("사진 1 · (3) 2026-11-20 기록 · 욕실"), dataUrl = data:image/jpeg;base64,... */
	public record Image(String label, String dataUrl) {}

	private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();

	/** 설정이 되어 있는지 (화면 안내용) */
	public boolean isConfigured() {
		return baseUrl != null && !baseUrl.isBlank() && model != null && !model.isBlank();
	}

	/**
	 * system · user 프롬프트 → 응답 본문. 설정이 없거나 호출이 실패하면 IllegalStateException (화면에는 400 + 문구).
	 * temperature 는 낮게 (공문서 문체, 같은 입력이면 비슷한 결과)
	 */
	public String chat(String system, String user) {
		return chat(system, user, null);
	}

	/**
	 * 사진을 같이 보내는 chat (OpenAI 호환 멀티모달 : content 배열 + image_url). 사진이 없거나 app.llm.vision=false 면 글자만.
	 * 모델이 이미지를 받지 못해 4xx 가 오면 사진 없이 한 번 더 보낸다 (보고서는 사진 분석 없이라도 나오게)
	 */
	public String chat(String system, String user, List<Image> images) {
		if (!isConfigured()) throw new IllegalStateException("AI 설정이 없습니다. app.properties 의 app.llm.base-url · app.llm.model 을 확인해 주세요.");
		if (images != null && !images.isEmpty() && vision) {
			List<Map<String, Object>> content = new ArrayList<>();
			content.add(Map.of("type", "text", "text", mask(user)));
			for (Image im : images) {
				content.add(Map.of("type", "text", "text", "[" + im.label() + "]"));
				content.add(Map.of("type", "image_url", "image_url", Map.of("url", im.dataUrl(), "detail", "low")));
			}
			try {
				return send(system, content);
			} catch (LlmHttpException e) {
				if (e.status / 100 != 4) throw e;
				log.warn("LLM rejected images (HTTP {}), retry without photos", e.status);
				return send(system, mask(user) + "\n\n(참고: 현장 사진 " + images.size() + "장은 AI 모델이 이미지를 받지 못해 보내지 못했다. 사진 내용은 쓰지 않는다.)");
			}
		}
		return send(system, mask(user));
	}

	/** 사진을 같이 보낼 수 있는 설정인지 */
	public boolean isVisionEnabled() {
		return isConfigured() && vision;
	}

	/** 보고서 한 번에 보낼 사진 수 (0 ~ 20) */
	public int getMaxPhotos() {
		return Math.max(0, Math.min(20, maxPhotos));
	}

	/** userContent = 글자(String) 또는 멀티모달 content 배열 */
	private String send(String system, Object userContent) {
		String url = baseUrl.trim().replaceAll("/+$", "") + "/chat/completions";
		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", model.trim());
		List<Map<String, Object>> msgs = new ArrayList<>();
		msgs.add(Map.of("role", "system", "content", system));
		msgs.add(Map.of("role", "user", "content", userContent));
		body.put("messages", msgs);
		body.put("temperature", 0.2);

		HttpResponse<String> res;
		try {
			HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
					.timeout(Duration.ofSeconds(Math.max(10, timeoutSec)))
					.header("Content-Type", "application/json; charset=UTF-8")
					.POST(HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body), StandardCharsets.UTF_8));
			if (apiKey != null && !apiKey.isBlank()) rb.header("Authorization", "Bearer " + apiKey.trim());
			res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("AI 응답을 기다리다 멈췄습니다. 다시 시도해 주세요.");
		} catch (Exception e) {
			log.warn("LLM call failed : {}", e.toString());
			throw new IllegalStateException("AI 서버에 연결하지 못했습니다. 잠시 뒤 다시 시도해 주세요.");
		}
		if (res.statusCode() / 100 != 2) {
			log.warn("LLM HTTP {} : {}", res.statusCode(), cut(res.body()));
			throw new LlmHttpException(res.statusCode());
		}
		try {
			JsonNode n = JSON.readTree(res.body()).path("choices").path(0).path("message").path("content");
			String text = n.isMissingNode() || n.isNull() ? null : n.asText();
			if (text == null || text.isBlank()) throw new IllegalStateException("AI 응답이 비어 있습니다. 다시 시도해 주세요.");
			return text.trim();
		} catch (IllegalStateException e) {
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("AI 응답을 읽지 못했습니다. 다시 시도해 주세요.");
		}
	}

	private static String cut(String s) {
		return s == null ? "" : s.substring(0, Math.min(300, s.length()));
	}

	/** chat/completions 가 2xx 가 아닐 때 (사진 거부 → 글자만 다시 보내기 판단용). 화면 문구는 기존과 같다 */
	static class LlmHttpException extends IllegalStateException {
		private static final long serialVersionUID = 1L;
		final int status;

		LlmHttpException(int status) {
			super("AI 서버가 요청을 처리하지 못했습니다. (HTTP " + status + ")");
			this.status = status;
		}
	}

	/** 전화번호 · 주민등록번호 모양 숫자 가리기 (외부로 보내는 글에만) */
	static String mask(String s) {
		if (s == null) return "";
		String out = RRN.matcher(s).replaceAll("[주민번호 가림]");
		return PHONE.matcher(out).replaceAll("[전화번호 가림]");
	}

	/** 응답에서 JSON 객체 부분만 ({ … }). 코드 블록(```json)으로 감싸 와도 읽는다. 없으면 null */
	static JsonNode extractJson(String text) {
		if (text == null) return null;
		int a = text.indexOf('{'), b = text.lastIndexOf('}');
		if (a < 0 || b <= a) return null;
		try {
			return JSON.readTree(text.substring(a, b + 1));
		} catch (Exception e) {
			return null;
		}
	}
}
