package itgeo.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import itgeo.to.AnalysisCriteriaTO;

/**
 * 점수 모델 선택 (app.properties app.analysis.model = legacy | b, 없으면 legacy).
 * 시작할 때 한 번 AnalysisCriteriaTO 에 넣어 두면 mapper-building.xml 이 test="modelB" 로 점수식을 고른다.
 * 요청 파라미터로는 바꿀 수 없다 (AnalysisCriteriaTO.getModel 은 설정값만 돌려준다).
 */
@Component
public class AnalysisModel {

	public AnalysisModel(@Value("${app.analysis.model:legacy}") String model) {
		AnalysisCriteriaTO.setModelConfig(model);
	}
}
