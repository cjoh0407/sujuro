-- ============================================================
-- 요구사항 v4.1 · ERD v4.1 반영 패치 (2026-10-31, 오창준)
-- 대상: 10/31 이전 01_schema.sql 로 이미 만든 DB (로컬 DB, 배포 서버 DB). 데이터는 지우지 않는다.
--       01_schema.sql · 02_code_data.sql 을 새로 실행한 DB에는 이미 들어 있으므로 실행하지 않는다.
-- 내용: TB_TOC          외부담당회사명(PIC_CO_NM) + 담당자아이디와 둘 중 하나만 채우는 제약(CK_TOC_PIC)
--         외부 회사는 계정·별도 테이블 없이 목차 담당자 칸에 회사명을 적는다 (FR-043-1, FR-049)
--       TB_BID_PBANC    원본공고일련번호(ORGNL_PBANC_SN, 자기 참조)
--         재공고가 원공고를 가리킨다. 원공고의 주관팀·참여 확정 상태 승계 (FR-024-1). 재공고 번호 규칙 조사 후 확정
--       TB_EVL_ARTCL    쪽번호(PAGE_NO)  조견표 웹 입력 (FR-071)
--       TB_QSTN_ANS     요구사항일련번호(RQT_MTTR_SN)  Q&A를 요구사항에 직접 연결 (FR-074, FR-076)
--       공통코드        알림유형 NT140·NT150·NT160 추가, 일정구분 SC020 이름 변경·SC030 사용 안 함
--       팀 이름        개발1팀 → 공공사업1Div, 개발2팀 → 공공사업2Div, 공공사업3Div·전략사업1~3Div 추가 (시연 DB)
-- 하지 않는 것: 질문답변 ANSWR_ID, 첨부파일 MEMO_CN 은 화면에서만 안 쓰고 컬럼은 남긴다(삭제 금지).
--              사용자 이메일 필수(TB_USER.EML_ADDR NOT NULL)는 사용자 등록 화면에 이메일 필수 검증이 들어간 뒤 따로 패치한다.
-- 실행: DBeaver에서 파일을 열고 Alt+X. 한 번만 실행한다.
--       이미 실행했다면 ORA-01430(이미 있는 컬럼) 등이 나는데, 그 문장은 [건너뛰기] 하면 된다.
-- ============================================================

-- 1) 컬럼 추가
ALTER TABLE TB_TOC ADD (
  PIC_CO_NM  VARCHAR2(200 CHAR)
);

ALTER TABLE TB_BID_PBANC ADD (
  ORGNL_PBANC_SN  NUMBER(22)
);

ALTER TABLE TB_EVL_ARTCL ADD (
  PAGE_NO  VARCHAR2(20 CHAR)
);

ALTER TABLE TB_QSTN_ANS ADD (
  RQT_MTTR_SN  NUMBER(22)
);

-- 2) 제약
ALTER TABLE TB_TOC ADD CONSTRAINT CK_TOC_PIC CHECK (PIC_ID IS NULL OR PIC_CO_NM IS NULL);

ALTER TABLE TB_BID_PBANC ADD CONSTRAINT FK_BID_PBANC_ORGNL_PBANC_SN FOREIGN KEY (ORGNL_PBANC_SN) REFERENCES TB_BID_PBANC (PBANC_SN);

ALTER TABLE TB_QSTN_ANS ADD CONSTRAINT FK_QSTN_ANS_RQT_MTTR_SN FOREIGN KEY (RQT_MTTR_SN) REFERENCES TB_RQT_MTTR (RQT_MTTR_SN);

-- 3) 컬럼·테이블 설명
COMMENT ON COLUMN TB_TOC.PIC_CO_NM IS '외부담당회사명 - 외부 회사가 맡은 최하위 목차의 회사명(별도 테이블 없음). 담당자아이디와 둘 중 하나만 채운다. 같은 사업건에 이미 쓴 회사명은 자동완성으로 고르고, 저장할 때 공백·(주) 표기 차이는 기존 이름으로 맞춘다 (FR-043-1, FR-049)';
COMMENT ON COLUMN TB_BID_PBANC.ORGNL_PBANC_SN IS '원본공고일련번호 - 재공고(유찰·취소 뒤 다시 올라온 새 사업건)가 가리키는 원래 공고. 원공고의 주관팀과 참여 확정 상태를 이어받는다 (FR-024-1). 재공고 번호 규칙 조사 후 최종 확정';
COMMENT ON COLUMN TB_EVL_ARTCL.PAGE_NO IS '쪽번호 - 조견표: 제안팀이 입력하는 제안서 쪽번호. 예: 12-15 (FR-071)';
COMMENT ON COLUMN TB_QSTN_ANS.RQT_MTTR_SN IS '요구사항일련번호 - 관련 요구사항. 요구사항·큰 목차별로 묶어 보는 기준 (FR-074, FR-076)';
COMMENT ON TABLE TB_EVL_TOC_MPNG IS '평가항목목차매핑 - 미사용 (v4.1: 조견표 쪽번호는 TB_EVL_ARTCL.PAGE_NO 에 저장)';
COMMENT ON COLUMN TB_ATCH_FILE.FILE_SZ IS '파일크기 - byte. 통합본은 300MB, 그 외 파일은 50MB 제한 (v4.1, 300MB는 구현 예정). API에 없으므로 내려받은 뒤 기록한다 (FR-036, FR-065)';

-- 4) 공통코드 (이미 있으면 건너뛴다)
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN)
SELECT 'NOTIT', 'NT140', '알림유형', '발주계획 등록', '발주계획을 등록하면 영업부에 메일·화면 알림 (FR-027)', 14, 'Y' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TB_COM_CD WHERE GROUP_CD = 'NOTIT' AND CD = 'NT140');

INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN)
SELECT 'NOTIT', 'NT150', '알림유형', '공고 단계 전환', '발주계획→사전규격, 발주계획·사전규격→입찰공고 전환 시 영업부에 메일·화면 알림 (FR-027)', 15, 'Y' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TB_COM_CD WHERE GROUP_CD = 'NOTIT' AND CD = 'NT150');

INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN)
SELECT 'NOTIT', 'NT160', '알림유형', '일정 등록·변경', '팀장이 일정을 등록·변경하면 팀원·참여팀에 실시간 알림 (FR-034)', 16, 'Y' FROM DUAL
 WHERE NOT EXISTS (SELECT 1 FROM TB_COM_CD WHERE GROUP_CD = 'NOTIT' AND CD = 'NT160');

UPDATE TB_COM_CD
   SET CD_NM = '내부 일정', CD_EXPLN = '팀장이 자유롭게 등록하는 내부 일정 (일정명 직접 입력, 시간 포함) (FR-093)'
 WHERE GROUP_CD = 'SCHSE' AND CD = 'SC020';

UPDATE TB_COM_CD
   SET USE_YN = 'N', CD_EXPLN = '사용 안 함. 내부 일정(SC020)으로 통합 (FR-093)'
 WHERE GROUP_CD = 'SCHSE' AND CD = 'SC030';

-- 5) 팀 이름 (시연·테스트 DB). 이미 있는 팀의 이름만 바꾸고 팀 번호·소속 인원은 그대로 둔다.
--    새 이름의 팀이 이미 있으면 건너뛴다. 새 DB는 03_test_data.sql · 06_test_team.sql 이 처음부터 새 이름으로 만든다.
UPDATE TB_TEAM T SET TEAM_NM = '공공사업1Div'
 WHERE TEAM_NM = '개발1팀'
   AND NOT EXISTS (SELECT 1 FROM TB_TEAM X WHERE X.TEAM_NM = '공공사업1Div');

UPDATE TB_TEAM T SET TEAM_NM = '공공사업2Div'
 WHERE TEAM_NM = '개발2팀'
   AND NOT EXISTS (SELECT 1 FROM TB_TEAM X WHERE X.TEAM_NM = '공공사업2Div');

INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '공공사업3Div', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '공공사업3Div');
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '전략사업1Div', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '전략사업1Div');
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '전략사업2Div', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '전략사업2Div');
INSERT INTO TB_TEAM (TEAM_NM, DEPT_CD)
SELECT '전략사업3Div', 'DP030' FROM DUAL WHERE NOT EXISTS (SELECT 1 FROM TB_TEAM WHERE TEAM_NM = '전략사업3Div');

COMMIT;
