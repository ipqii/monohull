FROM node:20-alpine

WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev --no-audit --no-fund
COPY server.js rules.js rules.yaml ./
COPY public ./public

ENV PORT=8085
ENV RULES_FILE=/app/rules.yaml
EXPOSE 8085
CMD ["node", "server.js"]
