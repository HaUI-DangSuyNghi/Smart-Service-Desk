# Bước 1: build ra file tĩnh. Chỉ để chạy, không kiểm (typecheck, lint, test tự chạy bằng npm khi cần)
FROM node:22.14.0-alpine AS build
WORKDIR /app

# Cài thư viện trước, tách khỏi mã nguồn: chỉ sửa code thì Docker dùng lại cache lớp này
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
# Biến VITE_* được nhúng vào bundle lúc build, nên truyền bằng build arg (compose.yaml)
ARG VITE_OIDC_AUTHORITY
ARG VITE_OIDC_CLIENT_ID
ENV VITE_OIDC_AUTHORITY=$VITE_OIDC_AUTHORITY \
    VITE_OIDC_CLIENT_ID=$VITE_OIDC_CLIENT_ID
# Gọi thẳng vite build, bỏ `tsc -b` của `npm run build` cho nhanh
RUN npx vite build

# Bước 2: chỉ giữ file tĩnh, phục vụ bằng nginx chạy không cần root
FROM nginxinc/nginx-unprivileged:1.27.4-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
