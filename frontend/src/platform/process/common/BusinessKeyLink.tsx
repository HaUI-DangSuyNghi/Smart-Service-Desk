import { Link } from "react-router";
import { shortKey } from "./display";
import { processLink } from "./processLinks";

interface Props {
  processKey: string;
  businessKey: string;
}

/** Mã yêu cầu; app có trang chi tiết (đăng ký processLinks) thì thành link sang trang đó. */
export const BusinessKeyLink = ({ processKey, businessKey }: Props) => {
  const href = processLink(processKey, businessKey);
  const text = (
    <span title={businessKey} className="font-mono text-crm-sm">
      {shortKey(businessKey)}
    </span>
  );

  if (!href) return text;
  return (
    // Chặn sự kiện lan lên dòng bảng (dòng cũng bấm được)
    <Link
      to={href}
      onClick={(event) => event.stopPropagation()}
      className="text-crm-accent hover:underline"
    >
      {text}
    </Link>
  );
};
