package itgeo.to;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 상세 패널 "점수 구성" 한 줄 = 위험 유형 묶음 ("+45 주거 공간 (반지하 · 옥탑)")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreItemTO {
	private String key;     // space | bld | flood | econ
	private String label;   // 주거 공간
	private int points;     // 맞으면 묶음 가중치, 아니면 0
	private int weight;     // 설정된 묶음 가중치 (묶음 요인이 전부 꺼져 있으면 0)
	private boolean hit;    // 이 건물이 해당하는지
	private String desc;    // 맞은 요인 이름 ("반지하 · 옥탑") / "해당 없음" / "분석 조건에서 제외"
	private String summary; // 묶음 줄의 짧은 요약 : "반지하" / "2개 중 2개" / "해당 없음" / "분석 조건에서 제외" (2026-11-17 K)
	private List<ScoreFactorTO> factors;
	private String pointsText;   // 모델 B : 기여 점수 소수 표시 ("+12.5"). 없으면 화면은 points

	public ScoreItemTO(String key, String label, int points, int weight, boolean hit, String desc, String summary, List<ScoreFactorTO> factors) {
		this(key, label, points, weight, hit, desc, summary, factors, null);
	}   // 펼쳤을 때의 요인별 표 (요인 | 이 건물 | 기준 | 충족). 없으면 화면은 desc 한 줄로 그린다
}
