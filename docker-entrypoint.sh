#!/bin/bash
set -e

ARGS="--upstream-dns=${UPSTREAM_DNS}"
ARGS="${ARGS} --local-ip=${LOCAL_IP}"

if [ "${VERBOSE}" = "true" ]; then
    ARGS="${ARGS} --verbose"
fi

exec java \
    -Djava.security.egd=file:/dev/./urandom \
    -Dserver.port=80 \
    -Dserver.address=0.0.0.0 \
    -jar /app/app.jar \
    ${ARGS}
