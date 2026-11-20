package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * 월간 현황 보고서의 "숫자" 부분 (보고서 1~4번). 시스템이 대시보드와 같은 기준으로 채우고, AI 는 이 값을 읽고 5번 종합 의견만 쓴다.
 * monthly_report.content 에 {"facts": 이것, "opinion": [..]} JSON 으로 저장해서 "지난 보고서 보기" 때 그대로 다시 그린다.
 */
@Data
public class ReportFactsTO {
	private String ym;              // 2026-11
	private int year;
	private int month;
	private String periodFrom;      // 2026-11-01
	private String periodTo;        // 작성일 (이번 달이면 오늘, 지난달 보고서면 말일)
	private String writtenAt;       // 2026-11-20
	private String area;            // 구로구 / 서울시 3개 구
	private String writer;          // 구로구청 복지정책과 김구로 주무관

	// 1. 고위험 가구 확인 현황 (누적)
	private int highTotal;
	private int highChecked;
	private int highRemain;
	private int highPct;
	private int prevPct;            // 이번 달 1일 0시 기준 확인율 (전월 말)

	// 2. 동별 남은 고위험 가구 상위 3개 동
	private List<DashboardTO.DongRiskTO> topDong = new ArrayList<>();

	// 3. 월별 현장 확인 완료 (건물 수, 최근 6개월)
	private List<DashboardTO.MonthStatTO> monthly = new ArrayList<>();

	// 4. 이번 달 방문 일정 (업무 캘린더 work_schedule)
	private int schedTotal;
	private int schedDone;          // 지금 시각까지 지난 일정
	public int getSchedRemain() { return Math.max(0, schedTotal - schedDone); }
	public int getSchedPct() { return DashboardTO.pct(schedDone, schedTotal); }

	// 참고 (AI 의견에만 쓰고 표에는 안 넣음)
	private List<String> warnings = new ArrayList<>();   // 지금 발효 중인 기상 특보
}
