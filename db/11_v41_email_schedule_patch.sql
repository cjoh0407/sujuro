-- ============================================================
-- 11_v41_email_schedule_patch.sql  (v4.1, 2026-10-31, 오창준)
-- ① 사용자 이메일 필수: TB_USER.EML_ADDR 를 NOT NULL 로 바꾼다.
--      마감 알림·단계 전환 알림 메일을 받을 주소가 없으면 알림이 가지 않기 때문.
-- ② 일정구분 정리: SC040 발표 · SC050 서류 마감을 사용 안 함으로 바꾼다. (캘린더에는 제출 마감과 직접 등록한 일정만 보인다)
-- 패치 규칙: ALTER / UPDATE 만 사용 (DROP · DELETE 없음).
-- 기존 DB 에서만 실행. 새 DB 는 01_schema.sql 에 이미 반영되어 있다.
-- 이메일 순서: 비어 있는 이메일을 임시 주소로 채운 뒤 NOT NULL 적용
--   ※ 임시 주소(<사번>@example.com)는 실제로 받을 수 없으니, 적용 후 사용자 관리 화면에서 실제 주소로 바꿀 것.
-- ============================================================

UPDATE TB_USER
   SET EML_ADDR = USER_ID || '@example.com'
 WHERE EML_ADDR IS NULL;

ALTER TABLE TB_USER MODIFY (EML_ADDR NOT NULL);

COMMENT ON COLUMN TB_USER.EML_ADDR IS '이메일주소 - 필수. 메일 알림 수신 주소';

UPDATE TB_COM_CD
   SET USE_YN = 'N', CD_EXPLN = '사용 안 함. 캘린더에 넣지 않는다 (FR-092)'
 WHERE GROUP_CD = 'SCHSE' AND CD IN ('SC040', 'SC050');

COMMIT;
