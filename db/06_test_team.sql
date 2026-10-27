-- ============================================================
-- 제안팀 구성(참여팀) 확인용 테스트 데이터 (01, 02, 03_test_data, 03_sample_data 실행 후)
-- 개발2팀과 그 팀장·팀원을 만들고, 샘플 사업건 2(R26BK00000002)에 개발2팀을 참여팀으로 넣는다.
--   2024-0201 정다은 (개발2팀 팀장) : 제안팀이 아니다 → 김가희가 "인원 추가"로 넣어 보는 용도
--   2024-0202 한지우 (개발2팀 팀원) : 샘플 사업건 2의 참여팀원(PT020) → 참여팀 인원 화면 확인용
-- 비밀번호는 다른 테스트 계정과 같다: Demo!2026a
-- 다시 실행해도 된다. (이미 있으면 넣지 않거나 덮어쓴다.) 배포 DB에는 넣지 않는다.
-- DBeaver: 파일을 열고 Alt+X. (SET DEFINE OFF 줄이 없으므로 그대로 실행하면 된다)
--
-- [변경 이력]
-- v1.0  2026-10-27  이세종  최초 작성 (FR-043~044 제안팀 구성 확인용)
-- ============================================================

-- 1) 개발2팀
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '개발2팀', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '개발2팀');

-- 2) 개발2팀 팀장·팀원 (직급 JG020 팀장 / JG010 사원, 권한 AU030 팀장 / AU040 팀원, 상태 AS030 활성)
MERGE INTO TB_USER u
USING (
  SELECT '2024-0201' AS USER_ID, '정다은' AS USER_NM, '개발2팀' AS TEAM_NM, 'JG020' AS JBGD_CD, 'AU030' AS AUTHRT_CD FROM DUAL
  UNION ALL SELECT '2024-0202', '한지우', '개발2팀', 'JG010', 'AU040' FROM DUAL
) s ON (u.USER_ID = s.USER_ID)
WHEN MATCHED THEN UPDATE SET
  u.USER_NM       = s.USER_NM,
  u.TEAM_SN       = (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
  u.JBGD_CD       = s.JBGD_CD,
  u.AUTHRT_CD     = s.AUTHRT_CD,
  u.ENPSWD        = '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq',
  u.ACNT_STTS_CD  = 'AS030',
  u.LGN_FAIL_NMTM = 0
WHEN NOT MATCHED THEN INSERT (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD)
VALUES (s.USER_ID, s.USER_NM, (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
        s.JBGD_CD, s.AUTHRT_CD, '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030');

-- 3) 샘플 사업건 2: 개발2팀을 참여팀으로, 한지우를 참여팀원(PT020)으로
INSERT INTO TB_PTCP_TEAM (PBANC_SN, TEAM_SN)
SELECT B.PBANC_SN, T.TEAM_SN
  FROM TB_BID_PBANC B, TB_TEAM T
 WHERE B.BID_PBANC_NO = 'R26BK00000002' AND T.TEAM_NM = '개발2팀'
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
-- DELETE FROM TB_PTCP_TEAM WHERE TEAM_SN = (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발2팀');
