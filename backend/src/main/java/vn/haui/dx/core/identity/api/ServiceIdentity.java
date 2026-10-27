package vn.haui.dx.core.identity.api;

public interface ServiceIdentity {
    void run(String workspace, String permission, Runnable operation);
}
