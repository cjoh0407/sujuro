-- ============================================================
-- 05_test_team.sql : 제안팀 구성 확인용 팀·팀원 (01~04 실행 후)
-- 순서: 01 → 02 → 03 → 04 → 05 → 06 → 07 (db/README.md)
-- 1~3) 참여팀 확인용 (옛 06_test_team.sql)
-- 공공사업2Div과 그 팀장·팀원을 만들고, 샘플 사업건 2(R26BK00000002)에 공공사업2Div을 참여팀으로 넣는다.
--   2024-0201 정다은 (공공사업2Div 팀장) : 제안팀이 아니다 → 김가희가 "인원 추가"로 넣어 보는 용도
--   2024-0202 한지우 (공공사업2Div 팀원) : 샘플 사업건 2의 참여팀원(PT020) → 참여팀 인원 화면 확인용
-- 비밀번호는 다른 테스트 계정과 같다: Demo!2026a
-- 다시 실행해도 된다. (이미 있으면 넣지 않거나 덮어쓴다.) 배포 DB에는 넣지 않는다.
-- DBeaver: 파일을 열고 Alt+X(스크립트 실행). 4)는 PL/SQL 블록이라 끝의 / 줄까지 한 번에 실행된다.
--
-- [변경 이력]
-- v1.1  2026-11-02  오창준  06_test_team.sql 을 05로 바꾸고 13_test_many_members.sql(팀원 40명)을 4)로 합침
-- v1.0  2026-10-27  이세종  최초 작성 (FR-043~044 제안팀 구성 확인용)
-- ============================================================

-- 1) 공공사업2Div
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '공공사업2Div', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '공공사업2Div');

-- 2) 공공사업2Div 팀장·팀원 (직급 JG020 팀장 / JG010 사원, 권한 AU030 팀장 / AU040 팀원, 상태 AS030 활성)
MERGE INTO TB_USER u
USING (
  SELECT '2024-0201' AS USER_ID, '정다은' AS USER_NM, '공공사업2Div' AS TEAM_NM, 'JG020' AS JBGD_CD, 'AU030' AS AUTHRT_CD, 'daeun.jung@example.com' AS EML_ADDR FROM DUAL
  UNION ALL SELECT '2024-0202', '한지우', '공공사업2Div', 'JG010', 'AU040', 'jiwoo.han@example.com' FROM DUAL
) s ON (u.USER_ID = s.USER_ID)
WHEN MATCHED THEN UPDATE SET
  u.USER_NM       = s.USER_NM,
  u.TEAM_SN       = (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
  u.JBGD_CD       = s.JBGD_CD,
  u.AUTHRT_CD     = s.AUTHRT_CD,
  u.EML_ADDR      = COALESCE(u.EML_ADDR, s.EML_ADDR),
  u.ENPSWD        = '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq',
  u.ACNT_STTS_CD  = 'AS030',
  u.LGN_FAIL_NMTM = 0
WHEN NOT MATCHED THEN INSERT (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, EML_ADDR)
VALUES (s.USER_ID, s.USER_NM, (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
        s.JBGD_CD, s.AUTHRT_CD, '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', s.EML_ADDR);

-- 3) 샘플 사업건 2: 공공사업2Div을 참여팀으로, 한지우를 참여팀원(PT020)으로
INSERT INTO TB_PTCP_TEAM (PBANC_SN, TEAM_SN)
SELECT B.PBANC_SN, T.TEAM_SN
  FROM TB_BID_PBANC B, TB_TEAM T
 WHERE B.BID_PBANC_NO = 'R26BK00000002' AND T.TEAM_NM = '공공사업2Div'
   AND NOT EXISTS (SELECT 1 FROM TB_PTCP_TEAM P WHERE P.PBANC_SN = B.PBANC_SN AND P.TEAM_SN = T.TEAM_SN);

INSERT INTO TB_PRP_TMMBR (PBANC_SN, USER_ID, PTCP_SE_CD)
SELECT B.PBANC_SN, '2024-0202', 'PT020'
  FROM TB_BID_PBANC B
 WHERE B.BID_PBANC_NO = 'R26BK00000002'
   AND NOT EXISTS (SELECT 1 FROM TB_PRP_TMMBR M WHERE M.PBANC_SN = B.PBANC_SN AND M.USER_ID = '2024-0202');

COMMIT;

-- 되돌리기 (필요할 때만 선택해서 실행)
-- DELETE FROM TB_PRP_TMMBR WHERE USER_ID IN ('2024-0201', '2024-0202');
-- UPDATE TB_TOC SET PIC_ID = NULL WHERE PIC_ID IN ('2024-0201', '2024-0202');
-- DELETE FROM TB_PTCP_TEAM WHERE TEAM_SN = (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '공공사업2Div');

-- ------------------------------------------------------------
-- 4) 제안팀 인원 찾기 팝업(무한 스크롤) 확인용 팀원 40명 (옛 13_test_many_members.sql, 이세종 v1.0 2026-10-31)
--    개발부(DP030) 팀에 차례로 나눈다. 팝업은 20명씩 불러오므로 스크롤이 2번 생긴다.
--    사번 2099-0001 ~ 2099-0040, 직급 JG010 사원, 권한 AU040 팀원, 상태 AS030 활성, 비밀번호 Demo!2026a
--    이미 있는 사번은 건너뛴다.
-- ------------------------------------------------------------
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
