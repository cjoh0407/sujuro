-- sujuro 공통코드 초기 데이터 (01_schema.sql 실행 후)
-- 실행: sqlplus 계정/비밀번호@//localhost:1521/XEPDB1 @02_code_data.sql
-- v3.2(2026-10-26, 이세종): 외부 인력 코드 삭제(JG030 직급 외부, AU050 권한 외부, AS010 계정 발급 요청, NT080 계정 요청, NT090 계정 발급, PT030 참여구분 외부인력),
--                          목차구분(TOCSE) 추가, FL040 설명 변경
-- v3.3(2026-10-28, 김종민): 처리구분 PC010 이름 '관심 공고 등록' → '공고 등록' (요구사항 v3.2 용어 통일, 공고 상세 배정·결정 이력에 그대로 보임)
--                          파일구분 FL090 '입찰공고 첨부' 추가. 나라장터 공고 첨부를 TB_ATCH_FILE 에 넣을 때 제안요청서는 FL010, 사전규격 규격서는 FL080, 나머지는 FL090
--                          수집결과 CL070 '계약 전환' 추가, 실행구분 설명을 '평일 오후 5시' · '[지금 수집] 버튼'으로 바꿈
SET DEFINE OFF
DELETE FROM TB_COM_CD;
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('DEPTS', 'DP010', '부서', '경영지원실', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('DEPTS', 'DP020', '부서', '영업부', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('DEPTS', 'DP030', '부서', '개발부', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('JBGDS', 'JG010', '직급', '사원', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('JBGDS', 'JG020', '직급', '팀장', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('AUTHS', 'AU010', '권한', '영업부', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('AUTHS', 'AU020', '권한', '경영지원실', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('AUTHS', 'AU030', '권한', '팀장', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('AUTHS', 'AU040', '권한', '팀원', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('ACSTS', 'AS020', '계정상태', '초기 비밀번호', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('ACSTS', 'AS030', '계정상태', '활성', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('ACSTS', 'AS040', '계정상태', '잠금', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('ACSTS', 'AS050', '계정상태', '비활성', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('BZTYP', 'BT010', '사업유형', 'SI', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('BZTYP', 'BT020', '사업유형', '유지보수', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PTFRM', 'PF010', '참여형태', '주관', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PTFRM', 'PF020', '참여형태', '컨소시엄 참여', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PTFRM', 'PF030', '참여형태', '하도급', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS010', '공고상태', '신규', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS020', '공고상태', '배정 완료', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS030', '공고상태', '참여 확정', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS040', '공고상태', '제안 작성 중', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS050', '공고상태', '제출 완료', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS060', '공고상태', '결과 등록', 6, 'Y');
-- 유찰(PS070). 개찰 결과가 유찰일 때만 이 상태가 된다(NFR-012). 입찰 자체가 성립하지 않은 것이라
-- 참여를 안 한 불참(PS090)과 다르다. 유찰 뒤 재공고가 나면 기존 사업건의 단계를 바꾸지 않고
-- 새 사업건으로 등록한다(FR-024-1). 개찰결과 API 의 progrsDivCdNm 이 '유찰'이면 여기에 해당한다.
--   예) R26BK01553009 = 개찰일시는 있고 progrsDivCdNm '유찰', '단독 응찰로 경쟁입찰 불성립' (2026-10-26 확인)
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS070', '공고상태', '유찰', '개찰했으나 입찰이 성립하지 않았다. 단독 응찰·참가업체 부족 등', 7, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PBSTS', 'PS090', '공고상태', '불참', 8, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC010', '처리구분', '공고 등록', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC020', '처리구분', '공고 정정', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC030', '처리구분', '배정', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC040', '처리구분', '재배정', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC050', '처리구분', '참여 결정', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC060', '처리구분', '불참 처리', 6, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC070', '처리구분', '중도 철회', 7, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC080', '처리구분', '제안 작성 시작', 8, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC090', '처리구분', '나라장터 등록', 9, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PRCSE', 'PC100', '처리구분', '결과 등록', 10, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT010', '알림유형', '본공고 등록', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT020', '알림유형', '공고 변경', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT030', '알림유형', '배정', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT040', '알림유형', '참여 결정', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT050', '알림유형', '마감 임박', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT060', '알림유형', '중도 철회', 6, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT070', '알림유형', '제출 완료', 7, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT100', '알림유형', '목차 담당', 10, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('NOTIT', 'NT110', '알림유형', '평가표 업로드 요청', 11, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('SCHSE', 'SC010', '일정구분', '제출 마감', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('SCHSE', 'SC020', '일정구분', '내부 1차 마감', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('SCHSE', 'SC030', '일정구분', '내부 2차 마감', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('SCHSE', 'SC040', '일정구분', '발표', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('SCHSE', 'SC050', '일정구분', '서류 마감', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PTCSE', 'PT010', '참여구분', '주관팀', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PTCSE', 'PT020', '참여구분', '참여팀', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('PTCSE', 'PT040', '참여구분', '수행인력', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TOCST', 'TS010', '목차진행상태', '미착수', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TOCST', 'TS020', '목차진행상태', '작성 중', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TOCST', 'TS030', '목차진행상태', '검토 완료', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TOCSE', 'TC010', '목차구분', '제안서', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TOCSE', 'TC020', '목차구분', '제안요약서', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('DCPST', 'DS010', '서류준비상태', '미준비', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('DCPST', 'DS020', '서류준비상태', '준비 중', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('DCPST', 'DS030', '서류준비상태', '준비 완료', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TKCGS', 'TK010', '담당구분', '경영지원실', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('TKCGS', 'TK020', '담당구분', '제안팀', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL010', '파일구분', 'RFP', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL020', '파일구분', '제안서 파트', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL030', '파일구분', '제안서 통합본', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL040', '파일구분', '제안요약서', '제안요약서 목차별 파일 (목차일련번호를 채워 저장)', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL050', '파일구분', '제출서류', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL060', '파일구분', '조견표', 6, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL070', '파일구분', '평가표', 7, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL080', '파일구분', '사전규격 규격서', 8, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('FILSE', 'FL090', '파일구분', '입찰공고 첨부', '나라장터 입찰공고에 붙은 공고서·과업지시서·협약서 등. 제안요청서는 FL010', 9, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('QNASE', 'QA010', '질문답변구분', '예상 Q&A', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('QNASE', 'QA020', '질문답변구분', '발표 Q&A', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('BDRST', 'BR010', '입찰결과', '수주', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('BDRST', 'BR020', '입찰결과', '탈락', 2, 'Y');
-- 나라장터 API 사용 프로세스(2026-10-19)에서 추가된 코드
-- 공고단계 (10/23 회의). 조달청 API 로 추적 가능한 단계만 둔다.
-- 회의에서 "요청접수"도 넣기로 했지만 조달요청 서비스에 용역(Servc) 오퍼레이션이 없어 뺐다.
--   getPrcrmntReqInfoListThng(물품)·ListCnstwk(공사)·ListFrgcpt(외자)는 있고 ListServc 만 없다. (2026-10-26 실호출 확인)
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBSTP', 'PP010', '공고단계', '발주계획', '발주계획번호(R26DD…)로 등록한 상태. 공고가 나기를 기다린다', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBSTP', 'PP020', '공고단계', '사전규격', '사전규격번호(R26BD…)로 등록한 상태. 입찰공고가 나기를 기다린다', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBSTP', 'PP030', '공고단계', '입찰공고', '입찰공고번호(R26BK…)가 있는 상태. 참여 결정은 이 단계부터 할 수 있다', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBSTP', 'PP040', '공고단계', '개찰', '개찰이 끝나 참가업체와 점수가 나온 상태', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBSTP', 'PP050', '공고단계', '계약', '계약이 체결된 상태', 5, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('TRSTS', 'TR010', '추적상태', '입찰공고 대기', '발주계획·사전규격 API의 bidNtceNoList 가 비어 있다', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('TRSTS', 'TR020', '추적상태', '연결됨', '입찰공고로 전환했다', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('TRSTS', 'TR030', '추적상태', '추적 종료', '정해진 기간 안에 입찰공고가 없어 영업부가 종료했다', 3, 'Y');
-- 공고유형 (PBTYP). API ntceKindNm 을 코드로 옮긴 것이다. 실제로 오는 값은 네 가지뿐이다.
--   2026-10 실측 300건: 등록공고 246 / 재공고 28 / 취소공고 16 / 변경공고 10.
--   API 는 '등록공고'라고 하지만 화면에는 '일반공고'로 보여 주기로 했다(10/26). 그 매핑을 이 코드표가 맡는다.
--   코드로 옮기는 규칙은 BidCode.pbancTypeCdOf 에도 같이 있다. 규칙을 바꾸면 양쪽을 함께 고친다.
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBTYP', 'PY010', '공고유형', '일반공고', 'API ntceKindNm = 등록공고. 차수 000 이고 정정·취소가 없는 보통 공고. 실측 전체의 82%', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBTYP', 'PY020', '공고유형', '변경공고', 'API ntceKindNm = 변경공고. 반드시 차수가 올라간다(실측 10/10). 마감일·예산이 바뀌었을 수 있어 담당 팀에 다시 알려야 한다', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBTYP', 'PY030', '공고유형', '재공고', 'API ntceKindNm = 재공고. 유찰 뒤 다시 낸 공고라 차수는 000 부터 새로 시작한다. reNtceYn=Y 와 정확히 일치한다(실측 28/28)', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('PBTYP', 'PY040', '공고유형', '취소공고', 'API ntceKindNm = 취소공고. 입찰이 없어졌으므로 영업부에 알리고 목록에서 걸러낸다. 취소도 정정의 한 형태라 차수가 올라간다(실측 16/16)', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('EXCSE', 'EX010', '실행구분', '자동', '평일 오후 5시 하루 1회 스케줄러', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('EXCSE', 'EX020', '실행구분', '수동', '공고 목록의 [지금 수집] 버튼', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL010', '수집결과', '변경 없음', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL020', '수집결과', '입찰공고 전환', 2, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL030', '수집결과', '정정 반영', 3, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL040', '수집결과', '취소 공고', 4, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL050', '수집결과', '개찰 결과', 5, 'Y');
-- 사전규격 전환(CL060). 발주계획으로 등록한 공고가 사전규격 단계로 올라간 경우다.
--   발주계획과 사전규격은 서로를 가리키는 번호가 API 에 없어서, 신규 사전규격 목록을 받아
--   실수요기관명 + 사업명(정규화)으로 맞춘 결과다. BidCode.sameBizCase 참고.
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL060', '수집결과', '사전규격 전환', 6, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL070', '수집결과', '계약 전환', 7, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, SORT_SEQ, USE_YN) VALUES ('CLRST', 'CL090', '수집결과', '실패', 9, 'Y');
-- UI 목업 v3.2 의 반환 표시(n.returned)에 쓰인다
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('RTRSE', 'RT010', '반환구분', '불참', '배정받은 팀장이 불참을 결정해 영업부로 돌아왔다', 1, 'Y');
INSERT INTO TB_COM_CD (GROUP_CD, CD, CD_GROUP_NM, CD_NM, CD_EXPLN, SORT_SEQ, USE_YN) VALUES ('RTRSE', 'RT020', '반환구분', '중도 철회', '제안 진행 중 팀장이 철회해 영업부로 돌아왔다', 2, 'Y');
COMMIT;
