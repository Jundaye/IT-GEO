package itgeo.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// 하루치 방문 묶음 추천의 후보 1곳 = 내 보관함 건물 1동 (같은 건물은 가장 최근 저장 1건)
@Data
public class VisitCandTO {
	private long factorId;
	private String bldId;
	private String sggCd;
	private String emdNm;
	private String addr;
	private String bldNm;
	private String useNm;
	private Double lat;                 // 좌표 없는 건물은 추천에서 빼고 따로 센다
	private Double lng;
	private int score;                  // 보관함 저장 점수 (vulnerability_score)
	private int vulnHhCnt;              // 취약 가구 수 (bld_resident_stat)
	private int prioritySum;            // 가구 우선순위 합
	private String factorText;
	private String lastResult;          // 이 건물의 가장 최근 방문 결과 MET | ABSENT | REFUSED | OTHER (없으면 null)
	private String lastVisitAt;         // yyyy-MM-dd
	private boolean pending;            // 내 다른 현장확인 리스트에 담겨 있고 아직 확인 전 (= 이미 일정이 잡힘)

	/* ---- 추천 계산 결과 (VisitBundler 가 채움) ---- */
	private int rank;                   // 우선순위 (1부터)
	private int cluster = -1;           // K-means 군집 번호
	private int distM;                  // 기준 건물에서 직선거리 (m)
	private List<String> reasons = new ArrayList<>();   // 선정 이유 ("남은 후보 중 1순위", "최근 방문 부재", "90점 이상", "기준 건물과 같은 군집", "반경 1km 안 추가")

	public boolean isAbsent() {
		return "ABSENT".equals(lastResult);
	}

	/** 목록에 쓰는 짧은 이름 : "구로동 123-4 ○○빌라" */
	public String getTitle() {
		String a = addr == null ? "" : addr.replace("서울특별시 ", "");
		int i = a.indexOf("구 ");
		if (i >= 0) a = a.substring(i + 2);
		String n = bldNm != null && !bldNm.isBlank() ? bldNm : (useNm == null ? "" : useNm);
		return (a + " " + n).trim();
	}
}
