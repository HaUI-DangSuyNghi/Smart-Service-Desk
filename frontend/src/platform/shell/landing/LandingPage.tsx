import { Architecture } from "./sections/Architecture";
import { CtaBanner } from "./sections/CtaBanner";
import { Faq } from "./sections/Faq";
import { Hero } from "./sections/Hero";
import { HowItWorks } from "./sections/HowItWorks";
import { Roles } from "./sections/Roles";
import { SecuritySection } from "./sections/SecuritySection";
import { ServicesShowcase } from "./sections/ServicesShowcase";
import { StatsSection } from "./sections/StatsSection";

export default function LandingPage() {
  return (
    <>
      <Hero />
      <HowItWorks />
      <Architecture />
      <Roles />
      <ServicesShowcase />
      <StatsSection />
      <SecuritySection />
      <Faq />
      <CtaBanner />
    </>
  );
}
