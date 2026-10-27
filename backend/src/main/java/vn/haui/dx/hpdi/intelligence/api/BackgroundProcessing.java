package vn.haui.dx.hpdi.intelligence.api;

public interface BackgroundProcessing {
    void run(String workspace, Runnable operation);
    void authorize(String workspace, String requester);
}
