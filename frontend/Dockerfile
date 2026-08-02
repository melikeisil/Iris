# Build the bundle, then serve it from nginx. Two stages so the runtime image carries
# no node_modules and no source.
FROM node:22-alpine AS build
WORKDIR /app

COPY package.json package-lock.json* ./
RUN npm ci --no-audit --no-fund || npm install --no-audit --no-fund

COPY . .
# The API base URL is baked at build time; in compose it is the relative /api/v1 path
# that nginx proxies to the backend, so the same image works in any environment.
ARG VITE_API_BASE_URL=/api/v1
ARG VITE_DEMO_MODE=true
ARG VITE_ORG_NAME=NovaTech
ENV VITE_API_BASE_URL=$VITE_API_BASE_URL \
    VITE_DEMO_MODE=$VITE_DEMO_MODE \
    VITE_ORG_NAME=$VITE_ORG_NAME
RUN npm run build

FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80
HEALTHCHECK --interval=15s --timeout=3s --start-period=5s --retries=5 \
  CMD wget -qO- http://localhost/healthz || exit 1
