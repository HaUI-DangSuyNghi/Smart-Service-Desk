import { Outlet } from "react-router";
import { LandingFooter } from "./LandingFooter";
import { LandingHeader } from "./LandingHeader";
import "./landing.css";

export default function LandingLayout() {
  return (
    <div className="landing min-h-svh">
      <LandingHeader />
      <Outlet />
      <LandingFooter />
    </div>
  );
}
