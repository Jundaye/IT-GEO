package itgeo.to;

import lombok.Data;

// api/map/floorStat.do 응답 한 줄 = 건물의 거주 층 하나 (3D 층 색칠용 집계. 이름 · 나이 같은 개인정보는 없다)
@Data
public class FloorStatTO {
	private String resFlr;   // 거주 층 (residency.res_flr : "지하1층" · "3층" · "옥탑층")
	private int hhCnt;       // 그 층 가구 수
	private int vulnHhCnt;   // 그 층 취약 가구 수 (가구 가점 10 이상, mapper-resident.vulnMin)
}
