import { Link } from "react-router";
import { ArrowRight } from "lucide-react";
import { APP_HOME, REPO_URL } from "../links";
import { Reveal } from "../Reveal";

export const CtaBanner = () => {
  return (
    <section className="relative overflow-hidden bg-linear-to-br from-(--landing-accent) via-[#3f5a16] to-[#1a260d] px-6 py-20 text-center">
      <div className="absolute inset-0 bg-[radial-gradient(circle_at_30%_50%,rgba(135,202,37,0.2)_0%,transparent_60%),radial-gradient(circle_at_70%_50%,rgba(255,255,255,0.05)_0%,transparent_60%)]" />
      <Reveal className="relative mx-auto max-w-180">
        <h2 className="mb-4 text-[clamp(28px,4vw,52px)] leading-[1.1] font-extrabold tracking-tight text-white">
          Sẵn sàng Số hoá
          <br />
          Hỗ trợ CNTT của Đơn vị?
        </h2>
        <p className="mb-9 text-lg leading-relaxed text-white/70">
          Dùng thử bằng tài khoản demo, hoặc đọc mã nguồn và tài liệu kiến trúc để hiểu rõ hệ thống
          trước khi triển khai.
        </p>
        <div className="flex flex-wrap justify-center gap-3.5">
          <Link
            to={APP_HOME}
            className="inline-flex items-center gap-2 rounded-[10px] bg-white px-7 py-3.5 text-base font-bold text-(--landing-accent) transition-all hover:-translate-y-0.5 hover:opacity-90"
          >
            Dùng thử demo
            <ArrowRight className="size-4" />
          </Link>
          <a
            href={REPO_URL}
            target="_blank"
            rel="noopener"
            className="inline-flex items-center gap-2 rounded-[10px] border-[1.5px] border-white/40 px-7 py-3.5 text-base font-bold text-white transition-all hover:-translate-y-0.5 hover:border-white/70 hover:bg-white/10"
          >
            Xem trên GitHub
          </a>
        </div>
      </Reveal>
    </section>
  );
};
