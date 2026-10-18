package vn.haui.dx.hpdi.intelligence.api;
import java.util.List;
import java.util.Map;
public record TriageSuggestion(String status, Map<String,String> result, List<Evidence> evidence,
                               String explanation, boolean needsReview, String profileVersion) {
    public TriageSuggestion { result=result==null ? null : Map.copyOf(result); evidence=List.copyOf(evidence); }
    public record Evidence(String field, String text) {}
    public static TriageSuggestion unavailable() { return new TriageSuggestion("DEGRADED",null,List.of(),null,true,null); }
}
