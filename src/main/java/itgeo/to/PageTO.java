package itgeo.to;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Data;

// 페이지 응답 (결과 리스트 등)
@Data
public class PageTO<T> {
	private int page;
	private int size;
	private int total;
	private int totalPages;
	private List<T> list;
	// 모델 B 결과 리스트만 (5차) : 조회 범위와 상관없이 "조건 충족 전체 N동 / 그중 우선확인 후보 M동". legacy · 주소 검색은 null
	@JsonInclude(JsonInclude.Include.NON_NULL) private Integer matchTotal;   // null 이면 응답에 넣지 않는다 (legacy 응답 모양 그대로)
	@JsonInclude(JsonInclude.Include.NON_NULL) private Integer candTotal;
	// 한파 시나리오만 (2026-11-24) : 기본 B 후보 중 한파 현장확인 대상 · 사용승인일 없는 후보(확인 불가). 그 밖에는 null (응답에 없음)
	@JsonInclude(JsonInclude.Include.NON_NULL) private Integer coldTotal;
	@JsonInclude(JsonInclude.Include.NON_NULL) private Integer coldUnknown;
	@JsonInclude(JsonInclude.Include.NON_NULL) private String scenario;   // 모델 B 계산 기준 base | rain | cold

	public PageTO() {}

	public PageTO(int page, int size, int total, List<T> list) {
		this.page = page;
		this.size = size;
		this.total = total;
		this.totalPages = size > 0 ? (int) Math.ceil(total / (double) size) : 0;
		this.list = list;
	}
}
