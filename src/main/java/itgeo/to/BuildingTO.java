package itgeo.to;

import lombok.Data;

// building 테이블 + 점수 계산 결과 (결과 리스트 · 상세 공용)
@Data
public class BuildingTO {
	// building 컬럼 (snake_case → camelCase 자동)
	private String bldId;
	private String sggCd;
	private String emdNm;
	private String addr;
	private String bldNm;
	private String dongNm;
	private String useCd;
	private String useNm;
	private Integer grndFlr;
	private Integer ugrndFlr;
	private String useaprDay;
	private String violYn;
	private Integer units;
	private int ugrndRes;
	private int nonresHh;
	private String spType;
	private String spNm;
	private double elevM;
	private double slopeDeg;
	private int floodCnt;
	private double floodDepth;
	private String priceType;
	private Double pricePct;
	private String hospNm;
	private double hospDist;
	private String busNm;
	private double busDist;
	private String welfNm;
	private double welfDist;
	private double lng;
	private double lat;
	// 2026-11-16 전처리 자동화로 추가된 컬럼 (값이 없으면 null)
	private Integer rentYn;      // 공공임대 단지 1/0
	private Integer narrowRd;    // 도로폭 4m 미만 도로에 접함 1/0
	private String strctNm;      // 건축물대장 구조명 (대장 미연결이면 null)
	private Integer ugrndUnits;  // 지하층 전유 호 수 (공동주택만). 점수는 ugrnd_res 기준 그대로

	// 계산 컬럼
	private double score;       // 취약 점수 (100점 만점). legacy 는 정수값, 모델 B 는 소수 둘째 자리
	private Double rawScore;       // 모델 B : 계산 점수 (소수 둘째 자리). 이때 score 는 환산 점수(소수 유지)
	private Integer displayScore;  // 모델 B : 화면 표시 = 환산 점수 버림 정수. legacy 면 null (score 그대로 표시)
	private Boolean scoreTarget;   // 모델 B : 점수 대상(units > 0) 여부. false 면 점수 없음 ("점수 대상 아님")
	private Boolean candidate;     // 모델 B : 우선확인 후보 (계산 점수 ≥ 경계 = 환산 80 이상)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String coldCheck;      // 한파 시나리오 (2026-11-24) : Y = 한파 현장확인 대상 (기본 B 후보 · 사용승인 연도 <= 1979), U = 후보인데 사용승인일 없음(확인 불가), null = 해당 없음 · 한파 아님
	private Double scoreH, scoreO, scoreR, scoreA;   // 모델 B 영역 값 0~1 (거처 · 건물 상태 · 지형·재해 · 접근성). legacy 면 null, 연수 모르면 scoreO null
	private int ptUgrnd, ptGosiwon, ptRooftop, ptViol, ptOld, ptFlood, ptLow, ptCheap;  // 요인별 점수
	private Integer bldAge;     // 사용승인 후 경과 년수 (없으면 null)
	private int vulnHhCnt;      // 취약 가구 수 (bld_resident_stat, 동점 2순위)
	private int prioritySum;    // 가구 가점 합 (동점 3순위)
	private String factorText;  // "지하층 · 노후 · 침수"
	private int rank;           // 결과 리스트 순번 (서비스에서)

	// 상세에서만
	private String checkedAt;   // 현장 확인 완료일 (없으면 null)
	private int archiveCnt;     // 내 보관함에 있으면 1
	private int visitCnt;       // 현장 방문 기록 수 (누가 갔든). 13단계 : 보관함에 있고 방문 0 → "저장됨" 안내만, 방문 있음 → "다시 저장 (점수 갱신)"
}
