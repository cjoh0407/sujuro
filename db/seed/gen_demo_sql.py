# 수주로 시연 전용 데이터 생성기: db/08_seed_demo.sql
# 실행: cd db/seed && python3 gen_demo_sql.py
#
# 03~07(gen_sql.py) 은 건드리지 않는다. 08 은 리셋 맨 끝에 돌면서
#   (1) 시연 마지막 장면에 쓸 실제 공고(고용노동 데이터분석 서비스 개선 사업)를 넣고
#   (2) 시연용 계정 이름·이메일을 바꾸고
#   (3) 가짜 번호로 제출 완료 상태였던 스마트 공항 공고를 한 단계 내린다.
# 시연이 끝나면 이 파일만 빼면 원래 시드로 돌아간다.
#
# 규칙(gen_sql.py 와 동일): DBeaver·SQL*Plus 겸용 (SET·/·PL/SQL 없음, 문자열에 & 없음, 문장 안 빈 줄 없음)
import os, json, random

random.seed(20261113)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "08_seed_demo.sql")

# ---------------------------------------------------------------- 기본값
NO       = "R26BK01541118"                  # 입찰공고번호
PLAN_NO  = "R26DD20751258"                  # 발주계획번호
SPEC_NO  = "R26BD00225065"                  # 사전규격등록번호
NM       = "고용노동 데이터분석 서비스 개선 사업"
TEAM     = "공공사업1Div"
FORM_NM  = "고용노동 데이터분석 제안서 목차"
BUDGET   = 262500610                        # 배정예산
LIMIT_PG = 200                              # 제안서 쪽수 제한

SALES    = "2023-0067"   # 영업부 김종민
MGMT     = "2022-0009"   # 경영지원실 오창준
LEADER   = "2024-0031"   # 공공사업1Div 팀장 이세종
MEMBERS  = ["2024-0102", "2023-0112", "2024-0115", "2025-0118", "2022-0121"]
TEAM_ALL = [LEADER] + MEMBERS

def q(s):
    if s is None: return "NULL"
    if isinstance(s, (int, float)): return str(s)
    s = str(s).replace("'", "''")
    assert "&" not in s, s
    return " || CHR(10) || ".join("'" + p + "'" for p in s.split("\n"))

def dt(s):   # '2026-06-23 11:00'
    return "TO_DATE('%s', 'YYYY-MM-DD HH24:MI')" % s

P    = "(SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = '%s')" % NO
TEAMSN = "(SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = '%s')" % TEAM
FORMSN = "(SELECT TOC_FORM_SN FROM TB_TOC_FORM WHERE TOC_FORM_NM = '%s')" % FORM_NM

def toc(no, se="TC010"):
    return ("(SELECT TOC_SN FROM TB_TOC WHERE PBANC_SN = %s AND TOC_SE_CD = '%s' AND TOC_NO = '%s')"
            % (P, se, no))
def ftoc(no):
    return ("(SELECT TOC_SN FROM TB_TOC WHERE TOC_FORM_SN = %s AND TOC_SE_CD = 'TC010' AND TOC_NO = '%s')"
            % (FORMSN, no))
def doc(nm):
    return "(SELECT DCMNT_SN FROM TB_SBMSN_DCMNT WHERE PBANC_SN = %s AND DCMNT_NM = %s)" % (P, q(nm))
def rqt(no):
    return "(SELECT RQT_MTTR_SN FROM TB_RQT_MTTR WHERE PBANC_SN = %s AND RQT_MTTR_NO = '%s')" % (P, no)

L = []
def ins(table, cols, vals):
    L.append("INSERT INTO %s (%s)" % (table, ", ".join(cols)))
    L.append("VALUES (%s);" % ", ".join(vals))
def say(s=""):
    L.append(("-- " + s) if s else "")

# ---------------------------------------------------------------- 목차 구조 (제안요청서 '3. 제안서 목차' 그대로)
# (장 제목, [(절 제목, 예상쪽수), ...])  · 별첨 성격인 평가항목별 자가점검표·참조표는 뺀다
CHAPTERS = [
    ("일반현황",      [("제안개요", 3), ("제안사 일반현황", 3), ("제안사의 조직 및 인원", 3), ("수행조직 및 업무분장", 4)]),
    ("전략 및 방법론", [("사업 이해도", 7), ("추진전략", 8), ("적용기술", 7), ("표준프레임워크 적용", 5), ("개발 방법론", 5)]),
    ("기술 및 기능",   [("기능 요구사항", 28), ("유지관리 인력 요구사항", 5), ("데이터 요구사항", 14),
                      ("보안 요구사항", 17), ("제약사항", 7)]),
    ("성능 및 품질",   [("성능 요구사항", 8), ("인터페이스 요구사항", 6), ("테스트 요구사항", 7), ("품질 요구사항", 9)]),
    ("프로젝트 관리",  [("관리방법론", 8), ("관리역량", 6), ("일정계획", 5), ("개발장비", 4)]),
    ("프로젝트지원",   [("품질보증", 6), ("시험운영", 4), ("교육훈련", 5), ("유지관리", 5), ("기밀보안", 4), ("비상대책", 3)]),
    ("기타 사항",      []),
]

flat = []          # (toc_no, 이름, 상위no, 예상쪽수 or None)
for ci, (cnm, subs) in enumerate(CHAPTERS, start=1):
    flat.append((str(ci), cnm, None, None if subs else 4))
    for si, (snm, pg) in enumerate(subs, start=1):
        flat.append(("%d.%d" % (ci, si), snm, str(ci), pg))
LEAF = [f for f in flat if f[3] is not None]
assert len(flat) == 35, len(flat)
assert sum(f[3] for f in LEAF) == LIMIT_PG, sum(f[3] for f in LEAF)

SUMMARY = [("1", "제안 개요 및 사업 이해"), ("2", "전략 및 방법론"), ("3", "기술·성능 및 품질"),
           ("4", "프로젝트 관리 및 지원")]

# ---------------------------------------------------------------- 요구사항 111건 (제안요청서 '나. 요구사항 목록표')
REQ_SE = {   # 접두어 -> (구분명, 매핑할 목차)
    "SFR": ("기능 요구사항",          ["3.1"]),
    "MHR": ("유지관리 인력 요구사항",  ["3.2"]),
    "DAR": ("데이터 요구사항",        ["3.3"]),
    "SER": ("보안 요구사항",          ["3.4"]),
    "COR": ("제약사항",              ["3.5"]),
    "PER": ("성능 요구사항",          ["4.1"]),
    "INR": ("인터페이스 요구사항",     ["4.2"]),
    "TER": ("테스트 요구사항",        ["4.3"]),
    "QUR": ("품질 요구사항",          ["4.4"]),
    "PMR": ("프로젝트관리 요구사항",   ["5.1", "5.2", "5.3", "5.4"]),
    "PSR": ("프로젝트지원 요구사항",   ["6.1", "6.2", "6.3", "6.4", "6.5", "6.6"]),
}
REQS = json.load(open(os.path.join(HERE, "demo_reqs.json"), encoding="utf-8"))
assert len(REQS) == 111, len(REQS)

# ---------------------------------------------------------------- 쓰기
say("============================================================")
say("08_seed_demo.sql : 시연 전용 데이터 (고용노동 데이터분석 서비스 개선 사업)")
say("순서: 01 → ... → 07 → 08. 테스트·시연 전용, 배포 DB 에는 넣지 않는다.")
say("이 파일은 db/seed/gen_demo_sql.py 로 만든다. 고칠 때는 생성기를 고치고 다시 만든다.")
say("03~07 은 건드리지 않는다. 시연이 끝나면 이 파일만 빼면 원래 시드로 돌아간다.")
say("")
say("넣는 것")
say("  - 실제 공고 %s (단계 입찰공고 PP030 · 상태 제출 완료 PS050 · 결과 없음)" % NO)
say("    [지금 수집] 을 누르면 개찰 -> 계약 -> 결과 등록까지 나라장터에서 가져온다.")
say("    그래서 업체평가결과·입찰결과는 일부러 비워 둔다.")
say("  - 목차 양식 '%s' (%s)" % (FORM_NM, TEAM))
say("  - 목차 35개(7장 28절) · 요구사항 111건 · 제안서 파일 · 제출서류 · 조견표 · 질의응답 · 일정")
say("고치는 것")
say("  - 시연 계정 이름·이메일 4명")
say("  - 스마트 공항 공고(R26BK8000303): 번호가 가짜라 제출 완료로 두면 수집이 헛돈다. 제안 작성 중으로 내린다.")
say("  - 업체평가결과의 우리 회사 이름 오티아이 -> 엑사아이엔티 (사업자번호 107-87-28988)")
say("날짜: 공고와 제안 데이터는 나라장터 실제 날짜(2026-05 ~ 06) 고정")
say("============================================================")
L.append("")

# 1) 목차 양식 ------------------------------------------------------------
say("1) 목차 양식: %s" % FORM_NM)
ins("TB_TOC_FORM", ["TEAM_SN", "TOC_FORM_NM", "TOC_SE_CD", "RGTR_ID", "REG_DT"],
    [TEAMSN, q(FORM_NM), "'TC010'", q(LEADER), dt("2026-05-28 09:00")])
seq = 0
for no, nm, up, pg in flat:
    seq += 1
    cols = ["TOC_FORM_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ"]
    vals = [FORMSN, ftoc(up) if up else "NULL", "'TC010'", q(no), q(nm), str(seq)]
    if pg is not None:
        cols.append("EXPC_PAGE_CNT"); vals.append(str(pg))
    ins("TB_TOC", cols, vals)
L.append("")

# 2) 공고 ----------------------------------------------------------------
say("2) 시연 사업 공고: %s (%s)" % (NM, TEAM))
say("   수요기관 한국고용정보원 · 발주기관 조달청 · 협상에 의한 계약 기술 90 / 가격 10")
ins("TB_BID_PBANC",
    ["BID_PBANC_NO", "BID_PBANC_CYCL", "ORDER_PLAN_NO", "BF_SPEC_RGST_NO", "PBANC_STEP_CD", "PBANC_TYPE_CD",
     "PBANC_NM", "ORDER_INST_NM", "DMND_INST_NM", "BIZ_TYPE_CD", "PTCP_SHP_CD", "SW_BIZ_YN",
     "ALTMNT_BGT_AMT", "BIZ_PRD_CN", "BID_DDLN_DT", "OPENG_DT", "PRSNTN_DT", "EVL_MTHD_NM",
     "TECH_SCOR_SCR", "PRC_SCOR_SCR", "LMT_PAGE_CNT", "PBANC_STTS_CD", "SPRVSN_TEAM_SN", "TOC_FORM_SN",
     "RGTR_ID", "SBMSN_DT", "PRSNR_ID", "CLCT_DT", "LAST_CFMTN_DT", "REG_DT"],
    [q(NO), "0", q(PLAN_NO), q(SPEC_NO), "'PP030'", "'PY010'",
     q(NM), q("조달청"), q("한국고용정보원"), "'BT010'", "'PF010'", "'Y'",
     str(BUDGET), q("계약일로부터 150일"), dt("2026-06-23 10:00"), dt("2026-06-23 11:00"),
     dt("2026-06-26 09:30"), q("협상에 의한 계약"),
     "90", "10", str(LIMIT_PG), "'PS050'", TEAMSN, FORMSN,
     q(SALES), dt("2026-06-22 18:00"), q(MGMT), dt("2026-05-26 18:20"), dt("2026-06-22 18:05"),
     dt("2026-05-07 09:10")])
L.append("")

say("3) 처리 이력: 등록 -> 배정 -> 참여 -> 제안 작성 시작 -> 나라장터 등록")
HIST = [
    (None,   "PC010", "발주계획번호 " + PLAN_NO, SALES,  "2026-05-07 09:10"),
    (TEAMSN, "PC030", "참여 형태 주관",            SALES,  "2026-05-27 14:00"),
    (TEAMSN, "PC050", "참여",                     LEADER, "2026-05-28 10:30"),
    (None,   "PC080", None,                      LEADER, "2026-05-28 11:00"),
    (None,   "PC090", None,                      MGMT,   "2026-06-22 18:00"),
]
for team, cd, rsn, who, when in HIST:
    ins("TB_PBANC_PRCS_HSTRY", ["PBANC_SN", "TRGT_TEAM_SN", "PRCS_SE_CD", "PRCS_RSN", "PRCR_ID", "PRCS_DT"],
        [P, team or "NULL", q(cd), q(rsn), q(who), dt(when)])
L.append("")

say("4) 수집 이력: 발주계획 -> 사전규격 -> 입찰공고 (단계 이력 화면에 보인다)")
ins("TB_PBANC_CLCT_HSTRY",
    ["PBANC_SN", "CLCT_SEQ", "PBANC_STEP_CD", "PBANC_NO", "ALTMNT_BGT_AMT", "CLCT_DT"],
    [P, "1", "'PP010'", q(PLAN_NO), str(BUDGET), dt("2026-05-07 09:10")])
ins("TB_PBANC_CLCT_HSTRY",
    ["PBANC_SN", "CLCT_SEQ", "PBANC_STEP_CD", "PBANC_NO", "ALTMNT_BGT_AMT", "CLCT_DT"],
    [P, "2", "'PP020'", q(SPEC_NO), str(BUDGET), dt("2026-05-10 17:05")])
ins("TB_PBANC_CLCT_HSTRY",
    ["PBANC_SN", "CLCT_SEQ", "PBANC_STEP_CD", "PBANC_NO", "BID_PBANC_CYCL", "PBANC_TYPE_CD",
     "ALTMNT_BGT_AMT", "BID_DDLN_DT", "OPENG_DT", "CLCT_DT"],
    [P, "3", "'PP030'", q(NO), "0", "'PY010'", str(BUDGET),
     dt("2026-06-23 10:00"), dt("2026-06-23 11:00"), dt("2026-05-26 18:20")])
L.append("")

# 5) 제안팀 --------------------------------------------------------------
say("5) 제안팀원 (주관팀 %s 6명)" % TEAM)
for u in TEAM_ALL:
    ins("TB_PRP_TMMBR", ["PBANC_SN", "USER_ID", "PTCP_SE_CD", "FLFMT_INPUT_YN", "REG_DT"],
        [P, q(u), "'PT010'", "'N'", dt("2026-05-28 11:10")])
L.append("")

# 6) 목차 ----------------------------------------------------------------
say("6) 제안서 목차 35개 (제안요청서 '3. 제안서 목차' 그대로) · 예상 쪽수 합계 %d" % LIMIT_PG)
pic = {}
seq = 0
for i, (no, nm, up, pg) in enumerate(flat):
    seq += 1
    cols = ["PBANC_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ"]
    vals = [P, toc(up) if up else "NULL", "'TC010'", q(no), q(nm), str(seq)]
    if pg is not None:
        who = TEAM_ALL[len(pic) % len(TEAM_ALL)]
        pic[no] = who
        cols += ["PIC_ID", "EXPC_PAGE_CNT"]; vals += [q(who), str(pg)]
    cols.append("PRGRS_STTS_CD"); vals.append("'TS030'")
    ins("TB_TOC", cols, vals)
L.append("")
say("   제안요약서 목차 4개")
for i, (no, nm) in enumerate(SUMMARY):
    ins("TB_TOC", ["PBANC_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ",
                   "PIC_ID", "EXPC_PAGE_CNT", "PRGRS_STTS_CD"],
        [P, "NULL", "'TC020'", q(no), q(nm), str(i + 1), q(TEAM_ALL[i % len(TEAM_ALL)]), "1", "'TS030'"])
L.append("")

# 7) 요구사항 ------------------------------------------------------------
say("7) 요구사항 111건 (제안요청서 '5. 상세 요구사항' 목록표) · 전부 최하위 목차에 매핑")
cnt_by_pre = {}
for r in REQS:
    pre = r["pre"]
    se_nm, targets = REQ_SE[pre]
    k = cnt_by_pre.get(pre, 0); cnt_by_pre[pre] = k + 1
    target = targets[k % len(targets)]
    ins("TB_RQT_MTTR", ["PBANC_SN", "RQT_MTTR_NO", "RQT_MTTR_NM", "RQT_MTTR_SE_NM", "TOC_SN", "RGTR_ID", "REG_DT"],
        [P, q("%s-%03d" % (pre, int(r["num"]))), q(r["nm"]), q(se_nm), toc(target),
         q(pic.get(target, LEADER)), dt("2026-05-29 10:00")])
L.append("")

# 8) 파일 ----------------------------------------------------------------
say("8) 파일: 공고 첨부 2 · 파트별 %d · 제안요약서 4 · 통합본 2 · 제출서류 8" % len(LEAF))
MF = []   # manifest 에 더할 (dest, src)
def file_row(cd, toc_sn, dcmnt_sn, intg, ver, last, dcs, cfm, orgnl, strg, path, size, extn, who, when, src):
    ins("TB_ATCH_FILE",
        ["PBANC_SN", "FILE_SE_CD", "TOC_SN", "DCMNT_SN", "INTG_CYCL", "VER_NO", "LAST_YN",
         "DCSMKR_ID", "CFMTN_DT", "ORGNL_FILE_NM", "STRG_FILE_NM", "FILE_PATH_NM", "FILE_SZ",
         "FILE_EXTN_NM", "RGTR_ID", "REG_DT"],
        [P, q(cd), toc_sn, dcmnt_sn, intg, q(ver), q(last), q(dcs), dt(cfm) if cfm else "NULL",
         q(orgnl), q(strg), q(path), str(size), q(extn), q(who), dt(when)])
    MF.append(("%s/%s" % (path, strg), src))

file_row("FL010", "NULL", "NULL", "NULL", "1", "N", None, None,
         "공고_제안요청서.hwpx", NO + "-FL010-1.hwpx", "seed/bid", 1363518, "hwpx",
         SALES, "2026-05-26 18:25", "rfp/고용노동_제안요청서.hwpx")
file_row("FL090", "NULL", "NULL", "NULL", "1", "N", None, None,
         NO + "_입찰공고서_한국고용정보원.pdf", NO + "-FL090-1.pdf", "seed/bid", 92306, "pdf",
         SALES, "2026-05-26 18:25", "rfp/제안요청서_A.pdf")
L.append("")
EXT = ["pptx", "pptx", "pdf"]
SZ = {"pptx": 32676, "pdf": 45134}
for i, (no, nm, up, pg) in enumerate(LEAF):
    e = EXT[i % 3]
    file_row("FL020", toc(no), "NULL", "NULL", "1", "N", None, None,
             "%s_%s.%s" % (no, nm.replace(" ", ""), e),
             "%s-FL020-%s.%s" % (NO, no.replace(".", "_"), e),
             "seed/proposal", SZ[e], e, pic[no], "2026-06-18 18:00",
             "proposal/제안서_%s.%s" % (["사업이해", "수행방안", "사업관리"][i % 3], e))
L.append("")
for i, (no, nm) in enumerate(SUMMARY):
    file_row("FL040", toc(no, "TC020"), "NULL", "NULL", "1", "N", None, None,
             "제안요약서_%s.pdf" % nm.replace(" ", ""), "%s-FL040-%s.pdf" % (NO, no),
             "seed/proposal", 45134, "pdf", TEAM_ALL[i % len(TEAM_ALL)], "2026-06-19 15:00",
             "proposal/제안요약서_%d장.pdf" % (i % 2 + 1))
L.append("")
base = NM.replace(" ", "")
file_row("FL030", "NULL", "NULL", "1", "1", "N", None, None,
         "%s_제안서_1차.pdf" % base, NO + "-FL030-1.pdf", "seed/proposal", 109384, "pdf",
         LEADER, "2026-06-20 17:00", "proposal/제안서_통합본_1차.pdf")
file_row("FL030", "NULL", "NULL", "2", "1", "Y", LEADER, "2026-06-22 11:00",
         "%s_제안서_최종.pdf" % base, NO + "-FL030-2.pdf", "seed/proposal", 109384, "pdf",
         LEADER, "2026-06-22 09:30", "proposal/제안서_통합본_최종.pdf")
L.append("")

# 9) 제출서류 ------------------------------------------------------------
say("9) 제출서류 8건 (v4.7: 파일이 있으면 준비 상태가 완료 DS030)")
DOCS = [
    ("사업자등록증 사본",                 "Y", "TK010", "사업자등록증.pdf",      "pdf", 37232, MGMT,   "docs/사업자등록증.pdf"),
    ("법인 등기사항증명서",               "Y", "TK010", "법인등기부등본.pdf",     "pdf", 37125, MGMT,   "docs/법인등기부등본.pdf"),
    ("재무제표",                        "N", "TK010", "재무제표.pdf",          "pdf", 31055, MGMT,   "docs/재무제표.pdf"),
    ("입찰참가신청서",                   "Y", "TK010", "입찰참가신청서.pdf",     "pdf", 36866, MGMT,   "docs/입찰참가신청서.pdf"),
    ("청렴서약서",                      "Y", "TK010", "청렴서약서.docx",       "docx", 37163, MGMT,   "docs/청렴서약서.docx"),
    ("보안확약서",                      "Y", "TK010", "보안확약서.docx",       "docx", 37154, MGMT,   "docs/보안확약서.docx"),
    ("입찰업체 서약서 및 공동책임 확인서",   "Y", "TK010", "입찰업체서약서.docx",    "docx", 37154, MGMT,   "docs/보안확약서.docx"),
    ("투입인력 현황표",                  "Y", "TK020", "투입인력현황.xlsx",      "xlsx", 5581,  LEADER, "docs/투입인력현황.xlsx"),
]
for nm, esntl, tk, _f, _e, _s, _w, _src in DOCS:
    ins("TB_SBMSN_DCMNT", ["PBANC_SN", "DCMNT_NM", "ESNTL_YN", "TKCG_SE_CD", "PRGRS_STTS_CD", "SBMSN_DDLN_DT"],
        [P, q(nm), q(esntl), q(tk), "'DS030'", dt("2026-06-22 10:00")])
L.append("")
for i, (nm, _e1, _tk, fnm, extn, size, who, src) in enumerate(DOCS, start=1):
    file_row("FL050", "NULL", doc(nm), "NULL", "1", "N", None, None,
             fnm, "%s-FL050-%d.%s" % (NO, i, extn), "seed/docs", size, extn, who,
             "2026-06-21 14:00", src)
L.append("")

# 10) 조견표 -------------------------------------------------------------
say("10) 조견표: 기술능력평가 90점 (항목은 경영지원실, 쪽번호는 제안팀이 적은 것)")
EVL = [("사업 이해도", 10, "17-23"), ("추진 전략 및 방법론", 10, "24-39"), ("기능 구현", 10, "40-63"),
       ("데이터·보안", 15, "64-95"), ("성능 및 품질", 10, "96-128"), ("프로젝트 관리", 15, "129-154"),
       ("프로젝트 지원", 10, "155-186"), ("상생협력", 5, "187-193"), ("기타 지원 사항", 5, "194-200")]
assert sum(e[1] for e in EVL) == 90
for i, (nm, scr, pg) in enumerate(EVL, start=1):
    ins("TB_EVL_ARTCL", ["PBANC_SN", "EVL_ARTCL_NM", "EVL_SCOR_SCR", "PAGE_NO", "SORT_SEQ"],
        [P, q(nm), str(scr), q(pg), str(i)])
L.append("")

# 11) 질의응답 -----------------------------------------------------------
say("11) 질의응답")
QA = [
    ("QA010", "이용신청을 일원화하면 기존 신청 건은 어떻게 됩니까?",
     "기존 신청 건은 그대로 두고 새 창구로만 받습니다. 이관 대상은 발주기관과 범위를 정해 일괄 이관합니다.", "SFR-001", MEMBERS[0], "2026-06-10 15:00"),
    ("QA010", "원격분석 통합 로그인은 기존 계정과 어떻게 연결합니까?",
     "기존 계정을 그대로 쓰고 통합 로그인 화면만 앞에 둡니다. 계정 매핑표를 만들어 시험 이관 때 대조합니다.", "SFR-003", MEMBERS[1], "2026-06-10 15:10"),
    ("QA010", "모바일용 서비스는 앱입니까, 웹입니까?",
     "반응형 웹으로 만듭니다. 설치 없이 모바일 브라우저에서 같은 기능을 씁니다.", "SFR-007", MEMBERS[2], "2026-06-11 10:00"),
    ("QA010", "결과 표출 성능 기준은 어떻게 검증합니까?",
     "성능 시험 도구로 단계별 부하를 주고 기준 시간 안에 드는지 확인한 결과서를 제출합니다.", "PER-002", MEMBERS[3], "2026-06-11 10:20"),
    ("QA010", "데이터 활용환경의 오픈소스 패치는 어디까지 책임집니까?",
     "유지관리 기간 동안 R·Python 패키지와 보안 패치를 정기 적용하고 적용 내역을 월 단위로 보고합니다.", "MHR-001", MEMBERS[4], "2026-06-12 09:30"),
    ("QA020", "유사 사업 경험에서 가장 어려웠던 점은 무엇이었습니까?",
     "기관 간 연계 일정이 늦어진 점이었습니다. 연계 시험 환경을 먼저 마련해 일정을 지켰습니다.", None, LEADER, "2026-06-26 10:00"),
    ("QA020", "투입 인력이 사업 기간 내내 유지됩니까?",
     "핵심 인력은 전 기간 전담으로 두고, 교체가 필요하면 동급 이상으로 사전 협의 후 투입합니다.", None, LEADER, "2026-06-26 10:10"),
]
for se, qs, ans, rno, who, when in QA:
    ins("TB_QSTN_ANS", ["PBANC_SN", "QSTN_ANS_SE_CD", "QSTN_CN", "ANS_CN", "RQT_MTTR_SN", "WRTR_ID", "WRT_DT"],
        [P, q(se), q(qs), q(ans), rqt(rno) if rno else "NULL", q(who), dt(when)])
L.append("")

# 12) 일정 ---------------------------------------------------------------
say("12) 일정")
SCH = [("SC010", "제안서 제출 마감", "2026-06-23 10:00", "Y"),
       ("SC020", "내부 1차 검토 회의", "2026-06-05 14:00", "N"),
       ("SC020", "통합본 최종 점검", "2026-06-20 10:00", "N"),
       ("SC020", "발표 리허설", "2026-06-25 16:00", "N"),
       ("SC020", "제안서 평가(발표)", "2026-06-26 09:30", "N")]
for cd, ttl, when, auto in SCH:
    ins("TB_SCHDL", ["TEAM_SN", "PBANC_SN", "SCHDL_SE_CD", "SCHDL_TTL", "SCHDL_BGNG_DT", "AUTO_REG_YN", "RGTR_ID"],
        [TEAMSN, P, q(cd), q(ttl), dt(when), q(auto), q(LEADER)])
L.append("")

# 13) 계정 손질 ----------------------------------------------------------
say("13) 시연 계정 이름·이메일")
say("    메일이 실제로 오는지 보여줄 계정은 영업부(단계 전환 알림 받는 쪽)와 그 팀 팀장이다.")
for uid, nm, eml in [(SALES,  "김종민", "jongminkim46170@gmail.com"),
                     (MGMT,   "오창준", "cjoh0407@gmail.com"),
                     (LEADER, "이세종", "lsj010705@gmail.com"),
                     ("2024-0102", "유정환", None)]:
    if eml:
        L.append("UPDATE TB_USER SET USER_NM = %s, EML_ADDR = %s WHERE USER_ID = %s;" % (q(nm), q(eml), q(uid)))
    else:
        L.append("UPDATE TB_USER SET USER_NM = %s WHERE USER_ID = %s;" % (q(nm), q(uid)))
L.append("")

# 14) 스마트 공항 내리기 --------------------------------------------------
say("14) 스마트 공항 공고(R26BK8000303) 를 제안 작성 중으로 내린다")
say("    가짜 번호라 제출 완료로 두면 [지금 수집] 의 결과 등록 단계가 헛돌고 수집 로그에 실패가 남는다.")
say("    제출 완료 공고는 시연 사업 하나만 남는다. 마감·개찰·발표일은 앞으로 민다.")
L.append("UPDATE TB_BID_PBANC SET PBANC_STTS_CD = 'PS040', SBMSN_DT = NULL, PRSNR_ID = NULL,")
L.append("    BID_DDLN_DT = TRUNC(SYSDATE) + 6 + 10.0/24, OPENG_DT = TRUNC(SYSDATE) + 8 + 11.0/24,")
L.append("    PRSNTN_DT = TRUNC(SYSDATE) + 10 + 14.0/24")
L.append("  WHERE BID_PBANC_NO = 'R26BK8000303';")
L.append("DELETE FROM TB_PBANC_PRCS_HSTRY")
L.append("  WHERE PBANC_SN = (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK8000303')")
L.append("    AND PRCS_SE_CD = 'PC090';")
L.append("UPDATE TB_SBMSN_DCMNT SET SBMSN_DDLN_DT = TRUNC(SYSDATE) + 5 + 10.0/24")
L.append("  WHERE PBANC_SN = (SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = 'R26BK8000303');")
L.append("")

say("15) 우리 회사 이름: 오티아이 -> 엑사아이엔티 (사업자번호 107-87-28988 은 그대로)")
L.append("UPDATE TB_CO_EVL_RSLT SET CO_NM = '엑사아이엔티' WHERE CO_NM = '오티아이';")
L.append("")
L.append("COMMIT;")
L.append("")

# 16) 확인 쿼리 ----------------------------------------------------------
say("16) 확인: 아래가 모두 기대값이어야 한다")
CHECKS = [
    ("시연 사업 입찰공고·제출완료·결과없음", 1,
     "SELECT COUNT(*) FROM TB_BID_PBANC p WHERE p.BID_PBANC_NO = '%s' AND p.PBANC_STEP_CD = 'PP030'"
     " AND p.PBANC_STTS_CD = 'PS050' AND NOT EXISTS (SELECT 1 FROM TB_BID_RSLT r WHERE r.PBANC_SN = p.PBANC_SN)"
     " AND NOT EXISTS (SELECT 1 FROM TB_CO_EVL_RSLT c WHERE c.PBANC_SN = p.PBANC_SN)" % NO),
    ("제출 완료 상태인 공고 수", 1,
     "SELECT COUNT(*) FROM TB_BID_PBANC WHERE PBANC_STTS_CD = 'PS050'"),
    ("시연 사업 목차 수 (제안서35+요약서4)", 39,
     "SELECT COUNT(*) FROM TB_TOC WHERE PBANC_SN = %s" % P),
    ("시연 사업 요구사항 수", 111,
     "SELECT COUNT(*) FROM TB_RQT_MTTR WHERE PBANC_SN = %s" % P),
    ("목차에 안 붙은 요구사항", 0,
     "SELECT COUNT(*) FROM TB_RQT_MTTR WHERE PBANC_SN = %s AND TOC_SN IS NULL" % P),
    ("최하위 아닌 목차에 붙은 요구사항", 0,
     "SELECT COUNT(*) FROM TB_RQT_MTTR r WHERE r.PBANC_SN = %s AND EXISTS"
     " (SELECT 1 FROM TB_TOC c WHERE c.UP_TOC_SN = r.TOC_SN)" % P),
    ("최하위 아닌 목차에 붙은 파일", 0,
     "SELECT COUNT(*) FROM TB_ATCH_FILE f WHERE f.PBANC_SN = %s AND f.TOC_SN IS NOT NULL"
     " AND EXISTS (SELECT 1 FROM TB_TOC c WHERE c.UP_TOC_SN = f.TOC_SN)" % P),
    ("예상 쪽수 합계 (쪽수 제한과 같아야)", LIMIT_PG,
     "SELECT NVL(SUM(EXPC_PAGE_CNT), 0) FROM TB_TOC WHERE PBANC_SN = %s AND TOC_SE_CD = 'TC010'" % P),
    ("조견표 배점 합계", 90,
     "SELECT NVL(SUM(EVL_SCOR_SCR), 0) FROM TB_EVL_ARTCL WHERE PBANC_SN = %s" % P),
    ("시연 사업 파일 수", 45,
     "SELECT COUNT(*) FROM TB_ATCH_FILE WHERE PBANC_SN = %s" % P),
    ("이름을 못 바꾼 계정", 0,
     "SELECT COUNT(*) FROM TB_USER WHERE USER_NM IN ('박서연', '최민정', '김가희')"),
    ("주관팀 팀장이 제안팀원 아닌 시연 사업", 0,
     "SELECT COUNT(*) FROM TB_BID_PBANC p WHERE p.BID_PBANC_NO = '%s' AND NOT EXISTS"
     " (SELECT 1 FROM TB_PRP_TMMBR m JOIN TB_USER u ON u.USER_ID = m.USER_ID"
     " WHERE m.PBANC_SN = p.PBANC_SN AND u.TEAM_SN = p.SPRVSN_TEAM_SN AND u.JBGD_CD = 'JG020')" % NO),
    ("마감 지났는데 참여확정·작성중인 사업", 0,
     "SELECT COUNT(*) FROM TB_BID_PBANC WHERE PBANC_STTS_CD IN ('PS030', 'PS040')"
     " AND BID_DDLN_DT IS NOT NULL AND BID_DDLN_DT < SYSDATE"),
    ("아직 오티아이로 남은 업체평가 행", 0,
     "SELECT COUNT(*) FROM TB_CO_EVL_RSLT WHERE CO_NM = '오티아이'"),
]
say("한 번에 보도록 UNION ALL 로 묶는다. 판정이 모두 OK 여야 한다.")
parts = []
for label, want, sql in CHECKS:
    # 인라인 뷰에 컬럼 별명을 붙이는 문법(x(v))은 오라클에 없다. 스칼라 서브쿼리로 감싼다.
    parts.append("SELECT %s AS 확인, TO_CHAR(v) || ' / %d' AS 값,"
                 " CASE WHEN v = %d THEN 'OK' ELSE '<<< 확인' END AS 판정, %d AS s"
                 " FROM (SELECT (%s) AS v FROM DUAL)" % (q(label), want, want, len(parts) + 1, sql))
L.append("\n UNION ALL\n".join(parts).replace("\\n", "\n") + "\n ORDER BY s;")

with open(OUT, "w", encoding="utf-8") as f:
    f.write("\n".join(L) + "\n")

# demo-files/manifest.tsv 를 고친다. 이 공고 줄만 지우고 다시 넣으므로 몇 번 돌려도 같다.
MFPATH = os.path.join(HERE, "..", "demo-files", "manifest.tsv")
lines = [l for l in open(MFPATH, encoding="utf-8").read().split("\n") if l != ""]
keep = [l for l in lines if NO not in l.split("\t")[0]]
with open(MFPATH, "w", encoding="utf-8") as f:
    f.write("\n".join(keep + ["%s\t%s" % (d, s) for d, s in MF]) + "\n")

print("썼다: %s (%d 줄)" % (OUT, len(L)))
print("manifest: %d 줄 중 %d 줄이 시연 공고 (%s)" % (len(keep) + len(MF), len(MF), MFPATH))
print("목차 %d · 최하위 %d · 요구사항 %d · 파일 %d" % (len(flat), len(LEAF), len(REQS), len(MF)))
