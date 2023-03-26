FROM gradle:7.4.2-jdk17-alpine AS build

WORKDIR /gradle

COPY build.gradle .
COPY settings.gradle .
COPY modules ./modules

RUN gradle downloadDependencies --no-daemon

COPY src src

RUN gradle assemble installDist --no-daemon

FROM amazoncorretto:17-alpine3.15

WORKDIR /app

COPY --from=build /gradle/build/install/vanilla/ .

RUN addgroup -S -g 1337 vanilla && adduser -S -D -H -u 1337 -s /sbin/nologin -G vanilla vanilla
USER matcha:matcha

ENTRYPOINT ["sh", "bin/vanilla"]
