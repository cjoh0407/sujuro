-- ============================================================
-- 제안팀 인원 찾기 팝업(무한 스크롤) 확인용 테스트 데이터 (01~06 실행 후)
-- 개발부(DP030) 팀에 테스트 팀원 40명을 돌아가며 넣는다. 팝업은 20명씩 불러오므로 스크롤이 2번 생긴다.
--   사번 2099-0001 ~ 2099-0040, 직급 JG010 사원, 권한 AU040 팀원, 상태 AS030 활성
--   팀은 지금 DB에 있는 개발부 팀(개발1팀·공공사업1Div 등 이름과 상관없이)에 차례로 나눈다.
-- 비밀번호는 다른 테스트 계정과 같다: Demo!2026a
-- 다시 실행해도 된다. (이미 있는 사번은 건너뛴다.) 배포 DB에는 넣지 않는다.
-- DBeaver: 파일을 열고 Alt+X.
--
-- [변경 이력]
-- v1.0  2026-10-31  이세종  최초 작성 (FR-043 v4.0 인원 찾기 팝업 확인용)
-- ============================================================

DECLARE
  TYPE t_names IS TABLE OF VARCHAR2(10);
  v_last  t_names := t_names('김', '이', '박', '최', '정', '강', '조', '윤', '장', '임');
  v_first t_names := t_names('민준', '서연', '도윤', '지우', '하준', '서윤', '시우', '하은');
  TYPE t_teams IS TABLE OF NUMBER;
  v_teams t_teams;
  v_id    VARCHAR2(20);
  v_nm    VARCHAR2(100);
  v_team  NUMBER;
  v_cnt   NUMBER;
BEGIN
  SELECT TEAM_SN BULK COLLECT INTO v_teams
    FROM TB_TEAM WHERE DEPT_CD = 'DP030' AND USE_YN = 'Y' ORDER BY TEAM_NM;
  IF v_teams.COUNT = 0 THEN
    RAISE_APPLICATION_ERROR(-20001, '개발부(DP030) 팀이 없습니다. 03_test_data.sql 을 먼저 실행하세요.');
  END IF;

  FOR i IN 1 .. 40 LOOP
    v_id   := '2099-' || LPAD(i, 4, '0');
    v_nm   := v_last(MOD(i - 1, v_last.COUNT) + 1) || v_first(MOD(TRUNC((i - 1) / v_last.COUNT), v_first.COUNT) + 1);
    v_team := v_teams(MOD(i - 1, v_teams.COUNT) + 1);
    SELECT COUNT(*) INTO v_cnt FROM TB_USER WHERE USER_ID = v_id;
    IF v_cnt = 0 THEN
      INSERT INTO TB_USER (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, EML_ADDR)
      VALUES (v_id, v_nm, v_team, 'JG010', 'AU040',
              '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030',
              'test' || LPAD(i, 4, '0') || '@example.com');
    END IF;
  END LOOP;
  COMMIT;
END;
/

-- 확인: 팀별로 몇 명 들어갔는지
SELECT T.TEAM_NM, COUNT(*) AS CNT
  FROM TB_USER U JOIN TB_TEAM T ON T.TEAM_SN = U.TEAM_SN
 WHERE U.USER_ID LIKE '2099-%'
 GROUP BY T.TEAM_NM ORDER BY T.TEAM_NM;

-- 되돌리기 (필요할 때만 선택해서 실행)
-- UPDATE TB_TOC SET PIC_ID = NULL WHERE PIC_ID LIKE '2099-%';
-- DELETE FROM TB_PRP_TMMBR WHERE USER_ID LIKE '2099-%';
-- DELETE FROM TB_NOTI WHERE RCVR_ID LIKE '2099-%';
-- DELETE FROM TB_USER WHERE USER_ID LIKE '2099-%';
-- COMMIT;
