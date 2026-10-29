package com.ojj.sujuro.bid.api;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ojj.sujuro.common.exception.BusinessException;

/**
 * 나라장터(조달청) OpenAPI 호출 (FR-021, 022, 025).
 *
 * 주의할 점 두 가지
 * 1) 인증키는 공공데이터포털이 이미 URL 인코딩해서 준 문자열이다(%2B, %3D 포함).
 *    UriComponentsBuilder 로 쿼리 파라미터를 붙이면 % 가 %25 로 또 인코딩돼 인증이 깨진다.
 *    그래서 주소를 문자열로 직접 만들고 URI.create 로 넘긴다.
 * 2) 조회구분(inqryDiv)은 서비스마다 값의 뜻이 다르다. 번호 조회가 사전규격·입찰공고는 2,
 *    낙찰정보는 4다. application.properties 에 서비스별로 적어 두었다.
 */
@Component
public class G2bApiClient {

    private static final Logger log = LoggerFactory.getLogger(G2bApiClient.class);

    /** 정상 응답 코드 */
    private static final String RESULT_OK = "00";

    /** Content-Disposition 의 filename= 값 (따옴표·filename*=UTF-8'' 형식도 받는다) */
    private static final Pattern FILE_NAME =
            Pattern.compile("filename\\*?=(?:UTF-8'')?\"?([^\";]+)\"?", Pattern.CASE_INSENSITIVE);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 첨부 헤더 읽기용. RestTemplate 은 응답을 닫을 때 본문을 끝까지 읽어 버려서 따로 둔다. */
    private final HttpClient fileClient;
    private final Duration readTimeout;

    private final String serviceKey;
    private final String baseUrl;
    private final String preSpecPath;
    private final String bidPath;
    private final String preSpecInqryDiv;
    private final String bidInqryDiv;
    private final String awardPath;
    private final String awardInqryDiv;
    private final String contractProcessPath;
    private final String orderPlanPath;

    public G2bApiClient(RestTemplateBuilder builder,
                        @Value("${g2b.service-key}") String serviceKey,
                        @Value("${g2b.base-url}") String baseUrl,
                        @Value("${g2b.path.pre-spec}") String preSpecPath,
                        @Value("${g2b.path.bid}") String bidPath,
                        @Value("${g2b.inqry-div.pre-spec}") String preSpecInqryDiv,
                        @Value("${g2b.inqry-div.bid}") String bidInqryDiv,
                        @Value("${g2b.path.award}") String awardPath,
                        @Value("${g2b.inqry-div.award}") String awardInqryDiv,
                        @Value("${g2b.path.contract-process}") String contractProcessPath,
                        @Value("${g2b.path.order-plan}") String orderPlanPath,
                        @Value("${g2b.connect-timeout}") Duration connectTimeout,
                        @Value("${g2b.read-timeout}") Duration readTimeout) {
        this.restTemplate = builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
        this.fileClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        this.readTimeout = readTimeout;
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.preSpecPath = preSpecPath;
        this.bidPath = bidPath;
        this.preSpecInqryDiv = preSpecInqryDiv;
        this.bidInqryDiv = bidInqryDiv;
        this.awardPath = awardPath;
        this.awardInqryDiv = awardInqryDiv;
        this.contractProcessPath = contractProcessPath;
        this.orderPlanPath = orderPlanPath;
    }

    /**
     * 사전규격등록번호로 사전규격 정보를 가져온다. (용역)
     * 찾지 못하면 item 이 비어 있는 결과를 돌려준다.
     */
    public G2bResponse findPreSpec(String bfSpecRgstNo) {
        String url = baseUrl + preSpecPath + "/getPublicPrcureThngInfoServc"
                + "?inqryDiv=" + preSpecInqryDiv
                + "&bfSpecRgstNo=" + bfSpecRgstNo
                + "&numOfRows=10&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "사전규격");
    }

    /**
     * 입찰공고번호로 입찰공고 정보를 가져온다. (용역)
     * 한 공고에 차수가 여러 개면 차수별로 여러 건이 온다. 현재값은 가장 큰 차수를 쓴다.
     * ({@link G2bResponse#latestOrd()})
     *
     * 아직 부르는 곳이 없다. 입찰공고번호로 바로 등록하는 경로와 본공고 연결·정정 확인(수집)에서 쓴다.
     */
    public G2bResponse findBidNotice(String bidNtceNo) {
        String url = baseUrl + bidPath + "/getBidPblancListInfoServc"
                + "?inqryDiv=" + bidInqryDiv
                + "&bidNtceNo=" + bidNtceNo
                + "&numOfRows=50&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "입찰공고");
    }

    /** 입찰공고 목록 조회구분: 등록일시 기간 */
    public static final String BID_BY_REG_DT = "1";

    /** 입찰공고 목록 조회구분: 변경일시 기간 */
    public static final String BID_BY_CHG_DT = "3";

    /**
     * 기간 안에 등록(또는 변경)된 입찰공고 목록 (용역). 정정·취소 확인에 쓴다.
     *
     * 정정·취소는 같은 번호에 새 차수로 '등록'되므로 등록일시 목록에 섞여 온다(실측 300건 중 변경공고 10·취소 16).
     * 변경일시(조회구분 3)는 차수를 올리지 않고 값만 고친 공고라 하루 몇 건뿐이다(10/27 2건).
     * 공고별로 번호를 묻지 않고 목록으로 받으면, 우리가 등록한 공고가 몇 건이든 호출 수가 같다.
     *
     * @param inqryDiv {@link #BID_BY_REG_DT} 또는 {@link #BID_BY_CHG_DT}
     */
    public G2bResponse findBidNoticesBetween(String inqryDiv, String inqryBgnDt, String inqryEndDt,
                                             int numOfRows, int pageNo) {
        String url = baseUrl + bidPath + "/getBidPblancListInfoServc"
                + "?inqryDiv=" + inqryDiv
                + "&inqryBgnDt=" + inqryBgnDt
                + "&inqryEndDt=" + inqryEndDt
                + "&numOfRows=" + numOfRows + "&pageNo=" + pageNo + "&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "입찰공고 목록");
    }

    /**
     * 입찰공고의 e발주 첨부파일 목록 (getBidPblancListInfoEorderAtchFileInfo, 조회구분 2 = 공고번호).
     *
     * 조달청 위탁 공고(공고기관이 '조달청 ○○지방조달청')는 입찰공고 응답의 첨부 칸(ntceSpecFileNm1~10)에
     * 공고서만 있고 제안요청서·협약서 같은 나머지 파일은 여기에만 있다. 기관이 직접 낸 공고는 0 건이다.
     * 응답의 eorderDocDivNm 이 '제안요청서'·'기타문서' 같은 문서구분이다. 파일명이 비어 오는 행도 있다.
     * (2026-10-28 실호출: R26BK01771602·01771039·01757104 세 건 모두 제안요청서가 여기에만 있었다)
     */
    public G2bResponse findEorderAttachments(String bidNtceNo, String bidNtceOrd) {
        String url = baseUrl + bidPath + "/getBidPblancListInfoEorderAtchFileInfo"
                + "?inqryDiv=2"
                + "&bidNtceNo=" + bidNtceNo
                + "&bidNtceOrd=" + bidNtceOrd
                + "&numOfRows=50&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "e발주 첨부파일");
    }

    /**
     * 나라장터 첨부 내려받기 주소에서 파일명과 크기만 읽는다. 사전규격 API 는 파일명을 주지 않고,
     * 어느 API 도 크기를 주지 않아서 내려받기 응답 헤더(Content-Disposition, Content-Length)로 얻는다.
     *
     * HEAD 요청은 403 으로 막혀 있고 Range 요청은 무시돼 파일 전체가 온다(2026-10-28 확인).
     * 그래서 GET 을 열어 헤더만 읽고 본문은 읽지 않은 채 닫는다.
     *
     * @return 못 읽으면 null. 첨부 저장은 이 값 없이도 진행한다
     */
    public FileHeader peekFile(String fileUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl)).timeout(readTimeout).GET().build();
            HttpResponse<InputStream> response = fileClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (InputStream body = response.body()) {
                if (response.statusCode() != 200) {
                    return null;
                }
                String name = fileNameOf(response.headers().firstValue("Content-Disposition").orElse(null));
                long size = response.headers().firstValueAsLong("Content-Length").orElse(-1);
                return new FileHeader(name, size < 0 ? null : size);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (IOException | RuntimeException e) {
            log.warn("나라장터 첨부 헤더를 읽지 못했습니다: {} ({})", fileUrl, e.toString());
            return null;
        }
    }

    /**
     * Content-Disposition 의 파일명. 나라장터는 UTF-8 퍼센트 인코딩으로 준다.
     *   attachment;filename=%5B%EC%A0%9C%EC%95%88%EC%9A%94%EC%B2%AD%EC%84%9C%5D%20….hwpx;
     * 공백은 %20 으로 오므로 '+' 는 글자 그대로 둔다.
     */
    static String fileNameOf(String disposition) {
        if (disposition == null) {
            return null;
        }
        Matcher m = FILE_NAME.matcher(disposition);
        if (!m.find()) {
            return null;
        }
        String raw = m.group(1).trim();
        try {
            String name = URLDecoder.decode(raw.replace("+", "%2B"), StandardCharsets.UTF_8).trim();
            return name.isEmpty() ? null : name;
        } catch (IllegalArgumentException e) {
            return raw;
        }
    }

    /** 내려받기 응답 헤더에서 읽은 파일명·크기. 둘 다 없을 수 있다. */
    public static final class FileHeader {
        private final String fileNm;
        private final Long fileSz;

        FileHeader(String fileNm, Long fileSz) {
            this.fileNm = fileNm;
            this.fileSz = fileSz;
        }

        public String getFileNm() {
            return fileNm;
        }

        public Long getFileSz() {
            return fileSz;
        }
    }

    /**
     * 발주계획번호로 발주계획을 가져온다. (용역)
     * 조회구분 2 가 번호 조회다. 파라미터 이름은 orderPlanNo 가 아니라 orderPlanUntyNo(통합번호)다.
     */
    public G2bResponse findOrderPlan(String orderPlanUntyNo) {
        String url = baseUrl + orderPlanPath + "/getOrderPlanSttusListServc"
                + "?inqryDiv=2"
                + "&orderPlanUntyNo=" + orderPlanUntyNo
                + "&numOfRows=10&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "발주계획");
    }

    /**
     * 기간 안에 등록된 사전규격 목록을 가져온다. (용역)
     * 발주계획과 사전규격을 잇는 번호가 없어서, 매일 이 목록을 받아 추적 중인 발주계획과
     * 값으로 맞춰 본다. ({@link com.ojj.sujuro.bid.code.BidCode#sameBizCase})
     *
     * 날짜는 12자리(yyyyMMddHHmm)이고 파라미터 이름은 inqryBgnDt·inqryEndDt 다.
     * 발주계획 서비스만 8자리 inqryBgnDate 를 쓰므로 섞지 않도록 주의한다.
     */
    public G2bResponse findPreSpecsRegistered(String inqryBgnDt, String inqryEndDt, int numOfRows, int pageNo) {
        String url = baseUrl + preSpecPath + "/getPublicPrcureThngInfoServc"
                + "?inqryDiv=1"
                + "&inqryBgnDt=" + inqryBgnDt
                + "&inqryEndDt=" + inqryEndDt
                + "&numOfRows=" + numOfRows + "&pageNo=" + pageNo + "&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "사전규격 목록");
    }

    /**
     * 계약과정통합공개. 어떤 번호로 묻든 한 행에 발주계획번호·사전규격번호·입찰공고번호와
     * 낙찰자목록·계약정보목록까지 같이 온다. (용역)
     *
     * 주의: 입찰공고가 나기 전에는 행이 아예 없다(totalCount 0). 그래서 발주계획이나 사전규격만
     * 있는 시점의 내용은 이 서비스로 받을 수 없고, 각 단계의 서비스를 따로 불러야 한다.
     * 거꾸로 "공고가 났는지" 를 묻는 데는 0 이냐 1 이냐가 그대로 신호가 된다.
     * (2026-10-26 실제 호출로 확인)
     */
    public G2bResponse findProcessByBidNo(String bidNtceNo) {
        return findProcess("1", "bidNtceNo", bidNtceNo);
    }

    /** 사전규격번호로 묻는다. 결과가 0 건이면 아직 입찰공고가 나지 않은 것이다. */
    public G2bResponse findProcessByPreSpecNo(String bfSpecRgstNo) {
        return findProcess("2", "bfSpecRgstNo", bfSpecRgstNo);
    }

    /** 발주계획번호로 묻는다. 사전규격을 건너뛰고 바로 입찰공고가 난 경우를 잡는 데 쓴다(실측 14%). */
    public G2bResponse findProcessByOrderNo(String orderPlanNo) {
        return findProcess("3", "orderPlanNo", orderPlanNo);
    }

    private G2bResponse findProcess(String inqryDiv, String noParam, String no) {
        String url = baseUrl + contractProcessPath + "/getCntrctProcssIntgOpenServc"
                + "?inqryDiv=" + inqryDiv
                + "&" + noParam + "=" + no
                + "&numOfRows=50&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "계약과정통합공개");
    }

    /**
     * 입찰공고번호로 개찰 결과를 가져온다. (용역)
     * 통합공개의 낙찰자목록만 보면 유찰 건을 놓친다. 낙찰자가 없어서 목록이 비기 때문에
     * "아직 개찰 안 함" 과 구별되지 않는다. 이 서비스의 progrsDivCdNm 으로 낙찰·유찰을 가린다.
     * (R26BK01553009 = 개찰했지만 "단독 응찰로 경쟁입찰 불성립" 유찰. 2026-10-26 확인)
     */
    public G2bResponse findOpeningResult(String bidNtceNo) {
        String url = baseUrl + awardPath + "/getOpengResultListInfoServc"
                + "?inqryDiv=" + awardInqryDiv
                + "&bidNtceNo=" + bidNtceNo
                + "&numOfRows=10&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "개찰결과");
    }

    /**
     * 입찰공고번호로 개찰완료 목록을 가져온다. 참가 업체마다 한 행이고 개찰순위·사업자번호·투찰금액이 온다.
     * 협상에 의한 계약이면 기술평가점수(techEvlVal)·가격점수(bidPrceEvlVal)·종합점수(totalEvlAmtVal)도 온다.
     * 점수 칸은 2025-09 에 추가됐다. (R26BK01541118 로 2026-10-29 확인)
     */
    public G2bResponse findOpeningCompanies(String bidNtceNo) {
        String url = baseUrl + awardPath + "/getOpengResultListInfoOpengCompt"
                + "?bidNtceNo=" + bidNtceNo
                + "&numOfRows=100&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "개찰 업체 목록");
    }

    /**
     * 입찰공고번호로 최종 낙찰자를 가져온다. (용역) 낙찰자가 정해지기 전에는 행이 없다.
     * 협상에 의한 계약은 개찰 몇 주 뒤에 올라온다. (R26BK01541118: 개찰 06-23, 최종 낙찰 07-15)
     */
    public G2bResponse findAward(String bidNtceNo) {
        String url = baseUrl + awardPath + "/getScsbidListSttusServc"
                + "?inqryDiv=" + awardInqryDiv
                + "&bidNtceNo=" + bidNtceNo
                + "&numOfRows=10&pageNo=1&type=json"
                + "&ServiceKey=" + serviceKey;
        return call(url, "낙찰자");
    }

    private G2bResponse call(String url, String what) {
        String body;
        try {
            ResponseEntity<String> res = restTemplate.getForEntity(URI.create(url), String.class);
            body = res.getBody();
        } catch (Exception e) {
            // 어떤 주소를 불렀는지 로그에 남기되 인증키는 지운다.
            log.warn("나라장터 {} 조회 실패: {}", what, maskKey(url), e);
            throw new BusinessException(
                    "나라장터에서 지금 " + what + " 정보를 불러오지 못했습니다. 잠시 후 다시 시도하세요.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body == null ? "" : body);
        } catch (Exception e) {
            log.warn("나라장터 {} 응답을 읽지 못했습니다: {}", what, cut(body), e);
            throw new BusinessException("나라장터 응답을 읽지 못했습니다. 잠시 후 다시 시도하세요.");
        }

        JsonNode header = root.path("response").path("header");
        String code = header.path("resultCode").asText("");
        if (!RESULT_OK.equals(code)) {
            String msg = header.path("resultMsg").asText("알 수 없는 오류");
            log.warn("나라장터 {} 조회 오류 resultCode={} resultMsg={}", what, code, msg);
            throw new BusinessException("나라장터 " + what + " 조회가 거부되었습니다. (" + msg + ")");
        }

        JsonNode resBody = root.path("response").path("body");
        return new G2bResponse(resBody.path("items"), body, resBody.path("totalCount").asInt(0));
    }

    /** 로그에 인증키가 남지 않게 지운다. */
    private static String maskKey(String url) {
        int i = url.indexOf("ServiceKey=");
        return i < 0 ? url : url.substring(0, i) + "ServiceKey=***";
    }

    private static String cut(String s) {
        if (s == null) {
            return "(응답 없음)";
        }
        return s.length() <= 300 ? s : s.substring(0, 300) + "...";
    }
}
