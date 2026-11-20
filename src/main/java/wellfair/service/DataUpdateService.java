package wellfair.service;

import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import wellfair.common.BatchException;
import wellfair.common.ForbiddenException;
import wellfair.to.MemberTO;

/**
 * 데이터 업데이트 : Python 배치 서버(FastAPI, pipeline/api.py)를 부른다. 실제 작업은 배치 서버가 하고 여기는 전달만.
 *  - 배치 서버 주소 = app.properties 의 batch.url (없으면 http://localhost:8000)
 *  - 연결 3초 · 읽기 10초 안에 답이 없으면 끊는다 (1초마다 오는 진행 상황 조회가 Tomcat 스레드를 붙잡고 있지 않게)
 *  - 실패는 종류와 상관없이 BatchException(상태 코드, 문구) 하나로 바꾼다 → DataUpdateRESTController 가 JSON 으로 돌려준다
 */
@Service
public class DataUpdateService {

	static final int CONNECT_MS = 3000;
	static final int READ_MS = 10000;

	@Value("${batch.url:http://localhost:8000}")
	String batchUrl;

	private final RestTemplate rest;

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
			return rest.getForObject(batchUrl + "/pipeline/jobs/{id}", Map.class, id);
		} catch (Exception e) {
			throw toBatch(e, null, "작업 기록이 없습니다. 다시 실행해 주세요.");   // 404 = 배치 서버가 다시 켜져 작업 기록이 사라진 경우
		}
	}

	/** 화면 처음 열 때 마지막 실행 결과. 못 읽으면 빈 값 (대기 화면만 보이면 된다) */
	@SuppressWarnings("unchecked")
	public Map<String, Object> latest() {
		try {
			Map<String, Object> m = rest.getForObject(batchUrl + "/pipeline/latest", Map.class);
			return m == null ? Collections.emptyMap() : m;
		} catch (Exception e) {
			return Collections.emptyMap();
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
