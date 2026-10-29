/**
 * 공고 조회·공고 등록·공고 상세·담당 팀 배정·참여 결정·제안팀 (FR-017~045)
 * 담당: 김종민
 *
 * 계층별로 나눈다. (README "업무 패키지 안은 아래처럼 나눕니다")
 *
 *   web           화면 요청을 받는 컨트롤러
 *   dto           화면·DB 사이를 오가는 값 묶음. 필드 이름은 테이블 컬럼명을 따른다
 *   mapper        MyBatis 매퍼 인터페이스
 *   service       업무 규칙 인터페이스
 *   service.impl  그 구현
 *   api           나라장터(조달청) OpenAPI 호출
 *   code          코드값·상수 (BidCode)
 *
 * 이 패키지 바로 아래에는 package-info.java만 둔다.
 * SQL은 src/main/resources/mapper/bid/ 아래 XML에 둔다.
 */
package com.ojj.sujuro.bid;
