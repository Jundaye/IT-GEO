package itgeo.to;

import lombok.Data;

/**
 * 보관함 = vulnerability_list(분석 1회, 개인) + vulnerability_factor(그 분석 안의 건물 1동).
 * 클래스명은 화면 기준(Archive) 그대로.
 */
@Data
public class ArchiveTO {
	// vulnerability_list
	private Long vulnerabilityId;
	private long userId;
	private String sggCd;
	private String title;
	private String criteria;        // 가중치·필터 JSON 문자열
	private String analyzedAt;

	// vulnerability_factor
	private Long factorId;
	private String bldId;
	private Integer vulnerabilityScore;
	private String vulnerabilityGrade;
	private String evaluatedAt;
	// 저장 점수의 산식 : "b" = 점수 모델 B 환산 점수로 저장 (보관함 리스트 criteria JSON 의 model). null = 산식 정보 없이 저장된 기존 점수
	private String scoreModel;
	// 모델 B 저장 점수의 계산 기준 (2026-11-24) : "B 기본" | "B 폭우" | "B 기본 + 한파 현장확인 표시". 그 전 저장분 · legacy 는 null (mapper-archive.savedBasisExpr)
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String scoreBasis;
	/** 폭우 시나리오 점수로 저장했는지 (기본 B 환산 점수와 다른 기준) */
	@com.fasterxml.jackson.annotation.JsonIgnore public boolean isRainBasis() { return "B 폭우".equals(scoreBasis); }
}
