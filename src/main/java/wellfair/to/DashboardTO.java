package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// 대시보드 한 화면 치 숫자 (DashboardService.getDashboard)
@Data
public class DashboardTO {
	private int highTotal;                                    // 고위험 가구 전체 (내 현장확인 리스트 건물)
	private int highChecked;                                  // 그중 확인한 가구
	private List<DongRiskTO> byDong = new ArrayList<>();      // 동별 남은 고위험 가구 (남은 곳 많은 순)
	private List<MonthStatTO> monthly = new ArrayList<>();    // 월별 추이 = 확인 완료 건물 수 (최근 6개월, 오래된 달부터)
	private ReportRefTO lastReport;                           // 보고서 박스 "지난 보고서 보기" (없으면 null)
	private List<RecentViewTO> recent = new ArrayList<>();    // 이어서 보기 (최대 3)

	public int getHighRemain() { return Math.max(0, highTotal - highChecked); }
	public int getHighPct() { return pct(highChecked, highTotal); }

	public static int pct(int part, int total) { return total == 0 ? 0 : (int) Math.round(part * 100.0 / total); }

	@Data public static class DongRiskTO { private String emdNm; private int total; private int checked; public int getRemain() { return Math.max(0, total - checked); } }
	@Data public static class MonthStatTO { private String ym; private int checked; }
	@Data public static class DayMarkTO { private String day; private int cnt; private int revisit; }

	/** 저장된 보고서 한 건의 머리 정보 (ym = yyyy-MM, createdAt = MM.dd) */
	@Data public static class ReportRefTO {
		private long reportId; private String ym; private String createdAt;
		public int getMonth() { return ym == null || ym.length() < 7 ? 0 : Integer.parseInt(ym.substring(5, 7)); }
	}
}
