package itgeo.to;

import java.util.List;

import lombok.Data;

/**
 * 취약 점수 기준값 + 지도 조회 조건 (쿼리 파라미터 → 그대로 바인딩).
 * 점수 SQL 은 mapper-building.xml 의 scoreExpr 한 곳. 여기 값이 #{...} 로 들어간다.
 * 가중치는 위험 유형 4묶음(기본 주거 공간 45 · 건물 상태 20 · 침수 위험 25 · 경제 여건 10). 묶음마다 0~100 을 따로 정하고,
 * 점수에는 비율로 환산해 넣는다 (getSpacePt … : 내 값 ÷ 켜진 묶음 값의 합 × 100). 100·100·100·100 → 25씩, 100·50·100·50 → 2:1:2:1
 */
@Data
public class AnalysisCriteriaTO {

	// ---- 요인 on/off · 기준값 (왼쪽 패널) ----
	private boolean ugrndOn = true;                              // 지하층 거주 (ugrnd_res = 1)          → 주거 공간
	private boolean gosiwonOn = true;                            // 고시원 (sp_type = '고시원')          → 주거 공간
	private boolean rooftopOn = true;                            // 옥탑 (bld_resident_stat.rooftop_hh_cnt > 0) → 주거 공간
	private boolean oldOn = true;     private int oldYears = 30; // 노후 (사용승인 N년+)                 → 건물 상태
	private boolean violOn = true;                               // 위반건축물 (viol_yn = '1')          → 건물 상태
	private boolean floodOn = true;   private int floodCnt = 1;  // 침수 (flood_cnt N회+)               → 침수 위험
	private boolean lowOn = true;     private double lowElev = 15; // 저지대 (elev_m N m 이하)          → 침수 위험
	private boolean cheapOn = true;   private double cheapPct = 20; // 저가 (price_pct N% 이하)         → 경제 여건

	// ---- 가중치 = 위험 유형 4묶음, 묶음마다 0~100 (2026-11-23). 점수 SQL 은 아래 get*Pt (합 100 으로 환산한 값) 를 쓴다 ----
	private int spaceW = 45;          // 주거 공간 (지하층 · 옥탑 · 고시원)
	private int bldW = 20;            // 건물 상태 (노후 · 위반건축물)
	private int floodW = 25;          // 침수 위험 (침수 이력 · 저지대)
	private int econW = 10;           // 경제 여건 (저가 주택)

	/** 묶음 요인이 하나라도 켜져 있나 (전부 꺼진 묶음은 가중치를 0 으로 보고 비율 계산에서 뺀다) */
	public boolean isSpaceActive() { return ugrndOn || gosiwonOn || rooftopOn; }
	public boolean isBldActive() { return oldOn || violOn; }
	public boolean isFloodActive() { return floodOn || lowOn; }
	public boolean isEconActive() { return cheapOn; }

	/** 켜진 묶음 가중치의 합 (0 이면 분석할 수 없음 → MapService.assertWeights 가 막는다) */
	public int getWeightSum() {
		return (isSpaceActive() ? spaceW : 0) + (isBldActive() ? bldW : 0) + (isFloodActive() ? floodW : 0) + (isEconActive() ? econW : 0);
	}
	private double pt(boolean active, int w) {
		int sum = getWeightSum();
		return !active || sum <= 0 ? 0 : w * 100.0 / sum;
	}
	/** 점수에 실제로 반영하는 묶음 점수 (합 100). mapper-building.xml 의 pt* 가 #{spacePt} … 로 쓴다 */
	public double getSpacePt() { return pt(isSpaceActive(), spaceW); }
	public double getBldPt() { return pt(isBldActive(), bldW); }
	public double getFloodPt() { return pt(isFloodActive(), floodW); }
	public double getEconPt() { return pt(isEconActive(), econW); }

	// ---- 점수 모델 B (2026-11-23, app.analysis.model = b 일 때만). 영역 4개 가중치 ----
	//  거처 H · 건물 상태 O · 지형·재해 R · 생활시설 접근성 A. 점수 = 100 × Σ(w·영역) ÷ Σw (연수를 모르면 O 를 빼고 나머지로)
	//  이름이 wH 가 아니라 weightH 인 것은 MyBatis · Spring 이 getWH() 를 "WH" 로 읽어 #{wH} 가 안 맞기 때문
	//  (2026-11-23 4차) 영역마다 0~100 을 따로 정하고 비율로 반영 (기본 100 씩 = 25%씩). scoreExprB 가 Σw 로 나누므로 SQL 에는 입력값 그대로 넣는다
	private int weightH = 100;
	private int weightO = 100;
	private int weightR = 100;
	private int weightA = 100;
	/** 실제 적용 비율 % (화면 · 보관함 기록용). 합 0 이면 0 */
	public double getWeightShare(String k) {
		int h = getWeightH(), o = getWeightO(), r = getWeightR(), a = getWeightA();
		int sum = h + o + r + a;
		int w = "H".equals(k) ? h : "O".equals(k) ? o : "R".equals(k) ? r : "A".equals(k) ? a : 0;
		return sum <= 0 ? 0 : w * 100.0 / sum;
	}

	// ---- 모델 B 기상 시나리오 (2026-11-24, reference/docs/weather_scenario_1124) ----
	//  scenario = 지도 시나리오 버튼 (base | rain | heat | cold). 화면이 늘 같이 보내던 값을 모델 B 에서만 읽는다 (legacy 는 예전처럼 무시)
	//   rain (폭우 P3) : 가중치 고정 H 30 · O 20 · F 40 · A 10, 지형·재해 R 대신 F = MIN(flood_cnt / 5, 1) (경사는 점수에서 뺌). 환산 기준(300번째 · 최고)은 폭우 점수로 따로
	//   cold (한파)    : 새 점수식 없음. 기본 B 25 · 25 · 25 · 25 그대로, 그 후보 중 사용승인 연도 <= COLD_YEAR_MAX 를 "한파 현장확인" 으로 표시
	//   heat (폭염) · 그 밖 : 준비 중 → 기본 B 와 같다 (점수를 따로 계산하지 않는다)
	//  폭우 · 한파는 고정 프리셋이라 화면이 보낸 weightH … 를 쓰지 않는다 (필드 값은 그대로 두고 getter 만 프리셋을 돌려준다 → 기본 모드 입력값이 덮이지 않음)
	private String scenario;
	/** 폭우 시나리오 가중치 (H · O · F · A) */
	public static final int[] RAIN_WEIGHTS = { 30, 20, 40, 10 };
	/** 한파 시나리오 가중치 = 기본 B (H · O · R · A) */
	public static final int[] COLD_WEIGHTS = { 25, 25, 25, 25 };
	/** 한파 현장확인 기준 : 사용승인 연도가 이 해 이하 (1979 포함). 바꿀 때는 이 숫자 하나만 */
	public static final int COLD_YEAR_MAX = 1979;
	/** 모델 B 기준 연도 (mapper-building ageExpr 의 2026 과 같다) */
	public static final int BASE_YEAR_B = 2026;
	public boolean isScenRain() { return isModelB() && "rain".equals(scenario); }
	public boolean isScenCold() { return isModelB() && "cold".equals(scenario); }
	/** 점수 계산 기준 이름 : base | rain | cold (모델 B). 폭염 · 모르는 값은 base */
	public String getScenarioB() { return isScenRain() ? "rain" : isScenCold() ? "cold" : "base"; }
	/** 화면 · 보관함 기록용 계산 기준 이름 */
	public String getScoreBasis() { return isScenRain() ? "B 폭우" : isScenCold() ? "B 기본 + 한파 현장확인 표시" : "B 기본"; }
	public int getWeightH() { return isScenRain() ? RAIN_WEIGHTS[0] : isScenCold() ? COLD_WEIGHTS[0] : weightH; }
	public int getWeightO() { return isScenRain() ? RAIN_WEIGHTS[1] : isScenCold() ? COLD_WEIGHTS[1] : weightO; }
	public int getWeightR() { return isScenRain() ? RAIN_WEIGHTS[2] : isScenCold() ? COLD_WEIGHTS[2] : weightR; }
	public int getWeightA() { return isScenRain() ? RAIN_WEIGHTS[3] : isScenCold() ? COLD_WEIGHTS[3] : weightA; }
	public int getColdYearMax() { return COLD_YEAR_MAX; }
	/** SQL 비교값 : 연수(ageExpr) >= 2026 - 1979 = 47 ⇔ 사용승인 연도 <= 1979 */
	public int getColdAgeMin() { return BASE_YEAR_B - COLD_YEAR_MAX; }
	/** 한파 보기 : true = 한파 현장확인 대상만 (기본 B 후보 ∩ 사용승인 연도 <= 1979), false = 전체 보기 */
	private boolean coldOnly;
	/** SQL test : 한파 시나리오에서 [한파 현장확인만] 일 때 */
	public boolean isColdFilter() { return isScenCold() && coldOnly; }

	// ---- 모델 B 조건별 조회 (2026-11-23 4차). 점수 · 가중치 · 환산 기준(300번째)과 무관한 "보기 조건" 이고, 고른 조건은 모두 충족(AND) ----
	//  이름이 fUgrnd 가 아니라 filterUgrnd 인 것은 weightH 와 같은 이유 (getFUgrnd → "FUgrnd")
	private boolean filterUgrnd;          // 지하층 보유 주택 (ugrnd_res = 1)
	private boolean filterLodging;        // 고시원 · 여관 · 여인숙 (sp_type)
	private Integer filterFloodMin;       // 침수 기록 연도 수 N개 이상 (flood_cnt >= N), null = 조건 없음
	private Integer filterAgeMin;         // 사용승인 후 N년 이상 (기준 연도 2026, ageExpr >= N)
	private Double filterSlopeMin;        // 경사도 N° 초과 (slope_deg > N)
	private boolean filterCand;           // 우선확인 후보만 (계산 점수 >= 3개 구 전체 300번째 경계)
	@com.fasterxml.jackson.annotation.JsonIgnore
	private boolean clusterAll;           // 묶음 도넛만 : 후보 조건을 SQL 에서 빼고 가운데 숫자 · 링 = 칸 전체, 빨간 배지 = 후보 (MapService.getClusterList 가 정함, 2026-11-27)
	// 추가 조회 조건 (legacy 요인. B 점수에는 넣지 않는다)
	private boolean filterViol;           // 위반건축물 (viol_yn = '1')
	private Double filterLowElev;         // 저지대 : 해발 N m 이하 (elev_m <= N)
	private Double filterCheapPct;        // 저가 주택 : 공시가 백분위 N% 이하 (price_pct <= N)
	/** 조건별 조회를 걸지 (모델 B · 지도 · 후보 목록 · 집계일 때만. 상세 · 주소 검색 · 보관함은 조건과 상관없이) */
	public boolean isFilterOn() { return isScoreTargetOnly(); }
	/** 결과 리스트를 "기준점 이상" 으로 자를지 : legacy 는 늘 scoreMin 이상, 모델 B 는 [우선확인 후보만 보기] 조건으로만 자른다 */
	public boolean isListByScore() { return !isModelB(); }
	/** 걸린 조건 수 (모델 B) */
	public int getFilterCountB() {
		int n = 0;
		if (filterUgrnd) n++;
		if (filterLodging) n++;
		if (filterFloodMin != null) n++;
		if (filterAgeMin != null) n++;
		if (filterSlopeMin != null) n++;
		if (filterCand) n++;
		if (filterViol) n++;
		if (filterLowElev != null) n++;
		if (filterCheapPct != null) n++;
		return n;
	}

	/** 모델 B 환산 기준 (MapService.prepareScale 이 채움. 3개 구 전체 · 지금 가중치). 경계 = 300번째 계산 점수, 최고 = 최고 계산 점수 */
	private Double scaleBoundary;
	private Double scaleMax;
	private boolean scaled() { return isModelB() && scaleBoundary != null && scaleMax != null; }
	/** SQL "기준점 이상" 비교값 : legacy = scoreMin 그대로, 모델 B = 환산 scoreMin 점에 해당하는 계산 점수 */
	public double getScoreMinRaw() { return scaled() ? itgeo.common.ScoreScale.inverse(scoreMin, scaleBoundary, scaleMax) : scoreMin; }
	/** 색 구간 집계 (80 / 60 / 40) 비교값 */
	public double getBand80Raw() { return scaled() ? itgeo.common.ScoreScale.inverse(80, scaleBoundary, scaleMax) : 80; }
	public double getBand60Raw() { return scaled() ? itgeo.common.ScoreScale.inverse(60, scaleBoundary, scaleMax) : 60; }
	public double getBand40Raw() { return scaled() ? itgeo.common.ScoreScale.inverse(40, scaleBoundary, scaleMax) : 40; }

	/** 점수 모델 (설정값, AnalysisModel 이 시작할 때 넣음). 요청 파라미터로는 안 바뀐다 */
	private static volatile String modelConfig = "b";
	public static void setModelConfig(String m) {
		modelConfig = m != null && "b".equalsIgnoreCase(m.trim()) ? "b" : "legacy";
	}
	public String getModel() { return modelConfig; }
	/** mapper-building.xml 의 test="modelB" */
	public boolean isModelB() { return "b".equals(modelConfig); }
	/** mapper 에서 파라미터 없이 모델을 볼 때 (test="@itgeo.to.AnalysisCriteriaTO@modelBConfig()") · 화면 공통 (LoginInterceptor) */
	public static boolean modelBConfig() { return "b".equals(modelConfig); }

	/** 보관함 목록 조회 (ArchiveService 가 켬). 모델 B 라도 저장된 건물은 분석 대상이 아니어도 보여 준다 */
	private boolean archiveScope;
	/** 모델 B 분석 대상(units > 0)만 남길지 : 지도 · 후보 목록 · 집계. 상세(bldId) · 주소 검색(q) · 보관함 목록은 제외 */
	public boolean isScoreTargetOnly() { return isModelB() && bldId == null && (q == null || q.isBlank()) && !archiveScope; }

	private int checkedMonths = 3;    // 현장 확인됨 기간 (9단계) : 3 | 12 | 0 = 전체 | -1 = 끄기. 범례의 "현장 확인됨" 에서 고른다
	private int scoreMin = 80;        // 이 점수 이상만 "위험" (결과 리스트 · 도넛 가운데 숫자 · 80점 이상만 보기)

	// ---- 지역 · 검색 ----
	private String gu;                // sgg_cd (11530 구로 / 11470 양천 / 11560 영등포), 없으면 전체
	private String dong;              // 법정동 emd_nm, 없으면 전체
	private String adm;               // 행정동 adm_nm (동 선택 2단계), 없으면 법정동 전체
	private List<String> adms;        // 행정동 여러 개 "법정동|행정동" (지도 동 선택 복수 선택). 있으면 dong · adm 대신 이 목록
	private String q;                 // 건물명·주소 검색어
	private String status;            // 보관함 탭 : TODO | DOING | DONE (없으면 전체)
	private String scen;              // 보관함 구분 선택칸 : base | rain | heat | cold | custom (저장할 때 쓴 가중치 시나리오, 없으면 전체)

	// ---- 보관함 [상세 필터] (archive.do, 5-1) : 비어 있으면 조건 없음 ----
	private List<String> factors;     // 취약 요인 키 (ugrnd rooftop gosiwon viol old flood low) → t.pt_<키> > 0
	private String factorMode = "all";// all = 고른 요인을 모두 가진 건물 / any = 하나라도
	private List<String> bands;       // 점수 구간 : 80 (80+) | 60 (60~79) | 40 (40~59), 저장 점수 기준
	private String use;               // 건물 용도 (use_nm)
	private boolean resHigh;          // 거주자 조건 : 고위험고립 있음 (bld_resident_stat.high_iso_cnt > 0)
	private boolean resOld75;         // 75세 이상 있음
	private boolean resSevere;        // 심한 장애 있음
	private List<String> visits;      // 방문 결과 : MET | ABSENT | REFUSED (기록이 하나라도 있으면)
	private String savedFrom;         // 저장일 기간 (yyyy-MM-dd)
	private String savedTo;
	private boolean fopen;            // 상세 필터 펼침 상태 (화면용)

	/** 상세 필터가 하나라도 걸려 있는지 (버튼 배지 · 펼침) */
	public int getFilterCount() {
		int n = 0;
		if (factors != null && !factors.isEmpty()) n++;
		if (bands != null && !bands.isEmpty()) n++;
		if (use != null && !use.isBlank()) n++;
		if (adm != null && !adm.isBlank()) n++;
		if (resHigh || resOld75 || resSevere) n++;
		if (visits != null && !visits.isEmpty()) n++;
		if ((savedFrom != null && !savedFrom.isBlank()) || (savedTo != null && !savedTo.isBlank())) n++;
		return n;
	}

	// ---- 지도 범위 (bbox=minLng,minLat,maxLng,maxLat → 서비스에서 풀어 넣음) ----
	private String bbox;
	private Double minLng, minLat, maxLng, maxLat;
	private int limit = 1500;         // 한 화면 핀 최대 수 (BUILDING_LIMIT)

	// ---- 격자 묶음 (clusters.do) : cell = 경도 도 단위 칸 크기, cellLat = cell × cos(기준위도) (서비스가 채움) ----
	private double cell;
	private double cellLat;
	private boolean snap = true;      // bbox 를 칸 경계까지 넓혀 가장자리 묶음도 칸 전체 건물 수로

	// ---- 결과 리스트 ----
	private String sort = "score";    // score | old | addr
	private int page = 1;
	private int size = 20;

	// ---- 상세 ----
	private String bldId;
	private Long userId;              // 보관함 여부 확인용 (세션 loginMember.userId)

	public int getOffset() {
		return (Math.max(page, 1) - 1) * size;
	}

	/** 목록 page 가 1 미만이면 1 로 (15단계 D-14). Lombok 은 같은 이름의 setter 가 있으면 만들지 않는다 */
	public void setPage(int page) {
		this.page = Math.max(1, page);
	}

	/** bbox 문자열을 min/max 로 풀어 넣는다. 형식이 다르면 범위 없음 */
	public void applyBbox() {
		if (bbox == null || bbox.isBlank()) return;
		String[] p = bbox.split(",");
		if (p.length != 4) return;
		try {
			minLng = Double.parseDouble(p[0].trim());
			minLat = Double.parseDouble(p[1].trim());
			maxLng = Double.parseDouble(p[2].trim());
			maxLat = Double.parseDouble(p[3].trim());
		} catch (NumberFormatException e) {
			minLng = minLat = maxLng = maxLat = null;
		}
	}
}
