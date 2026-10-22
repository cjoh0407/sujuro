package com.ojj.sujuro.bid;

import java.net.URI;
import java.time.Duration;

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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final String serviceKey;
    private final String baseUrl;
    private final String preSpecPath;
    private final String bidPath;
    private final String preSpecInqryDiv;
    private final String bidInqryDiv;

    public G2bApiClient(RestTemplateBuilder builder,
                        @Value("${g2b.service-key}") String serviceKey,
                        @Value("${g2b.base-url}") String baseUrl,
                        @Value("${g2b.path.pre-spec}") String preSpecPath,
                        @Value("${g2b.path.bid}") String bidPath,
                        @Value("${g2b.inqry-div.pre-spec}") String preSpecInqryDiv,
                        @Value("${g2b.inqry-div.bid}") String bidInqryDiv,
                        @Value("${g2b.connect-timeout}") Duration connectTimeout,
                        @Value("${g2b.read-timeout}") Duration readTimeout) {
        this.restTemplate = builder
                .setConnectTimeout(connectTimeout)
                .setReadTimeout(readTimeout)
                .build();
        this.serviceKey = serviceKey;
        this.baseUrl = baseUrl;
        this.preSpecPath = preSpecPath;
        this.bidPath = bidPath;
        this.preSpecInqryDiv = preSpecInqryDiv;
        this.bidInqryDiv = bidInqryDiv;
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

        return new G2bResponse(root.path("response").path("body").path("items"), body);
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
