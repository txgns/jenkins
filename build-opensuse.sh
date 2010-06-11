#!/bin/bash -e
if [ -z "$1" ]; then
  echo "Usage: build.sh path/to/hudson.war"
  exit 1
fi

sudo apt-get install -y rpm expect || true

rpm/build.sh "$2"

# sign the results
for rpm in $(find rpm -name '*.rpm'); do
   rpm-sign $(cat ~/.gpg.passphrase) $rpm
done
