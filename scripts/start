#!/bin/bash

if [ ! -d "${MASTER_HOME}" ]
then
  echo ${MASTER_HOME} directory does not exist
  exit -1;
fi

if [ -f "${MASTER_HOME}.pid" ]
then
  exit 0;
fi

bash ${MASTER_HOME}.start 
