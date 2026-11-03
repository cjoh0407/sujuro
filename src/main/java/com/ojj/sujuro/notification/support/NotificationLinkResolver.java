package com.ojj.sujuro.notification.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.ojj.sujuro.auth.code.Role;
import com.ojj.sujuro.auth.dto.LoginUser;
import com.ojj.sujuro.auth.support.AccessChecker;
import com.ojj.sujuro.notification.code.NotificationType;
import com.ojj.sujuro.notification.dto.NotificationDto;
import lombok.RequiredArgsConstructor;

/**
 * 알림을 눌렀을 때 갈 화면 (FR-033). 알림 종류와 "지금" 받은 사람의 역할·제안팀 여부로 정한다.
 *
 * 알림은 보낼 때 그 화면을 볼 수 있는 사람에게만 가지만, 받은 뒤에 제안팀에서 빠지거나(같이 빼기·중도 철회)
 * 역할이 바뀌면 그 화면을 못 볼 수 있다. 그때는 null 을 돌려주고, 화면은 읽음 처리만 하고 안내한다.
 *
 *   공고 상세 /notices/{번호}   정정·취소(NT020) 배정(NT030) 참여 결정(NT040) 중도 철회(NT060) 단계 전환(NT150), 제출 완료(NT070)의 영업부
 *   제안 화면 /proposal?bid=     마감 임박 [D-1](NT050), 그 밖에 사업이 붙은 알림의 제안팀원
 *   일정관리 /schedule?ym=&pbanc=  일정 [일정 D-1](NT050), 일정 변경(NT160). 알림 내용의 날짜로 그 일정이 있는 달을 연다
 *   제출서류 /docs?bid=          제출 완료(NT070)의 제안팀원
 *   평가·결과 /results?bid=      평가표 업로드 요청(NT110)의 경영지원실·주관팀 팀장
 *   공고 목록 /notices           공고 수집 결과(NT170)
 *
 * 주소는 앱 시작 경로(contextPath)를 빼고 돌려준다. 붙이는 것은 컨트롤러가 한다.
 */
@Component
@RequiredArgsConstructor
public class NotificationLinkResolver {

    /** 일정 D-1 알림 내용 맨 앞의 표시 (DeadlineNoticeService.SCHEDULE_TAG 와 같다) */
    static final String SCHEDULE_TAG = "[일정 D-1]";

    /** 알림 내용의 "(2026-11-28 14:00)" 같은 날짜. 일정 이름에 날짜가 있을 수 있어 마지막 것을 쓴다 */
    private static final Pattern WHEN = Pattern.compile("\\((\\d{4}-\\d{2})-\\d{2}");

    private final AccessChecker access;

    /** 갈 화면. 볼 수 없거나 갈 곳이 없으면 null */
    public String linkOf(NotificationDto n, LoginUser user) {
        if (n == null || user == null) {
            return null;
        }
        String type = n.getTypeCd() == null ? "" : n.getTypeCd().trim();
        Long sn = n.getPbancSn();
        Role role = user.getRole();
        switch (type) {
            case NotificationType.COLLECT_RESULT:
                return role == Role.SALES ? "/notices" : null;
            case NotificationType.BID_CHANGED:
            case NotificationType.ASSIGNED:
            case NotificationType.PARTICIPATION_DECIDED:
            case NotificationType.WITHDRAWN:
            case NotificationType.BID_STAGE_CHANGED:
                return sn != null && seesNotices(role) ? "/notices/" + sn : null;
            case NotificationType.SUBMITTED:
                if (sn == null) {
                    return null;
                }
                if (isTeamRole(role) && access.isProposalMember(sn, user)) {
                    return "/docs?bid=" + sn;
                }
                return role == Role.SALES ? "/notices/" + sn : null;
            case NotificationType.EVL_TABLE_REQUEST:
                if (sn == null) {
                    return null;
                }
                return role == Role.MGMT || access.isLeadTeamLeader(sn, user) ? "/results?bid=" + sn : null;
            case NotificationType.SCHEDULE_CHANGED:
                return scheduleLink(n, user);
            case NotificationType.DEADLINE_NEAR:
                String content = n.getContent() == null ? "" : n.getContent();
                return content.startsWith(SCHEDULE_TAG) ? scheduleLink(n, user) : proposalLink(sn, user);
            default:
                return proposalLink(sn, user);
        }
    }

    private String proposalLink(Long sn, LoginUser user) {
        return sn != null && isTeamRole(user.getRole()) && access.isProposalMember(sn, user) ? "/proposal?bid=" + sn : null;
    }

    /** 그 일정이 있는 달의 캘린더. 팀장·팀원은 지금도 그 사업의 제안팀원일 때만 (아니면 캘린더에 그 일정이 안 보인다) */
    private String scheduleLink(NotificationDto n, LoginUser user) {
        String ym = monthOf(n.getContent());
        String q = ym == null ? "" : "ym=" + ym;
        if (user.getRole() == Role.SALES || user.getRole() == Role.MGMT) {
            return "/schedule" + (q.isEmpty() ? "" : "?" + q);
        }
        if (n.getPbancSn() == null || !access.isProposalMember(n.getPbancSn(), user)) {
            return null;
        }
        return "/schedule?" + (q.isEmpty() ? "" : q + "&") + "pbanc=" + n.getPbancSn();
    }

    /** 알림 내용 속 마지막 날짜의 연월(2026-11). 없으면 null */
    static String monthOf(String content) {
        if (content == null) {
            return null;
        }
        Matcher m = WHEN.matcher(content);
        String last = null;
        while (m.find()) {
            last = m.group(1);
        }
        return last;
    }

    /** 공고 화면을 볼 수 있는 역할 (SecurityConfig /notices/**) */
    private static boolean seesNotices(Role role) {
        return role == Role.SALES || role == Role.LEADER || role == Role.MGMT;
    }

    /** 제안 화면·제출서류를 제안팀원으로 쓰는 역할 */
    private static boolean isTeamRole(Role role) {
        return role == Role.LEADER || role == Role.MEMBER;
    }
}
