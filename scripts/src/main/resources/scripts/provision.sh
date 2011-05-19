#!/bin/bash

WORK="$(dirname "$0")"

if [ ! -d "${MASTER_HOME}" ]
then
  mkdir -p "${MASTER_HOME}/plugins"
  find "${WORK}/../" -name \*.hpi -exec cp "{}" "${MASTER_HOME}/plugins/" \;

  echo "cd \"${MASTER_HOME}\";" > "${MASTER_HOME}.start"
  echo "java \"-DMASTER_ENDPOINT=http://localhost:${MASTER_PORT}\" \"-DJENKINS_HOME=${MASTER_HOME}\" \"-DMASTER_METANECTAR_ENDPOINT=${MASTER_METANECTAR_ENDPOINT}\" \"-DMASTER_GRANT_ID=${MASTER_GRANT_ID}\" -jar "${WORK}/../jenkins-war.war" --httpPort=${MASTER_PORT} --ajp13Port=-1 &> \"${MASTER_HOME}.log.txt\" &" >> "${MASTER_HOME}.start"
  echo 'echo $! > ${MASTER_HOME}.pid && cd -' >> ${MASTER_HOME}.start
fi

echo -n "MASTER_ENDPOINT=http://localhost:${MASTER_PORT}"
