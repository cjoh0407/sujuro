package com.ojj.sujuro.bid.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.web.client.RestTemplateBuilder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.ojj.sujuro.common.exception.BusinessException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 나라장터 호출 재시도 (11/04). 실제 소켓으로 가짜 서버를 불러서 시간 초과도 진짜로 일어난다.
 * 일시적인 실패만 다시 부르고, 다시 불러도 같은 실패는 바로 끝낸다.
 */
@ExtendWith(OutputCaptureExtension.class)
class G2bApiClientRetryTest {

    private static final String KEY = "TEST-SECRET-KEY";
    private static final String BID_NO = "R26BK99990099";
    private static final String OK_BODY = "{\"response\":{\"header\":{\"resultCode\":\"00\",\"resultMsg\":\"정상\"},"
            + "\"body\":{\"items\":[{\"bidNtceNo\":\"" + BID_NO + "\"}],\"totalCount\":1}}}";
    private static final String REJECTED_BODY =
            "{\"response\":{\"header\":{\"resultCode\":\"22\",\"resultMsg\":\"호출 한도 초과\"}}}";

    /** 읽기 제한보다 오래 잡고 있어 시간 초과를 일으키는 응답 */
    private static final int HANG = -1;
    private static final Duration READ_TIMEOUT = Duration.ofMillis(200);

    private HttpServer server;
    private ExecutorService pool;
    private final AtomicInteger calls = new AtomicInteger();

    /** n번째 호출에 줄 HTTP 상태. 200 이면 정상 본문을 준다. 다 쓰면 마지막 값을 계속 쓴다. */
    private volatile List<Integer> script = List.of(200);
    private volatile String okBody = OK_BODY;

    /**
     * RestTemplate 은 DEBUG 로 전체 주소(인증키 포함)를 남긴다. 앱은 기본 INFO 라 남지 않는데,
     * 스프링 없이 도는 이 테스트는 기본이 DEBUG 라서 앱과 같게 맞춘다.
     * RestTemplate 로거만 내리면 org.springframework.web.HttpLogging 으로 대신 남아서 위에서 내린다.
     */
    private final Logger webLog = (Logger) LoggerFactory.getLogger("org.springframework.web");

    @BeforeEach
    void start() throws IOException {
        webLog.setLevel(Level.INFO);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        // 시간 초과로 끊긴 앞 요청이 잡고 있어도 다음 요청을 받도록 스레드를 따로 쓴다
        pool = Executors.newCachedThreadPool();
        server.setExecutor(pool);
        server.createContext("/", this::answer);
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
        pool.shutdownNow();
        webLog.setLevel(null);
    }

    private void answer(HttpExchange exchange) throws IOException {
        int n = calls.incrementAndGet();
        int status = script.get(Math.min(n, script.size()) - 1);
        try {
            if (status == HANG) {
                try {
                    Thread.sleep(READ_TIMEOUT.toMillis() * 3);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return;
            }
            byte[] body = (status == 200 ? okBody : "{}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
        } finally {
            exchange.close();
        }
    }

    private G2bApiClient client(Duration retryWait) {
        String baseUrl = "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort();
        return new G2bApiClient(new RestTemplateBuilder(), KEY, baseUrl, "/pre", "/bid", "2", "2", "/award", "4",
                "/contract", "/order", Duration.ofSeconds(1), READ_TIMEOUT, 3, retryWait);
    }

    private String callBid() {
        return G2bResponse.textOf(client(Duration.ZERO).findBidNotice(BID_NO).first(), "bidNtceNo");
    }

    @Test
    void 시간_초과면_다시_불러_성공한다() {
        script = List.of(HANG, 200);

        assertEquals(BID_NO, callBid());
        assertEquals(2, calls.get());
    }

    @Test
    void 나라장터_오류_5xx_면_다시_불러_성공한다() {
        script = List.of(503, 500, 200);

        assertEquals(BID_NO, callBid());
        assertEquals(3, calls.get());
    }

    @Test
    void 호출_제한_429_면_다시_불러_성공한다() {
        script = List.of(429, 200);

        assertEquals(BID_NO, callBid());
        assertEquals(2, calls.get());
    }

    @Test
    void 세_번_모두_시간_초과면_같은_문구로_실패하고_로그에_인증키가_없다(CapturedOutput output) {
        script = List.of(HANG);

        BusinessException e = assertThrows(BusinessException.class, this::callBid);

        assertEquals("나라장터에서 지금 입찰공고 정보를 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.", e.getMessage());
        assertEquals(3, calls.get());
        assertTrue(output.getAll().contains("입찰공고 조회 실패"), "실패 로그가 없습니다");
        assertTrue(output.getAll().contains("ServiceKey=***"), "지운 주소가 로그에 없습니다");
        assertFalse(output.getAll().contains(KEY), "로그에 인증키가 있습니다");
    }

    @Test
    void 인증_오류_401_은_다시_부르지_않는다() {
        script = List.of(401, 200);

        assertThrows(BusinessException.class, this::callBid);
        assertEquals(1, calls.get());
    }

    @Test
    void 나라장터가_거부한_응답은_다시_부르지_않는다() {
        okBody = REJECTED_BODY;

        BusinessException e = assertThrows(BusinessException.class, this::callBid);

        assertTrue(e.getMessage().contains("호출 한도 초과"), e.getMessage());
        assertEquals(1, calls.get());
    }

    @Test
    void 기다리는_중에_서버가_내려가면_더_부르지_않는다() {
        script = List.of(503);
        G2bApiClient client = client(Duration.ofMinutes(5));

        Thread.currentThread().interrupt();
        try {
            assertThrows(BusinessException.class, () -> client.findBidNotice(BID_NO));
            assertTrue(Thread.currentThread().isInterrupted(), "인터럽트 표시가 지워졌습니다");
        } finally {
            Thread.interrupted();
        }
        assertEquals(1, calls.get());
    }
}
