package com.ojj.sujuro.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 스케줄러(@Scheduled)와 비동기(@Async)를 켜는 공통 설정.
 * 공고 수집(FR-025, NFR-015), 마감 임박 알림·메일(FR-031) 등이 함께 사용한다.
 * 다른 곳에서 @EnableScheduling, @EnableAsync를 또 붙이지 않는다.
 */
@Configuration
@EnableScheduling
@EnableAsync
public class AsyncSchedulingConfig {
}
