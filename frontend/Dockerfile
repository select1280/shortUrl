# ---- 建置階段 ----
FROM node:20-alpine AS build
WORKDIR /build

# 先只複製 lock 檔安裝依賴，改前端程式碼時這一層可以重用快取。
# 用 npm ci 而不是 npm install：嚴格照 lock 檔安裝，建置結果才可重現
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- 執行階段 ----
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /build/dist/frontend/browser /usr/share/nginx/html
EXPOSE 80
