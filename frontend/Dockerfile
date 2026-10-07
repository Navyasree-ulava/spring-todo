# ---- Build stage ----
FROM node:22-alpine AS build
WORKDIR /app

# Copy manifests first so `npm ci` is cached independently of source changes.
# Editing a component then does not re-download the whole dependency tree.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# ---- Run stage: static files behind nginx ----
FROM nginx:1.27-alpine AS runtime

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
  CMD wget -qO- http://localhost/ >/dev/null || exit 1

CMD ["nginx", "-g", "daemon off;"]
