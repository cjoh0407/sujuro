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
-- v1.2  2026-11-02  오창준  5) 팀별 진행 사업 추가: 실제 나라장터 IT·SI 공고 5건, 팀장 4명, 제안팀·목차·일정
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

-- ------------------------------------------------------------
-- 5) 팀별 진행 사업: 실제 나라장터 IT·SI 입찰공고 5건 (오창준 v1.2 2026-11-02)
--    공고 값(번호·사업명·기관·예산·마감·개찰·낙찰방법·배점·상세 URL)은 2026-11-02 나라장터 API 에서 받은 실제 값이다.
--    "공고번호로 등록"(NoticeRegisterServiceImpl.registerByBidNo)이 저장하는 항목과 같다.
--    주관팀·상태·제안팀·목차·일정은 시연용으로 붙인 것이다. 시연일(11/23)에도 진행 중이도록 11/24 이후 마감만 골랐다.
--    자동 수집(평일 17시)이 실제 공고를 따라가므로 정정이 나면 마감일 등이 바뀌고 알림이 온다.
--      공공사업2Div  R26BK01781589 국민건강보험공단 지능형 AX 고도화            12/03 마감  제안 작성 중
--      공공사업3Div  R26BK01786019 행정안전부 행정정보공동이용시스템 유지관리   11/24 마감  제안 작성 중 (시연일 D-1)
--      전략사업1Div  R26BK01775450 인천국제공항공사 출입증관리시스템 고도화     11/27 마감  제안 작성 중
--      전략사업2Div  R26BK01798670 경찰청 정보시스템 통합 유지관리            12/01 마감  참여 확정
--      전략사업3Div  R26BK01796531 한국항공우주연구원 천리안위성 5호 지상 송수신 12/08 마감  참여 확정
--    팀장 4명을 새로 만든다(비밀번호 Demo!2026a). 제안팀은 그 팀 팀장 + 그 팀 팀원 2명(주관팀 PT010).
--    다시 실행해도 된다. 이미 있는 공고(같은 공고·사전규격·발주계획 번호)는 건너뛴다.
-- ------------------------------------------------------------
MERGE INTO TB_USER u
USING (
  SELECT '2024-0301' AS USER_ID, '송재현' AS USER_NM, '공공사업3Div' AS TEAM_NM, 'jaehyun.song@example.com' AS EML_ADDR FROM DUAL
  UNION ALL SELECT '2024-0401', '문하린', '전략사업1Div', 'harin.moon@example.com' FROM DUAL
  UNION ALL SELECT '2024-0402', '배준호', '전략사업2Div', 'junho.bae@example.com' FROM DUAL
  UNION ALL SELECT '2024-0403', '신유나', '전략사업3Div', 'yuna.shin@example.com' FROM DUAL
) s ON (u.USER_ID = s.USER_ID)
WHEN MATCHED THEN UPDATE SET
  u.USER_NM       = s.USER_NM,
  u.TEAM_SN       = (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
  u.JBGD_CD       = 'JG020',
  u.AUTHRT_CD     = 'AU030',
  u.EML_ADDR      = COALESCE(u.EML_ADDR, s.EML_ADDR),
  u.ENPSWD        = '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq',
  u.ACNT_STTS_CD  = 'AS030',
  u.LGN_FAIL_NMTM = 0
WHEN NOT MATCHED THEN INSERT (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD, EML_ADDR)
VALUES (s.USER_ID, s.USER_NM, (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
        'JG020', 'AU030', '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', 'AS030', s.EML_ADDR);

DECLARE
  -- 공고 1건 + 이력 + 제안팀 + 목차. 이미 있으면 아무것도 하지 않는다.
  PROCEDURE add_bid(p_no VARCHAR2, p_nm VARCHAR2, p_order_inst VARCHAR2, p_dmnd_inst VARCHAR2, p_bgt NUMBER,
                    p_ddln VARCHAR2, p_openg VARCHAR2, p_evl VARCHAR2, p_spec VARCHAR2, p_plan VARCHAR2,
                    p_reg VARCHAR2, p_biz_type VARCHAR2, p_stts VARCHAR2, p_team_nm VARCHAR2, p_leader VARCHAR2,
                    p_tocs SYS.ODCIVARCHAR2LIST) IS
    v_sn    NUMBER;
    v_team  NUMBER;
    v_cnt   NUMBER;
    v_reg   DATE := TO_DATE(p_reg, 'YYYY-MM-DD HH24:MI');
    v_up    NUMBER;
    v_pic   VARCHAR2(20);
    v_stts  VARCHAR2(5);
    TYPE t_ids IS TABLE OF VARCHAR2(20);
    v_mem   t_ids;
    v_parts SYS.ODCIVARCHAR2LIST;
    v_line  VARCHAR2(400);
  BEGIN
    SELECT COUNT(*) INTO v_cnt FROM TB_BID_PBANC
     WHERE BID_PBANC_NO = p_no OR BF_SPEC_RGST_NO = p_spec OR ORDER_PLAN_NO = p_plan;
    IF v_cnt > 0 THEN
      RETURN;
    END IF;
    SELECT TEAM_SN INTO v_team FROM TB_TEAM WHERE TEAM_NM = p_team_nm;

    INSERT INTO TB_BID_PBANC (ORDER_PLAN_NO, BF_SPEC_RGST_NO, BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD,
           PBANC_TYPE_CD, PBANC_NM, ORDER_INST_NM, DMND_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT,
           BID_DDLN_DT, OPENG_DT, EVL_MTHD_NM, TECH_SCOR_SCR, PRC_SCOR_SCR, PBANC_DTL_URL,
           PBANC_STTS_CD, SPRVSN_TEAM_SN, RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
    VALUES (p_plan, p_spec, p_no, 0, 'PP030',
           'PY010', p_nm, p_order_inst, p_dmnd_inst, p_biz_type, 'PF010', p_bgt,
           TO_DATE(p_ddln, 'YYYY-MM-DD HH24:MI'), TO_DATE(p_openg, 'YYYY-MM-DD HH24:MI'), p_evl, 90, 10,
           'https://www.g2b.go.kr/link/PNPE027_01/single/?bidPbancNo=' || p_no || CHR(38) || 'bidPbancOrd=000',
           p_stts, v_team, '2023-0067', SYSDATE, SYSDATE, v_reg)
    RETURNING PBANC_SN INTO v_sn;

    -- 수집이력 1번: 상세 화면 '변경 내용'의 처음 값 (API 원문은 넣지 않는다)
    INSERT INTO TB_PBANC_CLCT_HSTRY (PBANC_SN, CLCT_SEQ, PBANC_STEP_CD, PBANC_NO, BID_PBANC_CYCL,
           PBANC_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, CLCT_DT)
    VALUES (v_sn, 1, 'PP030', p_no, 0, 'PY010', p_bgt,
           TO_DATE(p_ddln, 'YYYY-MM-DD HH24:MI'), TO_DATE(p_openg, 'YYYY-MM-DD HH24:MI'), v_reg);

    -- 처리이력: 등록(영업부) → 배정(영업부) → 참여 결정(팀장)
    INSERT INTO TB_PBANC_PRCS_HSTRY (PBANC_SN, TRGT_TEAM_SN, PRCS_SE_CD, PRCS_RSN, PRCR_ID, PRCS_DT)
    VALUES (v_sn, NULL, 'PC010', '입찰공고번호 ' || p_no, '2023-0067', v_reg);
    INSERT INTO TB_PBANC_PRCS_HSTRY (PBANC_SN, TRGT_TEAM_SN, PRCS_SE_CD, PRCS_RSN, PRCR_ID, PRCS_DT)
    VALUES (v_sn, v_team, 'PC030', '참여 형태 주관', '2023-0067', v_reg + 1);
    INSERT INTO TB_PBANC_PRCS_HSTRY (PBANC_SN, TRGT_TEAM_SN, PRCS_SE_CD, PRCS_RSN, PRCR_ID, PRCS_DT)
    VALUES (v_sn, v_team, 'PC050', '참여', p_leader, v_reg + 2);

    -- 제안팀: 팀장 + 그 팀 팀원 2명 (2099-xxxx 자동 생성 팀원보다 이름 있는 팀원을 먼저)
    INSERT INTO TB_PRP_TMMBR (PBANC_SN, USER_ID, PTCP_SE_CD) VALUES (v_sn, p_leader, 'PT010');
    SELECT USER_ID BULK COLLECT INTO v_mem
      FROM (SELECT USER_ID FROM TB_USER
             WHERE TEAM_SN = v_team AND AUTHRT_CD = 'AU040' AND ACNT_STTS_CD <> 'AS050'
             ORDER BY CASE WHEN USER_ID LIKE '2099-%' THEN 1 ELSE 0 END, USER_ID)
     WHERE ROWNUM <= 2;
    FOR i IN 1 .. v_mem.COUNT LOOP
      INSERT INTO TB_PRP_TMMBR (PBANC_SN, USER_ID, PTCP_SE_CD) VALUES (v_sn, v_mem(i), 'PT010');
    END LOOP;

    -- 목차: 'L|번호|이름' 은 1단계, 'C|번호|이름|담당(L=팀장,1,2=팀원)|쪽수|진행상태' 는 바로 위 1단계의 하위
    FOR i IN 1 .. p_tocs.COUNT LOOP
      v_line := p_tocs(i);
      SELECT REGEXP_SUBSTR(v_line, '[^|]+', 1, LEVEL) BULK COLLECT INTO v_parts
        FROM DUAL CONNECT BY REGEXP_SUBSTR(v_line, '[^|]+', 1, LEVEL) IS NOT NULL;
      IF v_parts(1) = 'L' THEN
        INSERT INTO TB_TOC (PBANC_SN, UP_TOC_SN, TOC_NO, TOC_NM, SORT_SEQ)
        VALUES (v_sn, NULL, v_parts(2), v_parts(3), i)
        RETURNING TOC_SN INTO v_up;
      ELSE
        v_pic := CASE v_parts(4) WHEN 'L' THEN p_leader
                                 WHEN '1' THEN CASE WHEN v_mem.COUNT >= 1 THEN v_mem(1) END
                                 WHEN '2' THEN CASE WHEN v_mem.COUNT >= 2 THEN v_mem(2) END END;
        -- 참여 확정(PS030)이면 아직 쓰기 전이라 모두 미착수
        v_stts := CASE WHEN p_stts = 'PS030' THEN 'TS010' ELSE v_parts(6) END;
        INSERT INTO TB_TOC (PBANC_SN, UP_TOC_SN, TOC_NO, TOC_NM, SORT_SEQ, PIC_ID, EXPC_PAGE_CNT, PRGRS_STTS_CD)
        VALUES (v_sn, v_up, v_parts(2), v_parts(3), i, v_pic, TO_NUMBER(v_parts(5)), v_stts);
      END IF;
    END LOOP;
  END;

  -- 팀장이 직접 등록한 일정 (내부 일정 SC020). 같은 사업·같은 이름이 있으면 건너뛴다.
  PROCEDURE add_sch(p_no VARCHAR2, p_dt VARCHAR2, p_ttl VARCHAR2) IS
    v_sn NUMBER; v_team NUMBER; v_leader VARCHAR2(20); v_cnt NUMBER;
  BEGIN
    SELECT PBANC_SN, SPRVSN_TEAM_SN INTO v_sn, v_team FROM TB_BID_PBANC WHERE BID_PBANC_NO = p_no;
    SELECT COUNT(*) INTO v_cnt FROM TB_SCHDL WHERE PBANC_SN = v_sn AND SCHDL_TTL = p_ttl;
    IF v_cnt > 0 THEN
      RETURN;
    END IF;
    SELECT MIN(USER_ID) INTO v_leader FROM TB_USER WHERE TEAM_SN = v_team AND AUTHRT_CD = 'AU030';
    INSERT INTO TB_SCHDL (TEAM_SN, PBANC_SN, SCHDL_SE_CD, SCHDL_TTL, SCHDL_BGNG_DT, AUTO_REG_YN, RGTR_ID)
    VALUES (v_team, v_sn, 'SC020', p_ttl, TO_DATE(p_dt, 'YYYY-MM-DD HH24:MI'), 'N', v_leader);
  END;
BEGIN
  add_bid('R26BK01781589', '지능형 AX 고도화 사업', '조달청', '국민건강보험공단', 10751886770,
          '2026-12-03 10:00', '2026-12-03 11:00', '협상에의한계약-협상에 의한 낙찰자 결정(SW사업)',
          'R26BD00279412', 'R26DD20830748', '2026-10-23 10:57', 'BT010', 'PS040', '공공사업2Div', '2024-0201',
          SYS.ODCIVARCHAR2LIST('L|1|사업 이해', 'C|1.1|사업 배경 및 현황 분석|1|6|TS030', 'C|1.2|AX 추진 전략|L|8|TS020',
                               'L|2|기술 부문', 'C|2.1|AI 모델 고도화 방안|2|18|TS020', 'C|2.2|데이터 연계 구조|1|12|TS010',
                               'L|3|사업 관리', 'C|3.1|품질·보안 관리 방안|2|7|TS010'));
  add_bid('R26BK01786019', '2027~2028년도 행정정보공동이용시스템 유지관리 사업', '조달청', '행정안전부', 10118000000,
          '2026-11-24 10:00', '2026-11-24 11:00', '협상에의한계약-협상에 의한 낙찰자 결정(SW사업)',
          'R26BD00283237', 'R26DD20889742', '2026-10-23 14:55', 'BT020', 'PS040', '공공사업3Div', '2024-0301',
          SYS.ODCIVARCHAR2LIST('L|1|사업 이해', 'C|1.1|현행 시스템 분석|1|8|TS030',
                               'L|2|유지관리 방안', 'C|2.1|장애 대응 체계|2|10|TS030', 'C|2.2|정기 점검 및 성능 관리|1|9|TS020',
                               'L|3|사업 관리', 'C|3.1|인력 운영 계획|L|6|TS020'));
  add_bid('R26BK01775450', '인천공항 출입증관리시스템 고도화 사업', '인천국제공항공사', '인천국제공항공사', 5519204104,
          '2026-11-27 16:00', '2026-11-27 17:00', '협상에의한계약-협상에 의한 낙찰자 결정(SW사업)',
          'R26BD00280232', 'R26DD20580793', '2026-10-16 15:03', 'BT010', 'PS040', '전략사업1Div', '2024-0401',
          SYS.ODCIVARCHAR2LIST('L|1|사업 이해', 'C|1.1|출입증 발급 업무 분석|1|7|TS020',
                               'L|2|기술 부문', 'C|2.1|출입증 관리 시스템 설계|2|15|TS010', 'C|2.2|보안 등급별 출입 통제|1|10|TS010',
                               'L|3|사업 관리', 'C|3.1|전환 및 안정화 계획|L|6|TS010'));
  add_bid('R26BK01798670', '2027년 경찰청 정보시스템 통합 유지관리', '조달청 서울지방조달청', '경찰청', 16241969800,
          '2026-12-01 10:00', '2026-12-01 11:00', '협상에의한계약-협상에 의한 낙찰자 결정(SW사업)',
          'R26BD00286967', 'R26DD20896841', '2026-10-30 14:28', 'BT020', 'PS030', '전략사업2Div', '2024-0402',
          SYS.ODCIVARCHAR2LIST('L|1|사업 이해', 'C|1.1|대상 시스템 현황|1|8|TS010',
                               'L|2|유지관리 방안', 'C|2.1|통합 유지관리 체계|2|12|TS010', 'C|2.2|보안 취약점 관리|L|8|TS010'));
  add_bid('R26BK01796531', '천리안위성 5호 지상 송수신시스템 개발 용역', '한국항공우주연구원', '한국항공우주연구원', 9408000000,
          '2026-12-08 10:00', '2026-12-08 11:00', '협상에의한계약-협상에 의한 낙찰자 결정',
          'R26BD00284678', 'R26DD20895086', '2026-10-28 14:57', 'BT010', 'PS030', '전략사업3Div', '2024-0403',
          SYS.ODCIVARCHAR2LIST('L|1|사업 이해', 'C|1.1|위성 지상국 현황 분석|1|8|TS010',
                               'L|2|기술 부문', 'C|2.1|송수신 시스템 구조 설계|2|16|TS010', 'C|2.2|시험 및 검증 방안|L|10|TS010'));

  -- 팀장이 직접 등록한 일정 (30자 이내). 11/23 시연 전후로 퍼지게 둔다
  add_sch('R26BK01781589', '2026-11-13 14:00', '착수 회의');
  add_sch('R26BK01781589', '2026-11-27 15:00', '제안서 1차 내부 검토');
  add_sch('R26BK01786019', '2026-11-12 10:00', '착수 회의');
  add_sch('R26BK01786019', '2026-11-20 14:00', '제안서 최종 검토');
  add_sch('R26BK01775450', '2026-11-16 14:00', '요구사항 분석 회의');
  add_sch('R26BK01775450', '2026-11-25 15:00', '제안서 최종 검토');
  add_sch('R26BK01798670', '2026-11-18 10:00', '착수 회의');
  add_sch('R26BK01798670', '2026-11-26 14:00', '유지관리 인력 확정');
  add_sch('R26BK01796531', '2026-11-19 14:00', '착수 회의');
  add_sch('R26BK01796531', '2026-12-04 14:00', '발표 리허설');
  COMMIT;
END;
/

-- 확인: 팀별 진행 사업
SELECT T.TEAM_NM, P.BID_PBANC_NO, P.PBANC_STTS_CD, TO_CHAR(P.BID_DDLN_DT, 'MM-DD HH24:MI') AS DDLN,
       (SELECT COUNT(*) FROM TB_PRP_TMMBR M WHERE M.PBANC_SN = P.PBANC_SN) AS MEMBERS,
       (SELECT COUNT(*) FROM TB_TOC C WHERE C.PBANC_SN = P.PBANC_SN) AS TOCS,
       (SELECT COUNT(*) FROM TB_SCHDL S WHERE S.PBANC_SN = P.PBANC_SN) AS SCHDLS
  FROM TB_BID_PBANC P JOIN TB_TEAM T ON T.TEAM_SN = P.SPRVSN_TEAM_SN
 WHERE P.BID_PBANC_NO IN ('R26BK01781589', 'R26BK01786019', 'R26BK01775450', 'R26BK01798670', 'R26BK01796531')
 ORDER BY T.TEAM_NM;

-- 되돌리기 (필요할 때만 선택해서 실행. 자식 → 부모 순서)
-- DELETE FROM TB_SCHDL WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531'));
-- DELETE FROM TB_TOC WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531')) AND UP_TOC_SN IS NOT NULL;
-- DELETE FROM TB_TOC WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531'));
-- DELETE FROM TB_PRP_TMMBR WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531'));
-- DELETE FROM TB_PBANC_PRCS_HSTRY WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531'));
-- DELETE FROM TB_PBANC_CLCT_HSTRY WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531'));
-- DELETE FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK01781589','R26BK01786019','R26BK01775450','R26BK01798670','R26BK01796531');
-- DELETE FROM TB_USER WHERE USER_ID IN ('2024-0301','2024-0401','2024-0402','2024-0403');
-- COMMIT;
