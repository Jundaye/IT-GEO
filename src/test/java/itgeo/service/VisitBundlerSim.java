package itgeo.service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import itgeo.to.VisitBundleTO;
import itgeo.to.VisitCandTO;

/**
 * 하루치 방문 묶음 추천 비교 시뮬레이션 (DB · Tomcat 없이). 서비스와 같은 VisitBundler 코드로 돈다.
 * 입력 CSV : sample,bld_id,lat,lng,score,vuln,prio,absent,emd  (sample 0 = 시연 60곳, 1~200 = 비교 표본)
 * 실행: mvn -q test-compile dependency:build-classpath -Dmdep.outputFile=cp.txt
 *       java -cp "target/classes:target/test-classes:$(cat cp.txt)" itgeo.service.VisitBundlerSim bundle_samples.csv
 * 출발지 = 구로구청. 거리 = 직선, 가까운 순 + 2-opt 편도 (복귀 없음)
 */
public class VisitBundlerSim {

	static final double OFFICE_LAT = 37.4954, OFFICE_LNG = 126.8876;   // 구로구청 (실험과 같은 값)

	public static void main(String[] args) throws Exception {
		Map<Integer, List<VisitCandTO>> samples = new LinkedHashMap<>();
		List<String> lines = Files.readAllLines(Path.of(args.length > 0 ? args[0] : "bundle_samples.csv"), StandardCharsets.UTF_8);
		for (String ln : lines.subList(1, lines.size())) {
			String[] f = ln.split(",");
			VisitCandTO c = new VisitCandTO();
			c.setBldId(f[1]); c.setLat(Double.parseDouble(f[2])); c.setLng(Double.parseDouble(f[3]));
			c.setScore(Integer.parseInt(f[4])); c.setVulnHhCnt(Integer.parseInt(f[5])); c.setPrioritySum(Integer.parseInt(f[6]));
			c.setLastResult("1".equals(f[7]) ? "ABSENT" : null); c.setEmdNm(f.length > 8 ? f[8] : "");
			samples.computeIfAbsent(Integer.parseInt(f[0]), k -> new ArrayList<>()).add(c);
		}
		VisitBundler.Mode[] modes = VisitBundler.Mode.values();
		Map<VisitBundler.Mode, double[]> sum = new HashMap<>();   // 묶음 수, 묶음 안 km, 구청 포함 km, 상위20 5일, 상위10 3일, 첫날 같음
		for (VisitBundler.Mode m : modes) sum.put(m, new double[6]);
		int nSim = 0, kmWinsRule = 0;
		for (Map.Entry<Integer, List<VisitCandTO>> e : samples.entrySet()) {
			Map<VisitBundler.Mode, double[]> one = new HashMap<>();
			String firstRule = null;
			for (VisitBundler.Mode m : new VisitBundler.Mode[] { VisitBundler.Mode.RULE, VisitBundler.Mode.KMEANS, VisitBundler.Mode.NEAREST }) {
				List<VisitCandTO> cs = copy(e.getValue());
				List<VisitBundleTO> B = VisitBundler.bundle(cs, 6, m);
				double[] r = eval(cs, B);
				String first = B.get(0).getItems().stream().map(VisitCandTO::getBldId).sorted().reduce("", String::concat);
				if (m == VisitBundler.Mode.RULE) firstRule = first;
				r[5] = first.equals(firstRule) ? 1 : 0;
				one.put(m, r);
				if (e.getKey() == 0) {
					System.out.printf("[시연 60곳] %-8s 묶음 %d · 묶음 안 %.1fkm · 구청 포함 %.1fkm · 상위20 5일 내 %d · 상위10 3일 내 %d · 첫날 기존과 같음 %s%n",
							m, (int) r[0], r[1], r[2], (int) r[3], (int) r[4], r[5] == 1);
					if (m == VisitBundler.Mode.KMEANS) for (VisitBundleTO b : B.subList(0, 1))
						for (VisitCandTO c : b.getItems()) System.out.println("    1일차 " + c.getRank() + "순위 " + c.getEmdNm() + " " + c.getScore() + "점 " + c.getReasons() + " " + c.getDistM() + "m");
				}
			}
			if (e.getKey() == 0) continue;
			nSim++;
			for (VisitBundler.Mode m : modes) for (int k = 0; k < 6; k++) sum.get(m)[k] += one.get(m)[k];
			if (one.get(VisitBundler.Mode.KMEANS)[2] < one.get(VisitBundler.Mode.RULE)[2]) kmWinsRule++;
		}
		System.out.println("--- " + nSim + "회 평균 ---");
		String[] names = { "묶음 수", "묶음 안 이동(km)", "구청 출발 포함(km)", "상위20 중 5일 내", "상위10 중 3일 내", "첫날 기존과 같음" };
		System.out.printf("%-18s %10s %10s %10s%n", "", "RULE", "KMEANS", "NEAREST");
		for (int k = 0; k < 6; k++)
			System.out.printf("%-18s %10.2f %10.2f %10.2f%n", names[k], sum.get(VisitBundler.Mode.RULE)[k] / nSim, sum.get(VisitBundler.Mode.KMEANS)[k] / nSim, sum.get(VisitBundler.Mode.NEAREST)[k] / nSim);
		double r2 = sum.get(VisitBundler.Mode.RULE)[2], k2 = sum.get(VisitBundler.Mode.KMEANS)[2];
		System.out.printf("KMEANS vs RULE : 구청 포함 %.1f%% 감소, 짧았던 표본 %.0f%%%n", 100 * (1 - k2 / r2), 100.0 * kmWinsRule / nSim);
		System.out.printf("KMEANS - NEAREST : 상위20 중 5일 내 %+.2f곳%n", (sum.get(VisitBundler.Mode.KMEANS)[3] - sum.get(VisitBundler.Mode.NEAREST)[3]) / nSim);
	}

	static List<VisitCandTO> copy(List<VisitCandTO> src) {
		List<VisitCandTO> out = new ArrayList<>();
		for (VisitCandTO s : src) {
			VisitCandTO c = new VisitCandTO();
			c.setBldId(s.getBldId()); c.setLat(s.getLat()); c.setLng(s.getLng()); c.setScore(s.getScore());
			c.setVulnHhCnt(s.getVulnHhCnt()); c.setPrioritySum(s.getPrioritySum()); c.setLastResult(s.getLastResult()); c.setEmdNm(s.getEmdNm());
			out.add(c);
		}
		return out;
	}

	static double[] eval(List<VisitCandTO> cs, List<VisitBundleTO> B) {
		double inner = 0, depot = 0;
		Map<String, Integer> day = new HashMap<>();
		VisitCandTO office = new VisitCandTO();
		office.setLat(OFFICE_LAT); office.setLng(OFFICE_LNG);
		for (VisitBundleTO b : B) {
			List<VisitCandTO> pts = new ArrayList<>(b.getItems());
			inner += VisitBundler.pathLen(VisitBundler.toMeters(pts));
			List<VisitCandTO> withOffice = new ArrayList<>();
			withOffice.add(office); withOffice.addAll(pts);
			depot += VisitBundler.pathLen(VisitBundler.toMeters(withOffice));
			for (VisitCandTO c : b.getItems()) day.put(c.getBldId(), b.getNo());
		}
		int t20 = 0, t10 = 0;
		for (VisitCandTO c : cs) {
			if (c.getRank() <= 20 && day.get(c.getBldId()) <= 5) t20++;
			if (c.getRank() <= 10 && day.get(c.getBldId()) <= 3) t10++;
		}
		return new double[] { B.size(), inner / 1000, depot / 1000, t20, t10, 0 };
	}
}
