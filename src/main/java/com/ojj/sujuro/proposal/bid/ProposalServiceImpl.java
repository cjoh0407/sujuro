package com.ojj.sujuro.proposal.bid;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ojj.sujuro.common.exception.BusinessException;

/** 사업건 선택과 접근 확인, 쪽수 제한 (FR-011, FR-016-1, FR-055) */
@Service
public class ProposalServiceImpl implements ProposalService {

    private final ProposalMapper mapper;

    public ProposalServiceImpl(ProposalMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<ProposalBidDto> myBids(String userId) {
        return mapper.findMyBids(userId);
    }

    /** 제안팀원인 사업건만 열 수 있다. 아니면 업무 예외로 안내한다. */
    @Override
    public ProposalBidDto openBid(Long pbancSn, String userId) {
        ProposalBidDto bid = mapper.findBid(pbancSn);
        if (bid == null || mapper.countMember(pbancSn, userId) == 0) {
            throw new BusinessException("제안팀에 속한 사업건만 볼 수 있습니다.");
        }
        return bid;
    }

    @Override
    public List<ProposalMemberDto> members(Long pbancSn) {
        return mapper.findMembers(pbancSn);
    }

    @Transactional
    @Override
    public void changePageLimit(Long pbancSn, Integer limit) {
        if (limit != null && (limit < 1 || limit > 9999)) {
            throw new BusinessException("제한 쪽수는 1~9999 사이로 입력해 주세요.");
        }
        mapper.updatePageLimit(pbancSn, limit);
    }
}
