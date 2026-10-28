package vn.haui.dx.apps.servicedesk;

import org.springframework.stereotype.Service;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.AgentDirectory;
import vn.haui.dx.hpdi.human.api.AgentDuty;

@Service
public class TicketAssignmentEligibility {
    private final AgentDirectory directory;
    private final AgentDuty duty;
    public TicketAssignmentEligibility(AgentDirectory directory, AgentDuty duty) {
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
        require(workspace,category,creator,subject,true);
    }
    public void requireExisting(String workspace, TicketReview.Category category, String creator, String subject) {
        require(workspace,category,creator,subject,false);
    }
    private void require(String workspace, TicketReview.Category category, String creator, String subject, boolean newAssignment) {
        if (subject == null) return;
        if (subject.equals(creator)) throw CapabilityException.forbidden("Independent agent required");
        if (directory.currentAgents(workspace, supportGroup(category)).stream().noneMatch(agent -> subject.equals(agent.subject())))
            throw CapabilityException.validation("Current support group agent membership required");
        if (!(newAssignment ? duty.onDuty(workspace,subject) : duty.manualDuty(workspace,subject)))
            throw CapabilityException.conflict("Agent is off duty");
    }
    public void requireIncumbent(String workspace, String creator, String subject) {
        if (subject == null || subject.equals(creator)
                || directory.currentAgents(workspace, null).stream().noneMatch(agent -> subject.equals(agent.subject())))
            throw CapabilityException.forbidden("Current independent agent required");
        if (!duty.manualDuty(workspace,subject)) throw CapabilityException.conflict("Agent is off duty");
    }
}
