# 컬럼명 표준 확인 목록

`db/sujuro_테이블정의서_표준용어.xlsx` 의 '사용 표준단어' 시트에 **없는 단어**를 쓰고 있는 컬럼
목록입니다. 기능 개발을 막지 않으려고 일단 이 이름으로 만들어 두고, 여기에 모아 둡니다.

엑셀의 '작성 기준' 시트 규칙은 이렇습니다.

> 1. 공통표준용어에 같은 이름이 있으면 그 용어의 영문약어와 도메인을 그대로 쓴다.
> 2. 없으면 공통표준단어를 조합해 새 용어를 만든다. 영문약어는 단어 약어를 `_` 로 잇는다.
> 3. 마지막 단어는 형식단어(명·코드·일시·여부·번호·내용·수 등)로 끝내고 도메인을 고른다.

아래 토큰들은 2번을 거치지 않고 제가 임의로 줄여 쓴 것입니다. **엑셀에 단어를 등록하거나**
(등록 시 '사용 표준단어'·'사용 표준용어'·'용어사전(단어 조합)' 세 시트를 함께 갱신),
**이미 있는 단어로 다시 지어야** 합니다. 어느 쪽으로 갈지는 아직 정하지 않았습니다.

마지막 점검: 2026-10-26 · 전체 컬럼 218개 중 **192개 통과 / 26개 확인 필요**

## 확인이 필요한 컬럼

| 테이블 | 컬럼 | 뜻 | 미등록 토큰 |
| --- | --- | --- | --- |
| TB_BID_PBANC | `ORDER_PLAN_NO` | 발주계획번호 | `PLAN` |
| TB_BID_PBANC | `BF_SPEC_RGST_NO` | 사전규격등록번호 | `BF` `SPEC` `RGST` |
| TB_BID_PBANC | `PBANC_STEP_CD` | 공고단계코드 | `STEP` |
| TB_BID_PBANC | `TRCK_STTS_CD` | 추적상태코드 | `TRCK` |
| TB_BID_PBANC | `DMND_INST_NM` | 수요기관명 | `DMND` |
| TB_BID_PBANC | `SW_BIZ_YN` | 소프트웨어사업여부 | `SW` |
| TB_BID_PBANC | `OPNN_DDLN_DT` | 의견마감일시 | `OPNN` |
| TB_BID_PBANC | `OPENG_DT` | 개찰일시 | `OPENG` |
| TB_BID_PBANC | `PBANC_DTL_URL` | 공고상세주소 | `URL` |
| TB_BID_PBANC | `RTRN_SE_CD` | 반환구분코드 | `RTRN` |
| TB_PBANC_CLCT_HSTRY | `PBANC_STEP_CD` | 공고단계코드 | `STEP` |
| TB_PBANC_CLCT_HSTRY | `OPENG_DT` | 개찰일시 | `OPENG` |
| TB_PBANC_CLCT_HSTRY | `API_RSPNS_CN` | API응답내용 | `API` `RSPNS` |
| TB_CLCT_LOG | `CLCT_LOG_SN` | 수집로그일련번호 | `LOG` |
| TB_CLCT_LOG | `EXCN_SE_CD` | 실행구분코드 | `EXCN` |
| TB_CLCT_LOG | `EXCTR_ID` | 실행자아이디 | `EXCTR` |
| TB_CLCT_LOG | `ERR_MSG_CN` | 오류메시지내용 | `ERR` `MSG` |
| TB_CLCT_LOG | `EXCN_DT` | 실행일시 | `EXCN` |
| TB_CO_EVL_RSLT | `OPENG_RNK` | 개찰순위 | `OPENG` |
| TB_CO_EVL_RSLT | `OUR_CO_YN` | 자사여부 | `OUR` |
| TB_CO_EVL_RSLT | `EVL_TBL_UPLD_YN` | 평가표업로드여부 | `TBL` `UPLD` |
| TB_ATCH_FILE | `ORGNL_URL` | 원본주소 | `URL` |
| TB_EVL_TBL_UPLD | `EVL_TBL_UPLD_SN` | 평가표업로드일련번호 | `TBL` `UPLD` |
| TB_EVL_TBL_UPLD | `CMSNR_CNT` | 평가위원수 | `CMSNR` |
| TB_EVL_CMSNR_SCR | `CMSNR_SCR_SN` | 평가위원점수일련번호 | `CMSNR` |
| TB_EVL_CMSNR_SCR | `CMSNR_NO` | 평가위원번호 | `CMSNR` |

### 미등록 단어 23개와 뜻

'엑셀에 있는 비슷한 단어' 는 **대안 후보**일 뿐입니다. 뜻이 어긋나는 것이 많아서, 그 단어로
바꿀지 아니면 새 단어를 등록할지는 따로 판단해야 합니다.

| 약어 | 뜻 | 영문 | 쓰는 컬럼 | 엑셀에 있는 비슷한 단어 |
| --- | --- | --- | --- | --- |
| `RGST` | 등록 | Registration | `BF_SPEC_RGST_NO` | **등록 = `REG`. 이미 있으므로 바꿔야 한다** |
| `PLAN` | 계획 | Plan | `ORDER_PLAN_NO` | — |
| `API` | API | API | `API_RSPNS_CN` | — |
| `BF` | 사전 | Before | `BF_SPEC_RGST_NO` | — |
| `CMSNR` | 평가위원 | Commissioner | `CMSNR_CNT` `CMSNR_SCR_SN` `CMSNR_NO` | 담당자 `PIC`, 책임자 `RBPRSN` (뜻 다름) |
| `DMND` | 수요 | Demand | `DMND_INST_NM` | 요구 `RQT` (뜻 다름) |
| `ERR` | 오류 | Error | `ERR_MSG_CN` | 원인 `CS`, 사유 `RSN` (뜻 다름) |
| `EXCN` | 실행 | Execution | `EXCN_SE_CD` `EXCN_DT` | 수행 `FLFMT`, 처리 `PRCS` |
| `EXCTR` | 실행자 | Executor | `EXCTR_ID` | 담당자 `PIC`, 처리자 `PRCR` |
| `LOG` | 로그 | Log | `CLCT_LOG_SN` | 이력 `HSTRY` |
| `MSG` | 메시지 | Message | `ERR_MSG_CN` | 내용 `CN` |
| `OPENG` | 개찰 | Opening | `OPENG_DT`(2곳) `OPENG_RNK` | — (낙찰 `SCSBD` 와 다른 단계다) |
| `OPNN` | 의견 | Opinion | `OPNN_DDLN_DT` | — |
| `OUR` | 자사 | Our | `OUR_CO_YN` | — |
| `RSPNS` | 응답 | Response | `API_RSPNS_CN` | 답변 `ANS` (뜻 다름) |
| `RTRN` | 반환 | Return | `RTRN_SE_CD` | — |
| `SPEC` | 규격 | Specification | `BF_SPEC_RGST_NO` | — |
| `STEP` | 단계 | Step | `PBANC_STEP_CD`(2곳) | 순서 `SEQ`, 순위 `RNK` (뜻 다름) |
| `SW` | 소프트웨어 | Software | `SW_BIZ_YN` | — |
| `TBL` | 표 | Table | `EVL_TBL_UPLD_SN` `EVL_TBL_UPLD_YN` | — |
| `TRCK` | 추적 | Tracking | `TRCK_STTS_CD` | — |
| `UPLD` | 업로드 | Upload | `EVL_TBL_UPLD_SN` `EVL_TBL_UPLD_YN` | — |
| `URL` | URL | URL | `PBANC_DTL_URL` `ORGNL_URL` | 주소 `ADDR` |

**먼저 볼 것 두 개**

- `RGST` 는 엑셀에 **등록 = `REG`** 가 이미 있으므로 잘못 지은 이름입니다. 다만 같은 컬럼의
  `BF`·`SPEC` 도 미등록이라 `BF_SPEC_RGST_NO` 는 컬럼 전체를 다시 지어야 합니다.
  Java 쪽 `bfSpecRgstNo` 가 DTO·매퍼·API 클라이언트·서비스에 퍼져 있어 한꺼번에 바꿔야 합니다.
- `OUR`(자사)는 한글 단어를 줄인 것이 아니라 영문 단어를 그대로 쓴 유일한 경우입니다.
  엑셀에 자사·우리·본사·내부·자체가 모두 없어서 대신 쓸 단어가 없었습니다.


## 엑셀 자체도 갱신이 필요합니다

엑셀은 2026-10-20 자이고 **테이블 16개 · 컬럼 161개** 기준입니다. 지금 스키마는
**테이블 21개 · 컬럼 217개** 라서, 나라장터 API 작업으로 늘어난 5개 테이블과
`TB_BID_PBANC` 에 더한 컬럼들이 들어 있지 않습니다. 공통코드도 `PBSTP`·`PBTYP`·`TRSTS`·
`EXCSE`·`CLRST`·`RTRSE` 가 빠져 있습니다.
