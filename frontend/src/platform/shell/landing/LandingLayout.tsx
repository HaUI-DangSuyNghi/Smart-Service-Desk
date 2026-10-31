import { LandingFooter } from "./LandingFooter";
import { LandingHeader } from "./LandingHeader";
import LandingPage from "./LandingPage";
import "./landing.css";

const LandingLayout = () => {
  return (
    <div className="landing min-h-svh">
      <LandingHeader />
      <LandingPage />
      <LandingFooter />
    </div>
  );
};

export default LandingLayout;
