# node:20-alpine
FROM node:26-alpine@sha256:725aeba2364a9b16beae49e180d83bd597dbd0b15c47f1f28875c290bfd255b9 AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# nginx:alpine
FROM nginx:alpine@sha256:4a73073bd557c65b759505da037898b61f1be6cbcc3c2c3aeac22d2a470c1752
# libexpat 2.8.1-r0 -> 2.8.2-r0 (CVE-2026-56131/56407/56408); c-ares 1.34.6-r0 -> 1.34.8-r0 (CVE-2026-33630)
# curl/libcurl 8.19.0-r0 -> 8.20.0-r0 (CVE-2026-5773/6276)
RUN apk upgrade --no-cache libexpat c-ares curl
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
