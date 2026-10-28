package vn.haui.dx.core.identity.api;

/** Live enabled-human/effective-realm-role authorization, independent of workspace membership. */
public interface FreshAdminAuthorization {
    /** Returns the authenticated subject; denies stale grants and fails closed on directory failure. */
    String requirePlatformAdmin();
}
