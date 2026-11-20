package wellfair.to;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

// 기상청 현재 기온 + 기상특보 (WeatherService)
@Data
public class WeatherTO {
	private boolean ok;
	private Double temp;                                  // 기온 ℃ (초단기실황 T1H)
	private List<String> warnings = new ArrayList<>();    // 서울에 발효 중인 특보 (한파주의보 …)
	private String baseTime;                              // 관측 기준 시각 HH:00 (1시간 단위)
	private String msg;
}
