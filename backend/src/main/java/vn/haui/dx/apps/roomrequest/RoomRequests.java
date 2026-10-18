package vn.haui.dx.apps.roomrequest;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.ProcessCapability;
import vn.haui.dx.hpdi.data.api.KnowledgeCatalog;
import vn.haui.dx.hpdi.intelligence.api.*;

@Service
public class RoomRequests {
    private final JdbcTemplate jdbc;
    private final HumanContext human;
    private final ProcessCapability processes;
    private final KnowledgeCatalog knowledge;
    private final IntelligenceCapability intelligence;
    private final ActivityJournal journal;
    public RoomRequests(JdbcTemplate jdbc,HumanContext human,ProcessCapability processes,KnowledgeCatalog knowledge,
                        IntelligenceCapability intelligence,ActivityJournal journal) {
        this.jdbc=jdbc;this.human=human;this.processes=processes;this.knowledge=knowledge;this.intelligence=intelligence;this.journal=journal;
    }
    @Transactional
    public RoomRequest create(String workspace,String room,UUID knowledgeId) {
        var actor=human.currentActor(workspace);
        if (room==null || room.isBlank() || room.length()>50 || room.codePoints().anyMatch(Character::isISOControl))
            throw CapabilityException.validation("Invalid room");
        if (knowledgeId!=null) knowledge.resolve(workspace,knowledgeId);
        UUID id=UUID.randomUUID();
        var process=processes.start(workspace,RoomProcess.KEY,id.toString(),Map.of("room",room));
        jdbc.update("insert into roomrequest_request(id,workspace,creator,room,knowledge_id,process_id) values (?,?,?,?,?,?)",
            id,workspace,actor.subject(),room,knowledgeId,process.processInstanceId());
        journal.record(workspace,new BusinessActivity("roomrequest.request.create","roomrequest.request",id.toString(),
            Map.of("processInstanceId",process.processInstanceId())));
        return new RoomRequest(id,workspace,actor.subject(),room,knowledgeId,new RoomProcessView(process.processInstanceId(),process.processKey(),process.businessKey(),process.workspace()));
    }
    @Transactional(readOnly=true)
    public RoomRequest get(String workspace,UUID id) {
        var actor=human.currentActor(workspace);
        return jdbc.query("select * from roomrequest_request where id=? and workspace=? and (creator=? or ?)",
            (row,n)->new RoomRequest(row.getObject("id",UUID.class),workspace,row.getString("creator"),row.getString("room"),
                row.getObject("knowledge_id",UUID.class),new RoomProcessView(row.getString("process_id"),RoomProcess.KEY,id.toString(),workspace)),
            id,workspace,actor.subject(),actor.hasRole("agent","coordinator")).stream().findFirst()
            .orElseThrow(()->CapabilityException.notFound("Room request not found"));
    }
    public RoomProgress timeline(String workspace,UUID id) { return RoomProgress.from(processes.timeline(workspace,get(workspace,id).process().processInstanceId())); }
    public RoomSuggestion suggest(String workspace,UUID id) {
        var request=get(workspace,id);
        return new RoomSuggestion(intelligence.suggest(workspace,new TriageInput("room-request","0.2.0","Room "+request.room(),"Single request; can wait")).attributes());
    }
}
