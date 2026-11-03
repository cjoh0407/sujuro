package com.ojj.sujuro.notification.web;

import com.ojj.sujuro.notification.dto.NotificationDto;
import com.ojj.sujuro.notification.mapper.NotificationInboxMapper;
import com.ojj.sujuro.notification.support.NotificationLinkResolver;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.ojj.sujuro.auth.dto.LoginUser;
import lombok.RequiredArgsConstructor;

/**
 * 알림 팝업 (FR-032, FR-033). 헤더 종 아이콘을 누르면 뜨는 팝업이 부른다. 로그인한 사용자 누구나 자기 알림만 본다.
 * 따로 알림 화면은 없다.
 *
 *   GET  /alerts/list?offset=0&size=20  내 알림 최신순 (JSON). 팝업이 스크롤 끝에 닿으면 다음 쪽을 부른다
 *   POST /alerts/{id}/read             읽음 처리하고 갈 화면 주소를 돌려준다 (JSON, 팝업·대시보드 스크립트)
 *   POST /alerts/{id}/open             스크립트 없이 폼으로 보낼 때: 읽음 처리하고 그 화면으로 보낸다
 *   POST /alerts/read-all              모두 읽음 (JSON)
 */
@Controller
@RequiredArgsConstructor
public class NotificationController {

    static final int PAGE_SIZE = 20;
    static final int MAX_SIZE = 50;

    private final NotificationInboxMapper mapper;
    private final NotificationLinkResolver links;

    @GetMapping(value = "/alerts/list", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> list(@AuthenticationPrincipal LoginUser user,
                                    @RequestParam(value = "offset", defaultValue = "0") int offset,
                                    @RequestParam(value = "size", defaultValue = "20") int size) {
        int sz = Math.max(1, Math.min(size, MAX_SIZE));
        int off = Math.max(0, offset);
        List<NotificationDto> rows = mapper.findPage(user.getEmpNo(), off, sz + 1); // 한 건 더 받아 다음 쪽이 있는지 본다
        boolean more = rows.size() > sz;
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        List<Map<String, Object>> items = new ArrayList<>();
        for (NotificationDto n : more ? rows.subList(0, sz) : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("notiSn", n.getNotiSn());
            m.put("typeNm", n.getTypeNm() != null ? n.getTypeNm() : n.getTypeCd());
            m.put("content", n.getContent());
            m.put("read", n.isRead());
            m.put("time", n.getCrtDt() == null ? "" : fmt.format(n.getCrtDt()));
            items.add(m);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", items);
        body.put("hasMore", more);
        body.put("unreadCount", mapper.countUnread(user.getEmpNo()));
        return body;
    }

    /** 팝업·대시보드에서 부른다. 남의 알림이면 아무것도 하지 않는다. link 가 null 이면 갈 화면이 없다(권한이 없어졌거나 사업이 없음) */
    @Transactional
    @PostMapping(value = "/alerts/{notiSn}/read", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> openJson(@AuthenticationPrincipal LoginUser user, @PathVariable("notiSn") Long notiSn,
                                        HttpServletRequest request) {
        String link = readAndLink(user, notiSn);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("link", link == null ? null : request.getContextPath() + link);
        body.put("unreadCount", mapper.countUnread(user.getEmpNo()));
        return body;
    }

    /** 스크립트 없이 폼으로 보낸 경우: 갈 화면으로, 없으면 대시보드로 */
    @Transactional
    @PostMapping("/alerts/{notiSn}/open")
    public String open(@AuthenticationPrincipal LoginUser user, @PathVariable("notiSn") Long notiSn) {
        String link = readAndLink(user, notiSn);
        return "redirect:" + (link != null ? link : "/dashboard");
    }

    @Transactional
    @PostMapping(value = "/alerts/read-all", produces = MediaType.APPLICATION_JSON_VALUE)
    @ResponseBody
    public Map<String, Object> readAll(@AuthenticationPrincipal LoginUser user) {
        mapper.markAllRead(user.getEmpNo());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("unreadCount", 0);
        return body;
    }

    /** 내 알림이면 읽음 처리하고 갈 화면 주소(시작 경로 제외)를 돌려준다 */
    String readAndLink(LoginUser user, Long notiSn) {
        NotificationDto n = mapper.findMineOne(user.getEmpNo(), notiSn);
        if (n == null) {
            return null;
        }
        mapper.markRead(user.getEmpNo(), notiSn);
        return links.linkOf(n, user);
    }
}
