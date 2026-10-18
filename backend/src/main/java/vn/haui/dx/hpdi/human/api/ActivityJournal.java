package vn.haui.dx.hpdi.human.api;
/** Actor/workspace correlation, safe reference policy and transaction-bound activity recording. */
public interface ActivityJournal { void record(String workspace, BusinessActivity activity); }
