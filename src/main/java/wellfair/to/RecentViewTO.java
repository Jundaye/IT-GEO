package wellfair.to;

import lombok.Data;

// 이어서 보기 (recent_view) : 탭마다 마지막으로 본 화면 1건
@Data
public class RecentViewTO {
	private long userId;
	private String menu;          // map | field | archive
	private String url;           // 컨텍스트 경로 뒤 주소 (fielddetail.do?listId=3)
	private String title;
	private String sub;
	private String viewedAt;      // yyyy-MM-dd HH:mm
}
