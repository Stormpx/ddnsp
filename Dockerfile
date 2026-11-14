# syntax=docker/dockerfile:1

# ---------------------------------------------------------------------------
# stage 1 - build a minimal, self-contained java runtime with jlink
# ---------------------------------------------------------------------------
# the jar is compiled with --enable-preview, therefore the runtime jdk has to be
# exactly the same feature release as the jdk that built build/libs/ddnsp.jar.
# bump this tag together with the build jdk, never on its own.
FROM eclipse-temurin:25-jdk AS jre-builder

ENV JAVA_MINIMAL=/opt/jre

# module list = what the shaded jar actually needs (jdeps) plus the optional
# providers that are resolved through the service loader at runtime
# (jdk.crypto.ec for tls/ec, jdk.jfr + jdk.management.agent for vert.x/jmx).
RUN java --version \
 && jlink --version \
 && jlink \
        --add-modules \
            java.base,java.compiler,java.desktop,java.management,java.naming,java.net.http,java.rmi,java.security.jgss,java.sql,jdk.crypto.ec,jdk.jfr,jdk.management.agent,jdk.unsupported \
        --compress zip-9 \
        --no-header-files \
        --no-man-pages \
        --strip-debug \
        --output "$JAVA_MINIMAL"

# ---------------------------------------------------------------------------
# stage 2 - runtime (only the trimmed jre and the application jar)
# ---------------------------------------------------------------------------
FROM debian:trixie-slim

LABEL org.opencontainers.image.title="ddnsp" \
      org.opencontainers.image.description="ddns + dns + proxy" \
      org.opencontainers.image.source="https://github.com/Stormpx/ddnsp" \
      org.opencontainers.image.licenses="MIT"

ENV JAVA_MINIMAL=/opt/jre \
    PATH="/opt/jre/bin:$PATH" \
    LANG=C.UTF-8 \
    LC_ALL=C.UTF-8

COPY --from=jre-builder /opt/jre /opt/jre
COPY build/libs/ddnsp.jar /app.jar
COPY entrypoint.sh /entrypoint.sh

RUN chmod 755 /entrypoint.sh

# the tun / wireguard transports need a tun device, raw sockets and nftables,
# so the container keeps running as root; drop it to a fixed uid only if you
# never enable proxy.tun / proxy.wireguard.
USER root

# exec form: the entrypoint script execs java, so java is pid 1 and receives
# sigterm directly instead of needing a trap + wait wrapper.
ENTRYPOINT ["/entrypoint.sh"]
