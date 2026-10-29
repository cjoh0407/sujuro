package com.ojj.sujuro.bid.service.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.ojj.sujuro.bid.code.BidCode;
import com.ojj.sujuro.bid.api.G2bApiClient;
import com.ojj.sujuro.bid.api.G2bResponse;
import com.ojj.sujuro.bid.dto.AtchFileDTO;
import com.ojj.sujuro.bid.mapper.BidNoticeMapper;
import com.ojj.sujuro.bid.service.NoticeAttachmentService;
import com.ojj.sujuro.common.exception.BusinessException;

/** 나라장터 공고 첨부 (FR-036). 설명은 인터페이스에 있다. */
@Service
public class NoticeAttachmentServiceImpl implements NoticeAttachmentService {

    private static final Logger log = LoggerFactory.getLogger(NoticeAttachmentServiceImpl.class);

    /** 입찰공고 응답의 첨부 칸 수 (ntceSpecFileNm1~10) */
    private static final int BID_FILE_SLOTS = 10;

    /** 사전규격 응답의 규격서 칸 수 (specDocFileUrl1~5) */
    private static final int PRE_SPEC_FILE_SLOTS = 5;

    /** e발주 첨부의 문서구분명 중 제안요청서 */
    private static final String EORDER_RFP = "제안요청서";

    /** 컬럼 길이 (ORGNL_FILE_NM 300자, ORGNL_URL 1000자) */
    private static final int MAX_FILE_NM = 300;
    private static final int MAX_URL = 1000;

    private final BidNoticeMapper bidNoticeMapper;
    private final G2bApiClient g2bApiClient;

    public NoticeAttachmentServiceImpl(BidNoticeMapper bidNoticeMapper, G2bApiClient g2bApiClient) {
        this.bidNoticeMapper = bidNoticeMapper;
        this.g2bApiClient = g2bApiClient;
    }

    @Override
    public List<AtchFileDTO> readBidFiles(JsonNode bidItem) {
        List<AtchFileDTO> files = new ArrayList<>();
        if (bidItem == null) {
            return files;
        }
        for (int i = 1; i <= BID_FILE_SLOTS; i++) {
            String nm = G2bResponse.textOf(bidItem, "ntceSpecFileNm" + i);
            String url = G2bResponse.textOf(bidItem, "ntceSpecDocUrl" + i);
            if (url != null) {
                String fileNm = nm != null ? nm : "입찰공고 첨부 " + i;
                files.add(AtchFileDTO.of(AtchFileDTO.isRfpName(fileNm) ? BidCode.FILE_RFP : BidCode.FILE_BID_ATCH,
                        fileNm, url));
            }
        }
        files.addAll(readEorderFiles(bidItem));
        // 버전은 차수+1. 정정 때 새 차수 첨부를 새 버전으로 더 넣는다.
        String verNo = String.valueOf(G2bResponse.intOf(bidItem, "bidNtceOrd", 0) + 1);
        files.forEach(file -> file.setVerNo(verNo));
        return distinctByContent(withHeaders(distinctByUrl(files)));
    }

    /**
     * 주소는 다른데 같은 파일인 것을 하나로 줄인다. 파일명과 크기가 둘 다 같으면 같은 파일로 본다.
     * e발주 첨부에 파일명이 빈 행이 따로 오는데, 헤더로 읽어 보면 앞 행과 같은 파일이었다.
     * (R26BK01771602: 첨부순번 4 와 7 이 같은 제안요청서, 392,380 바이트. 2026-10-28 실측)
     * 크기를 모르면 같은 파일인지 알 수 없어 그대로 둔다.
     */
    private static List<AtchFileDTO> distinctByContent(List<AtchFileDTO> files) {
        Set<String> seen = new HashSet<>();
        List<AtchFileDTO> distinct = new ArrayList<>();
        for (AtchFileDTO file : files) {
            if (file.getFileSz() == null || seen.add(file.getOrgnlFileNm() + "|" + file.getFileSz())) {
                distinct.add(file);
            }
        }
        return distinct;
    }

    /**
     * e발주 첨부. 문서구분이 '제안요청서'면 FL010 이다. 파일명이 빈 행도 주소는 있어서 헤더로 이름을 채운다.
     * 이 API 가 실패해도 입찰공고 응답의 첨부만으로 계속 간다.
     */
    private List<AtchFileDTO> readEorderFiles(JsonNode bidItem) {
        List<AtchFileDTO> files = new ArrayList<>();
        String bidNo = G2bResponse.textOf(bidItem, "bidNtceNo");
        String ord = G2bResponse.textOf(bidItem, "bidNtceOrd");
        if (bidNo == null) {
            return files;
        }
        G2bResponse res;
        try {
            res = g2bApiClient.findEorderAttachments(bidNo, ord == null ? "000" : ord);
        } catch (BusinessException e) {
            log.warn("e발주 첨부를 받지 못해 입찰공고 첨부만 넣습니다: {} ({})", bidNo, e.getMessage());
            return files;
        }
        for (JsonNode row : res.all()) {
            String url = G2bResponse.textOf(row, "eorderAtchFileUrl");
            if (url == null) {
                continue;
            }
            String docDiv = G2bResponse.textOf(row, "eorderDocDivNm");
            String nm = G2bResponse.textOf(row, "eorderAtchFileNm");
            String sno = G2bResponse.textOf(row, "atchSno");
            String fileNm = nm != null ? nm : (docDiv != null ? docDiv : "e발주 첨부") + (sno == null ? "" : " " + sno);
            boolean rfp = EORDER_RFP.equals(docDiv) || AtchFileDTO.isRfpName(fileNm);
            files.add(AtchFileDTO.of(rfp ? BidCode.FILE_RFP : BidCode.FILE_BID_ATCH, fileNm, url));
        }
        return files;
    }

    @Override
    public List<AtchFileDTO> readPreSpecFiles(JsonNode specItem) {
        List<AtchFileDTO> files = new ArrayList<>();
        if (specItem == null) {
            return files;
        }
        for (int i = 1; i <= PRE_SPEC_FILE_SLOTS; i++) {
            String url = G2bResponse.textOf(specItem, "specDocFileUrl" + i);
            if (url != null) {
                files.add(AtchFileDTO.of(BidCode.FILE_PRE_SPEC, "사전규격 규격서 " + i, url));
            }
        }
        return withHeaders(distinctByUrl(files));
    }

    /**
     * 파일마다 내려받기 응답 헤더를 읽어 크기를 채운다. 헤더의 파일명은 API 가 이름을 주지 않아
     * 임시 이름("사전규격 규격서 1")을 붙인 파일에만 쓴다. API 가 준 이름이 있으면 그것을 믿는다.
     */
    private List<AtchFileDTO> withHeaders(List<AtchFileDTO> files) {
        for (AtchFileDTO file : files) {
            G2bApiClient.FileHeader header = g2bApiClient.peekFile(file.getOrgnlUrl());
            if (header == null) {
                continue;
            }
            file.setFileSz(header.getFileSz());
            if (header.getFileNm() != null && isPlaceholder(file)) {
                file.setOrgnlFileNm(header.getFileNm());
                file.setFileExtnNm(AtchFileDTO.extOf(header.getFileNm()));
                // 사전규격은 저장 구분이 FL080 하나라 바꾸지 않는다. 제안요청서 표시는 화면에서 파일명으로 한다.
                if (!BidCode.is(file.getFileSeCd(), BidCode.FILE_PRE_SPEC) && AtchFileDTO.isRfpName(header.getFileNm())) {
                    file.setFileSeCd(BidCode.FILE_RFP);
                }
            }
        }
        return files;
    }

    /** API 가 이름을 주지 않아 우리가 붙인 임시 이름인지. 임시 이름에는 확장자가 없다. */
    private static boolean isPlaceholder(AtchFileDTO file) {
        return file.getFileExtnNm() == null;
    }

    /** 같은 주소가 입찰공고 첨부와 e발주 첨부에 둘 다 있으면 한 번만 넣는다. */
    private static List<AtchFileDTO> distinctByUrl(List<AtchFileDTO> files) {
        Set<String> seen = new HashSet<>();
        List<AtchFileDTO> distinct = new ArrayList<>();
        for (AtchFileDTO file : files) {
            if (seen.add(file.getOrgnlUrl())) {
                distinct.add(file);
            }
        }
        return distinct;
    }

    @Override
    public void save(Long pbancSn, String rgtrId, List<AtchFileDTO> files) {
        for (AtchFileDTO file : files) {
            if (file.getOrgnlUrl().length() > MAX_URL) {
                log.warn("첨부 주소가 {}자를 넘어 넣지 않습니다: {}", MAX_URL, file.getOrgnlFileNm());
                continue;
            }
            file.setPbancSn(pbancSn);
            file.setRgtrId(rgtrId);
            if (file.getOrgnlFileNm().length() > MAX_FILE_NM) {
                file.setOrgnlFileNm(file.getOrgnlFileNm().substring(0, MAX_FILE_NM));
            }
            bidNoticeMapper.insertAtchFile(file);
        }
    }

    @Override
    public List<AtchFileDTO> findFiles(Long pbancSn) {
        return bidNoticeMapper.findNoticeFiles(pbancSn);
    }
}
