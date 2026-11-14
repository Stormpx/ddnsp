#!/bin/sh
set -e

# --enable-preview
#   ddnsp is compiled with --enable-preview, the jar refuses to load without it.
# --enable-native-access=ALL-UNNAMED
#   the panama/ffi bindings (boringtun, tun) and the netty native transports.
# --sun-misc-unsafe-memory-access=allow
#   jdk.unsupported sun.misc.Unsafe, used by netty and caffeine.
# JAVA_OPTS is free-form, e.g. docker run -e JAVA_OPTS='-Xmx512m' ...
# shellcheck disable=SC2086
exec java \
    --enable-preview \
    --enable-native-access=ALL-UNNAMED \
    --sun-misc-unsafe-memory-access=allow \
    ${JAVA_OPTS:-} \
    -jar /app.jar "$@"
