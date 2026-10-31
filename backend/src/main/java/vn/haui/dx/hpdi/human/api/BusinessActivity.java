package vn.haui.dx.hpdi.human.api;
import java.util.Map;
public record BusinessActivity(String action, String resourceType, String resourceId, Map<String,String> references) {
    public BusinessActivity { references=Map.copyOf(references); }
}
