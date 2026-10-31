import { SILENT_PATH } from "./config";
import { getUserManager } from "./userManager";

/** Trang đang chạy trong iframe đăng nhập ngầm? Khi đó không render app. */
export const isSilentCallback = (): boolean => {
  return window.location.pathname === SILENT_PATH;
};

/** Trả kết quả đăng nhập ngầm về cho trang cha. */
export const completeSilentSignin = (): Promise<void> => {
  return getUserManager().signinSilentCallback();
};
