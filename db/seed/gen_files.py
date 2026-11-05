# 수주로 시연·테스트용 원본 파일 생성기 → db/demo-files
# 실행: cd db/seed && python3 gen_files.py  (python-docx · python-pptx · openpyxl · Pillow · fonttools, LibreOffice(soffice), Noto CJK 글꼴 필요)
# 원본을 다시 만들면 gen_sql.py 도 다시 실행한다 (파일 크기가 SQL 에 들어간다)
import os, subprocess, shutil, zipfile, random, io
from docx import Document
from docx.shared import Pt, RGBColor, Cm
from docx.enum.text import WD_ALIGN_PARAGRAPH
from docx.oxml.ns import qn
from pptx import Presentation
from pptx.util import Inches, Pt as PPt
from pptx.dml.color import RGBColor as PRGB
from pptx.enum.text import PP_ALIGN
from openpyxl import Workbook
from openpyxl.styles import Font, Alignment, Border, Side, PatternFill
from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTCollection
from fontTools import subset

random.seed(7)
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "..", "demo-files")
TMP = os.path.join(HERE, "_tmp")
shutil.rmtree(TMP, ignore_errors=True)
os.makedirs(TMP)
os.makedirs(OUT, exist_ok=True)
CO = "오티아이"
NAVY = "1D4B7E"; BLUE = "16419E"
FONT = "Noto Sans CJK KR"

def sub(*p):
    path = os.path.join(OUT, *p[:-1])
    os.makedirs(path, exist_ok=True)
    return os.path.join(path, p[-1])

# ---------------------------------------------------------------- 공통: DOCX
def new_doc():
    d = Document()
    st = d.styles["Normal"]
    st.font.name = FONT; st.font.size = Pt(10.5)
    st.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    for s in ("Heading 1", "Heading 2", "Title"):
        h = d.styles[s]; h.font.name = FONT; h.font.color.rgb = RGBColor.from_string(NAVY)
        h.element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    for sec in d.sections:
        sec.left_margin = sec.right_margin = Cm(2.2); sec.top_margin = sec.bottom_margin = Cm(2)
    return d

def para(d, text, size=None, bold=False, align=None, color=None, space=6):
    p = d.add_paragraph()
    r = p.add_run(text); r.bold = bold
    r.font.name = FONT; r._element.rPr.rFonts.set(qn("w:eastAsia"), FONT)
    if size: r.font.size = Pt(size)
    if color: r.font.color.rgb = RGBColor.from_string(color)
    if align == "c": p.alignment = WD_ALIGN_PARAGRAPH.CENTER
    p.paragraph_format.space_after = Pt(space)
    return p

def table(d, rows, widths=None, header=True):
    t = d.add_table(rows=len(rows), cols=len(rows[0]))
    t.style = "Table Grid"
    for i, row in enumerate(rows):
        for j, v in enumerate(row):
            c = t.cell(i, j); c.text = ""
            r = c.paragraphs[0].add_run(str(v))
            r.font.name = FONT; r._element.rPr.rFonts.set(qn("w:eastAsia"), FONT); r.font.size = Pt(9.5)
            if header and i == 0: r.bold = True
            if widths: c.width = Cm(widths[j])
    d.add_paragraph()
    return t

def cover(d, title, subtitle, note=None):
    for _ in range(6): d.add_paragraph()
    para(d, title, 22, True, "c", NAVY, 14)
    para(d, subtitle, 13, False, "c", "636E7E", 40)
    if note: para(d, note, 11, True, "c", "C13B3B", 30)
    for _ in range(8): d.add_paragraph()
    para(d, "2025. 00.", 12, False, "c")
    para(d, CO, 16, True, "c", BLUE)
    d.add_page_break()

def to_pdf(docx_path, pdf_name):
    subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", TMP, docx_path],
                   check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    base = os.path.splitext(os.path.basename(docx_path))[0] + ".pdf"
    shutil.move(os.path.join(TMP, base), pdf_name)

SECTIONS = [
    ("Ⅰ. 제안 개요", ["제안 배경 및 목적", "제안의 범위", "제안의 특장점"]),
    ("Ⅱ. 사업 이해", ["사업 추진 배경", "현황 분석 및 문제점", "목표 시스템 개념도"]),
    ("Ⅲ. 사업 수행 방안", ["추진 전략", "시스템 구성 방안", "기능 구현 방안", "데이터 이관 방안"]),
    ("Ⅳ. 사업 관리", ["추진 체계 및 조직", "일정 관리", "품질·보안 관리", "위험 관리"]),
    ("Ⅴ. 지원 방안", ["교육 계획", "하자보수 및 유지관리 계획"]),
]
BODY = [
    "본 사업은 기존 시스템의 노후화와 업무 증가에 대응하여 사용자 중심의 서비스 환경을 구축하는 것을 목표로 한다.",
    "{co}는 유사 공공 정보화 사업을 수행한 경험을 바탕으로 표준 프레임워크 기반의 안정적인 구축 방안을 제시한다.",
    "현행 업무 절차를 분석하여 중복 입력을 줄이고, 처리 단계별 이력을 남겨 업무 투명성을 높인다.",
    "단계별 산출물은 발주기관과 협의한 기준에 따라 검토·승인 절차를 거쳐 확정한다.",
    "개인정보와 중요 정보는 접근 권한을 최소화하고, 전송 구간과 저장 구간 모두 암호화한다.",
]

def proposal_doc(path_pdf, title, note=None, sections=SECTIONS, pages_per=1):
    d = new_doc()
    cover(d, "제 안 서", title, note)
    para(d, "목  차", 16, True, "c", NAVY, 14)
    for s, items in sections:
        para(d, s, 11.5, True, space=2)
        for k, it in enumerate(items, 1):
            para(d, "    %d. %s" % (k, it), 10.5, space=2)
    d.add_page_break()
    for s, items in sections:
        d.add_heading(s, level=1)
        for k, it in enumerate(items, 1):
            d.add_heading("%d. %s" % (k, it), level=2)
            for _ in range(pages_per):
                for b in random.sample(BODY, 3):
                    para(d, b.format(co=CO))
        d.add_page_break()
    dx = os.path.join(TMP, "x_" + os.path.basename(path_pdf).replace(".pdf", ".docx"))
    d.save(dx); to_pdf(dx, path_pdf)

# ---------------------------------------------------------------- 1. 통합본 2
proposal_doc(sub("proposal", "제안서_통합본_1차.pdf"), "통합본 (1차 검토용)", "검토용 1차 통합본")
proposal_doc(sub("proposal", "제안서_통합본_최종.pdf"), "통합본 (최종)")

# ---------------------------------------------------------------- 2. 파트 (PPTX 2, PDF 1)
def deck(path, part, slides):
    p = Presentation(); p.slide_width = Inches(13.333); p.slide_height = Inches(7.5)
    blank = p.slide_layouts[6]
    def box(s, x, y, w, h, text, size, bold=False, color="17202E", fill=None, align=None):
        tb = s.shapes.add_textbox(Inches(x), Inches(y), Inches(w), Inches(h))
        if fill:
            tb.fill.solid(); tb.fill.fore_color.rgb = PRGB.from_string(fill)
        tf = tb.text_frame; tf.word_wrap = True
        for i, line in enumerate(text.split("\n")):
            para_ = tf.paragraphs[0] if i == 0 else tf.add_paragraph()
            r = para_.add_run(); r.text = line
            r.font.size = PPt(size); r.font.bold = bold; r.font.name = FONT
            r.font.color.rgb = PRGB.from_string(color)
            if align == "c": para_.alignment = PP_ALIGN.CENTER
        return tb
    s = p.slides.add_slide(blank)
    box(s, 0, 0, 13.333, 7.5, "", 10, fill=NAVY)
    box(s, 0.8, 2.6, 11.7, 1.2, part, 40, True, "FFFFFF")
    box(s, 0.8, 3.9, 11.7, 0.8, "제안서 파트 · " + CO, 18, False, "C9D6EA")
    for i, (h, lines) in enumerate(slides, 1):
        s = p.slides.add_slide(blank)
        box(s, 0, 0, 13.333, 0.9, "", 10, fill=NAVY)
        box(s, 0.5, 0.15, 10, 0.6, "%s  |  %s" % (part, h), 22, True, "FFFFFF")
        box(s, 0.7, 1.3, 7.4, 5.2, "\n".join("• " + l for l in lines), 18)
        box(s, 8.6, 1.3, 4.2, 5.2, "핵심 포인트\n\n" + lines[0], 16, True, BLUE, "E7EDF8", "c")
        box(s, 11.8, 6.9, 1.3, 0.4, "- %d -" % (i + 1), 11, False, "99A2B1")
    p.save(path)

deck(sub("proposal", "제안서_사업이해.pptx"), "Ⅱ. 사업 이해", [
    ("사업 추진 배경", ["대국민 서비스 수요 증가와 처리 지연", "노후 장비·소프트웨어 지원 종료", "기관 간 정보 연계 요구 확대"]),
    ("현황 분석", ["업무 처리 단계 12개 중 수작업 5개", "중복 입력으로 평균 처리 시간 증가", "모바일 접근성 미흡"]),
    ("목표 시스템", ["단일 창구 통합 서비스", "표준 프레임워크 기반 확장 구조", "데이터 기반 업무 지원"]),
])
deck(sub("proposal", "제안서_수행방안.pptx"), "Ⅲ. 사업 수행 방안", [
    ("추진 전략", ["단계적 전환으로 서비스 중단 최소화", "공통 기능 우선 구축", "사용자 참여형 검증"]),
    ("시스템 구성", ["이중화 웹·WAS 구성", "통합 인증(SSO) 연계", "운영·개발 환경 분리"]),
    ("기능 구현", ["업무별 화면 표준 적용", "처리 이력·통계 자동 집계", "외부 기관 API 연계"]),
    ("데이터 이관", ["사전 정제와 3회 시험 이관", "건수·금액 검증 리포트", "이관 후 병행 운영 2주"]),
])
_tmp_deck = os.path.join(TMP, "제안서_사업관리.pptx")
deck(_tmp_deck, "Ⅳ. 사업 관리", [
    ("추진 체계", ["PM 1 · PL 3 · 개발 8 · QA 1", "발주기관 주간 보고", "이슈 48시간 내 대응"]),
    ("일정 관리", ["분석 2개월 · 설계 2개월 · 구현 4개월", "단계별 검토·승인", "WBS 주간 갱신"]),
    ("품질·보안", ["산출물 표준 점검표", "시큐어 코딩 점검", "개인정보 영향 평가 지원"]),
])
subprocess.run(["soffice", "--headless", "--convert-to", "pdf", "--outdir", TMP, _tmp_deck], check=True,
               stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
shutil.move(os.path.join(TMP, "제안서_사업관리.pdf"), sub("proposal", "제안서_사업관리.pdf"))

# ---------------------------------------------------------------- 3. 제안요약서 2
def summary(path, chap, rows):
    d = new_doc()
    para(d, "제안요약서", 18, True, "c", NAVY, 6)
    para(d, chap + "  ·  " + CO, 11, False, "c", "636E7E", 16)
    table(d, [["구분", "요약 내용", "제안서 쪽"]] + rows, [3.5, 10.5, 2.5])
    para(d, "※ 세부 내용은 제안서 본문의 해당 쪽을 참고한다.", 9.5, color="636E7E")
    dx = os.path.join(TMP, os.path.basename(path).replace(".pdf", ".docx")); d.save(dx); to_pdf(dx, path)

summary(sub("proposal", "제안요약서_1장.pdf"), "Ⅰ. 제안 개요 ~ Ⅱ. 사업 이해", [
    ["제안 목적", "노후 시스템을 표준 기반으로 재구축하여 처리 시간을 30% 단축", "3-5"],
    ["제안 범위", "업무 시스템 구축, 데이터 이관, 기관 연계, 교육·안정화", "6-8"],
    ["특장점", "유사 사업 수행 경험, 검증된 공통 컴포넌트, 단계적 전환", "9-10"],
    ["현황 분석", "수작업 5개 단계, 중복 입력, 모바일 미지원", "12-15"],
])
summary(sub("proposal", "제안요약서_2장.pdf"), "Ⅲ. 사업 수행 방안 ~ Ⅴ. 지원 방안", [
    ["추진 전략", "공통 기능 우선 구축, 사용자 참여형 검증", "20-22"],
    ["시스템 구성", "이중화 구성, 통합 인증 연계, 환경 분리", "23-27"],
    ["사업 관리", "주간 보고, 단계별 승인, 품질·보안 점검표", "40-48"],
    ["지원 방안", "역할별 교육 3회, 하자보수 1년", "50-53"],
])

# ---------------------------------------------------------------- 4. RFP 2 (요구사항 총괄표·목록표)
RFP_A = [("SFR", "기능 요구사항", ["통합 로그인 및 권한 관리", "민원 신청 접수", "민원 처리 현황 조회", "처리 단계별 알림 발송",
                                 "첨부 서류 관리", "통계 및 보고서 출력", "관리자 코드 관리", "모바일 화면 제공"]),
         ("PER", "성능 요구사항", ["화면 응답 시간 3초 이내", "동시 사용자 500명 처리", "대량 조회 성능 확보"]),
         ("SER", "보안 요구사항", ["개인정보 암호화", "접근 기록 보관", "시큐어 코딩 적용"]),
         ("DAR", "데이터 요구사항", ["기존 데이터 이관", "데이터 표준 준수"])]
RFP_B = [("SFR", "기능 요구사항", ["사용자 계정 관리", "업무 신청서 작성", "결재 및 승인 처리", "진행 현황 대시보드", "검색 및 필터 제공",
                                 "전자 문서 보관", "외부 기관 연계 조회", "공지사항 관리", "엑셀 내려받기", "이력 조회"]),
         ("INR", "인터페이스 요구사항", ["행정정보 공동이용 연계", "통합 인증 연계", "문자 발송 연계"]),
         ("SER", "보안 요구사항", ["권한별 메뉴 접근 통제", "비밀번호 정책 적용"]),
         ("QUR", "품질 요구사항", ["웹 접근성 준수", "호환성 확보"]),
         ("TER", "테스트 요구사항", ["통합 테스트 수행"])]

def rfp(path, title, inst, groups):
    d = new_doc()
    cover(d, "제안요청서", title + "\n" + inst)
    d.add_heading("Ⅰ. 사업 개요", level=1)
    para(d, "본 사업은 %s의 업무 효율화와 대국민 서비스 품질 향상을 위하여 추진한다." % inst)
    para(d, "사업 기간은 계약일로부터 10개월이며, 사업 범위는 시스템 구축, 데이터 이관, 교육 및 안정화를 포함한다.")
    d.add_heading("Ⅱ. 제안 요청 내용", level=1)
    para(d, "1. 요구사항 총괄표", 12, True, color=NAVY)
    total = sum(len(g[2]) for g in groups)
    table(d, [["요구사항 분류", "요구사항 ID", "개수"]] + [[g[1], g[0] + "-000", len(g[2])] for g in groups] + [["합계", "", total]],
          [6, 5, 3])
    d.add_page_break()
    para(d, "2. 요구사항 목록표", 12, True, color=NAVY)
    rows = [["No", "요구사항 ID", "요구사항 명칭"]]
    n = 0
    for pre, _, names in groups:
        for k, nm in enumerate(names, 1):
            n += 1
            rows.append([n, "%s-%03d" % (pre, k), nm])
    table(d, rows, [1.6, 3.6, 11])
    d.add_heading("Ⅲ. 제안서 작성 요령", level=1)
    para(d, "제안서는 제안요청서의 요구사항 ID를 기준으로 작성하며, 요구사항 대비표를 첨부한다.")
    dx = os.path.join(TMP, os.path.basename(path).replace(".pdf", ".docx")); d.save(dx); to_pdf(dx, path)

rfp(sub("rfp", "제안요청서_A.pdf"), "통합 민원 업무시스템 구축 사업", "발주기관", RFP_A)
rfp(sub("rfp", "제안요청서_B.pdf"), "업무 지원 플랫폼 고도화 사업", "발주기관", RFP_B)

# ---------------------------------------------------------------- 5~7. 제출서류
def simple_pdf(path, title, rows, foot):
    d = new_doc()
    para(d, title, 20, True, "c", NAVY, 20)
    table(d, rows, [4.5, 11.5], header=False)
    para(d, foot, 10.5, align="c", space=30)
    para(d, "%s 대표이사 (인)" % CO, 12, True, "c")
    para(d, "※ 수주로 시연용 견본 서류입니다. 실제 효력이 없습니다.", 9, align="c", color="C13B3B")
    dx = os.path.join(TMP, os.path.basename(path).replace(".pdf", ".docx")); d.save(dx); to_pdf(dx, path)

BASE = [["상호", "(주)%s (견본)" % CO], ["사업자등록번호", "107-87-28988"], ["대표자", "홍길동 (견본)"], ["소재지", "서울특별시 ○○구 ○○로 00 (견본)"]]
simple_pdf(sub("docs", "사업자등록증.pdf"), "사업자등록증 (견본)", BASE + [["업태 / 종목", "서비스 / 소프트웨어 개발 및 공급"], ["개업연월일", "2005년 00월 00일"]], "위와 같이 등록되었음을 확인합니다.")
simple_pdf(sub("docs", "법인등기부등본.pdf"), "법인 등기사항 전부증명서 (견본)", BASE + [["목적", "1. 소프트웨어 개발 및 공급업\n2. 정보시스템 구축 및 운영업"], ["자본금", "금 ○○억 원 (견본)"]], "이 증명서는 견본입니다.")
simple_pdf(sub("docs", "재무제표.pdf"), "재무제표 요약 (견본)", [["구분", "금액 (단위: 백만 원)"], ["자산 총계", "00,000"], ["부채 총계", "00,000"], ["자본 총계", "00,000"], ["매출액", "00,000"], ["당기순이익", "0,000"]], "회계연도 2024년 1월 1일 ~ 2024년 12월 31일 (견본)")
simple_pdf(sub("docs", "입찰참가신청서.pdf"), "입찰참가신청서 (견본)", BASE + [["입찰 건명", "(해당 사업명)"], ["입찰 방법", "협상에 의한 계약"]], "위와 같이 입찰에 참가하고자 신청합니다.")

def pledge(path, title, items):
    d = new_doc()
    para(d, title, 20, True, "c", NAVY, 24)
    para(d, "(주)%s는 위 사업의 입찰에 참가함에 있어 다음 사항을 성실히 지킬 것을 서약합니다." % CO, 11, space=14)
    for i, t in enumerate(items, 1):
        para(d, "%d. %s" % (i, t), 11, space=8)
    for _ in range(3): d.add_paragraph()
    para(d, "2025년 00월 00일", 11, align="c", space=16)
    para(d, "%s 대표이사  홍길동 (견본)  (인)" % CO, 12, True, "c")
    d.save(path)

pledge(sub("docs", "청렴서약서.docx"), "청 렴 서 약 서", ["입찰·계약 과정에서 금품·향응을 제공하지 않습니다.", "담합 등 공정한 경쟁을 해치는 행위를 하지 않습니다.", "위반 시 입찰 참가 제한 등 불이익을 감수합니다."])
pledge(sub("docs", "보안확약서.docx"), "보 안 확 약 서", ["사업 중 알게 된 정보를 외부에 유출하지 않습니다.", "투입 인력 전원에게 보안 교육을 실시합니다.", "사업 종료 시 자료를 반납하거나 파기합니다."])

wb = Workbook(); ws = wb.active; ws.title = "투입인력"
hdr = ["No", "성명", "역할", "등급", "투입 기간(개월)", "투입률(%)", "주요 경력"]
ws.append(hdr)
crew = [("김가희", "PM", "특급", 10, 100, "공공 민원 시스템 PM 6회"), ("오창준", "PL(개발)", "고급", 9, 100, "표준프레임워크 구축 5회"),
        ("임도현", "PL(DB)", "고급", 8, 80, "데이터 이관 4회"), ("서지민", "개발", "중급", 7, 100, "웹 화면 개발"),
        ("홍예린", "개발", "중급", 7, 100, "API 연계 개발"), ("강태윤", "QA", "고급", 4, 50, "품질 관리 7회")]
for i, c in enumerate(crew, 1): ws.append([i] + list(c))
for c in ws[1]:
    c.font = Font(bold=True, color="FFFFFF"); c.fill = PatternFill("solid", fgColor=BLUE); c.alignment = Alignment(horizontal="center")
for col, w in zip("ABCDEFG", [5, 10, 10, 8, 14, 10, 28]): ws.column_dimensions[col].width = w
wb.save(sub("docs", "투입인력현황.xlsx"))

# ---------------------------------------------------------------- 평가표 엑셀 (앱 양식) 샘플
def eval_xlsx(path, items, scores_by_cmsnr, title="기술평가 위원별 점수표"):
    wb = Workbook(); ws = wb.active; ws.title = "평가표"
    ws.append([title]); ws.append([])
    ncm = len(scores_by_cmsnr)
    ws.append(["평가항목", "배점기준"] + ["평가위원%d" % (i + 1) for i in range(ncm)])
    for k, (nm, mx) in enumerate(items):
        ws.append(["%s(%s)" % (nm, mx), mx] + [scores_by_cmsnr[c][k] for c in range(ncm)])
    ws.append(["합계", sum(m for _, m in items)] + [round(sum(scores_by_cmsnr[c]), 2) for c in range(ncm)])
    ws["A1"].font = Font(bold=True, size=13)
    for c in ws[3]: c.font = Font(bold=True)
    ws.column_dimensions["A"].width = 30
    wb.save(path)

EVAL_ITEMS = [("사업 이해도", 10), ("추진 전략", 10), ("기술 및 기능 구현", 25), ("데이터 이관", 10), ("사업 관리", 15), ("품질·보안 관리", 10), ("교육·유지관리", 10)]
sc = [[round(mx * random.uniform(0.82, 0.97), 1) for _, mx in EVAL_ITEMS] for _ in range(5)]
eval_xlsx(sub("evaluation", "평가표_샘플.xlsx"), EVAL_ITEMS, sc)

# ---------------------------------------------------------------- 참고자료: 폰트 (Noto CJK KR 부분 글꼴, OFL)
def _ks(c):
    try:
        return len(chr(c).encode("euc-kr")) == 2  # 완성형 2바이트만 (8바이트 조합형 표기는 뺀다)
    except UnicodeEncodeError:
        return False
HANGUL = "".join(chr(c) for c in range(0xAC00, 0xD7A4) if _ks(c))  # KS X 1001 완성형 2,350자
def make_font(ttc, idx, out):
    coll = TTCollection(ttc)
    f = coll.fonts[idx]
    tmp = os.path.join(TMP, "full.otf"); f.save(tmp)
    opts = subset.Options(); opts.name_IDs = ["*"]; opts.notdef_outline = True
    fnt = subset.load_font(tmp, opts)
    s = subset.Subsetter(opts)
    s.populate(text="".join(chr(c) for c in range(0x20, 0x7F)) + HANGUL + "·「」『』…※○●□■◇◆△▲→←↑↓①②③④⑤ⅠⅡⅢⅣⅤ")
    s.subset(fnt)
    fnt.save(out)

make_font("/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc", 1, sub("refs", "font", "NotoSansKR-Subset-Regular.otf"))
make_font("/usr/share/fonts/opentype/noto/NotoSerifCJK-Regular.ttc", 1, sub("refs", "font", "NotoSerifKR-Subset-Regular.otf"))
with open(sub("refs", "font", "OFL-폰트라이선스.txt"), "w", encoding="utf-8") as fh:
    fh.write("Noto Sans CJK / Noto Serif CJK (Korean subset)\nCopyright 2014-2021 Adobe (http://www.adobe.com/), with Reserved Font Name 'Source'.\n"
             "This Font Software is licensed under the SIL Open Font License, Version 1.1. https://openfontlicense.org\n"
             "수주로 시연용으로 한글 글자 일부만 남긴 부분 글꼴입니다.\n")

# ---------------------------------------------------------------- 참고자료: 이미지
FP = sub("refs", "font", "NotoSansKR-Subset-Regular.otf")
def f(sz): return ImageFont.truetype(FP, sz)
def boxd(dr, xy, text, fill="#E7EDF8", line="#16419E", size=26, color="#17202E"):
    dr.rounded_rectangle(xy, 14, fill=fill, outline=line, width=3)
    x0, y0, x1, y1 = xy
    w = dr.textlength(text, font=f(size))
    dr.text(((x0 + x1 - w) / 2, (y0 + y1) / 2 - size * 0.62), text, font=f(size), fill=color)

im = Image.new("RGB", (1600, 1000), "white"); dr = ImageDraw.Draw(im)
dr.text((60, 40), "사업 수행 조직도", font=f(44), fill="#1D4B7E")
boxd(dr, (650, 140, 950, 230), "발주기관", "#F7F8FA", "#636E7E")
boxd(dr, (650, 300, 950, 390), "PM (사업관리)", "#16419E", "#16419E", color="white")
for x, t in [(100, "품질관리(QA)"), (1200, "보안관리")]:
    boxd(dr, (x, 300, x + 300, 390), t)
for i, t in enumerate(["분석·설계 PL", "개발 PL", "DB·이관 PL", "인프라 PL"]):
    x = 100 + i * 370
    boxd(dr, (x, 540, x + 300, 630), t)
    for k in range(2):
        boxd(dr, (x + 30, 700 + k * 110, x + 270, 780 + k * 110), "%s %d" % ("개발자" if i == 1 else "담당", k + 1), "#FFFFFF", "#99A2B1", 22)
dr.line((800, 230, 800, 300), fill="#636E7E", width=3); dr.line((800, 390, 800, 480), fill="#636E7E", width=3)
dr.line((250, 480, 1360, 480), fill="#636E7E", width=3)
for i in range(4): dr.line((250 + i * 370, 480, 250 + i * 370, 540), fill="#636E7E", width=3)
dr.line((400, 345, 650, 345), fill="#636E7E", width=3); dr.line((950, 345, 1200, 345), fill="#636E7E", width=3)
im.save(sub("refs", "image", "조직도.png"))

im = Image.new("RGB", (1600, 1000), "white"); dr = ImageDraw.Draw(im)
dr.text((60, 40), "목표 시스템 구성도", font=f(44), fill="#1D4B7E")
for i, t in enumerate(["국민·민원인", "내부 업무 담당자", "관리자"]):
    boxd(dr, (80, 170 + i * 200, 380, 270 + i * 200), t, "#F7F8FA", "#636E7E")
boxd(dr, (520, 170, 820, 670), "", "#E7EDF8")
dr.text((585, 190), "웹 · WAS", font=f(30), fill="#16419E")
for i, t in enumerate(["통합 포털", "업무 처리", "통계·보고", "공통 컴포넌트"]):
    boxd(dr, (550, 260 + i * 100, 790, 335 + i * 100), t, "white", "#16419E", 24)
boxd(dr, (960, 260, 1260, 380), "통합 DB", "#16419E", "#16419E", color="white")
boxd(dr, (960, 470, 1260, 590), "파일 저장소", "white")
boxd(dr, (1320, 170, 1560, 270), "통합 인증", "#F7F8FA", "#636E7E", 24)
boxd(dr, (1320, 370, 1560, 470), "행정정보 연계", "#F7F8FA", "#636E7E", 24)
boxd(dr, (1320, 570, 1560, 670), "문자 발송", "#F7F8FA", "#636E7E", 24)
for i in range(3): dr.line((380, 220 + i * 200, 520, 420), fill="#636E7E", width=3)
dr.line((820, 320, 960, 320), fill="#636E7E", width=3); dr.line((820, 530, 960, 530), fill="#636E7E", width=3)
for y in (220, 420, 620): dr.line((1260, 320, 1320, y), fill="#636E7E", width=3)
dr.text((60, 880), "※ 수주로 시연용 견본 구성도", font=f(24), fill="#99A2B1")
im.save(sub("refs", "image", "시스템구성도.png"))

im = Image.new("RGB", (1920, 1080)); px = im.load()
for y in range(1080):
    for x in range(0, 1920, 4):
        t = (x / 1920 * 0.6 + y / 1080 * 0.4)
        c = (int(22 + (29 - 22) * t), int(65 + (75 - 65) * t), int(158 + (126 - 158) * t))
        for k in range(4): px[x + k, y] = c
dr = ImageDraw.Draw(im)
for i in range(9):
    dr.ellipse((1300 + i * 40, -200 + i * 60, 2300 + i * 40, 800 + i * 60), outline=(255, 255, 255), width=1)
dr.text((140, 860), CO, font=f(56), fill="white")
im.save(sub("refs", "image", "표지배경.jpg"), quality=82)

# ---------------------------------------------------------------- 참고자료: 문서
d = new_doc()
cover(d, "제안서 작성 가이드", "오티아이 제안팀 공통 기준 v2.1")
for h, lines in [("1. 문서 형식", ["본문 글꼴은 Noto Sans KR 12pt, 표는 10pt를 쓴다.", "쪽 번호는 아래 가운데에 '- 쪽 -' 형식으로 넣는다."]),
                 ("2. 목차 작성", ["제안요청서의 평가 항목 순서를 따른다.", "대목차는 로마 숫자, 소목차는 아라비아 숫자를 쓴다."]),
                 ("3. 그림·표", ["그림 번호와 제목은 그림 아래, 표 번호와 제목은 표 위에 둔다.", "출처가 있는 그림은 출처를 밝힌다."]),
                 ("4. 검토 절차", ["파트 담당 작성 → PL 검토 → 팀장 확정 순서로 진행한다.", "통합본은 1차 검토 후 최종본을 확정한다."])]:
    d.add_heading(h, level=1)
    for l in lines: para(d, l)
dx = os.path.join(TMP, "제안서작성가이드.docx"); d.save(dx); to_pdf(dx, sub("refs", "doc", "제안서작성가이드.pdf"))

d = new_doc()
para(d, "회사 소개서", 22, True, "c", NAVY, 10); para(d, "(주)%s" % CO, 14, True, "c", BLUE, 24)
table(d, [["구분", "내용"], ["설립", "2005년 (견본)"], ["주요 사업", "공공 정보시스템 구축, 유지관리, 컨설팅"], ["인증", "GS 인증, ISO 9001, ISO 27001 (견본)"], ["임직원", "약 300명 (견본)"]], [4, 12])
para(d, "오티아이는 공공기관 정보화 사업을 중심으로 분석·설계부터 구축, 운영까지 전 과정을 수행한다.")
d.save(sub("refs", "doc", "회사소개서.docx"))

d = new_doc()
para(d, "주요 수행 실적 (최근 3년)", 18, True, "c", NAVY, 14)
table(d, [["연도", "사업명", "발주기관", "금액(억)"], ["2025", "공공데이터 개방 플랫폼 기능 개선", "한국지능정보사회진흥원", "27"],
          ["2025", "질병보건 통합관리시스템 고도화", "질병관리청", "52"], ["2024", "고속도로 교통정보 통합 플랫폼 구축", "한국도로공사", "58"],
          ["2024", "112 신고 통합시스템 기능 개선", "경찰청", "34"], ["2024", "대학 학사정보 통합시스템 구축", "한국교육학술정보원", "24"]], [2, 8, 5, 2])
dx = os.path.join(TMP, "수행실적목록.docx"); d.save(dx); to_pdf(dx, sub("refs", "doc", "수행실적목록.pdf"))

# ---------------------------------------------------------------- 참고자료: 기타 (SVG, ZIP)
ICONS = {
    "문서": '<path d="M6 2h8l4 4v16H6z" fill="none" stroke="#16419E" stroke-width="1.7"/><path d="M14 2v4h4" fill="none" stroke="#16419E" stroke-width="1.7"/>',
    "사용자": '<circle cx="12" cy="8" r="4" fill="none" stroke="#16419E" stroke-width="1.7"/><path d="M4 21c1-4 4-6 8-6s7 2 8 6" fill="none" stroke="#16419E" stroke-width="1.7"/>',
    "서버": '<rect x="4" y="3" width="16" height="7" rx="1.5" fill="none" stroke="#16419E" stroke-width="1.7"/><rect x="4" y="14" width="16" height="7" rx="1.5" fill="none" stroke="#16419E" stroke-width="1.7"/>',
    "보안": '<path d="M12 2l8 3v6c0 5-3.5 9-8 11-4.5-2-8-6-8-11V5z" fill="none" stroke="#16419E" stroke-width="1.7"/>',
    "연계": '<circle cx="6" cy="12" r="3" fill="none" stroke="#16419E" stroke-width="1.7"/><circle cx="18" cy="6" r="3" fill="none" stroke="#16419E" stroke-width="1.7"/><circle cx="18" cy="18" r="3" fill="none" stroke="#16419E" stroke-width="1.7"/><path d="M9 11l6-4M9 13l6 4" stroke="#16419E" stroke-width="1.7"/>',
    "통계": '<path d="M4 20V10M10 20V4M16 20v-7M22 20H2" fill="none" stroke="#16419E" stroke-width="1.7"/>',
}
with zipfile.ZipFile(sub("refs", "etc", "제안서_아이콘모음.zip"), "w", zipfile.ZIP_DEFLATED) as z:
    for nm, body in ICONS.items():
        z.writestr("icons/%s.svg" % nm, '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="48" height="48">%s</svg>' % body)
    z.writestr("icons/README.txt", "제안서 그림에 쓰는 선형 아이콘 모음 (수주로 시연용). 선 두께 1.7, 색 #16419E\n")
svg = ('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1123 794" width="1123" height="794">'
       '<rect width="1123" height="794" fill="#16419E"/><rect x="0" y="560" width="1123" height="234" fill="#1D4B7E"/>'
       '<circle cx="980" cy="120" r="260" fill="none" stroke="#ffffff" stroke-opacity=".25" stroke-width="2"/>'
       '<text x="80" y="300" font-family="Noto Sans KR, sans-serif" font-size="64" font-weight="700" fill="#ffffff">제안서 표지 템플릿</text>'
       '<text x="80" y="370" font-family="Noto Sans KR, sans-serif" font-size="28" fill="#C9D6EA">사업명을 여기에 입력</text>'
       '<text x="80" y="700" font-family="Noto Sans KR, sans-serif" font-size="36" font-weight="700" fill="#ffffff">오티아이</text></svg>')
with open(sub("refs", "etc", "제안서_표지템플릿.svg"), "w", encoding="utf-8") as fh:
    fh.write(svg)

# ---------------------------------------------------------------- 시연 업로드용 (서버에 미리 넣지 않음)
deck(sub("demo-upload", "시연_제안서_사업이해.pptx"), "Ⅱ. 사업 이해", [
    ("사업 추진 배경", ["대국민 서비스 확대 요구", "노후 시스템 전환 시기 도래", "기관 간 연계 필요"]),
    ("현황 분석", ["처리 단계별 수작업 존재", "중복 입력", "모바일 미지원"])])
deck(sub("demo-upload", "시연_제안서_수행방안.pptx"), "Ⅲ. 사업 수행 방안", [
    ("추진 전략", ["단계적 전환", "공통 기능 우선", "사용자 참여 검증"]),
    ("시스템 구성", ["이중화 구성", "통합 인증 연계", "환경 분리"])])
proposal_doc(sub("demo-upload", "시연_제안서_통합본.pdf"), "통합본 (시연용)", sections=SECTIONS[:3])

shutil.rmtree(TMP)
for root, _, files in sorted(os.walk(OUT)):
    for fn in sorted(files):
        p = os.path.join(root, fn)
        print("%8.1f KB  %s" % (os.path.getsize(p) / 1024, p))
