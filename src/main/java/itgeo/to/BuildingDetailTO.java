package itgeo.to;

import java.util.List;

import lombok.Data;

// api/map/building.do 응답 : 건물 + 점수 구성 + 거주자 집계
@Data
public class BuildingDetailTO {
	private BuildingTO building;
	private List<ScoreItemTO> scoreItems;
	private ResidentCountTO residents;
	// 모델 B 점수 계산 기준 (2026-11-24) : base | rain | cold. 화면이 응답 기준으로 그리게 (시나리오를 바꾼 뒤 늦게 온 응답과 섞이지 않게). legacy 면 null
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private String scenario;
	@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) private Integer coldYearMax;
}
