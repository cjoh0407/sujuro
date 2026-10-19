package com.ojj.sujuro.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import com.ojj.sujuro.auth.InitialPasswordInterceptor;

/** 웹 MVC 설정. 초기 비밀번호 사용자를 비밀번호 변경 화면으로 보내는 인터셉터를 등록한다. (FR-002) */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final InitialPasswordInterceptor initialPasswordInterceptor;

    public WebMvcConfig(InitialPasswordInterceptor initialPasswordInterceptor) {
        this.initialPasswordInterceptor = initialPasswordInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(initialPasswordInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/css/**", "/js/**", "/lib/**", "/images/**", "/favicon.ico",
                        "/login", "/logout", "/error", "/error/**",
                        InitialPasswordInterceptor.CHANGE_URL);
    }
}
