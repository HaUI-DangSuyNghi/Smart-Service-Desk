package vn.haui.dx.apps.roomrequest;
import java.util.UUID;
import vn.haui.dx.hpdi.process.api.ProcessHandle;
public record RoomRequest(UUID id,String workspace,String creator,String room,UUID knowledgeId,ProcessHandle process) {}
