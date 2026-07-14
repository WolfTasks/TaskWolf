# node:20-alpine
FROM node:26-alpine@sha256:e88a35be04478413b7c71c455cd9865de9b9360e1f43456be5951032d7ac1a66 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# nginx:alpine
FROM nginx:alpine@sha256:54f2a904c251d5a34adf545a72d32515a15e08418dae0266e23be2e18c66fefa
# libexpat 2.8.1-r0 -> 2.8.2-r0 (CVE-2026-56131/56407/56408); c-ares 1.34.6-r0 -> 1.34.8-r0 (CVE-2026-33630)
RUN apk upgrade --no-cache libexpat c-ares
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
