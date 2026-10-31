package vn.haui.dx.apps.roomrequest;
import java.util.UUID;
public record RoomRequest(UUID id,String workspace,String creator,String room,UUID knowledgeId,RoomProcessView process) {}
