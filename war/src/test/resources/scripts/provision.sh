#!/bin/bash

WORK="$(dirname "$0")"

MASTER_HOME="${MASTER_HOME_LOCATION}/${MASTER_INDEX}-${MASTER_NAME}"
export MASTER_HOME
if [ ! -d "${MASTER_HOME}" ]
then
  mkdir -p "${MASTER_HOME}/plugins"

  if [ -n "${MASTER_SNAPSHOT}" ]
  then
    # assume file-based URL
    file=${MASTER_SNAPSHOT##*:}
    unzip -q ${file} -d "${MASTER_HOME}" 1>&2
  fi

  find "${WORK}" -name \*.hpi -exec cp "{}" "${MASTER_HOME}/plugins/" \;

  jvmArgs=""
  if [ -n "${MASTER_JAVA_DEBUG_PORT}" ]
  then
    jvmArgs="${jvmArgs} -Xdebug -Xrunjdwp:transport=dt_socket,server=y,suspend=n,address=${MASTER_JAVA_DEBUG_PORT}"
  fi

  echo "cd \"${MASTER_HOME}\";" > "${MASTER_HOME}.start"
  echo "java ${jvmArgs} \"-DMASTER_ENDPOINT=http://localhost:${MASTER_PORT}\" \"-DJENKINS_HOME=${MASTER_HOME}\" \"-DMASTER_INDEX=${MASTER_INDEX}\" \"-DMASTER_NAME=${MASTER_NAME}\" \"-DMASTER_METANECTAR_ENDPOINT=${MASTER_METANECTAR_ENDPOINT}\" \"-DMASTER_GRANT_ID=${MASTER_GRANT_ID}\" -jar "${WORK}/jenkins-war.war" --httpPort=${MASTER_PORT} --ajp13Port=-1 &> \"${MASTER_HOME}.log.txt\" &" >> "${MASTER_HOME}.start"
  echo 'echo $! > ${MASTER_HOME}.pid && cd -' >> ${MASTER_HOME}.start
fi

echo "MASTER_ENDPOINT=http://localhost:${MASTER_PORT}"
echo "MASTER_HOME=${MASTER_HOME}"

