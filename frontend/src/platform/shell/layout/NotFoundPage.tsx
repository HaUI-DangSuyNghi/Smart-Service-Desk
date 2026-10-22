import { Link } from "react-router";

export default function NotFoundPage() {
  return (
    <>
      <h1>Không tìm thấy trang</h1>
      <Link to="/home">Về trang chủ</Link>
    </>
  );
}
