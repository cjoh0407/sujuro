-- ============================================================
-- 제출서류 화면 확인용 테스트 데이터 (01, 02, 03_test_data, 03_sample_data 실행 후)
-- 샘플 사업건 2(R26BK00000002)에 제출서류 체크리스트를 넣는다. (조견표는 파일로 올리므로 데이터가 필요 없다)
-- 다시 실행해도 된다. (이미 있으면 넣지 않는다.) 배포 DB에는 넣지 않는다.
-- DBeaver: 파일을 열고 Alt+X. (SET DEFINE OFF 줄이 없으므로 그대로 실행하면 된다)
--
-- [변경 이력]
-- v1.0  2026-10-27  이세종  최초 작성 (FR-067~073 제출서류 확인용)
-- ============================================================

-- 제출서류 체크리스트 (담당 TK010 경영지원실 / TK020 제안팀)
INSERT INTO TB_SBMSN_DCMNT (PBANC_SN, DCMNT_NM, ESNTL_YN, TKCG_SE_CD, PRGRS_STTS_CD, SBMSN_DDLN_DT)
SELECT B.PBANC_SN, S.NM, S.YN, S.TK, 'DS010', B.BID_DDLN_DT
  FROM TB_BID_PBANC B,
       (SELECT '최종 제안서' AS NM, 'Y' AS YN, 'TK020' AS TK FROM DUAL
        UNION ALL SELECT '제안요약서', 'Y', 'TK020' FROM DUAL
        UNION ALL SELECT '입찰참가신청서', 'Y', 'TK010' FROM DUAL
        UNION ALL SELECT '사업자등록증 사본', 'N', 'TK010' FROM DUAL) S
 WHERE B.BID_PBANC_NO = 'R26BK00000002'
   AND NOT EXISTS (SELECT 1 FROM TB_SBMSN_DCMNT D WHERE D.PBANC_SN = B.PBANC_SN AND D.DCMNT_NM = S.NM);

COMMIT;

-- 되돌리기 (필요할 때만 선택해서 실행. 파일을 올린 서류는 남긴다)
-- DELETE FROM TB_SBMSN_DCMNT WHERE PBANC_SN = (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK00000002')
--    AND DCMNT_SN NOT IN (SELECT DCMNT_SN FROM TB_ATCH_FILE WHERE DCMNT_SN IS NOT NULL);
