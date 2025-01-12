FROM node:22-alpine AS build

WORKDIR /app

COPY ./package.json ./
COPY ./package-lock.json ./
RUN npm ci

ADD . .
RUN npm run build


FROM node:22-alpine AS build

COPY --from=build /app/dist ./dist

CMD ["npm", "run", "start"]
EXPOSE 1337
