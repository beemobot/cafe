FROM gradle:7.4.2-jdk17-alpine AS build

WORKDIR /build/vanilla

COPY vanilla/settings.gradle.kts .
COPY vanilla/build.gradle.kts .
COPY latte ../latte
COPY vanilla/src src

RUN --mount=type=cache,target=/home/gradle/.gradle/caches gradle assemble installDist --no-daemon


FROM amazoncorretto:17-alpine3.15

WORKDIR /vanilla

COPY --from=build /build/vanilla/build/install/vanilla/ .

RUN addgroup -S -g 1337 vanilla && adduser -S -D -H -u 1337 -s /sbin/nologin -G vanilla vanilla
USER vanilla:vanilla

ENTRYPOINT ["sh", "bin/vanilla"]
