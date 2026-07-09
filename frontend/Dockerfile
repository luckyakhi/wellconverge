# Dev-mode container for Iteration 1 (Vite dev server with API proxy).
# A later iteration can add a production build served by nginx.
FROM node:22-alpine
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
EXPOSE 5173
CMD ["npm", "run", "dev", "--", "--host"]
