-- =====================================================================
-- 시연용 데이터 (하루치 방문 묶음 추천)  05 다음에 실행
--   김구로(guro) 보관함에 '구로구 80점 이상 시연용 60곳' 저장 목록 추가
--     (비교 시뮬레이션 VisitBundlerSim 의 시연 표본 = 구로구 80점 이상 904동 중 60곳, 점수도 표본 값 그대로)
--   그중 6곳은 지난 순회(2026-11-12)에서 부재 → visit_history ABSENT + survey_record 남김
--     → 결과가 기록된 리스트라 '이미 일정'으로 빠지지 않고, 추천에서 부재 우선 후보가 된다
--   05 의 보관함 5동도 같은 보관함이라 후보에 섞인다 (만남 1동 · 리스트에 잡힌 1동은 제외, 부재 1동은 우선)
--   다시 넣고 싶으면 맨 아래 "지우기" 블록을 먼저 실행
-- =====================================================================
USE itgeo;
SET SQL_SAFE_UPDATES = 0;

SELECT user_id INTO @guro FROM users WHERE login_id = 'guro';

DROP TEMPORARY TABLE IF EXISTS demo_bundle;
CREATE TEMPORARY TABLE demo_bundle (bld_id VARCHAR(28) PRIMARY KEY, score DECIMAL(6,2), absent TINYINT);
INSERT INTO demo_bundle (bld_id, score, absent) VALUES
  ('1975190601144431465700000000', 100, 1),
  ('1971190497924428529400000000', 90, 1),
  ('1993189327264452634400000000', 90, 1),
  ('1991190106224440386600000000', 90, 1),
  ('1993189565644441884200000000', 88, 1),
  ('1991186986204427973600000000', 80, 1),
  ('1993186325194435275100000000', 100, 0),
  ('1993186845824435354600000000', 90, 0),
  ('1995186808764435348500000000', 90, 0),
  ('1991187105464434906400000000', 90, 0),
  ('1996187054924431284800000000', 90, 0),
  ('1994190354144446661300000000', 88, 0),
  ('1991186574634443922600000000', 80, 0),
  ('1987187733594443691900000000', 80, 0),
  ('1993189984384431646300000000', 80, 0),
  ('1995189360924437827700000000', 80, 0),
  ('2007190129444440592900000000', 80, 0),
  ('1991187008834431251800000000', 80, 0),
  ('2006190119854439137100000000', 80, 0),
  ('2006189339414438167300000000', 80, 0),
  ('1991189384264431325700000000', 80, 0),
  ('1991189404194431216400000000', 80, 0),
  ('1992186784664435809800000000', 80, 0),
  ('1992187122094429371600000000', 80, 0),
  ('1993186347734445345600000000', 80, 0),
  ('1975186770834435882700000000', 80, 0),
  ('1993187041814433703800000000', 80, 0),
  ('1986189994344444674600000000', 80, 0),
  ('2006190002424440903400000000', 80, 0),
  ('1987186682144443302100000000', 80, 0),
  ('1993190120074440758400000000', 80, 0),
  ('2006190085714439458600000000', 80, 0),
  ('1990186700304443514700000000', 80, 0),
  ('1987186586584443127100000000', 80, 0),
  ('1990186343314446114300000000', 80, 0),
  ('1976189334264437842900000000', 80, 0),
  ('1984189372254434588900000000', 80, 0),
  ('1988186711504441635600000000', 80, 0),
  ('1991186486164445107000000000', 80, 0),
  ('1976186986254434203600000000', 80, 0),
  ('1987186702224441876500000000', 80, 0),
  ('1989186684054436154100000000', 80, 0),
  ('1981190242704433701700000000', 80, 0),
  ('1992189364904431233900000000', 80, 0),
  ('1993189410594429403500000000', 80, 0),
  ('1988190035124438686100000000', 80, 0),
  ('1990186696614422053700000000', 80, 0),
  ('1995187537274448322600000000', 80, 0),
  ('1983189952784445412700000000', 80, 0),
  ('1983190291184447085800000000', 80, 0),
  ('1986190126564439976200000000', 80, 0),
  ('1987187034494441792200000000', 80, 0),
  ('1987190327774437498000000000', 80, 0),
  ('1988190072974442622800000000', 80, 0),
  ('1989186581194443218700000000', 80, 0),
  ('1989190115464439583100000000', 80, 0),
  ('1990186999294428829800000000', 80, 0),
  ('1991189764904430679600000000', 80, 0),
  ('1992189368384432793000000000', 80, 0),
  ('1995189702364424648700000000', 80, 0);

-- 보관함 (저장 목록 1개, 60곳)
INSERT INTO vulnerability_list (user_id, sgg_cd, title, criteria, analyzed_at)
VALUES (@guro, '11530', '구로구 80점 이상 시연용 60곳', '{"scenario":"normal","scoreMin":80}', '2026-11-10 09:00:00');
SET @vl = LAST_INSERT_ID();
INSERT INTO vulnerability_factor (vulnerability_id, bld_id, is_applicable, vulnerability_score, vulnerability_grade, evaluated_at)
SELECT @vl, d.bld_id, 1, d.score, '80점 이상', '2026-11-10 09:00:00'
  FROM demo_bundle d JOIN building b ON b.bld_id = d.bld_id;

-- 지난 순회 리스트 (부재 6곳, 결과까지 기록)
INSERT INTO survey_list (user_id, sgg_cd, title, move_mode, created_at) VALUES (@guro, '11530', '구로구 11월 2주 순회', 'WALK', '2026-11-11 17:00:00');
SET @sl = LAST_INSERT_ID();
INSERT INTO survey_plan (survey_id, factor_id, plan_date, visit_order)
SELECT @sl, f.factor_id, '2026-11-12', ROW_NUMBER() OVER (ORDER BY d.score DESC, d.bld_id)
  FROM demo_bundle d JOIN vulnerability_factor f ON f.vulnerability_id = @vl AND f.bld_id = d.bld_id
 WHERE d.absent = 1;

INSERT INTO visit_history (user_id, bld_id, visited_at, result)
SELECT @guro, f.bld_id, TIMESTAMPADD(MINUTE, 40 * p.visit_order, '2026-11-12 09:30:00'), 'ABSENT'
  FROM survey_plan p JOIN vulnerability_factor f ON f.factor_id = p.factor_id
 WHERE p.survey_id = @sl;
INSERT INTO survey_record (plan_id, visit_id, content, factor_check, created_at)
SELECT p.plan_id, v.visit_id, '부재. 우편함에 안내문 남김. 재방문 필요.', '{}', TIMESTAMPADD(MINUTE, 5, v.visited_at)
  FROM survey_plan p JOIN vulnerability_factor f ON f.factor_id = p.factor_id
  JOIN visit_history v ON v.bld_id = f.bld_id AND v.user_id = @guro AND v.result = 'ABSENT' AND DATE(v.visited_at) = '2026-11-12'
 WHERE p.survey_id = @sl;

DROP TEMPORARY TABLE demo_bundle;

-- 확인 : 보관함 60 / 부재 6
SELECT (SELECT COUNT(*) FROM vulnerability_factor WHERE vulnerability_id = @vl) archive_cnt,
       (SELECT COUNT(*) FROM survey_plan WHERE survey_id = @sl) absent_plan_cnt,
       (SELECT COUNT(*) FROM survey_record r JOIN survey_plan p ON p.plan_id = r.plan_id WHERE p.survey_id = @sl) absent_record_cnt;

/* ---------- 지우기 (이 파일로 넣은 것만) ----------
SELECT user_id INTO @guro FROM users WHERE login_id = 'guro';
SELECT survey_id INTO @sl FROM survey_list WHERE user_id = @guro AND title = '구로구 11월 2주 순회';
SELECT vulnerability_id INTO @vl FROM vulnerability_list WHERE user_id = @guro AND title = '구로구 80점 이상 시연용 60곳';
DELETE r FROM survey_record r JOIN survey_plan p ON p.plan_id = r.plan_id WHERE p.survey_id = @sl;
DELETE FROM visit_history WHERE user_id = @guro AND result = 'ABSENT' AND DATE(visited_at) = '2026-11-12';
DELETE FROM survey_plan WHERE survey_id = @sl;
DELETE FROM survey_list WHERE survey_id = @sl;
DELETE FROM vulnerability_factor WHERE vulnerability_id = @vl;
DELETE FROM vulnerability_list WHERE vulnerability_id = @vl;
*/
