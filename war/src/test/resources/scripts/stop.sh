#!/bin/bash

MASTER_HOME=${MASTER_HOME_LOCATION}/${MASTER_INDEX}-${MASTER_NAME}
if [ -f "${MASTER_HOME}.pid" ]
then
  pid=`cat ${MASTER_HOME}.pid`
  kill ${pid}
  rm ${MASTER_HOME}.pid
fi
exit 0
