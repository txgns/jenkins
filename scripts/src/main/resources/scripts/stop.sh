#!/bin/bash

if [ -f "${MASTER_HOME}.pid" ]
then
  pid=`cat ${MASTER_HOME}.pid`
  kill ${pid}
  rm ${MASTER_HOME}.pid
fi
exit 0
