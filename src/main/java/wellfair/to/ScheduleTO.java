package wellfair.to;

import lombok.Data;

// 업무 캘린더 일정 1건 (work_schedule). 대시보드는 읽기만, 추가 · 수정은 캘린더 페이지
@Data
public class ScheduleTO {
	private long scheduleId;
	private Long planId;          // 현장확인 리스트 항목 (없을 수 있음)
	private String bldId;
	private String schedDate;     // yyyy-MM-dd
	private String startTime;     // HH:mm
	private String title;
	private String schedType;     // VISIT | REVISIT | ETC
	private String memo;
	private String checkResult;   // 연결된 항목의 최근 확인 결과 MET | ABSENT | REFUSED | OTHER (없으면 null)
}
