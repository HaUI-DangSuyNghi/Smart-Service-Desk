package vn.haui.dx.hpdi.process.internal;

import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;

@Component
public class EstimateRequestLifecycle {
    private final JdbcTemplate jdbc;
    private final ActivityJournal journal;

    public EstimateRequestLifecycle(JdbcTemplate jdbc, ActivityJournal journal) {
        this.jdbc = jdbc;
        this.journal = journal;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void assignmentChanged(String workspace, String taskId, String assignee, String actor) {
        var ids = jdbc.queryForList("update hpdi_estimate_request set status='SUPERSEDED', superseded_by=?,"
            + " superseded_at=clock_timestamp(), superseded_reason='ASSIGNMENT_CHANGED'"
            + " where workspace=? and task_id=? and status='PENDING' and requested_by<>? returning id::text",
            String.class, actor, workspace, taskId, assignee);
        for (var id : ids)
            journal.record(workspace, new BusinessActivity("estimate.supersede", "process.task", taskId,
                Map.of("requestId", id, "reasonKey", "ASSIGNMENT_CHANGED", "assigneeId", assignee)));
    }
}
