package vn.haui.dx.hpdi.human.api;

import java.util.Map;

public interface AlertProcessing {
    void run(String workspace, Runnable operation);
    Map<String, Boolean> duty(String workspace, String supportGroup);
}
