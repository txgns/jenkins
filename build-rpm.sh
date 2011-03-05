#!/bin/bash -e
if [ -z "$1" -o -z "$2" ]; then
  echo "Usage: build.sh path/to/main/rpm path/to/hudson.war"
  exit 1
fi

sudo apt-get install -y rpm expect || true

$1/build.sh "$2"

# sign the results
for rpm in $(find $1 -name '*.rpm'); do
   $(dirname $0)/../rpm-sign $(cat ~/.gpg.passphrase) $rpm
done
