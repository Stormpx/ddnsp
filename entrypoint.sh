#!/bin/sh

terminate_children() {
  kill -15 "$child_pid" 2>/dev/null
  wait "$child_pid" 2>/dev/null
  exit 1
}

trap terminate_children 15

java  $JAVA_OPTS  -jar  /app.jar $@ &

child_pid=$!
wait $child_pid
