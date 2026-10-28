-- ============================================================
-- 파일 삭제(숨김) 컬럼 추가 패치 (2026-10-28, 이세종)
-- 대상: 10/28 이전 01_schema.sql 로 이미 만든 DB (로컬 DB, 배포 서버 DB). 데이터는 지우지 않는다.
--       01_schema.sql 을 새로 실행한 DB에는 이미 들어 있으므로 실행하지 않는다.
-- 내용: TB_ATCH_FILE 에 삭제여부·삭제자·삭제일시·삭제사유를 더한다.
--       화면에서 파일을 삭제하면 목록에서만 숨기고 원본과 이력은 남긴다. (NFR-009)
-- 실행: DBeaver에서 파일을 열고 Alt+X. 한 번만 실행한다.
--       이미 실행했다면 ORA-01430(이미 있는 컬럼) 등이 나는데, 그 문장은 [건너뛰기] 하면 된다.
-- ============================================================

ALTER TABLE TB_ATCH_FILE ADD (
  DEL_YN   CHAR(1)             DEFAULT 'N'  NOT NULL,
  DLTR_ID  VARCHAR2(20 CHAR),
  DEL_DT   DATE,
  DEL_RSN  VARCHAR2(1000 CHAR)
);

ALTER TABLE TB_ATCH_FILE ADD CONSTRAINT CK_ATCH_FILE_DEL_YN CHECK (DEL_YN IN ('Y','N'));

ALTER TABLE TB_ATCH_FILE ADD CONSTRAINT FK_ATCH_FILE_DLTR_ID FOREIGN KEY (DLTR_ID) REFERENCES TB_USER (USER_ID);

COMMENT ON COLUMN TB_ATCH_FILE.DEL_YN IS '삭제여부 - 화면에서 삭제하면 Y. 목록에서만 숨기고 원본과 이력은 남긴다 (NFR-009)';
COMMENT ON COLUMN TB_ATCH_FILE.DLTR_ID IS '삭제자아이디 - 삭제한 사람 (올린 사람, 주관팀 팀장, 경영지원실만 가능)';
COMMENT ON COLUMN TB_ATCH_FILE.DEL_DT IS '삭제일시';
COMMENT ON COLUMN TB_ATCH_FILE.DEL_RSN IS '삭제사유 - 삭제할 때 한 줄 입력';
