package com.ojj.sujuro.history.service.impl;

import com.ojj.sujuro.history.dto.HistoryDto;
import com.ojj.sujuro.history.mapper.HistoryMapper;
import com.ojj.sujuro.history.service.HistoryService;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HistoryServiceImpl implements HistoryService {

    private final HistoryMapper historyMapper;

    public HistoryServiceImpl(HistoryMapper historyMapper) {
        this.historyMapper = historyMapper;
    }

    @Override
    @Transactional
    public void record(Long pbancSn, String processTypeCd, String processorId, Long targetTeamSn, String reason) {
        historyMapper.insert(pbancSn, processTypeCd, processorId, targetTeamSn, reason);
    }

    @Override
    @Transactional
    public void record(Long pbancSn, String processTypeCd, String processorId) {
        record(pbancSn, processTypeCd, processorId, null, null);
    }

    @Override
    public List<HistoryDto> findByPbanc(Long pbancSn) {
        return historyMapper.findByPbanc(pbancSn);
    }
}
