-- ============================================================
-- 알림 화면 확인용 테스트 알림 (03_test_data.sql 실행 후)
-- 실행: docker exec -e NLS_LANG=.AL32UTF8 oracle sqlplus 계정/비밀번호@//localhost:1521/FREEPDB1 @/tmp/05_test_notifications.sql
-- 오창준(2024-0102)과 최민정(2022-0009)에게 알림을 넣는다. 공고와 연결하지 않아 이동 버튼 없이 "읽음 처리"만 보인다.
-- 다시 실행해도 된다. (이 두 계정의 알림을 지우고 다시 넣는다.) 배포 DB에는 넣지 않는다.
--
-- [변경 이력]
-- v1.0  2026-10-22  오창준  최초 작성
-- ============================================================
SET DEFINE OFF;

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
