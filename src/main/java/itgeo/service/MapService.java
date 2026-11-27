package itgeo.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import itgeo.common.BadRequestException;
import itgeo.common.ScoreScale;
import itgeo.dao.BuildingDAO;
import itgeo.to.AnalysisCriteriaTO;
import itgeo.to.BuildingDetailTO;
import itgeo.to.BuildingMarkerTO;
import itgeo.to.BuildingTO;
import itgeo.to.ClusterTO;
import itgeo.to.DongTO;
import itgeo.to.GuTO;
import itgeo.to.PageTO;
import itgeo.to.ResidentCountTO;
import itgeo.to.ScoreFactorTO;
import itgeo.to.ScoreItemTO;

// 메인(분석 지도). BuildingDAO 는 여기서만. 거주자는 ResidentService 를 거친다
@Service
public class MapService {

	/** 격자의 기준위도 (살피구로 refLat : 전체 건물 평균 위도로 고정) */
	static final double REF_LAT = 37.5;

	static final Map<String, String> GU_NAME = new LinkedHashMap<>();
	static {
		GU_NAME.put("11530", "구로구");
		GU_NAME.put("11470", "양천구");
		GU_NAME.put("11560", "영등포구");
	}

	@Autowired
	BuildingDAO dao;

	@Autowired
	ResidentService residentService;

	private Map<String, List<double[]>> ringCache;   // sgg_cd → 볼록 껍질 [[lng,lat],...] (한 번 계산)

	// ---------- 동 목록 기억 (1시간) ----------
	static final long DONG_CACHE_MS = 60L * 60 * 1000;
	private volatile List<DongTO> dongListCache;
	private final Map<String, List<DongTO>> dongTreeCache = new ConcurrentHashMap<>();   // gu("" = 전체) → 트리
	private volatile long dongCachedAt = System.currentTimeMillis();

	/** 기억해 둔 동 목록 · 구 경계 · 모델 B 환산 기준(300번째 · 최고 점수)을 비운다. 다음 요청 때 DB 에서 새로 만든다 (데이터 업데이트 뒤 DataUpdateService 가 부름) */
	public synchronized void clearCache() {
		dongListCache = null;
		dongTreeCache.clear();
		ringCache = null;
		scaleCache.clear();
		dongCachedAt = System.currentTimeMillis();
	}

	private void expireDongCache() {
		if (System.currentTimeMillis() - dongCachedAt > DONG_CACHE_MS) clearCache();
	}

	// 법정동 목록 (보관함 필터 등). 기억해 둔 목록은 바꿀 수 없는 목록으로 돌려준다
	public List<DongTO> getDongList() {
		expireDongCache();
		List<DongTO> l = dongListCache;
		if (l == null) {
			l = java.util.Collections.unmodifiableList(dao.getDongList(null));
			dongListCache = l;
		}
		return l;
	}

	/** 동 선택 2단계 (5-1) : 법정동 줄마다 행정동 목록(adms)을 단다. gu 가 없으면("" · null) 3개 구 전체.
	    기억하는 키는 전체("")와 3개 구 코드만 (그 밖의 값은 매번 조회 → 이상한 gu 값으로 캐시가 끝없이 늘지 않게) */
	public List<DongTO> getDongTree(String gu) {
		expireDongCache();
		String key = gu == null ? "" : gu;
		if (!key.isEmpty() && !GU_NAME.containsKey(key)) return buildDongTree(key);
		return dongTreeCache.computeIfAbsent(key, k -> java.util.Collections.unmodifiableList(buildDongTree(k)));
	}

	private List<DongTO> buildDongTree(String gu) {
		if (gu != null && gu.isEmpty()) gu = null;                                    // mapper 는 null · "" 모두 전체로 보지만 같은 값으로 맞춘다
		List<DongTO> emds = dao.getDongList(gu);
		Map<String, DongTO> byKey = new HashMap<>();
		for (DongTO e : emds) byKey.put(e.getSggCd() + "|" + e.getEmdNm(), e);
		for (DongTO a : dao.getAdmList(gu)) {
			DongTO e = byKey.get(a.getSggCd() + "|" + a.getEmdNm());
			if (e != null && isAdmPair(a.getSggCd(), a.getEmdNm(), a.getAdmNm())) e.getAdms().add(a);
		}
		return emds;
	}

	/**
	 * 법정동 아래에 보여 줄 행정동 짝 (경계가 실제로 겹치는 짝 : 법정동 면적 10% 이상 또는 행정동 면적 30% 이상).
	 * 건물 행정동은 좌표로 붙였기 때문에 경계선 근처 건물 몇 동이 옆 행정동으로 잡혀(신월동 아래 목5동 · 신정3동 등) 목록이 섞였다.
	 * 그런 건물은 법정동 전체를 고르면 그대로 나오고, 행정동 목록에만 안 보인다. 표에 없는 법정동은 거르지 않는다
	 */
	private static final Map<String, Set<String>> ADM_PAIRS = new HashMap<>();
	static {
		// 양천구
		pair("11470", "목동", "목1동", "목2동", "목3동", "목4동", "목5동");
		pair("11470", "신월동", "신월1동", "신월2동", "신월3동", "신월4동", "신월5동", "신월6동", "신월7동");
		pair("11470", "신정동", "신정1동", "신정2동", "신정3동", "신정4동", "신정6동", "신정7동");
		// 구로구
		pair("11530", "가리봉동", "가리봉동");
		pair("11530", "개봉동", "개봉1동", "개봉2동", "개봉3동");
		pair("11530", "고척동", "고척1동", "고척2동");
		pair("11530", "구로동", "구로1동", "구로2동", "구로3동", "구로4동", "구로5동");
		pair("11530", "궁동", "수궁동");
		pair("11530", "신도림동", "신도림동");
		pair("11530", "오류동", "오류1동", "오류2동");
		pair("11530", "온수동", "수궁동");
		pair("11530", "천왕동", "오류2동");
		pair("11530", "항동", "항동");
		// 영등포구
		pair("11560", "당산동", "당산2동");
		for (String g : new String[] { "1", "2", "3" }) pair("11560", "당산동" + g + "가", "당산1동");
		for (String g : new String[] { "4", "5", "6" }) pair("11560", "당산동" + g + "가", "당산2동");
		pair("11560", "대림동", "대림1동", "대림2동", "대림3동");
		pair("11560", "도림동", "도림동");
		for (int g = 1; g <= 6; g++) pair("11560", "문래동" + g + "가", "문래동");
		pair("11560", "신길동", "신길1동", "신길3동", "신길4동", "신길5동", "신길6동", "신길7동", "영등포본동");
		pair("11560", "양평동", "양평1동", "양평2동");
		pair("11560", "양평동1가", "양평1동");
		pair("11560", "양평동2가", "양평1동");
		pair("11560", "양평동3가", "양평1동", "양평2동");
		for (String g : new String[] { "4", "5", "6" }) pair("11560", "양평동" + g + "가", "양평2동");
		pair("11560", "양화동", "양평2동");
		pair("11560", "여의도동", "여의동");
		pair("11560", "영등포동", "영등포본동", "도림동");   // 영등포동 건물 12% 가 도림동 (건물 좌표로 확인 2026-11-22)
		for (int g = 1; g <= 8; g++) pair("11560", "영등포동" + g + "가", "영등포동");
	}

	private static void pair(String sggCd, String emdNm, String... admNms) {
		ADM_PAIRS.computeIfAbsent(sggCd + "|" + emdNm, k -> new HashSet<>()).addAll(Arrays.asList(admNms));
	}

	static boolean isAdmPair(String sggCd, String emdNm, String admNm) {
		Set<String> ok = ADM_PAIRS.get(sggCd + "|" + emdNm);
		return ok == null || ok.contains(admNm);
	}

	// ---------- 모델 B 환산 점수 (VSCODE_지시문_점수모델B "환산 점수") ----------
	/** 가중치 조합 → { 경계, 최고, 계산한 시각 }. 같은 가중치는 10분 동안 다시 구하지 않는다 */
	private final Map<String, double[]> scaleCache = new java.util.concurrent.ConcurrentHashMap<>();
	private static final long SCALE_TTL_MS = 10 * 60 * 1000L;

	/** 모델 B 면 지금 가중치의 환산 기준(3개 구 전체 300번째 · 최고 계산 점수)을 c 에 넣는다. legacy 면 아무것도 안 함 */
	public void prepareScale(AnalysisCriteriaTO c) {
		if (!c.isModelB()) return;
		// 시나리오마다 점수식이 다르므로(폭우 = F) 키에 계산 기준도 넣는다. 한파는 기본 B 와 같은 식이라 base 와 같은 기준
		String key = (c.isScenRain() ? "rain" : "base") + ":" + c.getWeightH() + "," + c.getWeightO() + "," + c.getWeightR() + "," + c.getWeightA();
		double[] v = scaleCache.get(key);
		if (v == null || System.currentTimeMillis() - (long) v[2] > SCALE_TTL_MS) {
			List<Double> top = dao.getScoreTopB(c);
			double max = top.isEmpty() ? 0 : top.get(0);
			double boundary = top.isEmpty() ? 0 : top.get(Math.min(299, top.size() - 1));
			v = new double[] { boundary, max, System.currentTimeMillis() };
			if (scaleCache.size() > 200) scaleCache.clear();
			scaleCache.put(key, v);
		}
		c.setScaleBoundary(v[0]);
		c.setScaleMax(v[1]);
	}

	/** 모델 B : score = 환산 점수(소수 유지), rawScore = 계산 점수, displayScore = 버림 정수, candidate = 계산 ≥ 경계 */
	private static void applyScale(BuildingTO b, AnalysisCriteriaTO c) {
		if (b == null || !c.isModelB() || c.getScaleBoundary() == null) return;
		// 분석 대상 = SQL 의 score_target (IFNULL(units, 0) > 0). 값이 없을 때만 units 로 판단
		boolean target = b.getScoreTarget() != null ? b.getScoreTarget() : b.getUnits() != null && b.getUnits() > 0;
		b.setScoreTarget(target);
		if (!target) { b.setScore(0); b.setCandidate(false); return; }
		double raw = b.getScore(), s = ScoreScale.scaled(raw, c.getScaleBoundary(), c.getScaleMax());
		b.setRawScore(raw);
		b.setScore(s);
		b.setDisplayScore(ScoreScale.display(s));
		b.setCandidate(raw >= c.getScaleBoundary());
	}
	private static void applyScale(BuildingMarkerTO m, AnalysisCriteriaTO c) {
		if (m == null || !c.isModelB() || c.getScaleBoundary() == null) return;
		double raw = m.getScore(), s = ScoreScale.scaled(raw, c.getScaleBoundary(), c.getScaleMax());
		m.setRawScore(raw);
		m.setScore(s);
		m.setDisplayScore(ScoreScale.display(s));
	}

	/** 구 단위 (zoom 14 이하) : 경계 + "위험 N곳". 경계는 building 좌표의 볼록 껍질 */
	public List<GuTO> getGuList(AnalysisCriteriaTO c) {
		prepareScale(c);
		Map<String, List<double[]>> rings = rings();
		List<GuTO> out = new ArrayList<>();
		for (GuTO g : dao.getGuSummary(c)) {
			g.setName(GU_NAME.getOrDefault(g.getSggCd(), g.getSggCd()));
			g.setRing(rings.get(g.getSggCd()));
			out.add(g);
		}
		return out;
	}

	/** 격자 묶음 (살피구로 AnalysisService.clusters() 를 SQL 로) */
	public List<ClusterTO> getClusterList(AnalysisCriteriaTO c) {
		prepareScale(c);
		c.applyBbox();
		if (c.getMinLat() == null) return new ArrayList<>();
		double cell = Math.max(1e-5, Math.min(c.getCell(), 5.0));
		double cellLat = cell * Math.cos(Math.toRadians(REF_LAT));
		c.setCell(cell);
		c.setCellLat(cellLat);
		if (c.isSnap()) {   // bbox 를 칸 경계까지 넓혀서 가장자리 묶음도 칸 전체 건물 수를 보여준다
			c.setMinLng(Math.floor(c.getMinLng() / cell) * cell);
			c.setMinLat(Math.floor(c.getMinLat() / cellLat) * cellLat);
			c.setMaxLng((Math.floor(c.getMaxLng() / cell) + 1) * cell);
			c.setMaxLat((Math.floor(c.getMaxLat() / cellLat) + 1) * cellLat);
		}
		// 모델 B · 우선확인 후보만 : 가운데 숫자 · 링 색 비율은 칸 안 분석 대상 전체, 빨간 배지는 후보 수 (mapper getClusterList). 요청 값은 무시하고 여기서만 정한다
		c.setClusterAll(c.isModelB() && (c.isFilterCand() || c.isColdFilter()) && c.getScaleBoundary() != null);   // 한파 [현장확인만] 도 같은 방식
		List<ClusterTO> list;
		try {
			list = dao.getClusterList(c);
		} finally {
			c.setClusterAll(false);
		}
		for (ClusterTO cl : list) {
			if (c.isModelB() && c.getScaleBoundary() != null) cl.setMaxScore(ScoreScale.scaled(cl.getMaxScore(), c.getScaleBoundary(), c.getScaleMax()));
			cl.setCell(new double[] { cl.getIx() * cell, cl.getIy() * cellLat, (cl.getIx() + 1) * cell, (cl.getIy() + 1) * cellLat });
			if (cl.getSize() == 1) {   // 칸에 건물이 1동이면 핀으로
				BuildingMarkerTO p = new BuildingMarkerTO();
				p.setBldId(cl.getOneBldId());
				p.setLat(cl.getLat());
				p.setLng(cl.getLng());
				p.setScore(cl.getMaxScore());
				p.setBldNm(cl.getOneBldNm());
				p.setAddr(cl.getOneAddr());
				p.setUseNm(cl.getOneUseNm());
				applyScale(p, c);
				cl.setPoint(p);
			}
		}
		return list;
	}

	// 확대 : bbox 안 건물 핀 (점수 높은 순 limit)
	public List<BuildingMarkerTO> getMarkerList(AnalysisCriteriaTO c) {
		c.applyBbox();
		if (c.getMinLat() == null) return new ArrayList<>();   // 범위 없이는 조회하지 않음
		if (c.getLimit() <= 0 || c.getLimit() > 3000) c.setLimit(1500);
		prepareScale(c);
		List<BuildingMarkerTO> list = dao.getMarkerList(c);
		for (BuildingMarkerTO m : list) applyScale(m, c);
		return list;
	}

	// 분석 전 : 현장 확인됨 핀
	public List<BuildingMarkerTO> getCheckedList(AnalysisCriteriaTO c) {
		return dao.getCheckedList(c);
	}

	// 오른쪽 결과 리스트. 범위 = gu/dong/adm (기본 구 전체) 또는 bbox ("지도 화면 안", [이 지역에서 재검색])
	public PageTO<BuildingTO> getResultPage(AnalysisCriteriaTO c) {
		c.applyBbox();
		if (c.getSize() <= 0 || c.getSize() > 100) c.setSize(20);
		prepareScale(c);
		Integer matchTotal = null, candTotal = null;
		int total;
		Integer coldTotal = null, coldUnknown = null;
		if (c.isModelB() && c.isFilterOn()) {
			// 모델 B (5차) : 조건 충족 전체 · 그중 우선확인 후보를 같이 센다 (조회 범위와 상관없이). total = 지금 조회 범위의 건수
			// 한파 (2026-11-24) : 그 후보 중 한파 현장확인 대상 · 확인 불가도 같이 센다. [한파 현장확인만] 이면 total = 한파 대상 수
			boolean cand = c.isFilterCand(), coldOnly = c.isColdOnly();
			c.setFilterCand(false);
			c.setColdOnly(false);
			java.util.Map<String, Object> m = dao.getResultCountB(c);
			c.setFilterCand(cand);
			c.setColdOnly(coldOnly);
			matchTotal = ((Number) m.get("match_cnt")).intValue();
			candTotal = ((Number) m.get("cand_cnt")).intValue();
			if (m.get("cold_cnt") != null) {
				coldTotal = ((Number) m.get("cold_cnt")).intValue();
				coldUnknown = ((Number) m.get("cold_unknown_cnt")).intValue();
			}
			total = c.isColdFilter() && coldTotal != null ? coldTotal : cand && c.getScaleBoundary() != null ? candTotal : matchTotal;
		} else total = dao.getResultCount(c);
		List<BuildingTO> list = dao.getResultList(c);
		int rank = c.getOffset();
		for (BuildingTO b : list) { b.setRank(++rank); applyScale(b, c); }
		PageTO<BuildingTO> page = new PageTO<>(c.getPage(), c.getSize(), total, list);
		page.setMatchTotal(matchTotal);
		page.setCandTotal(candTotal);
		page.setColdTotal(coldTotal);
		page.setColdUnknown(coldUnknown);
		if (c.isModelB()) page.setScenario(c.getScenarioB());
		return page;
	}

	/** 가중치 4묶음은 각각 0~100, 합 100 이어야 계산한다 (15단계 B-6). 화면은 늘 100 을 맞추므로 주소를 직접 고친 경우 대비. 아니면 400 */
	public static final String MSG_WEIGHT = "가중치는 묶음마다 0~100 사이로 정해 주세요";
	public static final String MSG_WEIGHT_B = "영역 가중치는 영역마다 0~100 사이로 정해 주세요";
	public static final String MSG_WEIGHT_B_ZERO = "영역 가중치를 하나 이상 0보다 크게 해 주세요";
	public static final String MSG_WEIGHT_B_ONLY_O = "건물 상태만으로는 분석할 수 없어요. 사용승인일이 없는 건물이 있어 다른 영역도 하나 이상 0보다 크게 해 주세요";
	public static final String MSG_WEIGHT_ZERO = "켜진 요인의 가중치를 하나 이상 0보다 크게 해 주세요";
	/** 묶음 가중치는 각각 0~100, 켜진 묶음 합은 0 보다 커야 한다 (점수에는 비율로 환산 : AnalysisCriteriaTO.getSpacePt …) */
	public static void assertWeights(AnalysisCriteriaTO c) {
		int[] w = { c.getSpaceW(), c.getBldW(), c.getFloodW(), c.getEconW() };
		for (int v : w) {
			if (v < 0 || v > 100) throw new BadRequestException(MSG_WEIGHT);
		}
		if (c.getWeightSum() <= 0) throw new BadRequestException(MSG_WEIGHT_ZERO);
		if (c.isModelB()) {   // 모델 B (4차) : 영역마다 0~100 따로, 점수에는 비율로 (scoreExprB 가 Σw 로 나눈다). 합이 0 이면 분석 불가
			int[] b = { c.getWeightH(), c.getWeightO(), c.getWeightR(), c.getWeightA() };
			for (int v : b) {
				if (v < 0 || v > 100) throw new BadRequestException(MSG_WEIGHT_B);
			}
			if (b[0] + b[1] + b[2] + b[3] <= 0) throw new BadRequestException(MSG_WEIGHT_B_ZERO);
			// 건물 상태만 0 보다 크면 사용승인일이 없는 건물은 나눌 가중치가 0 (점수식은 그대로 두고 입력을 막는다)
			if (b[0] + b[2] + b[3] <= 0) throw new BadRequestException(MSG_WEIGHT_B_ONLY_O);
		}
	}

	// 건물의 구 코드 (없는 건물이면 null). 구청 계정 관할 확인용 (15단계 A)
	public String getBuildingSgg(String bldId) {
		return bldId == null || bldId.isBlank() ? null : dao.getBuildingSgg(bldId);
	}

	// 건물 상세 : 건물 + 점수 구성 + 거주자 집계
	public BuildingDetailTO getBuildingDetail(AnalysisCriteriaTO c) {
		prepareScale(c);
		BuildingTO b = dao.getBuilding(c);
		if (b == null) return null;
		BuildingDetailTO d = new BuildingDetailTO();
		d.setBuilding(b);
		d.setScoreItems(c.isModelB() ? buildScoreItemsB(b, c) : buildScoreItems(b, c));   // B 는 영역 4개, legacy 는 4묶음 (섞지 않는다)
		applyScale(b, c);
		ResidentCountTO rc = residentService.getResidentCount(b.getBldId());
		d.setResidents(rc);
		if (c.isModelB()) {
			d.setScenario(c.getScenarioB());
			if (c.isScenCold()) d.setColdYearMax(AnalysisCriteriaTO.COLD_YEAR_MAX);
		}
		return d;
	}

	/** 점수 구성 = 위험 유형 4묶음 (10단계 규칙). 주거 공간 · 경제 여건 = 하나라도 맞으면 전부, 건물 상태 · 침수 위험 = 맞은 수 ÷ 켜진 수.
	 *  members 한 줄 = { desc 용 이름, 요인 점수(맞으면 > 0), 켜짐, 표의 요인 이름, 이 건물 값, 기준 }.
	 *  뒤의 셋은 펼침 표(ScoreFactorTO)용이고 점수 계산에는 쓰지 않는다. 기준 숫자는 지금 분석 조건(c)의 값 그대로 (2026-11-17 K) */
	private List<ScoreItemTO> buildScoreItems(BuildingTO b, AnalysisCriteriaTO c) {
		List<ScoreItemTO> items = new ArrayList<>();
		String none = "없음";
		items.add(anyGroup("space", "주거 공간", c.getSpacePt(), new Object[][] {
				{ "반지하", b.getPtUgrnd(), c.isUgrndOn(), "반지하", b.getUgrndRes() > 0 ? "지하 거주 있음" : none, "지하 거주 등록" },
				// 옥탑 여부는 건물 자료가 아니라 거주자 요약(옥탑층 거주 가구)이라 점수로만 알 수 있다 → 꺼져 있으면 "-"
				{ "옥탑", b.getPtRooftop(), c.isRooftopOn(), "옥탑", b.getPtRooftop() > 0 ? "옥탑층 거주 있음" : c.isRooftopOn() ? none : "-", "옥탑층 거주" },
				{ "고시원", b.getPtGosiwon(), c.isGosiwonOn(), "고시원", "고시원".equals(b.getSpType()) ? "고시원" : none, "대장 유형 고시원" } }));
		items.add(ratioGroup("bld", "건물 상태", c.getBldPt(), new Object[][] {
				{ "노후 " + c.getOldYears() + "년+" + (b.getBldAge() != null ? " (" + b.getBldAge() + "년 경과)" : ""), b.getPtOld(), c.isOldOn(),
					"노후", b.getBldAge() != null ? b.getBldAge() + "년 경과" : "자료 없음", "준공 " + c.getOldYears() + "년 이상" },
				{ "위반건축물", b.getPtViol(), c.isViolOn(),
					"위반건축물", "1".equals(b.getViolYn()) ? "대장 등재" : "0".equals(b.getViolYn()) ? none : "대장 미연계", "위반건축물 대장 등재" } }));
		items.add(ratioGroup("flood", "침수 위험", c.getFloodPt(), new Object[][] {
				{ "침수 " + b.getFloodCnt() + "회", b.getPtFlood(), c.isFloodOn(), "침수흔적", b.getFloodCnt() + "회", "침수 흔적 " + c.getFloodCnt() + "회 이상" },
				{ "저지대 해발 " + b.getElevM() + "m", b.getPtLow(), c.isLowOn(), "저지대", String.format("%.1fm", b.getElevM()), "해발 " + fmt(c.getLowElev()) + "m 이하" } }));
		items.add(anyGroup("econ", "경제 여건", c.getEconPt(), new Object[][] {
				{ "저가 주택" + (b.getPricePct() != null ? " (백분위 " + b.getPricePct() + "%)" : ""), b.getPtCheap(), c.isCheapOn(),
					"저가 주택", b.getPricePct() != null ? "백분위 " + fmt(b.getPricePct()) + "%" : "자료 없음", "공시가 백분위 " + fmt(c.getCheapPct()) + "% 이하" } }));
		return items;
	}

	/** 모델 B 점수 구성 = 영역 4개 (거처 특성 · 건물 상태 · 지형·재해 · 생활시설 접근성). 기여 점수 = 100 × 가중치 × 영역 값 ÷ 가중치 합 (연수를 모르면 건물 상태를 빼고 나머지로) */
	private List<ScoreItemTO> buildScoreItemsB(BuildingTO b, AnalysisCriteriaTO c) {
		List<ScoreItemTO> items = new ArrayList<>();
		if (b.getUnits() == null || b.getUnits() <= 0) return items;   // 점수 대상 아님
		Integer age = ageB(b.getUseaprDay());
		boolean oMissing = b.getScoreO() == null;
		double den = c.getWeightH() + (oMissing ? 0 : c.getWeightO()) + c.getWeightR() + c.getWeightA();
		String sp = b.getSpType() == null || b.getSpType().isBlank() ? null : b.getSpType();
		items.add(areaB("H", "거처 특성", c.getWeightH(), den, b.getScoreH(), b.getScoreH() != null && b.getScoreH() > 0 ? (b.getUgrndRes() == 1 ? "지하층 보유 주택" : "") + (b.getUgrndRes() == 1 && sp != null ? " · " : "") + (sp != null ? sp : "") : "해당 없음",
				List.of(new ScoreFactorTO("지하층 보유 주택", b.getUgrndRes() == 1 ? "있음 (실제 거주 여부는 현장 확인 필요)" : "없음", "있으면 1", true, b.getUgrndRes() == 1),
						new ScoreFactorTO("숙박형 거처", sp != null ? sp : "없음", "고시원 · 여관 · 여인숙이면 1", true, sp != null))));
		items.add(areaB("O", "건물 상태", oMissing ? 0 : c.getWeightO(), den, b.getScoreO(), oMissing ? "사용승인일 없음 · 이 영역을 빼고 계산" : age + "년 경과",
				List.of(new ScoreFactorTO("건물 연수", age != null ? age + "년 (기준 연도 2026)" : "사용승인일 없음", "30년 0 → 66년 이상 1", !oMissing, b.getScoreO() != null && b.getScoreO() > 0))));
		double f = Math.min(1, b.getFloodCnt() / 5.0), t = b.getSlopeDeg() >= 15 ? Math.min(1, (b.getSlopeDeg() - 15) / 10.8) : 0;
		if (c.isScenRain())   // 폭우 (2026-11-24) : 세 번째 영역 = 침수 기록 F 만 (경사는 점수에서 뺀다. 값은 참고로만 보여 준다)
			items.add(areaB("F", "침수 기록", c.getWeightR(), den, b.getScoreR(), f > 0 ? b.getFloodCnt() + "개 연도 기록" : "침수 기록 없음",
				List.of(new ScoreFactorTO("침수 기록 연도 수", b.getFloodCnt() + "개 연도", "5개 연도 이상 1 (연도 수 ÷ 5)", true, f > 0),
						new ScoreFactorTO("경사 (참고)", String.format("%.1f°", b.getSlopeDeg()), "폭우 점수에는 넣지 않음", false, false))));
		else items.add(areaB("R", "지형·재해", c.getWeightR(), den, b.getScoreR(), f == 0 && t == 0 ? "해당 없음" : (f >= t ? "침수 기록 연도 수 기준" : "경사 기준") + " (둘 중 큰 값)",
				List.of(new ScoreFactorTO("침수 기록 연도 수", b.getFloodCnt() + "개 연도", "5개 연도 이상 1", true, f > 0),
						new ScoreFactorTO("경사", String.format("%.1f°", b.getSlopeDeg()), "15° 부터, 25.8° 이상 1", true, t > 0))));
		items.add(areaB("A", "생활시설 접근성", c.getWeightA(), den, b.getScoreA(), b.getScoreA() != null && b.getScoreA() > 0 ? "두 거리 값의 평균" : "해당 없음",
				List.of(new ScoreFactorTO("버스정류장 거리", Math.round(b.getBusDist()) + "m", "500m 0 → 677.96m 이상 1", true, b.getBusDist() > 500),
						new ScoreFactorTO("의료기관 거리", Math.round(b.getHospDist()) + "m", "750m 0 → 1,122.64m 이상 1", true, b.getHospDist() > 750))));
		return items;
	}

	private static ScoreItemTO areaB(String key, String label, int w, double den, Double v, String summary, List<ScoreFactorTO> rows) {
		double share = den > 0 ? w * 100.0 / den : 0, pts = v == null ? 0 : share * v;
		ScoreItemTO it = new ScoreItemTO(key, label, (int) Math.round(pts), (int) Math.round(share), pts > 0, summary, summary, rows);
		it.setPointsText(v == null ? null : String.format("+%.1f", pts));
		return it;
	}

	/** 모델 B 연수 : 사용승인일 4 · 6 · 8자리 숫자, 연도 1900~2026 일 때만 (mapper-building ageExpr 와 같은 규칙) */
	static Integer ageB(String d) {
		if (d == null || !d.matches("\\d{4}(\\d{2}(\\d{2})?)?")) return null;
		int y = Integer.parseInt(d.substring(0, 4));
		return y >= 1900 && y <= 2026 ? 2026 - y : null;
	}

	/** members → 펼침 표의 요인 줄 */
	private static List<ScoreFactorTO> factorRows(Object[][] members) {
		List<ScoreFactorTO> rows = new ArrayList<>();
		for (Object[] m : members) {
			boolean on = (Boolean) m[2];
			rows.add(new ScoreFactorTO((String) m[3], (String) m[4], (String) m[5], on, on && (Integer) m[1] > 0));
		}
		return rows;
	}

	/** 하나라도 맞으면 묶음 점수 전부 (weight = 합 100 으로 환산한 묶음 점수). 요약 = 맞은 요인 이름 */
	private static ScoreItemTO anyGroup(String key, String label, double weight, Object[][] members) {
		List<String> hits = new ArrayList<>(), names = new ArrayList<>();
		boolean anyOn = false;
		for (Object[] m : members) {
			anyOn |= (Boolean) m[2];
			if ((Boolean) m[2] && (Integer) m[1] > 0) { hits.add((String) m[0]); names.add((String) m[3]); }
		}
		int max = (int) Math.round(weight);
		int points = hits.isEmpty() ? 0 : max;
		String desc = !anyOn ? "분석 조건에서 제외" : hits.isEmpty() ? "해당 없음" : String.join(" · ", hits);
		String summary = !anyOn ? "분석 조건에서 제외" : names.isEmpty() ? "해당 없음" : String.join(" · ", names);
		return new ScoreItemTO(key, label, points, anyOn ? max : 0, points > 0, desc, summary, factorRows(members));
	}

	/** 묶음 가중치 × (맞은 요인 수 ÷ 켜진 요인 수). desc "노후 · 2개 중 1개", 요약 "2개 중 1개" */
	private static ScoreItemTO ratioGroup(String key, String label, double weight, Object[][] members) {
		List<String> hits = new ArrayList<>();
		int on = 0;
		for (Object[] m : members) {
			if (!(Boolean) m[2]) continue;
			on++;
			if ((Integer) m[1] > 0) hits.add((String) m[0]);
		}
		int points = on == 0 ? 0 : (int) Math.round(weight * (double) hits.size() / on);
		String desc = on == 0 ? "분석 조건에서 제외"
				: (hits.isEmpty() ? "해당 없음" : String.join(" · ", hits)) + " · " + on + "개 중 " + hits.size() + "개";
		String summary = on == 0 ? "분석 조건에서 제외" : hits.isEmpty() ? "해당 없음" : on + "개 중 " + hits.size() + "개";
		return new ScoreItemTO(key, label, points, on == 0 ? 0 : (int) Math.round(weight), points > 0, desc, summary, factorRows(members));
	}

	private static String fmt(double v) {
		return v == Math.floor(v) ? String.valueOf((int) v) : String.valueOf(v);
	}

	// ---------- 구 경계 (볼록 껍질, 한 번만) ----------
	private synchronized Map<String, List<double[]>> rings() {
		if (ringCache != null) return ringCache;
		Map<String, List<double[]>> pts = new HashMap<>();
		for (DongTO p : dao.getCoordList()) {
			pts.computeIfAbsent(p.getSggCd(), k -> new ArrayList<>()).add(new double[] { p.getLng(), p.getLat() });
		}
		Map<String, List<double[]>> out = new HashMap<>();
		pts.forEach((k, v) -> out.put(k, convexHull(v)));
		ringCache = out;
		return out;
	}

	/** Andrew monotone chain. 입력 [lng, lat], 출력은 닫힌 고리 (첫 점 = 끝 점) */
	static List<double[]> convexHull(List<double[]> in) {
		List<double[]> p = new ArrayList<>(in);
		p.sort(Comparator.<double[]>comparingDouble(a -> a[0]).thenComparingDouble(a -> a[1]));
		if (p.size() < 3) return p;
		List<double[]> lower = new ArrayList<>(), upper = new ArrayList<>();
		for (double[] q : p) {
			while (lower.size() >= 2 && cross(lower.get(lower.size() - 2), lower.get(lower.size() - 1), q) <= 0) lower.remove(lower.size() - 1);
			lower.add(q);
		}
		for (int i = p.size() - 1; i >= 0; i--) {
			double[] q = p.get(i);
			while (upper.size() >= 2 && cross(upper.get(upper.size() - 2), upper.get(upper.size() - 1), q) <= 0) upper.remove(upper.size() - 1);
			upper.add(q);
		}
		List<double[]> hull = new ArrayList<>(lower.subList(0, lower.size() - 1));
		hull.addAll(upper.subList(0, upper.size() - 1));
		hull.add(hull.get(0));
		return hull;
	}

	private static double cross(double[] o, double[] a, double[] b) {
		return (a[0] - o[0]) * (b[1] - o[1]) - (a[1] - o[1]) * (b[0] - o[0]);
	}
}
