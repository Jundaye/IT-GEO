package wellfair.common;

/**
 * 배치 서버(데이터 업데이트, FastAPI) 호출 실패. DataUpdateService 가 던지고
 * DataUpdateRESTController 가 그 상태 코드 그대로 {"status":"error","message":...} JSON 으로 돌려준다 (HTML 오류 페이지가 나가지 않게).
 */
public class BatchException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final int status;   // 화면으로 돌려줄 HTTP 상태 코드 (배치 서버가 준 코드. 연결 실패 · 시간 초과는 503)

	public BatchException(int status, String msg) {
		super(msg);
		this.status = status;
	}

	public int getStatus() {
		return status;
	}
}
