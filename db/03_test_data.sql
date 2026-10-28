-- ============================================================
-- sujuro 개발용 테스트 데이터 (01_schema.sql, 02_code_data.sql 실행 후)
-- 실행: docker exec -e NLS_LANG=.AL32UTF8 oracle sqlplus 계정/비밀번호@//localhost:1521/FREEPDB1 @/tmp/03_test_data.sql
-- 로그인 화면 확인용 계정 5명. 비밀번호는 모두 Demo!2026a (BCrypt로 저장)
-- 2025-0001 은 계정 상태가 '초기 비밀번호'라서 로그인하면 비밀번호 변경 화면으로 간다. (FR-002 확인용)
-- 다시 실행해도 된다. (테스트 계정만 지우고 다시 넣는다.) 배포 DB에는 넣지 않는다.
--
-- [변경 이력]
-- v1.7  2026-10-28  김종민  4-3 공고 나라장터 첨부 3건(TB_ATCH_FILE: 사전규격 규격서·입찰공고 첨부·제안요청서)을 넣음.
--                           첨부는 이제 API 원문이 아니라 TB_ATCH_FILE 에서 읽어서 원문의 첨부 필드는 뺐다.
--                           공고를 지울 때 TB_ATCH_FILE 도 먼저 지운다.
-- v1.6  2026-10-27  김종민  4-3 공고 수집이력 2건에 API 원문(공고명·사업 내용·평가 비율)을 넣음. 공고 상세의
--                           변경 비교(FR-035-1)와 사업 내용 확인용.
-- v1.5  2026-10-26  김종민  공고 9건 추가 (사전규격 2 · 본공고 7, 공고상태 7종 전부 + 반환 1건).
--                           공고 목록 탭·검색·정렬과 영업부 대시보드 집계 확인용.
--                           지우는 공고를 번호 목록으로 한정해 03_sample_data.sql 의 샘플 공고
--                           (R26BK00000001, R26BK00000002)를 건드리지 않는다.
-- v1.4  2026-10-22  오창준  사용자 관리로 등록한 계정이 있어도 다시 실행되도록, 테스트 계정을 지우기 전에 등록자(RGTR_ID) 참조를 비움
-- v1.3  2026-10-22  오창준  테스트 계정을 지우기 전에 그 계정의 알림(TB_NOTI)을 먼저 지우도록 추가
-- v1.2  2026-10-22  오창준  외부 인력 제외에 따라 테스트 계정 EXT-001 삭제(이미 들어 있으면 정리), OTSD_MNPW_YN 컬럼 지정 제거
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

-- 2) 공고와 그 자식 행 삭제: 테스트 계정보다 먼저 지워야 한다.
--    공고가 등록자(RGTR_ID)·제출자(PRSNR_ID)로 사용자를 참조하므로, 계정을 먼저 지우려 하면
--    ORA-02292(자식 레코드 발견)로 막히고 이어지는 계정 INSERT가 ORA-00001(PK 위배)로 깨진다.
--    화면에서 공고를 등록하면 처리이력도 함께 생기므로 그 표까지 지운다.
--
--    지울 공고를 번호로 한정한다. 'R26B%' 로 지우면 03_sample_data.sql 이 만든 샘플 공고
--    (R26BK00000001, R26BK00000002)까지 지워져 그 목차·요구사항이 깨진다.
DELETE FROM TB_CLCT_LOG
 WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC
                     WHERE BID_PBANC_NO    IN ('R26BK00945112','R26BK00946730','R26BK00947181',
                                               'R26BK00941055','R26BK00938420','R26BK00944003',
                                               'R26BK00948820','R26BK00949310','R26BK00942500')
                        OR BF_SPEC_RGST_NO IN ('R26BD00012345','R26BD00013902','R26BD00011088')
                        OR ORDER_PLAN_NO         IN ('R26DD20900001'));
DELETE FROM TB_PBANC_CLCT_HSTRY
 WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC
                     WHERE BID_PBANC_NO    IN ('R26BK00945112','R26BK00946730','R26BK00947181',
                                               'R26BK00941055','R26BK00938420','R26BK00944003',
                                               'R26BK00948820','R26BK00949310','R26BK00942500')
                        OR BF_SPEC_RGST_NO IN ('R26BD00012345','R26BD00013902','R26BD00011088')
                        OR ORDER_PLAN_NO         IN ('R26DD20900001'));
DELETE FROM TB_PBANC_PRCS_HSTRY
 WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC
                     WHERE BID_PBANC_NO    IN ('R26BK00945112','R26BK00946730','R26BK00947181',
                                               'R26BK00941055','R26BK00938420','R26BK00944003',
                                               'R26BK00948820','R26BK00949310','R26BK00942500')
                        OR BF_SPEC_RGST_NO IN ('R26BD00012345','R26BD00013902','R26BD00011088')
                        OR ORDER_PLAN_NO         IN ('R26DD20900001'));
-- 나라장터 공고 첨부(FL010·FL080·FL090). 공고를 등록하면 함께 생긴다.
DELETE FROM TB_ATCH_FILE
 WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC
                     WHERE BID_PBANC_NO    IN ('R26BK00945112','R26BK00946730','R26BK00947181',
                                               'R26BK00941055','R26BK00938420','R26BK00944003',
                                               'R26BK00948820','R26BK00949310','R26BK00942500')
                        OR BF_SPEC_RGST_NO IN ('R26BD00012345','R26BD00013902','R26BD00011088')
                        OR ORDER_PLAN_NO         IN ('R26DD20900001'));
DELETE FROM TB_NOTI
 WHERE PBANC_SN IN (SELECT PBANC_SN FROM TB_BID_PBANC
                     WHERE BID_PBANC_NO    IN ('R26BK00945112','R26BK00946730','R26BK00947181',
                                               'R26BK00941055','R26BK00938420','R26BK00944003',
                                               'R26BK00948820','R26BK00949310','R26BK00942500')
                        OR BF_SPEC_RGST_NO IN ('R26BD00012345','R26BD00013902','R26BD00011088')
                        OR ORDER_PLAN_NO         IN ('R26DD20900001'));
DELETE FROM TB_BID_PBANC
 WHERE BID_PBANC_NO    IN ('R26BK00945112','R26BK00946730','R26BK00947181',
                           'R26BK00941055','R26BK00938420','R26BK00944003',
                           'R26BK00948820','R26BK00949310','R26BK00942500')
    OR BF_SPEC_RGST_NO IN ('R26BD00012345','R26BD00013902','R26BD00011088')
    OR ORDER_PLAN_NO         IN ('R26DD20900001');

-- 3) 외부 인력(EXT-001) 정리. 더 쓰지 않으므로 예전에 넣은 것이 있으면 참조하는 행과 함께 지운다.
DELETE FROM TB_PRP_TMMBR WHERE USER_ID = 'EXT-001';
UPDATE TB_TOC SET PIC_ID = NULL WHERE PIC_ID = 'EXT-001';
DELETE FROM TB_USER WHERE USER_ID = 'EXT-001';

-- 4) 테스트 계정을 "알려진 상태"로 되돌린다. (직급 JG010 사원 / JG020 팀장,
--    권한 AU010 영업부 / AU020 경영지원실 / AU030 팀장 / AU040 팀원, 상태 AS030 활성)
--
--    지웠다 다시 넣지 않고 MERGE 로 덮어쓴다. 계정을 지우려 하면
--    03_sample_data.sql 의 샘플 공고가 등록자(RGTR_ID)로 이 계정들을 참조하고 있어
--    ORA-02292(자식 레코드 발견)로 막히기 때문이다.
--    이 방식이면 알림·제안팀원·공고가 계정을 참조하고 있어도 몇 번이든 다시 실행된다.
--    로그인 실패 횟수도 0으로 되돌려 잠긴 계정이 풀린다.
MERGE INTO TB_USER u
USING (
  SELECT '2023-0067' AS USER_ID, '박서연' AS USER_NM, '영업부'     AS TEAM_NM, 'JG010' AS JBGD_CD, 'AU010' AS AUTHRT_CD, 'AS030' AS ACNT_STTS_CD FROM DUAL
  UNION ALL SELECT '2024-0031', '김가희', '개발1팀',   'JG020', 'AU030', 'AS030' FROM DUAL
  UNION ALL SELECT '2024-0102', '오창준', '개발1팀',   'JG010', 'AU040', 'AS030' FROM DUAL
  UNION ALL SELECT '2022-0009', '최민정', '경영지원실', 'JG010', 'AU020', 'AS030' FROM DUAL
  UNION ALL SELECT '2025-0001', '이신입', '개발1팀',   'JG010', 'AU040', 'AS020' FROM DUAL
) s ON (u.USER_ID = s.USER_ID)
WHEN MATCHED THEN UPDATE SET
  u.USER_NM      = s.USER_NM,
  u.TEAM_SN      = (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
  u.JBGD_CD      = s.JBGD_CD,
  u.AUTHRT_CD    = s.AUTHRT_CD,
  u.ENPSWD       = '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq',
  u.ACNT_STTS_CD = s.ACNT_STTS_CD,
  u.LGN_FAIL_NMTM = 0
WHEN NOT MATCHED THEN INSERT (USER_ID, USER_NM, TEAM_SN, JBGD_CD, AUTHRT_CD, ENPSWD, ACNT_STTS_CD)
VALUES (s.USER_ID, s.USER_NM, (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = s.TEAM_NM),
        s.JBGD_CD, s.AUTHRT_CD,
        '$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq', s.ACNT_STTS_CD);


-- 5) 공고 (공고 목록 탭·검색·정렬 확인용)
--    번호 규칙: R + 연도 2자리 + 단계 2자리(BD 사전규격 / BK 입찰공고) + 순번 8자리
--    단계 PP020 사전규격 / PP030 입찰공고, 추적 TR010 대기 / TR020 연결됨 / TR030 종료
--    상태 PS010 신규 / PS020 배정 완료 / PS030 참여 확정 / PS040 제안 작성 중 / PS050 제출 완료 /
--         PS060 결과 등록 / PS090 불참
-- 4-0) 발주계획 단계: 사전규격도 입찰공고도 아직 없다.
--      발주계획과 사전규격은 서로를 가리키는 번호가 API 에 없어서, 사전규격이 떴는지는
--      매일 신규 사전규격 목록을 받아 기관명+사업명으로 맞춰 봐야 안다(BidCode.sameBizCase).
--      사전규격을 건너뛰고 바로 입찰공고가 나는 경우도 14% 라서 입찰공고도 같이 봐야 한다.
INSERT INTO TB_BID_PBANC (ORDER_PLAN_NO, PBANC_STEP_CD, TRCK_STTS_CD, PBANC_NM, ORDER_INST_NM,
       ALTMNT_BGT_AMT, PBANC_STTS_CD, RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26DD20900001', 'PP010', 'TR010', '국가융합망 2단계 구축 사업', '과학기술정보통신부',
       1850000000, 'PS010', '2023-0067',
       TO_DATE('2026-10-22 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-22 08:10', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-10-22', 'YYYY-MM-DD'));

-- 4-1) 사전규격 단계: 본공고를 기다리는 중
INSERT INTO TB_BID_PBANC (BF_SPEC_RGST_NO, PBANC_STEP_CD, TRCK_STTS_CD, PBANC_NM, ORDER_INST_NM,
       DMND_INST_NM, SW_BIZ_YN, ALTMNT_BGT_AMT, OPNN_DDLN_DT, PBANC_STTS_CD, RGTR_ID, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BD00012345', 'PP020', 'TR010', '차세대 지방재정관리시스템 구축 ISP', '행정안전부',
       '지방재정경제실', 'Y', 480000000, TO_DATE('2026-10-27 18:00', 'YYYY-MM-DD HH24:MI'), 'PS010',
       '2023-0067', TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-14', 'YYYY-MM-DD'));

-- 4-2) 사전규격 단계: 기간 안에 본공고가 없어 영업부가 추적을 끊었다
INSERT INTO TB_BID_PBANC (BF_SPEC_RGST_NO, PBANC_STEP_CD, TRCK_STTS_CD, PBANC_NM, ORDER_INST_NM,
       SW_BIZ_YN, ALTMNT_BGT_AMT, PBANC_STTS_CD, RGTR_ID, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BD00013902', 'PP020', 'TR030', '건강보험 데이터 분석 플랫폼 사전규격', '국민건강보험공단',
       'Y', 1200000000, 'PS010', '2023-0067',
       TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-08-11', 'YYYY-MM-DD'));

-- 4-3) 사전규격에서 본공고로 전환된 공고. 사전규격번호를 지우지 않고 함께 둔다
INSERT INTO TB_BID_PBANC (BF_SPEC_RGST_NO, BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, TRCK_STTS_CD,
       PBANC_TYPE_CD, PBANC_NM, ORDER_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT, BIZ_PRD_CN,
       BID_DDLN_DT, OPENG_DT, EVL_MTHD_NM, LMT_PAGE_CNT, PBANC_DTL_URL, PBANC_STTS_CD,
       SPRVSN_TEAM_SN, RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BD00011088', 'R26BK00945112', 0, 'PP030', 'TR020',
       'PY010', '통합민원 포털 고도화 사업', '국민권익위원회', 'BT010', 'PF010', 760000000, '계약일로부터 8개월',
       TO_DATE('2026-11-05 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-11-05 11:00', 'YYYY-MM-DD HH24:MI'),
       '협상에 의한 계약', 150, 'https://www.g2b.go.kr/pn/pnp/pnpe/BidPbac/view.do', 'PS020',
       (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), '2023-0067',
       TO_DATE('2026-10-16 08:09', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-09-29', 'YYYY-MM-DD'));

-- 4-4) 본공고 직접 등록. 정정 공고가 한 번 나서 차수가 1이다
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT, BIZ_PRD_CN, BID_DDLN_DT, OPENG_DT,
       EVL_MTHD_NM, LMT_PAGE_CNT, PBANC_STTS_CD, SPRVSN_TEAM_SN, RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00946730', 1, 'PP030', 'PY020', '스마트 물류 관제시스템 구축',
       '한국교통연구원', 'BT010', 'PF020', 1430000000, '계약일로부터 12개월',
       TO_DATE('2026-11-12 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-11-12 14:00', 'YYYY-MM-DD HH24:MI'),
       '협상에 의한 계약', 200, 'PS030', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'),
       '2023-0067', TO_DATE('2026-10-19 08:11', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-02', 'YYYY-MM-DD'));

-- 4-5) 제안 작성 중. 마감이 가까워 마감 임박 알림 대상이다.
--      유찰 뒤 다시 난 재공고(PT030)라서 차수는 0 부터 새로 시작한다
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT, BIZ_PRD_CN, BID_DDLN_DT, OPENG_DT,
       PRSNTN_DT, EVL_MTHD_NM, LMT_PAGE_CNT, PBANC_STTS_CD, SPRVSN_TEAM_SN, RGTR_ID,
       CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00947181', 0, 'PP030', 'PY030', '전자문서 유통 기반 재구축',
       '국가기록원', 'BT010', 'PF010', 920000000, '계약일로부터 10개월',
       TO_DATE('2026-10-30 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-30 11:00', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-11-03 14:00', 'YYYY-MM-DD HH24:MI'), '협상에 의한 계약', 120, 'PS040',
       (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), '2023-0067',
       TO_DATE('2026-10-13 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-09-21', 'YYYY-MM-DD'));

-- 4-6) 제출 완료. 개찰일이 지났으므로 개찰 결과 수집 대상이다
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, EVL_MTHD_NM,
       PBANC_STTS_CD, SPRVSN_TEAM_SN, RGTR_ID, SBMSN_DT, PRSNR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00941055', 0, 'PP030', 'PY010', '공공 클라우드 전환 2차 사업',
       '한국지능정보사회진흥원', 'BT010', 'PF010', 2100000000,
       TO_DATE('2026-10-08 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-09 11:00', 'YYYY-MM-DD HH24:MI'),
       '협상에 의한 계약', 'PS050', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), '2023-0067',
       TO_DATE('2026-10-08 09:40', 'YYYY-MM-DD HH24:MI'), '2022-0009',
       TO_DATE('2026-10-07 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-09-02', 'YYYY-MM-DD'));

-- 4-7) 결과 등록까지 끝난 공고
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, EVL_MTHD_NM,
       PBANC_STTS_CD, SPRVSN_TEAM_SN, RGTR_ID, SBMSN_DT, PRSNR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00938420', 0, 'PP030', 'PY010', '통합 보안관제 체계 구축',
       '한국인터넷진흥원', 'BT010', 'PF010', 1680000000,
       TO_DATE('2026-09-24 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-09-25 11:00', 'YYYY-MM-DD HH24:MI'),
       '협상에 의한 계약', 'PS060', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), '2023-0067',
       TO_DATE('2026-09-24 09:30', 'YYYY-MM-DD HH24:MI'), '2022-0009',
       TO_DATE('2026-09-26 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-09-26 08:10', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-08-18', 'YYYY-MM-DD'));

-- 4-8) 팀장이 불참으로 결정한 공고 (유지보수 사업)
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, PBANC_STTS_CD,
       SPRVSN_TEAM_SN, RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00944003', 0, 'PP030', 'PY010', '노후 전산장비 유지보수',
       '조달청', 'BT020', 180000000,
       TO_DATE('2026-10-23 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-23 11:00', 'YYYY-MM-DD HH24:MI'),
       'PS090', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), '2023-0067',
       TO_DATE('2026-10-12 08:09', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-10-05', 'YYYY-MM-DD'));

-- 4-9) 팀장이 불참을 결정해 영업부로 반환된 공고. 상태는 신규지만 반환 표시가 남는다.
--      목업 v3.2 영업부 대시보드의 "반환 건" 집계와 목록의 반환 배지 확인용.
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, PBANC_STTS_CD,
       RTRN_SE_CD, RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00948820', 0, 'PP030', 'PY010', '지방세 수납 연계 모듈 개선',
       '대전광역시', 'BT010', 320000000,
       TO_DATE('2026-11-18 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-11-18 11:00', 'YYYY-MM-DD HH24:MI'),
       'PS010', 'RT010', '2023-0067',
       TO_DATE('2026-10-17 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-10-16', 'YYYY-MM-DD'));

-- 4-10) 취소공고. 발주기관이 입찰을 취소했다. 공고유형이 취소공고(PT040)면 영업부에 알리고
--       목록에서 걸러낼 대상이다. 취소도 정정의 한 형태라 차수가 올라간다(실측 16/16).
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, PBANC_STTS_CD,
       RGTR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00949310', 1, 'PP030', 'PY040', '재난안전 상황관리 시스템 재구축',
       '행정안전부', 'BT010', 540000000,
       TO_DATE('2026-11-20 10:00', 'YYYY-MM-DD HH24:MI'), 'PS010', '2023-0067',
       TO_DATE('2026-10-22 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-22 08:10', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-10-21', 'YYYY-MM-DD'));

-- 4-11) 유찰. 개찰은 했지만 입찰이 성립하지 않았다(단독 응찰). 단계는 개찰(PP040), 상태는 유찰(PS070).
--        우리 팀이 참여했던 기록은 그대로 둔다(FR-024-1). 재공고가 나면 새 사업건으로 등록한다.
INSERT INTO TB_BID_PBANC (BID_PBANC_NO, BID_PBANC_CYCL, PBANC_STEP_CD, PBANC_TYPE_CD, PBANC_NM,
       ORDER_INST_NM, BIZ_TYPE_CD, PTCP_SHP_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, EVL_MTHD_NM,
       PBANC_STTS_CD, SPRVSN_TEAM_SN, RGTR_ID, SBMSN_DT, PRSNR_ID, CLCT_DT, LAST_CFMTN_DT, REG_DT)
VALUES ('R26BK00942500', 0, 'PP040', 'PY010', '해양 안전관리 플랫폼 구축',
       '해양수산부', 'BT010', 'PF010', 640000000,
       TO_DATE('2026-10-14 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-15 11:00', 'YYYY-MM-DD HH24:MI'),
       '협상에 의한 계약', 'PS070', (SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '개발1팀'), '2023-0067',
       TO_DATE('2026-10-14 09:30', 'YYYY-MM-DD HH24:MI'), '2022-0009',
       TO_DATE('2026-10-16 08:10', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-16 08:10', 'YYYY-MM-DD HH24:MI'),
       TO_DATE('2026-09-28', 'YYYY-MM-DD'));

-- 6) 공고수집이력: 4-3 공고가 사전규격에서 본공고로 전환된 흐름 (예산이 7억 -> 7.6억으로 바뀌었다)
--    API 원문(API_RSPNS_CN)은 공고 상세가 공고명 비교(FR-035-1)와 첨부파일(FR-036)을 꺼내는 곳이다.
--    실제 응답의 필드 이름만 따서 줄인 것이다. 첨부 주소는 가짜라 눌러도 받아지지 않는다.
INSERT INTO TB_PBANC_CLCT_HSTRY (PBANC_SN, CLCT_SEQ, PBANC_STEP_CD, PBANC_NO, ALTMNT_BGT_AMT, API_RSPNS_CN, CLCT_DT)
SELECT PBANC_SN, 1, 'PP020', 'R26BD00011088', 700000000,
       '{"bfSpecRgstNo":"R26BD00011088","prdctClsfcNoNm":"통합민원 포털 고도화","orderInsttNm":"국민권익위원회","asignBdgtAmt":"700000000",'
       || '"swBizObjYn":"Y","dlvrTmlmtDt":"2027-06-30 00:00:00"}',
       TO_DATE('2026-09-29 09:05', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00945112';
INSERT INTO TB_PBANC_CLCT_HSTRY (PBANC_SN, CLCT_SEQ, PBANC_STEP_CD, PBANC_NO, BID_PBANC_CYCL,
       PBANC_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, OPENG_DT, API_RSPNS_CN, CLCT_DT)
SELECT PBANC_SN, 2, 'PP030', 'R26BK00945112', 0, 'PY010', 760000000,
       TO_DATE('2026-11-05 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-11-05 11:00', 'YYYY-MM-DD HH24:MI'),
       '{"bidNtceNo":"R26BK00945112","bidNtceOrd":"000","ntceKindNm":"등록공고","bidNtceNm":"통합민원 포털 고도화 사업",'
       || '"refNo":"국민권익위원회 공고 제2026-112호","pubPrcrmntLrgClsfcNm":"ICT 서비스","pubPrcrmntMidClsfcNm":"소프트웨어 개발",'
       || '"cntrctCnclsMthdNm":"제한경쟁","cmmnSpldmdMethdNm":"(전자)공동이행","presmptPrce":"690909091",'
       || '"sucsfbidMthdNm":"협상에의한계약-협상에 의한 낙찰자 결정(SW사업)","techAbltEvlRt":"90","bidPrceEvlRt":"10",'
       || '"ntceInsttOfclNm":"김담당","ntceInsttOfclTelNo":"044-200-0000"}',
       TO_DATE('2026-10-16 08:09', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00945112';

-- 4-3 공고의 나라장터 첨부 (FR-036). 등록·전환 때 받아 TB_ATCH_FILE 에 넣는 것과 같은 모양이다.
--   FL080 사전규격 규격서(제안요청서 초안) / FL090 입찰공고 첨부 / FL010 제안요청서
--   주소는 가짜라 눌러도 받아지지 않는다. 크기는 지어낸 값이다.
INSERT INTO TB_ATCH_FILE (PBANC_SN, FILE_SE_CD, ORGNL_FILE_NM, ORGNL_URL, FILE_SZ, FILE_EXTN_NM, RGTR_ID, REG_DT)
SELECT PBANC_SN, 'FL080', '제안요청서(안)_통합민원 포털 고도화.hwpx',
       'https://www.g2b.go.kr/pn/pnz/pnza/UntyAtchFile/downloadFile.do?bfSpecRegNo=R26BD00011088&fileType=BFDTL&fileSeq=1',
       1843200, 'hwpx', '2023-0067', TO_DATE('2026-09-29 09:05', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00945112';
INSERT INTO TB_ATCH_FILE (PBANC_SN, FILE_SE_CD, ORGNL_FILE_NM, ORGNL_URL, FILE_SZ, FILE_EXTN_NM, RGTR_ID, REG_DT)
SELECT PBANC_SN, 'FL090', '[입찰공고문] 통합민원 포털 고도화 사업.hwp',
       'https://www.g2b.go.kr/pn/pnp/pnpe/UntyAtchFile/downloadFile.do?bidPbancNo=R26BK00945112&bidPbancOrd=000&fileSeq=1',
       96256, 'hwp', '2023-0067', TO_DATE('2026-10-16 08:09', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00945112';
INSERT INTO TB_ATCH_FILE (PBANC_SN, FILE_SE_CD, ORGNL_FILE_NM, ORGNL_URL, FILE_SZ, FILE_EXTN_NM, RGTR_ID, REG_DT)
SELECT PBANC_SN, 'FL010', '[제안요청서] 통합민원 포털 고도화 사업.hwpx',
       'https://www.g2b.go.kr/pn/pnp/pnpe/UntyAtchFile/downloadFile.do?bidPbancNo=R26BK00945112&bidPbancOrd=000&fileSeq=2',
       2097152, 'hwpx', '2023-0067', TO_DATE('2026-10-16 08:09', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00945112';

-- 4-4 공고의 정정 이력 (차수 0 -> 1, 마감일이 11/05 에서 11/12 로 밀렸다)
INSERT INTO TB_PBANC_CLCT_HSTRY (PBANC_SN, CLCT_SEQ, PBANC_STEP_CD, PBANC_NO, BID_PBANC_CYCL,
       PBANC_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, CLCT_DT)
SELECT PBANC_SN, 1, 'PP030', 'R26BK00946730', 0, 'PY010', 1430000000,
       TO_DATE('2026-11-05 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-02 08:08', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00946730';
INSERT INTO TB_PBANC_CLCT_HSTRY (PBANC_SN, CLCT_SEQ, PBANC_STEP_CD, PBANC_NO, BID_PBANC_CYCL,
       PBANC_TYPE_CD, ALTMNT_BGT_AMT, BID_DDLN_DT, CLCT_DT)
SELECT PBANC_SN, 2, 'PP030', 'R26BK00946730', 1, 'PY020', 1430000000,
       TO_DATE('2026-11-12 10:00', 'YYYY-MM-DD HH24:MI'), TO_DATE('2026-10-19 08:11', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00946730';

-- 7) 수집로그: 목록의 마지막 불러온 시각 표시 확인용 (자동 1건 / 수동 1건 / 실패 1건)
INSERT INTO TB_CLCT_LOG (PBANC_SN, EXCN_SE_CD, CLCT_RSLT_CD, EXCN_DT)
SELECT PBANC_SN, 'EX010', 'CL010', TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO IN ('R26BK00947181', 'R26BK00941055', 'R26BK00944003');
INSERT INTO TB_CLCT_LOG (PBANC_SN, EXCN_SE_CD, EXCTR_ID, CLCT_RSLT_CD, EXCN_DT)
SELECT PBANC_SN, 'EX020', '2023-0067', 'CL030', TO_DATE('2026-10-19 08:11', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00946730';
INSERT INTO TB_CLCT_LOG (PBANC_SN, EXCN_SE_CD, CLCT_RSLT_CD, ERR_MSG_CN, EXCN_DT)
SELECT PBANC_SN, 'EX010', 'CL090', '응답 시간 초과 (10초)', TO_DATE('2026-10-20 08:12', 'YYYY-MM-DD HH24:MI')
  FROM TB_BID_PBANC WHERE BF_SPEC_RGST_NO = 'R26BD00013902';

COMMIT;
