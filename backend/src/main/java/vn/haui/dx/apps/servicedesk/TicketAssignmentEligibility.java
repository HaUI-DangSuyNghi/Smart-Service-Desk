package vn.haui.dx.apps.servicedesk;

import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.MemberDirectory;

@Service
public class TicketAssignmentEligibility {
    private final MemberDirectory directory;
    private final AgentDutyService duty;
    public TicketAssignmentEligibility(MemberDirectory directory, AgentDutyService duty) {
        this.directory = directory; this.duty = duty;
    }
    public static String supportGroup(TicketReview.Category category) {
        return switch (category) {
            case HARDWARE -> "hardware";
            case NETWORK -> "network";
            case SOFTWARE -> "software";
            case ACCOUNT -> "sysadmin";
            case OTHER -> null;
        };
    }
    public void require(String workspace, TicketReview.Category category, String creator, String subject) {
        if (subject == null) return;
        if (subject.equals(creator)) throw CapabilityException.forbidden("Independent agent required");
        if (directory.currentAgents(workspace, supportGroup(category)).stream().noneMatch(agent -> subject.equals(agent.subject())))
            throw CapabilityException.validation("Current support group agent membership required");
        if (!duty.onDuty(workspace, subject)) throw CapabilityException.conflict("Agent is off duty");
    }
}
