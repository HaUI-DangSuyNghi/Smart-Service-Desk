import { createContext, type ReactNode } from "react";

/** Shell cung cấp các thao tác chung mà không để UI phụ thuộc vào tính năng của app. */
export const PageHeaderActionsContext = createContext<ReactNode>(null);
export const PageHeaderActionsProvider = PageHeaderActionsContext.Provider;
