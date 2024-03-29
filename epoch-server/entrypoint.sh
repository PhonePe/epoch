#!/bin/bash
#set -x

pid=0

# SIGTERM-handler
term_handler() {
  if [ $pid -ne 0 ]; then
    kill -SIGTERM "$pid"
    wait "$pid"
  fi
  echo "Exiting on sigterm"
  exit 143; # 128 + 15 -- SIGTERM
}

# setup handlers
# on callback, kill the last background process, which is `tail -f /dev/null` and execute the specified handler
trap 'kill ${!}; term_handler' SIGTERM

if [ -z "${DROVE_ENDPOINT}" ]; then
    echo "Error: DROVE_ENDPOINT is a mandatory parameter for epoch to work."
    exit 1
fi

export EPOCH_ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin}"
export EPOCH_GUEST_PASSWORD="${GUEST_PASSWORD:-guest}"
export DROVE_USERNAME="${DROVE_USERNAME:-admin}"
export DROVE_PASSWORD="${DROVE_PASSWORD:-password}"

CONFIG_PATH=${CONFIG_FILE_PATH:-config.yml}

if [ ! -f "${CONFIG_PATH}" ]; then
  echo "Config file ${CONFIG_PATH} not found."
  echo "File system:"
  ls -l /
  exit 1
else
  echo "Config ${CONFIG_PATH} file exists. Proceeding to service startup."
fi

export JAVA_HOME="${JAVA_HOME}:${PWD}"

DEBUG_ENABLED="${DEBUG-0}"
if [ "$DEBUG_ENABLED" -ne 0 ]; then

  echo "Environment variables:"
  printenv

  echo "Java version details:"
  java -version

  echo "Contents of working dir: ${PWD}"
  ls -l "${PWD}"

fi

# run application
CMD=$(eval echo "java -jar -XX:+${GC_ALGO:-UseG1GC} -Xms${JAVA_PROCESS_MIN_HEAP:-1g} -Xmx${JAVA_PROCESS_MAX_HEAP:-1g} ${JAVA_OPTS} epoch-server.jar server ${CONFIG_PATH}")
echo "Starting Epoch Server by running command: ${CMD}"

eval "${CMD}" &

pid="$!"

# wait forever
while true
do
  tail -f /dev/null & wait ${!}
done


