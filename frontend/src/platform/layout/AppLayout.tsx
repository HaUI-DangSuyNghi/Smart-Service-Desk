import { Suspense } from "react";
import { NavLink, Outlet } from "react-router";
import type { MenuItem } from "../router/registry";

interface Props {
  menu: readonly MenuItem[];
}

export default function AppLayout({ menu }: Props) {
  return (
    <div className="app">
      <header>
        <NavLink to="/">Smart Service Desk</NavLink>
      </header>
      <nav>
        <ul>
          {menu.map((item) => (
            <li key={item.path}>
              <NavLink to={item.path}>{item.label}</NavLink>
            </li>
          ))}
        </ul>
      </nav>
      <main>
        <Suspense fallback={<p>Đang tải...</p>}>
          <Outlet />
        </Suspense>
      </main>
    </div>
  );
}
