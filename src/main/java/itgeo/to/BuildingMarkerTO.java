package itgeo.to;

import lombok.Data;

// 확대 상태 건물 핀 / 묶음 안 1동 / 현장 확인됨 핀
@Data
public class BuildingMarkerTO {
	private String bldId;
	private double lat;
	private double lng;
	private double score;   // legacy 는 정수값, 모델 B 는 환산 점수 (소수 유지)
	private Double rawScore;       // 모델 B : 계산 점수
	private Integer displayScore;  // 모델 B : 화면 표시 (환산 점수 버림)
	private int vulnHhCnt;      // 취약 가구 수 (툴팁)
	private String bldNm;
	private String addr;
	private String useNm;       // 핀 머리 그림(용도) 용
	private boolean checked;    // 기간 안 현장 확인 기록 있음 (핀에 파란 체크 배지)
	private String checkedAt;   // 현장 확인됨 핀일 때만 (yyyy-MM-dd)
}
