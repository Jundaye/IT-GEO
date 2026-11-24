package itgeo.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// 하루치 방문 묶음 1개 (no = 1 이 오늘 추천)
@Data
public class VisitBundleTO {
	private int no;
	private String area;                // 가장 많은 법정동 ("구로동")
	private double km;                  // 묶음 안 직선 이동 (기준 건물부터 가까운 순 + 2-opt, km)
	private List<VisitCandTO> items = new ArrayList<>();   // [0] = 기준 건물
}
