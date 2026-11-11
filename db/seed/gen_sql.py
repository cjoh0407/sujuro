# 수주로 테스트·시연 데이터 생성기: db/03~07_seed_*.sql + db/demo-files/manifest.tsv + 사업별 평가표 엑셀
# 실행: cd db/seed && python3 gen_sql.py   (openpyxl 필요. 먼저 gen_files.py 로 원본 파일이 있어야 한다)
# 규칙: DBeaver·SQL*Plus 겸용 (SET·/·PL/SQL 없음, 문자열에 & 없음, 문장 안 빈 줄 없음)
import os, random, datetime, csv
from openpyxl import Workbook
from openpyxl.styles import Font

random.seed(20261104)
HERE = os.path.dirname(os.path.abspath(__file__))
OUTDIR = os.path.join(HERE, "..")                 # db/ 에 03~07 을 쓴다
FILES = os.path.join(HERE, "..", "demo-files")    # 원본 (db/demo-files)
os.makedirs(OUTDIR, exist_ok=True)
os.makedirs(os.path.join(FILES, "evaluation", "gen"), exist_ok=True)

PW = "$2b$10$nFBh95LuF8O959s4LLejnO6SKJJQeVjPQrpdEtPD1ExF6hfr54Vzq"   # Demo!2026a
SALES, MGMT = "2023-0067", "2022-0009"
OUR_BIZ, OUR_NM = "1078728988", "오티아이"

def q(s):
    if s is None: return "NULL"
    if isinstance(s, (int, float)): return str(s)
    s = str(s).replace("'", "''")
    assert "&" not in s, s
    # 문자열 안 줄바꿈은 CHR(10) 으로 이어 붙인다 (SQL*Plus·DBeaver 가 줄 단위로 끊지 않게)
    return " || CHR(10) || ".join("'" + part + "'" for part in s.split("\n"))

def dt_fixed(s):  # 'YYYY-MM-DD HH:MI'
    return "TO_DATE('%s', 'YYYY-MM-DD HH24:MI')" % s

def dt_rel(days, hour=9, minute=0):  # 실행일 기준
    h = hour + minute / 60.0
    return "TRUNC(SYSDATE) %s %s + %s/24" % ("+" if days >= 0 else "-", abs(days), round(h, 4))

def add_days(s, d, hm=None):
    t = datetime.datetime.strptime(s, "%Y-%m-%d %H:%M") + datetime.timedelta(days=d)
    if hm: t = t.replace(hour=hm[0], minute=hm[1])
    return t.strftime("%Y-%m-%d %H:%M")

def T(team): return "(SELECT TEAM_SN FROM TB_TEAM WHERE TEAM_NM = %s)" % q(team)

class Out:
    def __init__(self, name, title, desc):
        self.name = name; self.lines = []
        self.lines += ["-- ============================================================",
                       "-- %s : %s" % (name, title),
                       "-- 순서: 01 → 02 → 03 → 04 → 05 → 06 → 07 (db/README.md). 테스트·시연 전용, 배포 DB에는 넣지 않는다.",
                       "-- 이 파일은 db/seed/gen_sql.py 로 만든다. 고칠 때는 생성기를 고치고 다시 만든다."]
        self.lines += ["-- " + d for d in desc]
        self.lines += ["-- 날짜: 2024·2025 과거 사업은 고정, 2026 진행·대기 공고는 실행일(SYSDATE) 기준", "-- ============================================================", ""]
    def c(self, text): self.lines.append(""); self.lines.append("-- " + text)
    def s(self, sql): self.lines.append(sql.rstrip().rstrip(";") + ";")
    def ins(self, table, cols, vals):
        self.s("INSERT INTO %s (%s)\nVALUES (%s)" % (table, ", ".join(cols), ", ".join(vals)))
    def commit(self): self.lines.append("COMMIT;")
    def save(self):
        body = "\n".join(self.lines) + "\n"
        for ln in body.split("\n"):
            assert "&" not in ln, ln
        open(os.path.join(OUTDIR, self.name), "w", encoding="utf-8").write(body)

# ================================================================ 사람
TEAMS_DEV = ["공공사업1Div", "공공사업2Div", "공공사업3Div", "전략사업1Div", "전략사업2Div", "전략사업3Div"]
L, M = "AU030", "AU040"
people = [
    ("2023-0067", "박서연", "영업부", "AU010", "AS030", "seoyeon.park@example.com"),
    ("2022-0009", "최민정", "경영지원실", "AU020", "AS030", "minjung.choi@example.com"),
    ("2024-0031", "김가희", "공공사업1Div", L, "AS030", "gahee.kim@example.com"),
    ("2024-0102", "오창준", "공공사업1Div", M, "AS030", "changjun.oh@example.com"),
    ("2025-0001", "이신입", "공공사업1Div", M, "AS020", "sinip.lee@example.com"),
    ("2023-0112", "임도현", "공공사업1Div", M, "AS030", None), ("2024-0115", "서지민", "공공사업1Div", M, "AS030", None),
    ("2025-0118", "홍예린", "공공사업1Div", M, "AS030", None), ("2022-0121", "강태윤", "공공사업1Div", M, "AS030", None),
    ("2024-0201", "정다은", "공공사업2Div", L, "AS030", "daeun.jung@example.com"),
    ("2024-0202", "한지우", "공공사업2Div", M, "AS030", "jiwoo.han@example.com"),
    ("2023-0211", "윤서준", "공공사업2Div", M, "AS030", None), ("2025-0212", "백하늘", "공공사업2Div", M, "AS030", None),
    ("2022-0213", "노은채", "공공사업2Div", M, "AS030", None), ("2024-0214", "류민호", "공공사업2Div", M, "AS030", None),
    ("2025-0215", "장수빈", "공공사업2Div", M, "AS030", None),
    ("2024-0301", "송재현", "공공사업3Div", L, "AS030", "jaehyun.song@example.com"),
    ("2023-0311", "권나연", "공공사업3Div", M, "AS030", None), ("2024-0312", "조현우", "공공사업3Div", M, "AS030", None),
    ("2025-0313", "남지호", "공공사업3Div", M, "AS030", None), ("2022-0314", "문채원", "공공사업3Div", M, "AS030", None),
    ("2024-0315", "양승민", "공공사업3Div", M, "AS030", None), ("2025-0316", "황보라", "공공사업3Div", M, "AS030", None),
    ("2024-0401", "문하린", "전략사업1Div", L, "AS030", "harin.moon@example.com"),
    ("2023-0411", "차준영", "전략사업1Div", M, "AS030", None), ("2024-0412", "엄지수", "전략사업1Div", M, "AS030", None),
    ("2025-0413", "고태현", "전략사업1Div", M, "AS030", None), ("2022-0414", "손다인", "전략사업1Div", M, "AS030", None),
    ("2024-0415", "표민석", "전략사업1Div", M, "AS030", None), ("2025-0416", "하윤슬", "전략사업1Div", M, "AS030", None),
    ("2024-0402", "배준호", "전략사업2Div", L, "AS030", "junho.bae@example.com"),
    ("2023-0421", "진서윤", "전략사업2Div", M, "AS030", None), ("2024-0422", "구본재", "전략사업2Div", M, "AS030", None),
    ("2025-0423", "석하람", "전략사업2Div", M, "AS030", None), ("2022-0424", "설민규", "전략사업2Div", M, "AS030", None),
    ("2024-0425", "위소연", "전략사업2Div", M, "AS030", None), ("2025-0426", "탁건우", "전략사업2Div", M, "AS030", None),
    ("2024-0403", "신유나", "전략사업3Div", L, "AS030", "yuna.shin@example.com"),
    ("2023-0431", "맹지훈", "전략사업3Div", M, "AS030", None), ("2024-0432", "변수아", "전략사업3Div", M, "AS030", None),
    ("2025-0433", "육현수", "전략사업3Div", M, "AS030", None), ("2022-0434", "채다솜", "전략사업3Div", M, "AS030", None),
    ("2024-0435", "편우진", "전략사업3Div", M, "AS040", None), ("2025-0436", "국하영", "전략사업3Div", M, "AS030", None),
]
NAME = {p[0]: p[1] for p in people}
LEADER = {p[2]: p[0] for p in people if p[3] == L}
MEMBERS = {t: [p[0] for p in people if p[2] == t and p[3] == M and p[4] == "AS030"] for t in TEAMS_DEV}

# ================================================================ 사업 정의
SECTOR = {"공공사업1Div": "민원", "공공사업2Div": "보건복지", "공공사업3Div": "교육", "전략사업1Div": "교통물류", "전략사업2Div": "치안안전", "전략사업3Div": "에너지공간"}
past = [
    ("공공사업1Div", "2024-03-14", "지방자치단체 통합 민원서비스 고도화", "행정안전부", 32, "수주", 1, 93.42, None, "A"),
    ("공공사업1Div", "2024-08-22", "전자정부 표준프레임워크 기반 업무포털 재구축", "한국지역정보개발원", 21, "수주", 1, 92.10, None, "B"),
    ("공공사업1Div", "2025-04-10", "국민신문고 상담지원시스템 개선", "국민권익위원회", 18, "탈락", 2, 89.75, None, "A"),
    ("공공사업1Div", "2025-09-18", "공공데이터 개방 플랫폼 기능 개선", "한국지능정보사회진흥원", 27, "수주", 1, 94.03, None, "B"),
    ("공공사업2Div", "2024-05-09", "보건의료 빅데이터 개방시스템 확대 구축", "국민건강보험공단", 45, "탈락", 3, 86.90, None, "B"),
    ("공공사업2Div", "2024-10-17", "사회보장 정보시스템 기능 개선", "한국사회보장정보원", 38, "수주", 1, 92.88, None, "A"),
    ("공공사업2Div", "2025-03-27", "질병보건 통합관리시스템 고도화", "질병관리청", 52, "수주", 1, 93.15, "공공사업1Div", "B"),
    ("공공사업2Div", "2025-11-06", "장기요양 업무시스템 재구축", "국민건강보험공단", 41, "탈락", 2, 90.12, None, "A"),
    ("공공사업3Div", "2024-04-18", "대학 학사정보 통합시스템 구축", "한국교육학술정보원", 24, "수주", 1, 92.55, None, "A"),
    ("공공사업3Div", "2024-11-14", "문화예술 지원사업 통합관리 플랫폼 구축", "한국문화예술위원회", 12, "유찰", None, None, None, "B"),
    ("공공사업3Div", "2025-05-22", "국가평생교육 통합포털 고도화", "국가평생교육진흥원", 16, "탈락", 2, 88.40, None, "A"),
    ("공공사업3Div", "2025-10-16", "도서관 정보나루 서비스 개선", "국립중앙도서관", 9.5, "수주", 1, 93.70, None, "B"),
    ("전략사업1Div", "2024-02-29", "공항 수하물 처리 정보시스템 고도화", "한국공항공사", 36, "탈락", 2, 89.20, None, "B"),
    ("전략사업1Div", "2024-09-12", "고속도로 교통정보 통합 플랫폼 구축", "한국도로공사", 58, "수주", 1, 93.05, "전략사업3Div", "A"),
    ("전략사업1Div", "2025-02-20", "철도 차량 유지보수 정보시스템 개선", "한국철도공사", 29, "수주", 1, 92.64, None, "B"),
    ("전략사업1Div", "2025-08-28", "항만 물류 통합정보시스템 재구축", "해양수산부", 47, "탈락", 3, 87.15, None, "A"),
    ("전략사업2Div", "2024-06-13", "112 신고 통합시스템 기능 개선", "경찰청", 34, "수주", 1, 93.30, None, "A"),
    ("전략사업2Div", "2024-12-05", "재난안전 상황관리 시스템 고도화", "행정안전부", 26, "탈락", 2, 90.45, None, "B"),
    ("전략사업2Div", "2025-06-12", "119 출동 지령 시스템 재구축", "소방청", 43, "탈락", 4, 85.60, None, "A"),
    ("전략사업2Div", "2025-12-04", "교통단속 정보 연계 플랫폼 구축", "경찰청", 19, "수주", 1, 92.90, None, "B"),
    ("전략사업3Div", "2024-07-25", "전력 설비 자산관리시스템 고도화", "한국전력공사", 55, "탈락", 3, 86.25, None, "B"),
    ("전략사업3Div", "2024-11-28", "국가 공간정보 통합 플랫폼 기능 개선", "국토교통부", 31, "탈락", 2, 89.95, None, "A"),
    ("전략사업3Div", "2025-03-13", "수자원 통합 관측 정보시스템 구축", "한국수자원공사", 39, "수주", 1, 92.20, None, "B"),
    ("전략사업3Div", "2025-07-24", "신재생에너지 통합 모니터링 시스템 개선", "한국에너지공단", 22, "탈락", 2, 90.05, None, "A"),
]
seqs = {2024: 0, 2025: 0}
BIDS = []   # 모든 공고 dict
for (team, ddln, nm, inst, bgt, rs, rnk, scr, ptcp, rfp) in past:
    yr = int(ddln[:4]); seqs[yr] += 1
    BIDS.append(dict(kind="past", no="R%02dBK8%07d" % (yr % 100, seqs[yr] * 101), team=team, ddln=ddln + " 10:00",
                     nm=nm, inst=inst, bgt=int(bgt * 100000000), rs=rs, rnk=rnk, scr=scr, ptcp=ptcp, rfp=rfp))

NOW = [
    dict(kind="N1", no="R26BK8000101", team="공공사업2Div", nm="국민연금 차세대 연금정보시스템 1단계 구축", inst="국민연금공단", bgt=8600000000, ddln=16, rfp="A", stts="PS030"),
    dict(kind="N2", no="R26BK8000202", team="공공사업3Div", nm="디지털교과서 통합 플랫폼 고도화", inst="한국교육학술정보원", bgt=3300000000, ddln=3, rfp="B", stts="PS040", ptcp="공공사업2Div"),
    dict(kind="N3", no="R26BK8000303", team="전략사업1Div", nm="스마트 공항 운항정보 통합시스템 구축", inst="한국공항공사", bgt=4800000000, ddln=-1, rfp="A", stts="PS050"),
]
WAIT = [
    dict(kind="W", no="R26BK8000404", nm="지방세 통합정보시스템 기능 개선", inst="행정안전부", bgt=2500000000, reg=-3, ddln=21),
    dict(kind="W", no="R26BK8000505", nm="해양 안전정보 통합관제 플랫폼 구축", inst="해양경찰청", bgt=3700000000, reg=-2, ddln=28),
    dict(kind="W", no="R26BK8000606", nm="전기차 충전 인프라 통합관리시스템 구축", inst="한국환경공단", bgt=1800000000, reg=-1, ddln=24),
    dict(kind="W", no="R26BK8000707", nm="국가 R" + "D 과제관리 시스템 개선", inst="한국연구재단", bgt=2900000000, reg=0, ddln=35),
]
WAIT[3]["nm"] = "국가 연구개발 과제관리 시스템 개선"   # & 를 쓰지 않는다
EXC = [
    dict(kind="E1", plan="R26DD80000801", nm="2027년 지능형 행정 업무지원 시스템 구축", inst="조달청", bgt=4000000000, reg=-10),
    dict(kind="E2", spec="R26BD80000902", nm="스마트시티 통합 플랫폼 확산 사업", inst="국토교통부", bgt=5200000000, reg=-8, opnn=4, trck="TR010"),
    dict(kind="E3", spec="R26BD80001003", nm="공공기관 클라우드 전환 컨설팅", inst="행정안전부", bgt=900000000, reg=-60, opnn=-50, trck="TR030"),
    dict(kind="E4", no="R26BK8001104", nm="전자기록물 보존 관리시스템 구축", inst="국가기록원", bgt=1400000000, reg=-12, ddln=10, cancel=-4),
    dict(kind="E5", no="R26BK8001205", nm="시설물 안전관리 시스템 유지관리", inst="국토안전관리원", bgt=1100000000, reg=-11, ddln=17, team="전략사업3Div"),
    dict(kind="E6", no="R26BK8001306", nm="공공 와이파이 통합관리 시스템 구축", inst="과학기술정보통신부", bgt=2000000000, reg=-75, ddln=-40, team="전략사업1Div"),
    dict(kind="E7", no="R26BK8001407", nm="공공 와이파이 통합관리 시스템 구축 (재공고)", inst="과학기술정보통신부", bgt=2000000000, reg=-5, ddln=18),
]
for b in NOW + WAIT + EXC: BIDS.append(b)

def P(b):
    if b.get("no"): return "(SELECT PBANC_SN FROM TB_BID_PBANC WHERE BID_PBANC_NO = %s)" % q(b["no"])
    if b.get("spec"): return "(SELECT PBANC_SN FROM TB_BID_PBANC WHERE BF_SPEC_RGST_NO = %s)" % q(b["spec"])
    return "(SELECT PBANC_SN FROM TB_BID_PBANC WHERE ORDER_PLAN_NO = %s)" % q(b["plan"])

# ================================================================ 목차·요구사항·서류·평가 항목 정의
TOC = [("1", "제안 개요", [("1.1", "제안 배경 및 목적", 4), ("1.2", "제안의 범위", 3), ("1.3", "제안의 특장점", 3)]),
       ("2", "사업 이해", [("2.1", "사업 추진 배경", 4), ("2.2", "현황 분석 및 문제점", 6), ("2.3", "목표 시스템 개념도", 5)]),
       ("3", "사업 수행 방안", [("3.1", "추진 전략", 6), ("3.2", "시스템 구성 방안", 10), ("3.3", "기능 구현 방안", 22), ("3.4", "데이터 이관 방안", 8)]),
       ("4", "사업 관리", [("4.1", "추진 체계 및 조직", 5), ("4.2", "일정 관리", 4), ("4.3", "품질·보안 관리", 8), ("4.4", "위험 관리", 4)]),
       ("5", "지원 방안", [("5.1", "교육 계획", 4), ("5.2", "하자보수 및 유지관리 계획", 4)])]
SUMMARY_TOC = [("1", "제안 개요 및 사업 이해"), ("2", "사업 수행 방안"), ("3", "사업 관리"), ("4", "지원 방안")]
LEAVES = [(no, nm, pg) for _, _, ch in TOC for (no, nm, pg) in ch]
RFP = {"A": [("SFR", ["통합 로그인 및 권한 관리", "민원 신청 접수", "민원 처리 현황 조회", "처리 단계별 알림 발송", "첨부 서류 관리", "통계 및 보고서 출력", "관리자 코드 관리", "모바일 화면 제공"]),
             ("PER", ["화면 응답 시간 3초 이내", "동시 사용자 500명 처리", "대량 조회 성능 확보"]),
             ("SER", ["개인정보 암호화", "접근 기록 보관", "시큐어 코딩 적용"]), ("DAR", ["기존 데이터 이관", "데이터 표준 준수"])],
       "B": [("SFR", ["사용자 계정 관리", "업무 신청서 작성", "결재 및 승인 처리", "진행 현황 대시보드", "검색 및 필터 제공", "전자 문서 보관", "외부 기관 연계 조회", "공지사항 관리", "엑셀 내려받기", "이력 조회"]),
             ("INR", ["행정정보 공동이용 연계", "통합 인증 연계", "문자 발송 연계"]), ("SER", ["권한별 메뉴 접근 통제", "비밀번호 정책 적용"]),
             ("QUR", ["웹 접근성 준수", "호환성 확보"]), ("TER", ["통합 테스트 수행"])]}
RQ_TOC = {"SFR": ["3.3", "3.3", "3.2", "3.3", "3.3", "3.3", "3.2", "3.3", "3.3", "3.3"], "PER": ["3.2", "3.2", "3.2"], "SER": ["4.3", "4.3", "4.3"],
          "DAR": ["3.4", "3.4"], "INR": ["3.2", "3.2", "3.2"], "QUR": ["4.3", "4.3"], "TER": ["4.3"]}
def rqts(key):
    out = []
    for pre, names in RFP[key]:
        for k, nm in enumerate(names, 1):
            out.append(("%s-%03d" % (pre, k), nm, RQ_TOC[pre][k - 1]))
    return out
DOCS = [("사업자등록증 사본", "Y", "TK010", "docs/사업자등록증.pdf"), ("법인 등기사항증명서", "Y", "TK010", "docs/법인등기부등본.pdf"),
        ("재무제표", "N", "TK010", "docs/재무제표.pdf"), ("입찰참가신청서", "Y", "TK010", "docs/입찰참가신청서.pdf"),
        ("청렴서약서", "Y", "TK010", "docs/청렴서약서.docx"), ("보안확약서", "Y", "TK010", "docs/보안확약서.docx"),
        ("투입인력 현황표", "Y", "TK020", "docs/투입인력현황.xlsx")]
EVL = [("사업 이해도", 10), ("추진 전략", 10), ("기술 및 기능 구현", 20), ("데이터 이관", 10), ("사업 관리", 15), ("품질·보안 관리", 15), ("교육·유지관리", 10)]
EVL_PAGE = ["9-15", "16-21", "22-44", "45-52", "53-61", "62-70", "71-78"]
COMPETITORS = [("2148612345", "한빛정보기술"), ("1208745566", "누리시스템즈"), ("2158890123", "다온아이앤씨"), ("1138654321", "세움데이타"), ("6058177889", "미르솔루션")]
PART_SRC = ["proposal/제안서_사업이해.pptx", "proposal/제안서_수행방안.pptx", "proposal/제안서_사업관리.pdf"]
SUM_SRC = ["proposal/제안요약서_1장.pdf", "proposal/제안요약서_2장.pdf"]

QNA_POOL = {
    "SFR": [("기존 시스템 사용자 권한을 그대로 옮길 수 있습니까?", "권한 매핑표를 만들어 시험 이관 때 사용자별로 대조하고, 차이는 발주기관 확인 후 반영합니다."),
            ("모바일 화면은 앱입니까, 웹입니까?", "반응형 웹으로 구현하여 별도 설치 없이 모바일 브라우저에서 같은 기능을 씁니다."),
            ("처리 단계 알림은 어떤 수단으로 보냅니까?", "기관 문자 발송 시스템과 연계하고, 시스템 안 알림함에도 같은 내용을 남깁니다.")],
    "PER": [("동시 사용자 500명 기준은 어떻게 검증합니까?", "성능 시험 도구로 단계별 부하를 주고 응답 시간 3초 이내를 확인한 결과서를 제출합니다.")],
    "SER": [("개인정보 암호화 범위는 어디까지입니까?", "주민등록번호·계좌번호 등 고유식별정보는 저장 시 암호화하고 전 구간 TLS를 적용합니다.")],
    "DAR": [("데이터 이관 중 서비스 중단은 얼마나 됩니까?", "주말 야간 전환으로 중단 시간을 4시간 이내로 계획하고 사전 시험 이관을 3회 진행합니다.")],
    "INR": [("행정정보 공동이용 연계 승인은 누가 받습니까?", "승인 신청은 발주기관이 하고, 우리는 연계 규격서와 시험 결과를 준비해 지원합니다.")],
    "QUR": [("웹 접근성 인증까지 범위에 포함됩니까?", "접근성 지침 준수 점검과 개선을 포함하며, 인증 심사 대응도 지원합니다.")],
    "TER": [("통합 테스트 시나리오는 누가 작성합니까?", "우리가 초안을 작성하고 업무 담당자 검토를 거쳐 확정합니다.")],
}
PRES_QNA = [("유사 사업 경험에서 가장 어려웠던 점은 무엇이었습니까?", "기관 간 연계 일정이 늦어진 점이었고, 연계 시험 환경을 먼저 마련해 일정을 지켰습니다."),
            ("투입 인력이 바뀌면 어떻게 대응합니까?", "동급 이상 인력으로 교체하고 발주기관 승인 후 2주간 인수인계 기간을 둡니다.")]

STRENGTH = {
    "수주": ["유사 %s 분야 수행 경험과 검증된 공통 컴포넌트가 높은 평가를 받음", "단계적 전환 계획과 시험 이관 3회로 데이터 이관 위험을 낮춘 점이 강점",
             "발표에서 PM의 업무 이해도와 질의 대응이 좋았다는 평가"],
    "탈락": ["시스템 구성 방안과 보안 관리 항목은 상위권 점수", "%s 분야 이해도는 경쟁사와 비슷한 수준으로 평가됨"],
}
WEAK = {
    "수주": ["교육 계획의 세부 일정이 부족하다는 지적", "위험 관리 방안을 더 구체적으로 써야 함"],
    "탈락": ["가격 점수에서 1위 업체와 0.6점 이상 차이", "기능 구현 방안이 요구사항 대비 일부 추상적이라는 평가", "투입 인력 중 고급 기술자 비율이 낮게 평가됨"],
}
SPLMNT = {"수주": "교육 일정표와 위험 관리 대응표를 표준 양식으로 만들어 다음 제안에 쓴다", "탈락": "가격 산정 기준을 재검토하고, 기능 구현 방안은 요구사항 ID별 대비표로 보강한다"}

manifest = []   # (상대 저장 경로, 원본 경로)
def file_row(o, b, se, src, strg_key, regdt, rgtr, toc=None, dcmnt=None, cycl=None, ver="1", last="N", dcsmkr=None, cfmtn=None, orgnl=None):
    ext = src.rsplit(".", 1)[1].lower()
    strg = "%s-%s.%s" % (b.get("no") or b.get("spec") or b.get("plan"), strg_key, ext)
    sub = {"FL010": "seed/bid", "FL020": "seed/proposal", "FL030": "seed/proposal", "FL040": "seed/proposal", "FL050": "seed/docs", "FL070": "seed/evaluation"}[se]
    size = os.path.getsize(os.path.join(FILES, src))
    manifest.append((sub + "/" + strg, src))
    cols = ["PBANC_SN", "FILE_SE_CD", "TOC_SN", "DCMNT_SN", "INTG_CYCL", "VER_NO", "LAST_YN", "DCSMKR_ID", "CFMTN_DT",
            "ORGNL_FILE_NM", "STRG_FILE_NM", "FILE_PATH_NM", "FILE_SZ", "FILE_EXTN_NM", "RGTR_ID", "REG_DT"]
    o.ins("TB_ATCH_FILE", cols, [P(b), q(se), toc or "NULL", dcmnt or "NULL", q(cycl) if cycl else "NULL", q(ver), q(last), q(dcsmkr), cfmtn or "NULL",
                                  q(orgnl or os.path.basename(src)), q(strg), q(sub), str(size), q(ext), q(rgtr), regdt])
    return strg

def toc_ref(b, se, no):
    return "(SELECT TOC_SN FROM TB_TOC WHERE PBANC_SN = %s AND TOC_SE_CD = %s AND TOC_NO = %s)" % (P(b), q(se), q(no))

# ================================================================ 03: 정리 + 팀·계정 + 목차 양식
o3 = Out("03_seed_org.sql", "팀 · 계정 · 목차 양식", ["기존 데이터를 모두 지우고(공통코드 제외) 팀 8개, 계정 %d명, 팀별 목차 양식을 넣는다." % len(people),
                                                      "테스트 계정 비밀번호는 모두 Demo!2026a. 이신입(2025-0001)은 초기 비밀번호, 편우진(2024-0435)은 잠금 상태."])
o3.c("0) 정리: 자식 → 부모 순서 (공통코드 TB_COM_CD 는 지우지 않는다)")
for t in ["TB_EVL_CMSNR_SCR", "TB_EVL_TBL_UPLD", "TB_CO_EVL_RSLT", "TB_BID_RSLT", "TB_QSTN_ANS", "TB_ATCH_FILE", "TB_RQT_MTTR", "TB_EVL_ARTCL",
          "TB_SBMSN_DCMNT", "TB_TOC", "TB_PRP_TMMBR", "TB_PTCP_TEAM", "TB_SCHDL", "TB_NOTI", "TB_CLCT_LOG", "TB_PBANC_CLCT_HSTRY",
          "TB_PBANC_PRCS_HSTRY", "TB_REF_FILE"]:
    o3.s("DELETE FROM %s" % t)
o3.s("UPDATE TB_BID_PBANC SET ORGNL_PBANC_SN = NULL")
o3.s("DELETE FROM TB_BID_PBANC")
o3.s("DELETE FROM TB_TOC_FORM")
o3.s("UPDATE TB_USER SET RGTR_ID = NULL")
o3.s("DELETE FROM TB_USER")
o3.s("DELETE FROM TB_TEAM")
o3.commit()
o3.c("1) 팀")
for nm, dept in [("경영지원실", "DP010"), ("영업부", "DP020")] + [(t, "DP030") for t in TEAMS_DEV]:
    o3.ins("TB_TEAM", ["TEAM_NM", "DEPT_CD"], [q(nm), q(dept)])
o3.c("2) 계정 (권한 AU010 영업부 / AU020 경영지원실 / AU030 팀장 / AU040 팀원, 직급 JG020 팀장 / JG010 사원)")
for uid, nm, team, auth, st, eml in people:
    eml = eml or "user%s@example.com" % uid.replace("-", "")
    o3.ins("TB_USER", ["USER_ID", "USER_NM", "TEAM_SN", "JBGD_CD", "AUTHRT_CD", "ENPSWD", "ACNT_STTS_CD", "EML_ADDR", "LGN_FAIL_NMTM", "REG_DT"],
           [q(uid), q(nm), T(team), q("JG020" if auth == L else "JG010"), q(auth), q(PW), q(st), q(eml), "5" if st == "AS040" else "0",
            dt_fixed("2026-01-02 09:00")])
o3.c("3) 팀별 목차 양식 (제안서 TC010 · 제안요약서 TC020). 시연 때 '목차 양식 불러오기'로 쓴다")
for t in TEAMS_DEV:
    for se, fnm in [("TC010", "%s 표준 제안서 목차" % t), ("TC020", "%s 제안요약서 목차" % t)]:
        o3.ins("TB_TOC_FORM", ["TEAM_SN", "TOC_FORM_NM", "TOC_SE_CD", "RGTR_ID", "REG_DT"], [T(t), q(fnm), q(se), q(LEADER[t]), dt_fixed("2025-01-10 10:00")])
        F = "(SELECT TOC_FORM_SN FROM TB_TOC_FORM WHERE TOC_FORM_NM = %s)" % q(fnm)
        seq = 0
        if se == "TC010":
            for no, nm, ch in TOC:
                seq += 1
                o3.ins("TB_TOC", ["TOC_FORM_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ"], [F, "NULL", q(se), q(no), q(nm), str(seq)])
                for cno, cnm, pg in ch:
                    seq += 1
                    o3.ins("TB_TOC", ["TOC_FORM_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ", "EXPC_PAGE_CNT"],
                           [F, "(SELECT TOC_SN FROM TB_TOC WHERE TOC_FORM_SN = %s AND TOC_NO = %s)" % (F, q(no)), q(se), q(cno), q(cnm), str(seq), str(pg)])
        else:
            for no, nm in SUMMARY_TOC:
                seq += 1
                o3.ins("TB_TOC", ["TOC_FORM_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ", "EXPC_PAGE_CNT"], [F, "NULL", q(se), q(no), q(nm), str(seq), "1"])
o3.commit()
o3.save()

# ================================================================ 04: 공고 · 처리 이력 · 수집 이력
o4 = Out("04_seed_bids.sql", "공고 · 처리 이력 · 수집 이력", ["과거 사업 24건(2024·2025), 2026 진행 3건, 배정 대기 4건, 예외 7건. 시연 공고(W0)는 맨 끝 자리에 사용자가 넣는다."])
def bid_insert(b, cols_vals):
    cols = [c for c, _ in cols_vals]; vals = [v for _, v in cols_vals]
    o4.ins("TB_BID_PBANC", cols, vals)
def hist(o, b, cd, rsn, prcr, when, team=None):
    o.ins("TB_PBANC_PRCS_HSTRY", ["PBANC_SN", "TRGT_TEAM_SN", "PRCS_SE_CD", "PRCS_RSN", "PRCR_ID", "PRCS_DT"], [P(b), T(team) if team else "NULL", q(cd), q(rsn), q(prcr), when])
def clct(o, b, seq, step, no, typ, bgt, ddln, opng, when):
    o.ins("TB_PBANC_CLCT_HSTRY", ["PBANC_SN", "CLCT_SEQ", "PBANC_STEP_CD", "PBANC_NO", "BID_PBANC_CYCL", "PBANC_TYPE_CD", "ALTMNT_BGT_AMT", "BID_DDLN_DT", "OPENG_DT", "CLCT_DT"],
          [P(b), str(seq), q(step), q(no), "0", q(typ), str(bgt), ddln or "NULL", opng or "NULL", when])

o4.c("1) 과거 사업 24건: 등록 → 배정 → 참여 결정 → 제안 작성 시작 → 나라장터 등록 → (개찰) → 결과 등록")
for b in [x for x in BIDS if x["kind"] == "past"]:
    d = b["ddln"]; leader = LEADER[b["team"]]
    stts = "PS070" if b["rs"] == "유찰" else "PS060"
    step = "PP050" if b["rs"] == "수주" else "PP040"
    months = random.choice([8, 10, 12])
    b["dates"] = dict(reg=add_days(d, -35, (9, 0)), asg=add_days(d, -33, (14, 0)), dec=add_days(d, -32, (10, 30)), start=add_days(d, -31, (9, 30)),
                      sub=add_days(d, -1, (16, 0)), opng=add_days(d, 0, (11, 0)), prsn=add_days(d, 3, (14, 0)), rslt=add_days(d, 7, (15, 0)))
    D = b["dates"]
    bid_insert(b, [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "0"), ("PBANC_STEP_CD", q(step)), ("PBANC_TYPE_CD", q("PY010")),
                   ("PBANC_NM", q(b["nm"])), ("ORDER_INST_NM", q(b["inst"])), ("DMND_INST_NM", q(b["inst"])), ("BIZ_TYPE_CD", q("BT010")),
                   ("PTCP_SHP_CD", q("PF020" if b["ptcp"] else "PF010")), ("SW_BIZ_YN", q("Y")), ("ALTMNT_BGT_AMT", str(b["bgt"])),
                   ("BIZ_PRD_CN", q("계약일로부터 %d개월" % months)), ("BID_DDLN_DT", dt_fixed(d)), ("OPENG_DT", dt_fixed(D["opng"])),
                   ("PRSNTN_DT", dt_fixed(D["prsn"])), ("EVL_MTHD_NM", q("협상에 의한 계약")), ("TECH_SCOR_SCR", "90"), ("PRC_SCOR_SCR", "10"),
                   ("LMT_PAGE_CNT", "120"), ("PBANC_STTS_CD", q(stts)), ("SPRVSN_TEAM_SN", T(b["team"])),
                   ("TOC_FORM_SN", "(SELECT TOC_FORM_SN FROM TB_TOC_FORM WHERE TOC_FORM_NM = %s)" % q("%s 표준 제안서 목차" % b["team"])),
                   ("RGTR_ID", q(SALES)), ("SBMSN_DT", dt_fixed(D["sub"])), ("PRSNR_ID", q(MGMT)), ("CLCT_DT", dt_fixed(D["rslt"])),
                   ("LAST_CFMTN_DT", dt_fixed(D["rslt"])), ("REG_DT", dt_fixed(D["reg"]))])
    hist(o4, b, "PC010", "입찰공고번호 " + b["no"], SALES, dt_fixed(D["reg"]))
    hist(o4, b, "PC030", "참여 형태 " + ("컨소시엄 참여" if b["ptcp"] else "주관"), SALES, dt_fixed(D["asg"]), b["team"])
    hist(o4, b, "PC050", "참여", leader, dt_fixed(D["dec"]), b["team"])
    hist(o4, b, "PC080", None, leader, dt_fixed(D["start"]))
    hist(o4, b, "PC090", None, MGMT, dt_fixed(D["sub"]))
    if b["rs"] != "유찰":
        hist(o4, b, "PC100", b["rs"], MGMT, dt_fixed(D["rslt"]))
    clct(o4, b, 1, "PP030", b["no"], "PY010", b["bgt"], dt_fixed(d), dt_fixed(D["opng"]), dt_fixed(D["reg"]))
    clct(o4, b, 2, "PP040", b["no"], "PY010", b["bgt"], dt_fixed(d), dt_fixed(D["opng"]), dt_fixed(add_days(D["opng"], 0, (17, 0))))
    if b["rs"] == "수주":
        clct(o4, b, 3, "PP050", b["no"], "PY010", b["bgt"], dt_fixed(d), dt_fixed(D["opng"]), dt_fixed(add_days(d, 20, (17, 0))))
o4.commit()

o4.c("2) 2026 진행 중 3건 (실행일 기준)")
for b in [x for x in BIDS if x["kind"] in ("N1", "N2", "N3")]:
    leader = LEADER[b["team"]]
    if b["kind"] == "N1": D = dict(reg=-8, asg=-6, dec=-5)
    elif b["kind"] == "N2": D = dict(reg=-22, asg=-20, dec=-19, start=-18)
    else: D = dict(reg=-33, asg=-30, dec=-29, start=-28, sub=-1)
    b["rel"] = D
    cols = [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "0"), ("PBANC_STEP_CD", q("PP030")), ("PBANC_TYPE_CD", q("PY010")),
            ("PBANC_NM", q(b["nm"])), ("ORDER_INST_NM", q(b["inst"])), ("DMND_INST_NM", q(b["inst"])), ("BIZ_TYPE_CD", q("BT010")),
            ("PTCP_SHP_CD", q("PF010")), ("SW_BIZ_YN", q("Y")), ("ALTMNT_BGT_AMT", str(b["bgt"])), ("BIZ_PRD_CN", q("계약일로부터 12개월")),
            ("BID_DDLN_DT", dt_rel(b["ddln"], 10)), ("OPENG_DT", dt_rel(b["ddln"] + (2 if b["kind"] == "N3" else 0), 11)),
            ("PRSNTN_DT", dt_rel(b["ddln"] + 4, 14)), ("EVL_MTHD_NM", q("협상에 의한 계약")), ("TECH_SCOR_SCR", "90"), ("PRC_SCOR_SCR", "10"),
            ("LMT_PAGE_CNT", "120"), ("PBANC_STTS_CD", q(b["stts"])), ("SPRVSN_TEAM_SN", T(b["team"])), ("RGTR_ID", q(SALES)),
            ("CLCT_DT", dt_rel(D["reg"], 17)), ("LAST_CFMTN_DT", dt_rel(0, 8, 12)), ("REG_DT", dt_rel(D["reg"], 9))]
    if b["kind"] != "N1":
        cols.append(("TOC_FORM_SN", "(SELECT TOC_FORM_SN FROM TB_TOC_FORM WHERE TOC_FORM_NM = %s)" % q("%s 표준 제안서 목차" % b["team"])))
    if b["kind"] == "N3":
        cols += [("SBMSN_DT", dt_rel(-1, 16)), ("PRSNR_ID", q(MGMT))]
    bid_insert(b, cols)
    hist(o4, b, "PC010", "입찰공고번호 " + b["no"], SALES, dt_rel(D["reg"], 9))
    hist(o4, b, "PC030", "참여 형태 주관", SALES, dt_rel(D["asg"], 14), b["team"])
    hist(o4, b, "PC050", "참여", leader, dt_rel(D["dec"], 10, 30), b["team"])
    if "start" in D: hist(o4, b, "PC080", None, leader, dt_rel(D["start"], 9, 30))
    if "sub" in D: hist(o4, b, "PC090", None, MGMT, dt_rel(D["sub"], 16))
    clct(o4, b, 1, "PP030", b["no"], "PY010", b["bgt"], dt_rel(b["ddln"], 10), None, dt_rel(D["reg"], 9))

o4.c("3) 배정 대기 신규 공고 4건 (영업부 공고 목록)")
for b in [x for x in BIDS if x["kind"] == "W"]:
    bid_insert(b, [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "0"), ("PBANC_STEP_CD", q("PP030")), ("PBANC_TYPE_CD", q("PY010")),
                   ("PBANC_NM", q(b["nm"])), ("ORDER_INST_NM", q(b["inst"])), ("DMND_INST_NM", q(b["inst"])), ("BIZ_TYPE_CD", q("BT010")),
                   ("SW_BIZ_YN", q("Y")), ("ALTMNT_BGT_AMT", str(b["bgt"])), ("BIZ_PRD_CN", q("계약일로부터 10개월")),
                   ("BID_DDLN_DT", dt_rel(b["ddln"], 10)), ("OPENG_DT", dt_rel(b["ddln"], 11)), ("EVL_MTHD_NM", q("협상에 의한 계약")),
                   ("TECH_SCOR_SCR", "90"), ("PRC_SCOR_SCR", "10"), ("LMT_PAGE_CNT", "100"), ("PBANC_STTS_CD", q("PS010")), ("RGTR_ID", q(SALES)),
                   ("CLCT_DT", dt_rel(b["reg"], 8, 10)), ("LAST_CFMTN_DT", dt_rel(0, 8, 12)), ("REG_DT", dt_rel(b["reg"], 8, 10))])
    hist(o4, b, "PC010", "입찰공고번호 " + b["no"], SALES, dt_rel(b["reg"], 8, 10))
    clct(o4, b, 1, "PP030", b["no"], "PY010", b["bgt"], dt_rel(b["ddln"], 10), None, dt_rel(b["reg"], 8, 10))

o4.c("4) 예외 공고 7건: 발주계획 · 사전규격(추적 중 / 추적 종료) · 취소 · 불참 반환 · 유찰 · 재공고")
for b in [x for x in BIDS if x["kind"].startswith("E")]:
    k = b["kind"]
    base = [("PBANC_NM", q(b["nm"])), ("ORDER_INST_NM", q(b["inst"])), ("DMND_INST_NM", q(b["inst"])), ("BIZ_TYPE_CD", q("BT010")), ("SW_BIZ_YN", q("Y")),
            ("ALTMNT_BGT_AMT", str(b["bgt"])), ("RGTR_ID", q(SALES)), ("REG_DT", dt_rel(b["reg"], 9)), ("CLCT_DT", dt_rel(b["reg"], 9)), ("LAST_CFMTN_DT", dt_rel(0, 8, 12))]
    if k == "E1":
        bid_insert(b, [("ORDER_PLAN_NO", q(b["plan"])), ("PBANC_STEP_CD", q("PP010")), ("PBANC_STTS_CD", q("PS010"))] + base)
        hist(o4, b, "PC010", "발주계획번호 " + b["plan"], SALES, dt_rel(b["reg"], 9))
    elif k in ("E2", "E3"):
        bid_insert(b, [("BF_SPEC_RGST_NO", q(b["spec"])), ("PBANC_STEP_CD", q("PP020")), ("TRCK_STTS_CD", q(b["trck"])),
                       ("OPNN_DDLN_DT", dt_rel(b["opnn"], 18)), ("PBANC_STTS_CD", q("PS010"))] + base)
        hist(o4, b, "PC010", "사전규격번호 " + b["spec"], SALES, dt_rel(b["reg"], 9))
        clct(o4, b, 1, "PP020", b["spec"], None, b["bgt"], None, None, dt_rel(b["reg"], 9))
    elif k == "E4":
        bid_insert(b, [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "1"), ("PBANC_STEP_CD", q("PP030")), ("PBANC_TYPE_CD", q("PY040")),
                       ("BID_DDLN_DT", dt_rel(b["ddln"], 10)), ("EVL_MTHD_NM", q("협상에 의한 계약")), ("PBANC_STTS_CD", q("PS010"))] + base)
        hist(o4, b, "PC010", "입찰공고번호 " + b["no"], SALES, dt_rel(b["reg"], 9))
        hist(o4, b, "PC020", "취소공고 (발주기관 사정으로 입찰 취소)", SALES, dt_rel(b["cancel"], 17, 5))
        clct(o4, b, 1, "PP030", b["no"], "PY010", b["bgt"], dt_rel(b["ddln"], 10), None, dt_rel(b["reg"], 9))
        o4.ins("TB_PBANC_CLCT_HSTRY", ["PBANC_SN", "CLCT_SEQ", "PBANC_STEP_CD", "PBANC_NO", "BID_PBANC_CYCL", "PBANC_TYPE_CD", "ALTMNT_BGT_AMT", "BID_DDLN_DT", "CLCT_DT"],
               [P(b), "2", q("PP030"), q(b["no"]), "1", q("PY040"), str(b["bgt"]), dt_rel(b["ddln"], 10), dt_rel(b["cancel"], 17, 5)])
    elif k == "E5":
        bid_insert(b, [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "0"), ("PBANC_STEP_CD", q("PP030")), ("PBANC_TYPE_CD", q("PY010")),
                       ("BIZ_PRD_CN", q("계약일로부터 12개월")), ("BID_DDLN_DT", dt_rel(b["ddln"], 10)), ("EVL_MTHD_NM", q("협상에 의한 계약")),
                       ("PBANC_STTS_CD", q("PS010")), ("RTRN_SE_CD", q("RT010"))] + base)
        hist(o4, b, "PC010", "입찰공고번호 " + b["no"], SALES, dt_rel(b["reg"], 9))
        hist(o4, b, "PC030", "참여 형태 주관", SALES, dt_rel(-9, 14), b["team"])
        hist(o4, b, "PC060", "유지보수 사업이라 팀 수행 방향과 맞지 않아 불참합니다.", LEADER[b["team"]], dt_rel(-7, 11), b["team"])
        clct(o4, b, 1, "PP030", b["no"], "PY010", b["bgt"], dt_rel(b["ddln"], 10), None, dt_rel(b["reg"], 9))
    elif k == "E6":
        bid_insert(b, [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "0"), ("PBANC_STEP_CD", q("PP040")), ("PBANC_TYPE_CD", q("PY010")),
                       ("PTCP_SHP_CD", q("PF010")), ("BIZ_PRD_CN", q("계약일로부터 8개월")), ("BID_DDLN_DT", dt_rel(b["ddln"], 10)),
                       ("OPENG_DT", dt_rel(b["ddln"], 11)), ("EVL_MTHD_NM", q("협상에 의한 계약")), ("TECH_SCOR_SCR", "90"), ("PRC_SCOR_SCR", "10"),
                       ("PBANC_STTS_CD", q("PS070")), ("SPRVSN_TEAM_SN", T(b["team"])), ("SBMSN_DT", dt_rel(b["ddln"] - 1, 15)), ("PRSNR_ID", q(MGMT))] + base)
        leader = LEADER[b["team"]]
        hist(o4, b, "PC010", "입찰공고번호 " + b["no"], SALES, dt_rel(b["reg"], 9))
        hist(o4, b, "PC030", "참여 형태 주관", SALES, dt_rel(b["reg"] + 2, 14), b["team"])
        hist(o4, b, "PC050", "참여", leader, dt_rel(b["reg"] + 3, 10), b["team"])
        hist(o4, b, "PC080", None, leader, dt_rel(b["reg"] + 4, 9))
        hist(o4, b, "PC090", None, MGMT, dt_rel(b["ddln"] - 1, 15))
        clct(o4, b, 1, "PP030", b["no"], "PY010", b["bgt"], dt_rel(b["ddln"], 10), dt_rel(b["ddln"], 11), dt_rel(b["reg"], 9))
        clct(o4, b, 2, "PP040", b["no"], "PY010", b["bgt"], dt_rel(b["ddln"], 10), dt_rel(b["ddln"], 11), dt_rel(b["ddln"], 17))
    elif k == "E7":
        bid_insert(b, [("BID_PBANC_NO", q(b["no"])), ("BID_PBANC_CYCL", "0"), ("PBANC_STEP_CD", q("PP030")), ("PBANC_TYPE_CD", q("PY030")),
                       ("ORGNL_PBANC_SN", P(EXC[5])), ("BIZ_PRD_CN", q("계약일로부터 8개월")), ("BID_DDLN_DT", dt_rel(b["ddln"], 10)),
                       ("OPENG_DT", dt_rel(b["ddln"], 11)), ("EVL_MTHD_NM", q("협상에 의한 계약")), ("PBANC_STTS_CD", q("PS010"))] + base)
        hist(o4, b, "PC010", "입찰공고번호 " + b["no"] + " (재공고)", SALES, dt_rel(b["reg"], 9))
        clct(o4, b, 1, "PP030", b["no"], "PY030", b["bgt"], dt_rel(b["ddln"], 10), None, dt_rel(b["reg"], 9))
o4.c("5) 수집 로그: 목록의 '마지막 불러온 시각' 표시용 (자동 · 수동 · 실패)")
o4.ins("TB_CLCT_LOG", ["PBANC_SN", "EXCN_SE_CD", "EXCTR_ID", "CLCT_RSLT_CD", "EXCN_DT"], ["NULL", q("EX010"), "NULL", q("CL010"), dt_rel(0, 8, 12)])
o4.ins("TB_CLCT_LOG", ["PBANC_SN", "EXCN_SE_CD", "EXCTR_ID", "CLCT_RSLT_CD", "EXCN_DT"], [P(EXC[3]), q("EX010"), "NULL", q("CL040"), dt_rel(-4, 17, 5)])
o4.ins("TB_CLCT_LOG", ["PBANC_SN", "EXCN_SE_CD", "EXCTR_ID", "CLCT_RSLT_CD", "EXCN_DT"], ["NULL", q("EX020"), q(SALES), q("CL010"), dt_rel(-1, 14, 20)])
o4.ins("TB_CLCT_LOG", ["PBANC_SN", "EXCN_SE_CD", "EXCTR_ID", "CLCT_RSLT_CD", "ERR_MSG_CN", "EXCN_DT"], ["NULL", q("EX010"), "NULL", q("CL090"), q("나라장터 응답 지연 (10초 초과)"), dt_rel(-2, 17)])
o4.c("6) 시연 공고(W0) 자리: 사용자가 찾은 실제 공고를 여기에 넣는다 (신규 PS010, 등록 D-1, 마감 D+25, 주관팀 비움)")
o4.commit()
o4.save()

# ================================================================ 05: 사업 진행 데이터
o5 = Out("05_seed_proposal.sql", "제안팀 · 목차 · 요구사항 · 일정 · 질의응답 · 제출서류 · 조견표 · 파일",
         ["과거 24건과 2026 진행 3건(N1 참여 확정, N2 제안 작성 중, N3 제출 완료), 유찰 E6 의 사업 진행 데이터.",
          "파일 행은 FILE_PATH_NM 'seed/...' 아래에 저장된 것으로 넣는다. 실제 파일은 db/load_demo_files.sh 로 복사한다."])

def team_for(b, n_members):
    mem = MEMBERS[b["team"]][:]
    random.shuffle(mem)
    return mem[:n_members]

def write_project(b, level, dates):
    """level: past | N1 | N2 | N3 | E6. dates: dict of SQL 날짜식 (team, toc, rq, files, docs, qna)"""
    o = o5
    leader = LEADER[b["team"]]
    mem = team_for(b, 4)
    b["members"] = mem
    o.c("[%s] %s (%s)" % (b.get("no"), b["nm"], b["team"]))
    o.ins("TB_PRP_TMMBR", ["PBANC_SN", "USER_ID", "PTCP_SE_CD", "FLFMT_INPUT_YN", "REG_DT"], [P(b), q(leader), q("PT010"), q("Y" if b.get("rs") == "수주" else "N"), dates["team"]])
    for i, u in enumerate(mem):
        o.ins("TB_PRP_TMMBR", ["PBANC_SN", "USER_ID", "PTCP_SE_CD", "FLFMT_INPUT_YN", "REG_DT"], [P(b), q(u), q("PT010"), q("Y" if b.get("rs") == "수주" and i < 3 else "N"), dates["team"]])
    ptcp_mem = []
    if b.get("ptcp"):
        o.ins("TB_PTCP_TEAM", ["PBANC_SN", "TEAM_SN", "REG_DT"], [P(b), T(b["ptcp"]), dates["team"]])
        ptcp_mem = MEMBERS[b["ptcp"]][:2]
        for u in ptcp_mem:
            o.ins("TB_PRP_TMMBR", ["PBANC_SN", "USER_ID", "PTCP_SE_CD", "REG_DT"], [P(b), q(u), q("PT020"), dates["team"]])
    if level == "N1":
        return
    # 제안서 목차 (TC010)
    writers = [leader] + mem + ptcp_mem
    seq = 0
    pic = {}
    for no, nm, ch in TOC:
        seq += 1
        o.ins("TB_TOC", ["PBANC_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ", "PRGRS_STTS_CD"], [P(b), "NULL", q("TC010"), q(no), q(nm), str(seq), q("TS030" if level != "N2" else "TS020")])
        for j, (cno, cnm, pg) in enumerate(ch):
            seq += 1
            who = writers[(seq + j) % len(writers)]
            pic[cno] = who
            st = "TS030"
            if level == "N2":
                st = ["TS030", "TS020", "TS010"][(seq * 7) % 3]
            o.ins("TB_TOC", ["PBANC_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ", "PIC_ID", "EXPC_PAGE_CNT", "PRGRS_STTS_CD"],
                  [P(b), toc_ref(b, "TC010", no), q("TC010"), q(cno), q(cnm), str(seq), q(who), str(pg), q(st)])
    # 제안요약서 목차 (TC020)
    for k, (no, nm) in enumerate(SUMMARY_TOC, 1):
        o.ins("TB_TOC", ["PBANC_SN", "UP_TOC_SN", "TOC_SE_CD", "TOC_NO", "TOC_NM", "SORT_SEQ", "PIC_ID", "EXPC_PAGE_CNT", "PRGRS_STTS_CD"],
              [P(b), "NULL", q("TC020"), q(no), q(nm), str(k), q(leader if k == 1 else writers[k % len(writers)]), "1", q("TS030" if level != "N2" else "TS020")])
    # 요구사항
    rq = rqts(b["rfp"])
    for i, (rno, rnm, tno) in enumerate(rq):
        unmapped = level == "N2" and i in (len(rq) - 1, len(rq) - 3)
        o.ins("TB_RQT_MTTR", ["PBANC_SN", "RQT_MTTR_NO", "RQT_MTTR_NM", "TOC_SN", "RGTR_ID", "REG_DT"],
              [P(b), q(rno), q(rnm), "NULL" if unmapped else toc_ref(b, "TC010", tno), q(leader), dates["rq"]])
    # 질의응답
    picked = []
    for rno, rnm, _ in rq:
        pool = QNA_POOL[rno[:3]]
        if len(picked) < 4 and (rno.endswith("01") or rno.endswith("04")):
            qq, aa = pool[(len(picked)) % len(pool)]
            picked.append((rno, qq, aa))
    nq = 0
    for rno, qq, aa in picked:
        nq += 1
        o.ins("TB_QSTN_ANS", ["PBANC_SN", "QSTN_ANS_SE_CD", "QSTN_CN", "ANS_CN", "RQT_MTTR_SN", "WRTR_ID", "WRT_DT"],
              [P(b), q("QA010"), q(qq), q(aa), "(SELECT RQT_MTTR_SN FROM TB_RQT_MTTR WHERE PBANC_SN = %s AND RQT_MTTR_NO = %s)" % (P(b), q(rno)),
               q(writers[nq % len(writers)]), dates["qna"]])
    if level in ("past", "N3", "E6"):
        for qq, aa in PRES_QNA:
            o.ins("TB_QSTN_ANS", ["PBANC_SN", "QSTN_ANS_SE_CD", "QSTN_CN", "ANS_CN", "WRTR_ID", "WRT_DT"], [P(b), q("QA020"), q(qq), q(aa), q(leader), dates["qna"]])
    # 제출서류 체크리스트 + 조견표(평가 항목·쪽번호)
    for i, (dn, yn, tk, src) in enumerate(DOCS):
        if level in ("past", "N3", "E6"): st = "DS030"
        else: st = ["DS030", "DS030", "DS020", "DS010", "DS020", "DS010", "DS010"][i]
        o.ins("TB_SBMSN_DCMNT", ["PBANC_SN", "DCMNT_NM", "ESNTL_YN", "TKCG_SE_CD", "PRGRS_STTS_CD", "SBMSN_DDLN_DT"], [P(b), q(dn), q(yn), q(tk), q(st), dates["ddln"]])
    for i, (an, mx) in enumerate(EVL, 1):
        page = EVL_PAGE[i - 1] if level in ("past", "N3", "E6") else (EVL_PAGE[i - 1] if i <= 3 else None)
        o.ins("TB_EVL_ARTCL", ["PBANC_SN", "EVL_ARTCL_NM", "EVL_SCOR_SCR", "PAGE_NO", "SORT_SEQ"], [P(b), q(an), str(mx), q(page), str(i)])
    # 일정 (내부 일정 SC020)
    for ttl, rel in dates["sched"]:
        o.ins("TB_SCHDL", ["TEAM_SN", "PBANC_SN", "SCHDL_SE_CD", "SCHDL_TTL", "SCHDL_BGNG_DT", "AUTO_REG_YN", "RGTR_ID"], [T(b["team"]), P(b), q("SC020"), q(ttl), rel, q("N"), q(leader)])
    # 파일: RFP
    file_row(o, b, "FL010", "rfp/제안요청서_%s.pdf" % b["rfp"], "FL010-1", dates["reg"], SALES, orgnl="제안요청서_%s.pdf" % b["nm"].replace(" ", "")[:20])
    # 파트 (최하위 목차마다)
    for k, (cno, cnm, _) in enumerate(LEAVES):
        if level == "N2" and k % 3 != 0:
            continue
        src = PART_SRC[k % 3]
        file_row(o, b, "FL020", src, "FL020-%s" % cno.replace(".", "_"), dates["files"], pic[cno], toc=toc_ref(b, "TC010", cno),
                 orgnl="%s_%s.%s" % (cno, cnm.replace(" ", ""), src.rsplit(".", 1)[1]))
    if level == "N2":
        return
    # 요약서 (요약서 목차마다)
    for k, (no, nm) in enumerate(SUMMARY_TOC):
        file_row(o, b, "FL040", SUM_SRC[k % 2], "FL040-%s" % no, dates["files"], leader, toc=toc_ref(b, "TC020", no), orgnl="제안요약서_%s.pdf" % nm.replace(" ", ""))
    # 통합본 1차 · 최종
    file_row(o, b, "FL030", "proposal/제안서_통합본_1차.pdf", "FL030-1", dates["intg1"], leader, cycl=1, orgnl="%s_제안서_1차.pdf" % b["nm"].replace(" ", "")[:20])
    file_row(o, b, "FL030", "proposal/제안서_통합본_최종.pdf", "FL030-2", dates["intg2"], leader, cycl=2, last="Y", dcsmkr=leader, cfmtn=dates["intg2"],
             orgnl="%s_제안서_최종.pdf" % b["nm"].replace(" ", "")[:20])
    # 제출서류 파일
    for i, (dn, yn, tk, src) in enumerate(DOCS):
        file_row(o, b, "FL050", src, "FL050-%d" % (i + 1), dates["files"], MGMT if tk == "TK010" else leader,
                 dcmnt="(SELECT DCMNT_SN FROM TB_SBMSN_DCMNT WHERE PBANC_SN = %s AND DCMNT_NM = %s)" % (P(b), q(dn)))

for b in [x for x in BIDS if x["kind"] == "past"]:
    D = b["dates"]
    write_project(b, "past", dict(team=dt_fixed(D["dec"]), rq=dt_fixed(add_days(D["start"], 1, (10, 0))), qna=dt_fixed(add_days(D["start"], 12, (15, 0))),
                                  ddln=dt_fixed(b["ddln"]), reg=dt_fixed(D["reg"]), files=dt_fixed(add_days(D["sub"], -6, (18, 0))),
                                  intg1=dt_fixed(add_days(D["sub"], -5, (19, 0))), intg2=dt_fixed(add_days(D["sub"], -2, (20, 0))),
                                  sched=[("내부 1차 검토 회의", dt_fixed(add_days(D["start"], 10, (14, 0)))), ("통합본 최종 점검", dt_fixed(add_days(D["sub"], -2, (10, 0)))),
                                         ("발표 리허설", dt_fixed(add_days(D["prsn"], -1, (16, 0))))]))
for b in [x for x in BIDS if x["kind"] in ("N1", "N2", "N3")]:
    R = b["rel"]
    if b["kind"] == "N1":
        write_project(b, "N1", dict(team=dt_rel(R["dec"], 10, 40)))
    elif b["kind"] == "N2":
        write_project(b, "N2", dict(team=dt_rel(R["dec"], 10, 40), rq=dt_rel(R["start"] + 1, 10), qna=dt_rel(-4, 15), ddln=dt_rel(b["ddln"], 10),
                                    reg=dt_rel(R["reg"], 9), files=dt_rel(-2, 18),
                                    sched=[("내부 1차 검토 회의", dt_rel(-7, 14)), ("요구사항 대비표 점검", dt_rel(-2, 10)),
                                           ("통합본 최종 점검", dt_rel(1, 10)), ("제출 전 최종 확인", dt_rel(2, 16)), ("발표 리허설", dt_rel(6, 14))]))
    else:
        write_project(b, "N3", dict(team=dt_rel(R["dec"], 10, 40), rq=dt_rel(R["start"] + 1, 10), qna=dt_rel(-12, 15), ddln=dt_rel(b["ddln"], 10),
                                    reg=dt_rel(R["reg"], 9), files=dt_rel(-6, 18), intg1=dt_rel(-5, 19), intg2=dt_rel(-2, 20),
                                    sched=[("내부 1차 검토 회의", dt_rel(-18, 14)), ("통합본 최종 점검", dt_rel(-3, 10)), ("발표 리허설", dt_rel(4, 16)), ("발표 평가", dt_rel(5, 14))]))
E6 = EXC[5]; E6["rfp"] = "B"
write_project(E6, "E6", dict(team=dt_rel(E6["reg"] + 3, 10, 40), rq=dt_rel(E6["reg"] + 5, 10), qna=dt_rel(E6["ddln"] - 10, 15), ddln=dt_rel(E6["ddln"], 10),
                            reg=dt_rel(E6["reg"], 9), files=dt_rel(E6["ddln"] - 6, 18), intg1=dt_rel(E6["ddln"] - 5, 19), intg2=dt_rel(E6["ddln"] - 2, 20),
                            sched=[("내부 1차 검토 회의", dt_rel(E6["ddln"] - 15, 14)), ("통합본 최종 점검", dt_rel(E6["ddln"] - 2, 10))]))
o5.commit()
o5.save()

# ================================================================ 06: 결과 · 평가 · 장단점
o6 = Out("06_seed_results.sql", "입찰 결과 · 업체별 평가 · 위원별 점수 · 평가표 · 장단점",
         ["과거 24건: 수주 12 · 탈락 11 · 유찰 1(결과 없음, 유찰 사유만 E6·과거 유찰 건 비고). 위원 5명, 평가 항목 7개(기술 90점).",
          "평가표 엑셀은 우리 회사와 비교 업체 1곳만 올린 것으로 넣는다 (통계는 올린 업체만 센다)."])
summary_rows = []
def scores_for(target_tech):
    # 항목별 득점률을 만든 뒤 기술 점수 합이 target_tech 에 가깝게 맞춘다 (위원 5명)
    base = [random.uniform(0.80, 0.98) for _ in EVL]
    s = sum(r * mx for r, (_, mx) in zip(base, EVL))
    base = [min(0.995, r * target_tech / s) for r in base]
    out = []
    for r, (_, mx) in zip(base, EVL):
        vals = [round(min(mx, max(0, mx * (r + random.uniform(-0.04, 0.04)))) * 2) / 2 for _ in range(5)]
        out.append(vals)
    tech = round(sum(sum(v) / 5 for v in out), 3)
    return out, tech

for b in [x for x in BIDS if x["kind"] == "past"]:
    o6.c("[%s] %s · %s" % (b["no"], b["nm"], b["rs"]))
    leader = LEADER[b["team"]]
    sector = SECTOR[b["team"]]
    if b["rs"] == "유찰":
        o6.s("UPDATE TB_BID_PBANC SET PBANC_STEP_CD = 'PP040' WHERE PBANC_SN = %s" % P(b))
        summary_rows.append((b["no"], b["team"], b["ddln"][:4], "유찰", "-", "-"))
        continue
    rank = b["rnk"]
    ncomp = 2 if rank <= 2 else 3
    comps = random.sample(COMPETITORS, ncomp)
    # 목표 종합 점수: 우리 = 목록표 값, 경쟁사는 순위에 맞게
    our_total = b["scr"]
    totals = []
    higher = rank - 1
    for i in range(ncomp):
        if i < higher: totals.append(round(our_total + 0.9 + i * 1.1 + random.uniform(0, 0.4), 2))
        else: totals.append(round(our_total - 1.2 - (i - higher) * 1.3 - random.uniform(0, 0.5), 2))
    entries = [(OUR_BIZ, OUR_NM, our_total, True)] + [(c[0], c[1], t, False) for c, t in zip(comps, totals)]
    for _try in range(200):
      rows = []
      for biz, nm, tot, ours in entries:
        prc = round(random.uniform(8.6, 9.9), 3)
        sc, tech = scores_for(tot - prc)
        rows.append(dict(biz=biz, nm=nm, ours=ours, prc=prc, sc=sc, tech=tech, total=round(tech + prc, 3),
                         amt=int(b["bgt"] * random.uniform(0.86, 0.95) / 1000) * 1000))
      rows.sort(key=lambda r: -r["total"])
      for i, r in enumerate(rows, 1): r["rnk"] = i
      our = [r for r in rows if r["ours"]][0]
      if our["rnk"] == rank and abs(our["total"] - our_total) < 0.6:
          break
    else:
        raise SystemExit("순위 맞추기 실패 %s" % b["no"])
    winner = rows[0]
    upload = [our] + [r for r in rows if not r["ours"]][:1]
    if not winner["ours"]: upload = [our, winner]
    for r in rows:
        o6.ins("TB_CO_EVL_RSLT", ["PBANC_SN", "BIZ_NO", "CO_NM", "OPENG_RNK", "BID_AMT", "TECH_EVL_SCR", "PRC_EVL_SCR", "SNTHS_EVL_SCR", "OUR_CO_YN", "SCSBD_YN", "EVL_TBL_UPLD_YN", "CLCT_DT"],
               [P(b), q(r["biz"]), q(r["nm"]), str(r["rnk"]), str(r["amt"]), "%.3f" % r["tech"], "%.3f" % r["prc"], "%.3f" % r["total"],
                q("Y" if r["ours"] else "N"), q("Y" if r is winner else "N"), q("Y" if r in upload else "N"), dt_fixed(add_days(b["ddln"], 0, (17, 0)))])
    strong = [s % sector if "%s" in s else s for s in STRENGTH[b["rs"]]]
    weak = WEAK[b["rs"]]
    o6.ins("TB_BID_RSLT", ["PBANC_SN", "BID_RSLT_CD", "EVL_RNK", "TECH_EVL_SCR", "PRC_EVL_SCR", "SNTHS_EVL_SCR", "SCSBD_AMT", "STRPT_CN", "SBPT_CS_CN", "SPLMNT_CN",
                           "RBPRSN_ID", "FLFMT_BGNG_YMD", "FLFMT_END_YMD", "CLCT_DT", "MDFR_ID", "MDFCN_DT"],
           [P(b), q("BR010" if b["rs"] == "수주" else "BR020"), str(rank), "%.3f" % our["tech"], "%.3f" % our["prc"], "%.3f" % our["total"],
            str(our["amt"]) if b["rs"] == "수주" else "NULL", q("\n".join("- " + s for s in strong)), q("\n".join("- " + s for s in weak)), q(SPLMNT[b["rs"]]),
            q(leader) if b["rs"] == "수주" else "NULL",
            q(add_days(b["ddln"], 30)[:10].replace("-", "")) if b["rs"] == "수주" else "NULL",
            q(add_days(b["ddln"], 30 + 300)[:10].replace("-", "")) if b["rs"] == "수주" else "NULL",
            dt_fixed(add_days(b["ddln"], 0, (17, 0))), q(MGMT), dt_fixed(b["dates"]["rslt"])])
    # 평가표 엑셀 파일 + 업로드 기록 + 위원별 점수 (올린 업체만)
    for r in upload:
        fn = "evaluation/gen/%s_%s.xlsx" % (b["no"], r["biz"])
        wb = Workbook(); ws = wb.active; ws.title = "평가표"
        ws.append(["%s 기술평가 위원별 점수표 (%s)" % (b["nm"], r["nm"])]); ws.append([])
        ws.append(["평가항목", "배점기준"] + ["평가위원%d" % (i + 1) for i in range(5)])
        for (an, mx), vals in zip(EVL, r["sc"]):
            ws.append(["%s(%s)" % (an, mx), mx] + vals)
        ws.append(["합계", sum(m for _, m in EVL)] + [sum(v[i] for v in r["sc"]) for i in range(5)])
        ws["A1"].font = Font(bold=True); ws.column_dimensions["A"].width = 30
        wb.save(os.path.join(FILES, fn))
        strg = file_row(o6, b, "FL070", fn, "FL070-%s" % r["biz"], dt_fixed(b["dates"]["rslt"]), MGMT, orgnl="평가표_%s.xlsx" % r["nm"])
        co = "(SELECT CO_EVL_RSLT_SN FROM TB_CO_EVL_RSLT WHERE PBANC_SN = %s AND BIZ_NO = %s)" % (P(b), q(r["biz"]))
        o6.ins("TB_EVL_TBL_UPLD", ["CO_EVL_RSLT_SN", "FILE_SN", "CMSNR_CNT", "LAST_YN", "RGTR_ID", "REG_DT"],
               [co, "(SELECT FILE_SN FROM TB_ATCH_FILE WHERE STRG_FILE_NM = %s)" % q(strg), "5", q("Y"), q(MGMT), dt_fixed(b["dates"]["rslt"])])
        vals_sql = []
        for (an, mx), vals in zip(EVL, r["sc"]):
            for n, v in enumerate(vals, 1):
                vals_sql.append("SELECT %s AS NM, %d AS NO, %s AS SCR FROM DUAL" % (q(an), n, v))
        o6.s("INSERT INTO TB_EVL_CMSNR_SCR (CO_EVL_RSLT_SN, EVL_ARTCL_SN, CMSNR_NO, EVL_SCR)\n"
             "SELECT %s, A.EVL_ARTCL_SN, S.NO, S.SCR\n  FROM (%s) S\n  JOIN TB_EVL_ARTCL A ON A.PBANC_SN = %s AND A.EVL_ARTCL_NM = S.NM" % (co, "\n        UNION ALL ".join(vals_sql), P(b)))
    summary_rows.append((b["no"], b["team"], b["ddln"][:4], b["rs"], rank, "%.3f" % our["total"]))
o6.c("유찰 E6(2026): 결과 없이 둔다. 처리 이력과 수집 이력만 있다 (04)")
o6.commit()
o6.save()

# ================================================================ 07: 알림 · 참고자료 · 확인 쿼리
o7 = Out("07_seed_etc.sql", "알림 · 참고자료 · 정합성 확인 쿼리", ["알림은 역할별로 읽음·안 읽음을 섞고, 참고자료는 팀마다 10개. 맨 끝 확인 쿼리는 모두 0건이어야 한다."])
N1, N2, N3 = NOW
W = [b for b in BIDS if b["kind"] == "W"]
notis = []
def nt(rcvr, b, cd, cn, days, hour, read="N"):
    notis.append((rcvr, b, cd, cn, days, hour, read))
nt(SALES, W[3], "NT010", "새 입찰공고 '%s'이(가) 등록되었습니다." % W[3]["nm"], 0, 8.2)
nt(SALES, W[2], "NT010", "새 입찰공고 '%s'이(가) 등록되었습니다." % W[2]["nm"], -1, 8.2)
nt(SALES, W[1], "NT010", "새 입찰공고 '%s'이(가) 등록되었습니다." % W[1]["nm"], -2, 8.2, "Y")
nt(SALES, EXC[3], "NT020", "'%s' 공고가 취소되었습니다. (취소공고)" % EXC[3]["nm"], -4, 17.1)
nt(SALES, EXC[4], "NT040", "전략사업3Div가 '%s' 공고에 불참을 결정했습니다. 다시 배정해 주세요." % EXC[4]["nm"], -7, 11)
nt(SALES, N1, "NT040", "공공사업2Div가 '%s' 공고에 참여를 결정했습니다." % N1["nm"], -5, 10.5, "Y")
nt(SALES, EXC[6], "NT150", "유찰된 '%s' 공고의 재공고가 올라왔습니다." % EXC[5]["nm"], -5, 9, "Y")
nt(SALES, None, "NT170", "오늘 공고 수집을 마쳤습니다: 변경 없음", 0, 8.2)
nt(MGMT, N3, "NT070", "'%s' 제안서 제출(나라장터 등록)이 완료되었습니다." % N3["nm"], -1, 16)
nt(MGMT, N2, "NT050", "'%s' 제출 마감이 3일 남았습니다. 경영지원실 서류를 확인해 주세요." % N2["nm"], 0, 8)
nt(MGMT, N1, "NT040", "'%s' 참여가 확정되었습니다. 제출서류를 준비해 주세요." % N1["nm"], -5, 10.5)
nt(LEADER["공공사업2Div"], N1, "NT030", "'%s' 공고가 공공사업2Div에 배정되었습니다. 참여 여부를 결정해 주세요." % N1["nm"], -6, 14, "Y")
nt(LEADER["공공사업3Div"], N2, "NT050", "'%s' 제출 마감이 3일 남았습니다." % N2["nm"], 0, 8)
nt(LEADER["공공사업3Div"], N2, "NT160", "[%s] '요구사항 대비표 점검' 일정이 등록되었습니다." % N2["nm"], -9, 15, "Y")
nt(LEADER["전략사업1Div"], N3, "NT070", "'%s' 제출이 완료되었습니다." % N3["nm"], -1, 16)
nt(LEADER["전략사업3Div"], EXC[4], "NT030", "'%s' 공고가 전략사업3Div에 배정되었습니다." % EXC[4]["nm"], -9, 14, "Y")
nt(LEADER["공공사업1Div"], None, "NT170", "이번 주 새로 들어온 공고가 4건 있습니다. 영업부 배정을 기다리는 중입니다.", -1, 9)
for u in N2.get("members", [])[:4]:
    nt(u, N2, "NT050", "'%s' 제출 마감이 3일 남았습니다." % N2["nm"], 0, 8)
    nt(u, N2, "NT160", "[%s] '통합본 최종 점검' 일정이 등록되었습니다." % N2["nm"], -3, 11, "Y")
for u in N3.get("members", [])[:3]:
    nt(u, N3, "NT070", "'%s' 제출이 완료되었습니다. 수고하셨습니다." % N3["nm"], -1, 16)
for u in N1.get("members", [])[:3]:
    nt(u, N1, "NT040", "'%s' 제안팀에 들어갔습니다." % N1["nm"], -5, 10.6)
nt("2024-0102", None, "NT170", "공공사업1Div 이전 사업의 질의응답을 '이전 사업 Q" + "A 보기'에서 볼 수 있습니다.", -2, 10, "Y")
notis[-1] = ("2024-0102", None, "NT170", "공공사업1Div 이전 사업의 질의응답은 질의응답 화면의 '이전 사업 보기'에서 볼 수 있습니다.", -2, 10, "Y")
o7.c("1) 알림 %d건" % len(notis))
for rcvr, b, cd, cn, days, hour, read in notis:
    o7.ins("TB_NOTI", ["RCVR_ID", "PBANC_SN", "NOTI_TYPE_CD", "NOTI_CN", "PRSL_YN", "CRT_DT"], [q(rcvr), P(b) if b else "NULL", q(cd), q(cn), q(read), dt_rel(days, int(hour), int(round((hour % 1) * 60)))])
o7.c("2) 참고자료: 팀마다 폰트 2 · 이미지 3 · 문서 3 · 기타 2")
REFS = [("RF010", "refs/font/NotoSansKR-Subset-Regular.otf", "제안서 본문 글꼴 (Noto Sans KR)"), ("RF010", "refs/font/NotoSerifKR-Subset-Regular.otf", "표지·제목용 명조 글꼴"),
        ("RF020", "refs/image/조직도.png", "사업 수행 조직도 원본"), ("RF020", "refs/image/시스템구성도.png", "목표 시스템 구성도 예시"), ("RF020", "refs/image/표지배경.jpg", "제안서 표지 배경"),
        ("RF030", "refs/doc/제안서작성가이드.pdf", "제안팀 공통 작성 기준"), ("RF030", "refs/doc/회사소개서.docx", "회사 소개 본문 (최신본)"), ("RF030", "refs/doc/수행실적목록.pdf", "최근 3년 수행 실적"),
        ("RF040", "refs/etc/제안서_아이콘모음.zip", "선형 아이콘 모음"), ("RF040", "refs/etc/제안서_표지템플릿.svg", "표지 템플릿 (SVG)")]
for ti, t in enumerate(TEAMS_DEV, 1):
    people_t = [LEADER[t]] + MEMBERS[t]
    for k, (se, src, memo) in enumerate(REFS, 1):
        ext = src.rsplit(".", 1)[1]
        strg = "T%d-RF-%02d.%s" % (ti, k, ext)
        sub = "seed/reference/T%d" % ti
        manifest.append((sub + "/" + strg, src))
        o7.ins("TB_REF_FILE", ["TEAM_SN", "REF_SE_CD", "ORGNL_FILE_NM", "STRG_FILE_NM", "FILE_PATH_NM", "FILE_SZ", "FILE_EXTN_NM", "MEMO_CN", "RGTR_ID", "REG_DT"],
               [T(t), q(se), q(os.path.basename(src)), q(strg), q(sub), str(os.path.getsize(os.path.join(FILES, src))), q(ext), q(memo), q(people_t[k % len(people_t)]),
                dt_fixed("2026-%02d-%02d 10:00" % (1 + (k % 9), 5 + ti))])
o7.commit()
o7.c("3) 정합성 확인: 아래 쿼리가 모두 0이어야 한다")
CHECKS = [
    ("진행 중인데 주관팀 팀장이 제안팀원이 아닌 사업",
     "SELECT COUNT(*) FROM TB_BID_PBANC P WHERE P.PBANC_STTS_CD IN ('PS030','PS040','PS050') AND NOT EXISTS (SELECT 1 FROM TB_PRP_TMMBR M JOIN TB_USER U ON U.USER_ID = M.USER_ID WHERE M.PBANC_SN = P.PBANC_SN AND U.TEAM_SN = P.SPRVSN_TEAM_SN AND U.AUTHRT_CD = 'AU030')"),
    ("참여 확정 이후인데 참여 결정 이력이 없는 사업",
     "SELECT COUNT(*) FROM TB_BID_PBANC P WHERE P.PBANC_STTS_CD IN ('PS030','PS040','PS050','PS060','PS070') AND NOT EXISTS (SELECT 1 FROM TB_PBANC_PRCS_HSTRY H WHERE H.PBANC_SN = P.PBANC_SN AND H.PRCS_SE_CD = 'PC050')"),
    ("마감이 지났는데 참여 확정·제안 작성 중인 사업",
     "SELECT COUNT(*) FROM TB_BID_PBANC WHERE PBANC_STTS_CD IN ('PS030','PS040') AND BID_DDLN_DT < SYSDATE"),
    ("결과 등록인데 입찰 결과나 우리 회사 평가가 없는 사업",
     "SELECT COUNT(*) FROM TB_BID_PBANC P WHERE P.PBANC_STTS_CD = 'PS060' AND (NOT EXISTS (SELECT 1 FROM TB_BID_RSLT R WHERE R.PBANC_SN = P.PBANC_SN) OR NOT EXISTS (SELECT 1 FROM TB_CO_EVL_RSLT C WHERE C.PBANC_SN = P.PBANC_SN AND C.OUR_CO_YN = 'Y'))"),
    ("2024·2025 사업이 팀마다 2건씩이 아닌 팀·연도",
     "SELECT COUNT(*) FROM (SELECT T.TEAM_SN, Y.YR FROM TB_TEAM T CROSS JOIN (SELECT '2024' YR FROM DUAL UNION ALL SELECT '2025' FROM DUAL) Y WHERE T.DEPT_CD = 'DP030' AND (SELECT COUNT(*) FROM TB_BID_PBANC P WHERE P.SPRVSN_TEAM_SN = T.TEAM_SN AND TO_CHAR(P.BID_DDLN_DT, 'YYYY') = Y.YR) <> 2)"),
    ("결과 합계가 수주 12 · 탈락 11 · 유찰 1 이 아닌 경우 (0 이면 맞음)",
     "SELECT ABS((SELECT COUNT(*) FROM TB_BID_RSLT WHERE BID_RSLT_CD = 'BR010') - 12) + ABS((SELECT COUNT(*) FROM TB_BID_RSLT WHERE BID_RSLT_CD = 'BR020') - 11) + ABS((SELECT COUNT(*) FROM TB_BID_PBANC WHERE PBANC_STTS_CD = 'PS070' AND TO_CHAR(BID_DDLN_DT, 'YYYY') < '2026') - 1) FROM DUAL"),
    ("파일·요구사항이 최하위가 아닌 목차에 붙은 건",
     "SELECT COUNT(*) FROM (SELECT TOC_SN FROM TB_ATCH_FILE WHERE TOC_SN IS NOT NULL UNION ALL SELECT TOC_SN FROM TB_RQT_MTTR WHERE TOC_SN IS NOT NULL) X WHERE EXISTS (SELECT 1 FROM TB_TOC C WHERE C.UP_TOC_SN = X.TOC_SN)"),
    ("평가 항목 이름으로 위원 점수가 연결되지 않은 업로드 업체",
     "SELECT COUNT(*) FROM TB_CO_EVL_RSLT C WHERE C.EVL_TBL_UPLD_YN = 'Y' AND (SELECT COUNT(*) FROM TB_EVL_CMSNR_SCR S WHERE S.CO_EVL_RSLT_SN = C.CO_EVL_RSLT_SN) <> 35"),
]
for title, sql in CHECKS:
    o7.lines.append("-- " + title)
    o7.s(sql)
o7.save()

# ---------------------------------------------------------------- manifest + 요약
with open(os.path.join(FILES, "manifest.tsv"), "w", encoding="utf-8", newline="") as fh:
    w = csv.writer(fh, delimiter="\t", lineterminator="\n")
    w.writerow(["dest", "src"])
    for d, s in manifest: w.writerow([d, s])
print("manifest", len(manifest))
for r in summary_rows: print(r)
for f in sorted(os.listdir(OUTDIR)):
    p = os.path.join(OUTDIR, f)
    print(f, sum(1 for _ in open(p, encoding="utf-8")), "lines", os.path.getsize(p) // 1024, "KB")
