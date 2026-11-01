-- ============================================================
-- 제안서 목차의 현재 양식 컬럼 추가 패치 (2026-10-31, 이세종)
-- 대상: 이 패치 전 01_schema.sql 로 이미 만든 DB (로컬 DB, 배포 서버 DB). 데이터는 지우지 않는다.
--       01_schema.sql 을 새로 실행한 DB에는 이미 들어 있으므로 실행하지 않는다.
-- 내용: TB_BID_PBANC  목차양식일련번호(TOC_FORM_SN, TB_TOC_FORM 참조)
--         사업건 제안서 목차가 마지막으로 불러오거나 저장한 주관팀 양식. 제안팀원 모두가 목차 화면에서 "현재 목차 양식"을 본다 (FR-048)
--         제안요약서는 양식 목록 없이 기본 목차로 시작하므로 컬럼을 두지 않는다.
-- 실행: DBeaver에서 파일을 열고 Alt+X. 한 번만 실행한다.
--       이미 실행했다면 ORA-01430(이미 있는 컬럼) 등이 나는데, 그 문장은 [건너뛰기] 하면 된다.
-- ============================================================

ALTER TABLE TB_BID_PBANC ADD (
  TOC_FORM_SN  NUMBER(22)
);

ALTER TABLE TB_BID_PBANC ADD CONSTRAINT FK_BID_PBANC_TOC_FORM_SN FOREIGN KEY (TOC_FORM_SN) REFERENCES TB_TOC_FORM (TOC_FORM_SN);

COMMENT ON COLUMN TB_BID_PBANC.TOC_FORM_SN IS '목차양식일련번호 - 이 사업건 제안서 목차가 마지막으로 불러오거나 저장한 주관팀 양식. 화면에 현재 목차 양식으로 보여 준다. 직접 만든 목차면 NULL. 제안요약서는 양식 없이 기본 목차를 쓴다 (FR-048)';
