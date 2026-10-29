/**
 * 목차·담당자·요구사항·제안서 파일·제출서류·조견표·제출·질의응답 (FR-043~076)
 * 담당: 이세종
 *
 * 패키지 구성 (팀 공통 구조)
 *   web          — 컨트롤러. URL별로 나눈다.
 *                  ProposalController(/proposal, /proposal/toc/**, /proposal/limit)
 *                  ProposalTeamController(/proposal/team/**) · RqtController(/proposal/rqt/**)
 *                  PrpFileController(/files/**) · DocController(/docs/**)
 *   service      — 서비스 인터페이스 / service.impl — 구현
 *   mapper       — MyBatis 매퍼 인터페이스. SQL은 src/main/resources/mapper/proposal/*.xml
 *   dto          — 화면·매퍼에서 쓰는 데이터 클래스
 * 파일 저장(FileStorage)은 다른 기능에서도 쓰도록 com.ojj.sujuro.common.file 에 둔다.
 */
package com.ojj.sujuro.proposal;
