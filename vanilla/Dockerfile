FROM gradle:7.4.2-jdk17-alpine AS build

WORKDIR /gradle/vanilla

COPY vanilla/build.gradle .
COPY vanilla/settings.gradle .
COPY latte ../latte

RUN gradle downloadDependencies --no-daemon

COPY vanilla/src src

RUN gradle assemble installDist --no-daemon


FROM amazoncorretto:17-alpine3.15

WORKDIR /app

COPY --from=build /gradle/vanilla/build/install/vanilla/ .

RUN addgroup -S -g 1337 vanilla && adduser -S -D -H -u 1337 -s /sbin/nologin -G vanilla vanilla
USER vanilla:vanilla

ENTRYPOINT ["sh", "bin/vanilla"]
