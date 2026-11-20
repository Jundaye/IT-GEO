package itgeo.to;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// 상세 패널 "점수 구성" 을 펼쳤을 때의 요인 한 줄 : 요인 | 이 건물 | 기준 | 충족 (2026-11-17 K)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScoreFactorTO {
	private String name;       // 반지하 / 노후 / 저지대 ...
	private String value;      // 이 건물 값 : "지하 거주 있음" / "31년 경과" / "15.0m" / "0.5%" / "자료 없음"
	private String criterion;  // 기준 : "지하 거주 등록" / "준공 30년 이상" / "해발 15m 이하" / "공시가 백분위 20% 이하" (슬라이더로 바꾼 값 그대로)
	private boolean on;        // 분석 조건에서 켜졌는지
	private boolean hit;       // 이 건물이 맞는지
}
