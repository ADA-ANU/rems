# For documentation see docs/installing-upgrading.md

FROM --platform=linux/amd64 eclipse-temurin:17-jre-alpine
# FROM eclipse-temurin:17-jre-alpine

RUN apk add --no-cache bash

RUN mkdir /rems
WORKDIR /rems

ENTRYPOINT ["bash","./docker-entrypoint.sh"]

COPY empty-config.edn /rems/config/config.edn
COPY example-theme/extra-styles.css /rems/example-theme/extra-styles.css
COPY target/uberjar/rems.jar /rems/rems.jar
COPY docker-entrypoint.sh /rems/docker-entrypoint.sh

RUN chmod 664 /opt/java/openjdk/lib/security/cacerts
