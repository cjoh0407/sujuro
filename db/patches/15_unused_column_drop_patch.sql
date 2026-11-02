-- ============================================================
-- 쓰지 않는 컬럼·테이블 삭제 패치 (2026-11-02, 오창준 · 이세종 확인)
-- 대상: 이 패치 전 01_schema.sql 로 이미 만든 DB (로컬 DB, 배포 서버 DB).
--       01_schema.sql 을 새로 실행한 DB에는 이미 빠져 있으므로 실행하지 않는다.
-- 내용:
--   TB_QSTN_ANS   답변자아이디(ANSWR_ID)·목차일련번호(TOC_SN)·일치여부(CCRNC_YN) 삭제
--                 질의응답은 질문·답변·관련 요구사항만 쓴다 (v4.1).
--   TB_ATCH_FILE  메모내용(MEMO_CN) 삭제 - 파일 올릴 때 메모 입력 칸을 없앴다.
--   TB_EVL_TOC_MPNG 테이블 삭제 - 조견표 쪽번호는 TB_EVL_ARTCL.PAGE_NO 에 저장한다 (v4.1).
-- 주의: 지우면 안의 값도 함께 사라지고 되돌릴 수 없다.
--       이 패치를 실행하기 전에 이번 코드(위 컬럼·테이블을 쓰지 않는 버전)가 배포돼 있어야 한다.
--       예전 코드가 도는 서버에서 먼저 실행하면 목차 삭제, 파일 올리기, Q&A 화면이
--       "부적합한 식별자" 또는 "테이블 또는 뷰가 존재하지 않습니다" 오류로 멈춘다.
-- 서버 DB는 실행 전에 백업한다 (실행 후 3~7일 정상 운영을 보고 백업 테이블을 지운다).
--   CREATE TABLE TB_QSTN_ANS_BAK     AS SELECT * FROM TB_QSTN_ANS;
--   CREATE TABLE TB_ATCH_FILE_BAK    AS SELECT FILE_SN, MEMO_CN FROM TB_ATCH_FILE;
--   CREATE TABLE TB_EVL_TOC_MPNG_BAK AS SELECT * FROM TB_EVL_TOC_MPNG;
-- 실행: DBeaver에서 파일을 열고 Alt+X. 한 번만 실행한다.
--       이미 실행했다면 ORA-00904(부적합한 식별자)나 ORA-00942(테이블 없음)가 나는데, 그러면 이미 반영된 것이다.
-- ============================================================

ALTER TABLE TB_QSTN_ANS DROP (ANSWR_ID, TOC_SN, CCRNC_YN) CASCADE CONSTRAINTS;

ALTER TABLE TB_ATCH_FILE DROP (MEMO_CN);

DROP TABLE TB_EVL_TOC_MPNG CASCADE CONSTRAINTS PURGE;
