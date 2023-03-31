FROM  eclipse-temurin:19.0.2_7-jdk as packager
MAINTAINER crowds
RUN { \
        java --version ; \
        echo "jlink version:" && \
        jlink --version ; \
    }

ENV JAVA_MINIMAL=/opt/jre

# build modules distribution
RUN jlink \
    --verbose \
    --add-modules \
        java.base,jdk.crypto.ec,java.compiler,java.desktop,java.management,java.naming,java.sql,jdk.unsupported \
    --compress 2 \
    --no-header-files \
    --no-man-pages \
    --output "$JAVA_MINIMAL"

# Second stage, add only our minimal "JRE" distr and our app
FROM debian:stable-slim

#RUN apt-get install autoconf automake libtool make tar gcc

RUN ln -sf /usr/share/zoneinfo/Asia/Shanghai /etc/localtime
RUN echo 'Asia/Shanghai' >/etc/timezone

ENV JAVA_MINIMAL=/opt/jre
ENV PATH="$PATH:$JAVA_MINIMAL/bin"

COPY --from=packager "$JAVA_MINIMAL" "$JAVA_MINIMAL"
COPY "build/libs/ddnsp.jar" "/app.jar"

ENTRYPOINT [ "java" ,"-XX:+UseShenandoahGC", "-Xmx200M" ,"-jar" ,"/app.jar" ]