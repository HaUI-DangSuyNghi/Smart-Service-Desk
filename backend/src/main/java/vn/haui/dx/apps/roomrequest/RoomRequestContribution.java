package vn.haui.dx.apps.roomrequest;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.process.api.RequestContribution;

@Component
public class RoomRequestContribution implements RequestContribution {
    private final JdbcTemplate jdbc;
    public RoomRequestContribution(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Set<String> processKeys() { return Set.of(RoomProcess.KEY); }
    public Form describe(Context context) {
        var row=jdbc.queryForMap("select room from roomrequest_request where workspace=? and process_id=?",context.process().workspace(),context.process().id());
        boolean open=!context.process().ended() && context.process().currentTasks().size()==1
            && "approve".equals(context.process().currentTasks().get(0).key());
        return new Form((String)row.get("room"),open,open,"Chỉ sửa hoặc hủy khi yêu cầu đặt phòng chưa được duyệt.",
            List.of(new Field("room","Phòng",(String)row.get("room"),50,false)));
    }
    public Map<String,Object> update(Context context,Form expected,Map<String,String> values,String reason) {
        lock(context,expected);
        jdbc.update("update roomrequest_request set room=? where workspace=? and process_id=?",values.get("room"),context.process().workspace(),context.process().id());
        return Map.of("room",values.get("room"));
    }
    public void cancel(Context context,Form expected,String reason) { lock(context,expected); }
    private void lock(Context context,Form expected) {
        var rows=jdbc.queryForList("select room from roomrequest_request where workspace=? and process_id=? for update nowait",
                context.process().workspace(),context.process().id());
        if (rows.isEmpty()) throw CapabilityException.notFound("Room request not found");
        if (!rows.get(0).get("room").equals(expected.revision())) throw CapabilityException.conflict("Room request changed");
    }
}
