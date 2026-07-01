# node:20-alpine
FROM node:20-alpine@sha256:afdf98210b07b586eb71fa22ba2e432e058e4cd1304d31ed60888755b8c865fb AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build

# nginx:alpine
FROM nginx:alpine@sha256:35cd77497979abe70dc8d26f5ae60811eea233a2eb5dc03c2ee30972caeb303e
COPY --from=builder /app/dist /usr/share/nginx/html
COPY nginx-spa.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
