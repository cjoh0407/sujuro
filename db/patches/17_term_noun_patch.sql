-- ============================================================
-- 용어 명사화 패치 (2026-11-06, 오창준 · 11/06 멘토링)
-- 대상: 이 패치 전 02_code_data.sql 로 이미 만든 DB (로컬 DB, 배포 서버 DB).
--       02_code_data.sql 을 새로 실행한 DB에는 이미 들어 있으므로 실행하지 않아도 된다(다시 실행해도 결과는 같다).
-- 내용: 공통코드 이름만 바꾼다. 코드값·데이터는 건드리지 않는다.
--       서류준비상태(DCPST) DS010·DS020 → 미완료, DS030 → 완료 / 입찰결과(BDRST) BR020 탈락 → 실주
-- 실행: DBeaver에서 파일을 열고 Alt+X.
-- ============================================================
UPDATE TB_COM_CD SET CD_NM = '미완료' WHERE GROUP_CD = 'DCPST' AND CD IN ('DS010', 'DS020');
UPDATE TB_COM_CD SET CD_NM = '완료'   WHERE GROUP_CD = 'DCPST' AND CD = 'DS030';
UPDATE TB_COM_CD SET CD_NM = '실주'   WHERE GROUP_CD = 'BDRST' AND CD = 'BR020';
COMMIT;
