FROM node:18-alpine

WORKDIR /app

COPY ./package.json ./
COPY ./yarn.lock ./
RUN yarn install --frozen-lockfile

ADD . .
RUN yarn build

CMD [ "yarn", "serve" ]