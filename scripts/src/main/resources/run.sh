#!/bin/bash

WORK="$(dirname "$0")"

source "${WORK}/run.conf"

CURDIR="$(pwd)"
cd "${WORK}/scripts"
chmod +x *.sh

cd "${JENKINS_HOME}"
java "-DMETANECTAR_PROPERTIES_URL=${METANECTAR_PROPERTIES_URL}" \
  -Dmetanectar.provisioning.MasterProvisioner.initialDelay=10000 \
  -Dmetanectar.provisioning.MasterProvisioner.recurrencePeriod=5000 \
  "-DJENKINS_HOME=${JENKINS_HOME}" \
   -jar "${WORK}/metanectar-war.war" $*
cd "${CURDIR}"