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

echo "Environment variables:"
printenv

CONFIG_PATH=${CONFIG_FILE_PATH:-/configs/config.yml}

if [ ! -f "${CONFIG_PATH}" ]; then
  echo "Config file ${CONFIG_PATH} not found"
  echo "File system:"
  ls -l /
  exit 1
else
  echo "Config ${CONFIG_PATH} file exists. Proceeding to service startup."
fi

# run application
echo "Java version details:"
java -version

echo "Starting Epoch Server"
java -jar -XX:+"${GC_ALGO-UseG1GC}" -Xms"${JAVA_PROCESS_MIN_HEAP-1g}" -Xmx"${JAVA_PROCESS_MAX_HEAP-1g}" "${JAVA_OPTS}" epoch-server.jar server /rosey/config.yml &

pid="$!"

# wait forever
while true
do
  tail -f /dev/null & wait ${!}
done


