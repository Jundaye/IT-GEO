package itgeo.common;

/**
 * 모델 B 환산 점수 (VSCODE_지시문_점수모델B_반영_1123.md "환산 점수"). 순위는 바뀌지 않는다.
 *  경계(boundary) = 3개 구 점수 대상 전체(48,463동)에서 계산 점수가 높은 순 300번째 점수, 최고(max) = 가장 높은 계산 점수. 둘 다 지금 가중치로
 *  계산 점수 < 경계 : 환산 = 계산 × 80 / 경계
 *  계산 점수 ≥ 경계 : 환산 = 80 + (계산 - 경계) × 20 / (최고 - 경계)   (최고 = 경계면 100)
 *  경계 = 0 이면 환산하지 않는다 (계산 점수 그대로)
 * 화면 표시는 display() = 소수점 이하 버림 정수. 순위 · 후보 판정은 계산 점수(또는 inverse 로 되돌린 기준)로 하고 표시 정수는 쓰지 않는다.
 */
public final class ScoreScale {

	private ScoreScale() {}

	/** 아주 작은 계산 오차로 60.0 이 59.999999 가 되어 59 로 버려지지 않게 하는 여유 */
	private static final double EPS = 1e-9;

	public static double scaled(double raw, double boundary, double max) {
		if (boundary <= 0) return raw;
		if (raw >= boundary) {                       // 우선확인 후보 : 정확히 80 이상
			if (max <= boundary) return 100;
			return Math.max(80, Math.min(100, 80 + (raw - boundary) * 20 / (max - boundary)));
		}
		return Math.min(raw * 80 / boundary, Math.nextDown(80.0));   // 후보가 아니면 80 미만
	}

	/** 환산 m 점 이상 ⇔ 계산 점수 inverse(m) 이상 (SQL 필터 · 색 구간 집계용) */
	public static double inverse(double m, double boundary, double max) {
		if (boundary <= 0) return m;
		if (m >= 80) return max <= boundary ? boundary : boundary + (m - 80) * (max - boundary) / 20;
		return Math.max(0, m) * boundary / 80;
	}

	/** 화면 표시 = 소수점 이하 버림 */
	public static int display(double scaled) {
		return (int) Math.floor(scaled + EPS);
	}
}
