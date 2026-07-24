# Playwright image already ships Chromium/Firefox/WebKit + Node, so we only need to
# install the project's dev dependencies and run the suite.
FROM mcr.microsoft.com/playwright:v1.50.0-jammy

WORKDIR /e2e

COPY package.json ./
RUN npm install --no-audit --no-fund

COPY . .

CMD ["npx", "playwright", "test"]
