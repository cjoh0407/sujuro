-- ============================================================
-- 기술·가격 배점(비율)과 기술 원점수 컬럼 추가 패치 (2026-10-29, 김종민)
-- 대상: 10/29 이전 01_schema.sql 로 이미 만든 DB (로컬 DB, 배포 서버 DB). 데이터는 지우지 않는다.
--       01_schema.sql 을 새로 실행한 DB에는 이미 들어 있으므로 실행하지 않는다.
-- 내용: TB_BID_PBANC  기술배점점수(TECH_SCOR_SCR)·가격배점점수(PRC_SCOR_SCR)
--         입찰공고 API 의 기술능력평가비율(techAbltEvlRt)·입찰가격평가비율(bidPrceEvlRt). 기술·가격점수의 만점이다.
--       TB_BID_RSLT · TB_CO_EVL_RSLT  기술평가원본점수(TECH_EVL_ORGNL_SCR)
--         개찰 결과 API 의 기술평가원점수(techEvlNaturVal). 차등점수제를 적용하기 전 점수다.
--       컬럼명은 표준단어(기술 TECH · 가격 PRC · 배점 SCOR · 평가 EVL · 원본 ORGNL · 점수 SCR)만 썼다.
--       '비율'은 표준단어에 없어서 '배점'으로 지었다 (평가배점점수 EVL_SCOR_SCR 와 같은 방식).
-- 실행: DBeaver에서 파일을 열고 Alt+X. 한 번만 실행한다.
--       이미 실행했다면 ORA-01430(이미 있는 컬럼)이 나는데, 그 문장은 [건너뛰기] 하면 된다.
-- ============================================================

ALTER TABLE TB_BID_PBANC ADD (
  TECH_SCOR_SCR  NUMBER(10,3),
  PRC_SCOR_SCR   NUMBER(10,3)
);

ALTER TABLE TB_BID_RSLT ADD (
  TECH_EVL_ORGNL_SCR NUMBER(10,3)
);

ALTER TABLE TB_CO_EVL_RSLT ADD (
  TECH_EVL_ORGNL_SCR NUMBER(10,3)
);

COMMENT ON COLUMN TB_BID_PBANC.TECH_SCOR_SCR IS '기술배점점수 - 기술 평가 비율. API techAbltEvlRt (예: 90). 기술점수 만점이다';
COMMENT ON COLUMN TB_BID_PBANC.PRC_SCOR_SCR IS '가격배점점수 - 가격 평가 비율. API bidPrceEvlRt (예: 10). 가격점수 만점이다';
COMMENT ON COLUMN TB_BID_RSLT.TECH_EVL_ORGNL_SCR IS '기술평가원본점수 - 차등점수제 적용 전 기술 원점수. API techEvlNaturVal';
COMMENT ON COLUMN TB_CO_EVL_RSLT.TECH_EVL_ORGNL_SCR IS '기술평가원본점수 - 차등점수제 적용 전 기술 원점수. API techEvlNaturVal';
