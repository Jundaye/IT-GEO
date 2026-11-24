package itgeo.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import itgeo.to.VisitBundleTO;
import itgeo.to.VisitCandTO;

/**
 * 하루치 방문 묶음 추천 (K-means + 우선순위). DB 를 모르는 순수 계산이라 시뮬레이션(VisitBundlerSim)도 같은 코드를 쓴다.
 *
 * 1) 후보 정렬 : 최근 방문 부재 → 점수 → 취약 가구 수 → 가구 우선순위 합 → bld_id
 * 2) 후보 좌표로 K-means 1회 (K = ⌈후보 수 ÷ 하루 정원⌉, 서로 다른 좌표 수가 상한. k-means++ 초기화, 시드 0, 10번 중 가장 촘촘한 결과)
 * 3) 남은 후보 중 1순위 = 기준 건물 (그날 포함 보장)
 * 4) 기준 건물에서 직선 1km 안 후보 중 90점 이상 · 최근 방문 부재를 우선순위순으로
 * 5) 같은 군집 후보를 우선순위순으로
 * 6) 자리가 남으면 1km 안 다른 군집 후보도 우선순위순으로 (기준 포함 최대 정원)
 * 7) 담긴 건물을 빼고 3) 부터 반복
 * 1km · 정원 · 90점은 업무 규칙이고, 군집은 "1km 안 후보가 많을 때 누구를 같은 날 담을지"에만 쓴다.
 */
public final class VisitBundler {

	public static final int RADIUS_M = 1000;
	public static final int HIGH_SCORE = 90;
	public static final int DEFAULT_PER_DAY = 6;
	public static final int KMEANS_SEED = 0, KMEANS_INIT = 10, KMEANS_ITER = 300;

	private VisitBundler() {
	}

	/** 우선순위 : 부재 먼저 → 점수 → 취약 가구 → 우선순위 합 → bld_id */
	public static final Comparator<VisitCandTO> PRIORITY = Comparator
			.comparing((VisitCandTO c) -> !c.isAbsent())
			.thenComparing(VisitCandTO::getScore, Comparator.reverseOrder())
			.thenComparing(VisitCandTO::getVulnHhCnt, Comparator.reverseOrder())
			.thenComparing(VisitCandTO::getPrioritySum, Comparator.reverseOrder())
			.thenComparing(c -> c.getBldId() == null ? "" : c.getBldId());

	/** 묶는 방식. 서비스는 KMEANS 만 쓰고, RULE(1km 안 우선순위순) · NEAREST(1km 안 가까운 순)는 비교 시뮬레이션용 */
	public enum Mode { KMEANS, RULE, NEAREST }

	/** cands : 좌표가 있는 후보만 (호출한 쪽에서 거른다) */
	public static List<VisitBundleTO> bundle(List<VisitCandTO> cands, int perDay) {
		return bundle(cands, perDay, Mode.KMEANS);
	}

	public static List<VisitBundleTO> bundle(List<VisitCandTO> cands, int perDay, Mode mode) {
		boolean useKmeans = mode == Mode.KMEANS;
		int cap = Math.max(1, perDay);
		List<VisitCandTO> c = new ArrayList<>(cands);
		c.sort(PRIORITY);
		int n = c.size();
		List<VisitBundleTO> out = new ArrayList<>();
		if (n == 0) return out;
		for (int i = 0; i < n; i++) c.get(i).setRank(i + 1);

		double[][] xy = toMeters(c);
		int[] lab = new int[n];
		if (useKmeans) {
			int k = Math.min((n + cap - 1) / cap, distinct(xy));
			lab = kmeans(xy, k, KMEANS_SEED, KMEANS_INIT, KMEANS_ITER);
		}
		for (int i = 0; i < n; i++) c.get(i).setCluster(useKmeans ? lab[i] : -1);

		boolean[] used = new boolean[n];
		int left = n, no = 0;
		while (left > 0) {
			int sd = -1;
			for (int i = 0; i < n; i++) if (!used[i]) { sd = i; break; }
			List<Integer> near = new ArrayList<>();              // 우선순위순 (c 가 이미 정렬돼 있음)
			for (int i = 0; i < n; i++) if (!used[i] && i != sd && dist(xy, i, sd) <= RADIUS_M) near.add(i);

			List<Integer> pick = new ArrayList<>();
			pick.add(sd);
			Map<Integer, String> why = new HashMap<>();
			if (mode == Mode.RULE) {
				for (int i : near) { if (pick.size() >= cap) break; pick.add(i); why.put(i, "반경 1km 안 추가"); }
			} else {
				for (int i : near) {
					if (pick.size() >= cap) break;
					VisitCandTO v = c.get(i);
					if (v.isAbsent() || v.getScore() >= HIGH_SCORE) { pick.add(i); why.put(i, v.isAbsent() ? "최근 방문 부재" : HIGH_SCORE + "점 이상"); }
				}
				if (mode == Mode.NEAREST) {
					final int s0 = sd;
					near.sort(Comparator.comparingDouble(i -> dist(xy, i, s0)));
				}
				if (useKmeans) for (int i : near) {
					if (pick.size() >= cap) break;
					if (!pick.contains(i) && lab[i] == lab[sd]) { pick.add(i); why.put(i, "기준 건물과 같은 군집"); }
				}
				for (int i : near) {
					if (pick.size() >= cap) break;
					if (!pick.contains(i)) { pick.add(i); why.put(i, "반경 1km 안 추가"); }
				}
			}

			VisitBundleTO b = new VisitBundleTO();
			b.setNo(++no);
			Map<String, Integer> dongs = new HashMap<>();
			for (int i : pick) {
				VisitCandTO v = c.get(i);
				used[i] = true;
				left--;
				v.setDistM((int) Math.round(dist(xy, i, sd)));
				v.getReasons().clear();
				if (i == sd) {
					v.getReasons().add("남은 후보 중 1순위");
					if (v.isAbsent()) v.getReasons().add("최근 방문 부재");
				} else {
					v.getReasons().add(why.get(i));
				}
				b.getItems().add(v);
				if (v.getEmdNm() != null) dongs.merge(v.getEmdNm(), 1, Integer::sum);
			}
			b.setArea(dongs.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(""));
			double[][] pts = new double[pick.size()][];
			for (int j = 0; j < pick.size(); j++) pts[j] = xy[pick.get(j)];
			b.setKm(Math.round(pathLen(pts) / 100.0) / 10.0);
			out.add(b);
		}
		return out;
	}

	/* ---------------------------------------------------------------- 좌표 · 거리 */

	/** 위경도 → 후보 평균 위도 기준 평면 미터 (서울 3개 구 범위에서 EPSG:5186 과 0.5% 안쪽 차이) */
	public static double[][] toMeters(List<VisitCandTO> c) {
		double lat0 = c.stream().mapToDouble(VisitCandTO::getLat).average().orElse(37.5);
		double kx = 111320.0 * Math.cos(Math.toRadians(lat0)), ky = 110540.0;
		double[][] xy = new double[c.size()][2];
		for (int i = 0; i < c.size(); i++) {
			xy[i][0] = c.get(i).getLng() * kx;
			xy[i][1] = c.get(i).getLat() * ky;
		}
		return xy;
	}

	static double dist(double[][] xy, int a, int b) {
		return Math.hypot(xy[a][0] - xy[b][0], xy[a][1] - xy[b][1]);
	}

	static int distinct(double[][] xy) {
		Set<String> s = new HashSet<>();
		for (double[] p : xy) s.add(Math.round(p[0] * 10) + ":" + Math.round(p[1] * 10));
		return Math.max(1, s.size());
	}

	/** 첫 점에서 가까운 순으로 잇고 2-opt 로 교차를 푼 편도 길이 (m). 복귀 구간 없음 */
	public static double pathLen(double[][] p) {
		int n = p.length;
		if (n < 2) return 0;
		List<Integer> o = new ArrayList<>();
		o.add(0);
		boolean[] in = new boolean[n];
		in[0] = true;
		for (int s = 1; s < n; s++) {
			int last = o.get(o.size() - 1), best = -1;
			double bd = Double.MAX_VALUE;
			for (int j = 0; j < n; j++) {
				if (in[j]) continue;
				double d = Math.hypot(p[j][0] - p[last][0], p[j][1] - p[last][1]);
				if (d < bd) { bd = d; best = j; }
			}
			o.add(best);
			in[best] = true;
		}
		boolean imp = true;
		while (imp) {
			imp = false;
			for (int i = 1; i < n - 1; i++) {
				for (int j = i + 1; j < n; j++) {
					List<Integer> t = new ArrayList<>(o.subList(0, i));
					List<Integer> mid = new ArrayList<>(o.subList(i, j + 1));
					java.util.Collections.reverse(mid);
					t.addAll(mid);
					t.addAll(o.subList(j + 1, n));
					if (len(p, t) + 1e-9 < len(p, o)) { o = t; imp = true; }
				}
			}
		}
		return len(p, o);
	}

	private static double len(double[][] p, List<Integer> o) {
		double s = 0;
		for (int k = 0; k + 1 < o.size(); k++) s += Math.hypot(p[o.get(k + 1)][0] - p[o.get(k)][0], p[o.get(k + 1)][1] - p[o.get(k)][1]);
		return s;
	}

	/* ---------------------------------------------------------------- K-means */

	/** Lloyd K-means, k-means++ 초기화. 같은 입력·시드면 같은 결과. nInit 번 돌려 군집 안 거리제곱합이 가장 작은 결과 */
	public static int[] kmeans(double[][] x, int k, long seed, int nInit, int maxIter) {
		int n = x.length;
		int[] best = new int[n];
		if (n == 0 || k <= 1) return best;
		Random rnd = new Random(seed);
		double bestInertia = Double.MAX_VALUE;
		for (int run = 0; run < nInit; run++) {
			double[][] cen = initPlusPlus(x, k, rnd);
			int[] lab = new int[n];
			java.util.Arrays.fill(lab, -1);
			for (int it = 0; it < maxIter; it++) {
				boolean changed = false;
				for (int i = 0; i < n; i++) {
					int bj = 0;
					double bd = Double.MAX_VALUE;
					for (int j = 0; j < k; j++) {
						double d = sq(x[i], cen[j]);
						if (d < bd) { bd = d; bj = j; }
					}
					if (lab[i] != bj) { lab[i] = bj; changed = true; }
				}
				double[][] sum = new double[k][2];
				int[] cnt = new int[k];
				for (int i = 0; i < n; i++) { sum[lab[i]][0] += x[i][0]; sum[lab[i]][1] += x[i][1]; cnt[lab[i]]++; }
				for (int j = 0; j < k; j++) {
					if (cnt[j] > 0) { cen[j][0] = sum[j][0] / cnt[j]; cen[j][1] = sum[j][1] / cnt[j]; }
					else {                                     // 빈 군집 : 현재 중심에서 가장 먼 점으로 옮긴다
						int far = 0; double fd = -1;
						for (int i = 0; i < n; i++) { double d = sq(x[i], cen[lab[i]]); if (d > fd) { fd = d; far = i; } }
						cen[j] = x[far].clone();
						changed = true;
					}
				}
				if (!changed) break;
			}
			double inertia = 0;
			for (int i = 0; i < n; i++) inertia += sq(x[i], cen[lab[i]]);
			if (inertia < bestInertia - 1e-9) { bestInertia = inertia; best = lab.clone(); }
		}
		return best;
	}

	private static double[][] initPlusPlus(double[][] x, int k, Random rnd) {
		int n = x.length;
		double[][] cen = new double[k][];
		cen[0] = x[rnd.nextInt(n)].clone();
		double[] d2 = new double[n];
		for (int i = 0; i < n; i++) d2[i] = sq(x[i], cen[0]);
		for (int j = 1; j < k; j++) {
			double tot = 0;
			for (double v : d2) tot += v;
			int pick = n - 1;
			if (tot > 0) {
				double r = rnd.nextDouble() * tot, acc = 0;
				for (int i = 0; i < n; i++) { acc += d2[i]; if (acc >= r) { pick = i; break; } }
			} else pick = rnd.nextInt(n);
			cen[j] = x[pick].clone();
			for (int i = 0; i < n; i++) d2[i] = Math.min(d2[i], sq(x[i], cen[j]));
		}
		return cen;
	}

	private static double sq(double[] a, double[] b) {
		double dx = a[0] - b[0], dy = a[1] - b[1];
		return dx * dx + dy * dy;
	}
}
