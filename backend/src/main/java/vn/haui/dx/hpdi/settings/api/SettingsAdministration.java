package vn.haui.dx.hpdi.settings.api;

import com.fasterxml.jackson.databind.JsonNode;

public interface SettingsAdministration {
    String requireAdmin();
    void audit(String action, JsonNode before, JsonNode after);
}
