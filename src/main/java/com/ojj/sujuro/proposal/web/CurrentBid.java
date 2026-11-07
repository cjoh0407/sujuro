package com.ojj.sujuro.proposal.web;

import java.util.List;

import javax.servlet.http.HttpSession;

import com.ojj.sujuro.proposal.dto.ProposalBidDto;

/**
 * 제안 화면(스토리보드 관리·제안서 관리·제출서류·질의응답)이 같이 쓰는 "지금 보고 있는 사업건".
 * 주소에 bid 가 있으면 그 사업건을 세션에 기억하고, 없으면 기억해 둔 사업건(내 사업건 목록에 있을 때만)을 연다.
 * 기억한 것이 없으면 진행 중인 첫 사업건 (ProposalBidDto.defaultPbancSn).
 */
final class CurrentBid {

    static final String SESSION_KEY = "proposalCurrentBid";

    private CurrentBid() {
    }

    static Long pick(List<ProposalBidDto> bids, Long bid, HttpSession session) {
        if (bid != null) {
            session.setAttribute(SESSION_KEY, bid);
            return bid;
        }
        Object saved = session.getAttribute(SESSION_KEY);
        if (saved instanceof Long) {
            for (ProposalBidDto b : bids) {
                if (saved.equals(b.getPbancSn())) {
                    return (Long) saved;
                }
            }
        }
        return ProposalBidDto.defaultPbancSn(bids);
    }
}
