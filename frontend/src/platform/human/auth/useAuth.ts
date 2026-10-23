import { useContext } from "react";
import { AuthContext, type AuthContextValue } from "./AuthContext";

export const useAuth = (): AuthContextValue => {
  const value = useContext(AuthContext);
  if (!value) throw new Error("useAuth phải nằm trong <AuthProvider>");
  return value;
};
