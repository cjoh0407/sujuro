-- ============================================================
-- 07_test_screens.sql : 화면 확인용 데이터 (01~04 실행 후)
-- 순서: 01 → 02 → 03 → 04 → 05 → 06 → 07 (db/README.md)
-- 1) 알림 화면 확인용 테스트 알림 (옛 05_test_notifications.sql)
--    오창준(2024-0102)과 최민정(2022-0009)에게 알림을 넣는다. 공고와 연결하지 않아 이동 버튼 없이 "읽음 처리"만 보인다.
-- 2) 제출서류 화면 확인용 체크리스트 (옛 07_test_docs.sql, 이세종 v1.0 2026-10-27)
--    샘플 사업건 2(R26BK00000002)에 제출서류 4건. 조견표는 웹 입력이라 서류 목록에 넣지 않는다 (FR-072).
-- 다시 실행해도 된다. (알림은 두 계정 것을 지우고 다시 넣고, 서류는 이미 있으면 넣지 않는다.) 배포 DB에는 넣지 않는다.
--
-- [변경 이력]
-- v1.1  2026-11-02  오창준  05_test_notifications.sql 을 07로 바꾸고 07_test_docs.sql(제출서류)을 2)로 합침
-- v1.0  2026-10-22  오창준  최초 작성
-- ============================================================

DELETE FROM TB_NOTI WHERE RCVR_ID IN ('2024-0102', '2022-0009');

INSERT INTO TB_NOTI (RCVR_ID, PBANC_SN, NOTI_TYPE_CD, NOTI_CN, CRT_DT)
VALUES ('2024-0102', NULL, 'NT030', '[테스트] 새 사업건이 공공사업1Div에 배정되었습니다. 참여 여부를 결정해 주세요.', SYSDATE - 2);
INSERT INTO TB_NOTI (RCVR_ID, PBANC_SN, NOTI_TYPE_CD, NOTI_CN, CRT_DT)
VALUES ('2024-0102', NULL, 'NT050', '[테스트] 제출 마감이 하루 남은 사업건이 있습니다.', SYSDATE - 1);
INSERT INTO TB_NOTI (RCVR_ID, PBANC_SN, NOTI_TYPE_CD, NOTI_CN, PRSL_YN, CRT_DT)
VALUES ('2024-0102', NULL, 'NT010', '[테스트] 이미 읽은 알림입니다.', 'Y', SYSDATE - 5);
INSERT INTO TB_NOTI (RCVR_ID, PBANC_SN, NOTI_TYPE_CD, NOTI_CN, CRT_DT)
VALUES ('2022-0009', NULL, 'NT070', '[테스트] 제안서 제출이 완료되었습니다. 결과를 입력해 주세요.', SYSDATE - 1/24);

COMMIT;

-- ------------------------------------------------------------
-- 2) 제출서류 체크리스트 (담당 TK010 경영지원실 / TK020 제안팀)
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
