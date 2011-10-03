#!/bin/bash

MASTER_HOME=${MASTER_HOME_LOCATION}/${MASTER_INDEX}-${MASTER_NAME}
export MASTER_HOME
if [ ! -d "${MASTER_HOME}" ]
then
  echo ${MASTER_HOME} directory does not exist
  exit -1;
fi

if [ -f "${MASTER_HOME}.pid" ]
then
  pid=$(cat ${MASTER_HOME}.pid)
  ps -p $pid > /dev/null
  if [ $? == 0 ]; then
    echo "master is already running as PID=$pid according to ${MASTER_HOME}.pid"
    exit 0
  else
    # stale PID file
    echo "stale pid file at ${MASTER_HOME}.pid. Removing"
    rm ${MASTER_HOME}.pid
  fi
fi

bash ${MASTER_HOME}.start 
