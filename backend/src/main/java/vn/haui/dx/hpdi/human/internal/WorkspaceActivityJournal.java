package vn.haui.dx.hpdi.human.internal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.haui.dx.core.audit.api.AuditService;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
@Service
public class WorkspaceActivityJournal implements ActivityJournal {
    private final HumanContext human;
    private final AuditService audit;
    public WorkspaceActivityJournal(HumanContext human, AuditService audit) { this.human=human; this.audit=audit; }
    @Transactional(propagation=Propagation.MANDATORY)
    public void record(String workspace, BusinessActivity event) {
        human.currentActor(workspace);
        if (event==null || !name(event.action()) || !name(event.resourceType()) || event.resourceId()==null
                || !event.resourceId().matches("[a-zA-Z0-9:._/-]{1,200}") || event.references().size()>16)
            throw CapabilityException.validation("Invalid business activity");
        Map<String,Object> references=new HashMap<>();
        event.references().forEach((key,value)->{
            if (!key.matches("[a-zA-Z][a-zA-Z0-9]*(Id|Key|Version)")
                    || key.toLowerCase(java.util.Locale.ROOT).matches(".*(token|secret|password|content|description).*" )
                    || !value.matches("[a-zA-Z0-9:._/@-]{1,200}"))
                throw CapabilityException.validation("Activity details must contain safe references only");
            references.put(key,value);
        });
        audit.record(event.action(),event.resourceType(),event.resourceId(),workspace,references);
    }
    private static boolean name(String value) { return value!=null && value.length()<=100 && value.matches("[a-z][a-z0-9]*([.][a-z][a-z0-9]*)+"); }
}
