package com.ojj.sujuro.proposal.service;

import java.util.List;

import org.springframework.stereotype.Component;

import com.ojj.sujuro.proposal.dto.PrpFileDto;

/**
 * 제안서 파일 화면(Thymeleaf)에서 부르는 보조 메서드.
 * 화면에서 T(...)로 정적 메서드를 직접 부르면 DevTools 자동 재시작 뒤에 옛 클래스가 남아
 * "PrpFileDto cannot be cast to PrpFileDto" 오류가 나므로, 빈(@prpFileView)을 거쳐 부른다.
 */
@Component("prpFileView")
public class PrpFileView {

    /** 같은 버전(이름이 같은 PDF·PPT)끼리 묶고 최신순으로 */
    public List<List<PrpFileDto>> groupVersions(List<?> files) {
        return PrpFileService.groupVersions(files);
    }
}
