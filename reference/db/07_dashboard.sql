-- =====================================================================
-- WellFAIR DB 변경 (2026-11-20)  01 ~ 06 다음에 실행. 여러 번 실행해도 됨 (IF NOT EXISTS)
--   대시보드(dashboard.do) 개편용 테이블 3개. 기존 테이블은 바꾸지 않는다.
--     [A] work_schedule  : 업무 캘린더 일정. 캘린더 페이지(구현 예정)가 쓰고 고치며, 대시보드는 읽기만 한다
--     [B] recent_view    : "이어서 보기" = 탭(지도 · 현장 확인 리스트 · 보관함)마다 마지막으로 본 화면 1건
--     [C] monthly_report : "이번 달 현황 보고서 만들기" 로 LLM 이 쓴 월간 보고서 초안
-- =====================================================================
USE wellfair;

-- [A] 업무 캘린더 일정
CREATE TABLE IF NOT EXISTS work_schedule (
  schedule_id  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '일정ID (PK)',
  user_id      BIGINT UNSIGNED NOT NULL COMMENT '담당자',
  plan_id      BIGINT UNSIGNED NULL     COMMENT '현장확인 리스트 항목 (있으면 확인 결과로 완료 · 부재를 표시)',
  bld_id       VARCHAR(28)     NULL     COMMENT '건물',
  sched_date   DATE            NOT NULL COMMENT '날짜',
  start_time   TIME            NOT NULL COMMENT '시작 시각',
  title        VARCHAR(100)    NOT NULL COMMENT '화면 글자 (예: 개봉동 12-34)',
  sched_type   ENUM('VISIT','REVISIT','ETC') NOT NULL DEFAULT 'VISIT' COMMENT '현장 방문 / 재방문 / 기타',
  memo         VARCHAR(300)    NULL,
  created_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (schedule_id),
  KEY idx_work_schedule_user_date (user_id, sched_date, start_time),
  CONSTRAINT fk_work_schedule_user FOREIGN KEY (user_id) REFERENCES users (user_id),
  CONSTRAINT fk_work_schedule_plan FOREIGN KEY (plan_id) REFERENCES survey_plan (plan_id) ON DELETE SET NULL,
  CONSTRAINT fk_work_schedule_bld FOREIGN KEY (bld_id)  REFERENCES building (bld_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='업무 캘린더 일정';

-- [B] 이어서 보기 (사용자 · 탭마다 1줄, 다시 보면 덮어씀)
CREATE TABLE IF NOT EXISTS recent_view (
  user_id    BIGINT UNSIGNED NOT NULL,
  menu       ENUM('map','field','archive') NOT NULL COMMENT '상단 탭 (header.jspf activeMenu)',
  url        VARCHAR(500)    NOT NULL COMMENT '다시 열 주소 (컨텍스트 경로 뒤, 예: fielddetail.do?listId=3)',
  title      VARCHAR(100)    NULL     COMMENT '무엇을 보고 있었나 (리스트 이름 · 건물 주소 …)',
  sub        VARCHAR(100)    NULL     COMMENT '보조 글자 (리스트 편집 중 …)',
  viewed_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (user_id, menu),
  CONSTRAINT fk_recent_view_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='이어서 보기';

-- [C] 월간 보고서 초안 (LLM)
CREATE TABLE IF NOT EXISTS monthly_report (
  report_id   BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id     BIGINT UNSIGNED NOT NULL,
  sgg_cd      CHAR(5)         NULL COMMENT '구 (NULL = 3개 구 전체)',
  ym          CHAR(7)         NOT NULL COMMENT 'yyyy-MM',
  content     MEDIUMTEXT      NOT NULL,
  status      ENUM('DRAFT','CONFIRMED') NOT NULL DEFAULT 'DRAFT' COMMENT '초안 / 담당자 확정 (검토 대기는 지금 화면에서 제외)',
  created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (report_id),
  KEY idx_report_user_ym (user_id, ym),
  CONSTRAINT fk_monthly_report_user FOREIGN KEY (user_id) REFERENCES users (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='월간 보고서';

-- ---------------------------------------------------------------------
-- 시연용 일정 (guro 계정, 오늘 · 이번 달). 캘린더 페이지가 생기면 지워도 됨
-- ---------------------------------------------------------------------
INSERT INTO work_schedule (user_id, sched_date, start_time, title, sched_type)
SELECT u.user_id, d.dt, d.tm, d.title, d.tp
  FROM users u
  JOIN (SELECT CURDATE() dt, '10:00:00' tm, '개봉동 12-34' title, 'VISIT' tp
        UNION ALL SELECT CURDATE(), '11:00:00', '구로동 88-2', 'VISIT'
        UNION ALL SELECT CURDATE(), '14:00:00', '가리봉동 31-7', 'VISIT'
        UNION ALL SELECT CURDATE(), '15:30:00', '오류동 115', 'VISIT'
        UNION ALL SELECT CURDATE(), '16:45:00', '신도림동 204', 'REVISIT'
        UNION ALL SELECT DATE_ADD(CURDATE(), INTERVAL 1 DAY), '10:30:00', '고척동 52-1', 'VISIT'
        UNION ALL SELECT DATE_ADD(CURDATE(), INTERVAL 3 DAY), '13:00:00', '수궁동 7-3', 'VISIT'
        UNION ALL SELECT DATE_ADD(CURDATE(), INTERVAL 3 DAY), '15:00:00', '구로동 101', 'REVISIT') d
 WHERE u.login_id = 'guro'
   AND NOT EXISTS (SELECT 1 FROM work_schedule w WHERE w.user_id = u.user_id);
