package itgeo.service;

import java.io.File;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.context.ServletContextAware;

import jakarta.servlet.ServletContext;

import itgeo.common.BatchException;
import itgeo.common.ForbiddenException;
import itgeo.dao.DataUpdateDAO;
import itgeo.to.MemberTO;

/**
 * 데이터 업데이트 : Python 배치 서버(FastAPI, pipeline/api.py)를 부른다. 실제 작업은 배치 서버가 하고 여기는 전달만.
 *  - 배치 서버 주소 = app.properties 의 batch.url (없으면 http://localhost:8000)
 *  - 연결 3초 · 읽기 10초 안에 답이 없으면 끊는다 (1초마다 오는 진행 상황 조회가 Tomcat 스레드를 붙잡고 있지 않게)
 *  - 실패는 종류와 상관없이 BatchException(상태 코드, 문구) 하나로 바꾼다 → DataUpdateRESTController 가 JSON 으로 돌려준다
 */
@Service
public class DataUpdateService implements ServletContextAware {

	static final int CONNECT_MS = 3000;
	static final int READ_MS = 10000;

	@Value("${batch.url:http://localhost:8000}")
	String batchUrl;

	private final RestTemplate rest;

	@Autowired
	MapService mapService;

	@Autowired
	DataUpdateDAO dao;

	private volatile String clearedJobId;   // 같은 작업으로 여러 번 비우지 않게

	/* ---------- 최종 업데이트 시각 (2026-11-27) ----------
	   배치 서버 응답에는 시각이 없어서 웹이 직접 기록한다 : 작업이 "DB 반영 완료"(db_update.updated 또는 db_swap.swapped)로 끝난 것을
	   job.do 로 처음 확인한 순간 = 최종 업데이트. {user.home}/.itgeo/data-updated-at.txt 에 남겨 톰캣을 껐다 켜도 유지한다.
	   파일이 없으면 building 테이블을 만든 시각(마지막 전체 반영)을 처음 값으로 쓴다.
	   화면 : 헤더 = "11월 27일 17시" (applicationScope.dataUpdatedHead), 업데이트 모달 = "2026년 11월 27일 17시 05분 32초" (dataUpdatedFull) */
	static final File UPDATED_FILE = new File(System.getProperty("user.home"), ".itgeo/data-updated-at.txt");
	static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
	static final DateTimeFormatter HEAD = DateTimeFormatter.ofPattern("M월 d일 H시");
	static final DateTimeFormatter FULL = DateTimeFormatter.ofPattern("yyyy년 M월 d일 H시 mm분 ss초");
	private ServletContext servletContext;
	private volatile LocalDateTime updatedAt;
	private volatile String recordedJobId;   // 같은 작업으로 두 번 기록하지 않게

	@Override
	public void setServletContext(ServletContext sc) {
		servletContext = sc;
		updatedAt = readUpdatedAt();
		publishUpdatedAt();
	}

	private LocalDateTime readUpdatedAt() {
		try {
			if (UPDATED_FILE.isFile()) return LocalDateTime.parse(Files.readString(UPDATED_FILE.toPath(), StandardCharsets.UTF_8).trim(), ISO);
		} catch (Exception e) { /* 깨진 파일 → 아래 처음 값 */ }
		try {
			String t = dao.getBuildingLoadedAt();
			return t == null ? null : LocalDateTime.parse(t, ISO);
		} catch (Exception e) {
			return null;
		}
	}

	private void publishUpdatedAt() {
		if (servletContext == null) return;
		Map<String, String> v = updatedAtView();
		servletContext.setAttribute("dataUpdatedHead", v.get("updatedHead"));
		servletContext.setAttribute("dataUpdatedFull", v.get("updatedFull"));
	}

	/** {updatedAt "yyyy-MM-dd HH:mm:ss", updatedHead "11월 27일 17시", updatedFull "2026년 11월 27일 17시 05분 32초"} (없으면 값 null) */
	public Map<String, String> updatedAtView() {
		LocalDateTime t = updatedAt;
		Map<String, String> m = new LinkedHashMap<>();
		m.put("updatedAt", t == null ? null : t.format(ISO));
		m.put("updatedHead", t == null ? null : t.format(HEAD));
		m.put("updatedFull", t == null ? null : t.format(FULL));
		return m;
	}

	/** 배치 응답이 "DB 반영 완료" 인지 (dataupdate-modal.js dbChanged · resultOf 의 ok 와 같은 판정) */
	@SuppressWarnings("unchecked")
	static boolean dbUpdated(Map<String, Object> s) {
		if (s == null || "error".equals(String.valueOf(s.get("status")))) return false;
		Object r = s.get("report");
		if (!(r instanceof Map)) return false;
		Map<String, Object> rep = (Map<String, Object>) r;
		Object u = rep.get("db_update"), w = rep.get("db_swap");
		return (u instanceof Map && Boolean.TRUE.equals(((Map<String, Object>) u).get("updated")))
				|| (w instanceof Map && Boolean.TRUE.equals(((Map<String, Object>) w).get("swapped")));
	}

	private void recordUpdated(String jobId) {
		if (jobId.equals(recordedJobId)) return;
		recordedJobId = jobId;
		updatedAt = LocalDateTime.now().withNano(0);
		try {
			UPDATED_FILE.getParentFile().mkdirs();
			Files.writeString(UPDATED_FILE.toPath(), updatedAt.format(ISO), StandardCharsets.UTF_8);
		} catch (Exception e) { /* 파일을 못 써도 화면 값은 바뀐다 (톰캣을 다시 켜면 처음 값으로) */ }
		publishUpdatedAt();
	}

	public DataUpdateService() {
		SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
		f.setConnectTimeout(CONNECT_MS);
		f.setReadTimeout(READ_MS);
		rest = new RestTemplate(f);
	}

	/** 3개 구 데이터를 한꺼번에 바꾸는 작업이라 서울시청 계정(sggCd 없음)만 */
	public void assertCity(MemberTO m) {
		if (m == null || m.isGuUser()) throw new ForbiddenException("데이터 업데이트는 서울시청 계정만 할 수 있습니다.");
	}

	/** 시작. full=false : 바뀐 원천만 다시 계산 (침수흔적도만 바뀌면 침수 단계만) / full=true : 12단계 전부 → {jobId} */
	@SuppressWarnings("unchecked")
	public Map<String, Object> run(boolean full) {
		try {
			return rest.postForObject(batchUrl + "/pipeline/run?full={full}", null, Map.class, full);
		} catch (Exception e) {
			throw toBatch(e, "이미 실행 중인 작업이 있습니다.", null);
		}
	}

	/** 진행 상황. 배치 서버 응답을 그대로 넘긴다 (feed · report.diff 가 생겨도 그대로 지나간다) */
	@SuppressWarnings("unchecked")
	public Map<String, Object> job(String id) {
		try {
			Map<String, Object> s = rest.getForObject(batchUrl + "/pipeline/jobs/{id}", Map.class, id);
			/* 작업이 끝났으면(running 이 아님 : 완료 · error) 지도 캐시(동 목록 · 구 경계 · 모델 B 환산 기준)를 한 번 비운다.
			   error 도 비운다 : 중간까지 DB 가 바뀌었을 수 있고, 비우기는 다시 계산할 뿐이라 성공으로 보지 않는다 (응답은 그대로 넘김) */
			if (s != null && s.get("status") != null && !"running".equals(s.get("status")) && !id.equals(clearedJobId)) {
				clearedJobId = id;
				mapService.clearCache();
			}
			// 실제로 DB 반영까지 끝났을 때만 최종 업데이트 시각을 바꾼다. 응답에 지금 값을 같이 넣는다 (헤더 · 모달 갱신용)
			if (s != null && !"running".equals(s.get("status")) && dbUpdated(s)) recordUpdated(id);
			if (s != null) s.putAll(updatedAtView());
			return s;
		} catch (Exception e) {
			throw toBatch(e, null, "작업 기록이 없습니다. 다시 실행해 주세요.");   // 404 = 배치 서버가 다시 켜져 작업 기록이 사라진 경우
		}
	}

	/** 화면 처음 열 때 마지막 실행 결과. 못 읽으면 빈 값 (대기 화면만 보이면 된다) */
	@SuppressWarnings("unchecked")
	public Map<String, Object> latest() {
		try {
			Map<String, Object> m = rest.getForObject(batchUrl + "/pipeline/latest", Map.class);
			Map<String, Object> out = m == null ? new LinkedHashMap<>() : new LinkedHashMap<>(m);
			out.putAll(updatedAtView());
			return out;
		} catch (Exception e) {
			return new LinkedHashMap<>(updatedAtView());
		}
	}

	/** 어떤 오류든 상태 코드 + 문구 하나로. 배치 서버가 준 상태 코드는 그대로 둔다 */
	private static BatchException toBatch(Exception e, String msg409, String msg404) {
		if (e instanceof HttpStatusCodeException) {                    // 배치 서버가 4xx · 5xx 로 답함 (409 · 404 · 422 · 500 …)
			int code = ((HttpStatusCodeException) e).getStatusCode().value();
			if (code == 409 && msg409 != null) return new BatchException(409, msg409);
			if (code == 404 && msg404 != null) return new BatchException(404, msg404);
			return new BatchException(code, "배치 서버가 오류를 돌려주었습니다. (" + code + ")");
		}
		if (e instanceof ResourceAccessException) {                    // 연결 실패 · 시간 초과
			if (e.getCause() instanceof SocketTimeoutException) return new BatchException(503, "배치 서버(8000) 응답이 늦습니다. 잠시 뒤 다시 확인합니다.");
			return new BatchException(503, "배치 서버(8000)가 꺼져 있습니다.");
		}
		return new BatchException(500, "배치 서버 응답을 읽지 못했습니다.");
	}
}
