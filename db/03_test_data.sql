-- ============================================================
-- sujuro 개발용 테스트 데이터 (01_schema.sql, 02_code_data.sql 실행 후)
-- 실행: docker exec -e NLS_LANG=.AL32UTF8 oracle sqlplus 계정/비밀번호@//localhost:1521/FREEPDB1 @/tmp/03_test_data.sql
-- 로그인 화면 확인용 계정 6명. 비밀번호는 모두 Demo!2026a (BCrypt로 저장)
-- 2025-0001 은 계정 상태가 '초기 비밀번호'라서 로그인하면 비밀번호 변경 화면으로 간다. (FR-002 확인용)
-- 다시 실행해도 된다. (테스트 계정만 지우고 다시 넣는다.) 배포 DB에는 넣지 않는다.
--
-- [변경 이력]
-- v1.1  2026-10-19  오창준  초기 비밀번호 상태 테스트 계정 2025-0001 추가
-- v1.0  2026-10-19  오창준  최초 작성 (팀 3개, 테스트 계정 5명)
-- ============================================================

SET DEFINE OFF

-- 1) 팀: 같은 이름이 이미 있으면 넣지 않는다
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '경영지원실', 'DP010' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '경영지원실');
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '영업부', 'DP020' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '영업부');
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '개발1팀', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '개발1팀');

-- 2) 테스트 계정 삭제 (외부 인력이 팀장을 참조하므로 외부 인력부터)
DELETE FROM TB_USER WHERE USER_ID = 'EXT-001';
DELETE FROM TB_USER WHERE USER_ID IN ('2023-0067', '2024-0031', '2024-0102', '2022-0009', '2025-0001');

-- 3) 테스트 계정 (직급 JG010 사원 / JG020 팀장 / JG030 외부, 권한 AU010 영업부 / AU020 경영지원실 / AU030 팀장 / AU040 팀원 / AU050 외부, 상태 AS030 활성)
INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, OTSD_MNPW_YN)
VALUES ('2023-0067', '박서연', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '영업부'), 'JG010', 'AU010',
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', 'N');
INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, OTSD_MNPW_YN)
VALUES ('2024-0031', '김가희', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), 'JG020', 'AU030',
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', 'N');
INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, OTSD_MNPW_YN)
VALUES ('2024-0102', '오창준', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), 'JG010', 'AU040',
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', 'N');
INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, OTSD_MNPW_YN)
VALUES ('2022-0009', '최민정', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '경영지원실'), 'JG010', 'AU020',
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', 'N');
INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, OTSD_MNPW_YN)
VALUES ('2025-0001', '이신입', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), 'JG010', 'AU040',
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS020', 'N');
INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, OTSD_MNPW_YN, CO_NM, RGTR_ID)
VALUES ('EXT-001', '서진우', NULL, 'JG030', 'AU050',
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', 'Y', '㈜가온시스템', '2024-0031');

COMMIT;
