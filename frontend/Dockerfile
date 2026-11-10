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
FROM nginxinc/nginx-unprivileged:1.30-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
USER root
RUN apk add --upgrade --no-cache \
    brotli-libs=1.2.1-r0 curl=8.23.0-r0 libcurl=8.23.0-r0 libexpat=2.10.0-r0 \
    musl=1.2.6-r3 musl-utils=1.2.6-r3 zlib=1.4-r0
USER nginx
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
