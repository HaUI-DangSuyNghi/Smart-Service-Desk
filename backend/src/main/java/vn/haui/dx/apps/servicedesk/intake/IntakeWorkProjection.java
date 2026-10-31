package vn.haui.dx.apps.servicedesk.intake;

import java.util.List;
import org.springframework.stereotype.Component;
import vn.haui.dx.apps.servicedesk.ServiceDeskProcess;
import vn.haui.dx.apps.servicedesk.TicketReviewService;
import vn.haui.dx.hpdi.process.api.QueueItem;
import vn.haui.dx.hpdi.process.api.WorkExecution;
import vn.haui.dx.hpdi.process.api.WorkProjection;

@Component
public class IntakeWorkProjection implements WorkProjection {
    private final TicketReviewService reviews;
    public IntakeWorkProjection(TicketReviewService reviews) { this.reviews = reviews; }
    public String processKey() { return ServiceDeskProcess.INTAKE_KEY; }
    public QueueItem project(String workspace, WorkExecution.Item execution) { return reviews.project(workspace, execution); }
    public List<QueueItem> projectBatch(String workspace, List<WorkExecution.Item> executions) { return reviews.projectBatch(workspace, executions); }
}
