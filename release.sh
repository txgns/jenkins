#!/bin/bash -ex
#
# Kohsuke's automated release script
#

# this is the main module
ws="$PWD"

# figure out where the release scripts are
pushd "$(dirname "$0")"
  bin="$PWD"
popd


umask 022 # we'll transfer files created during builds to Apache, where go=rx for directories are expected
xmlstarlet --version > /dev/null
if [ $? != 0 ]; then
  echo xmlstarlet is not installed
  exit -1
fi

# make sure we have up to date workspace
cd "$ws"
# svn update
# if left-over hudson.war for Debian build from the last time, delete it.
rm hudson.war || true

tag=hudson-$(show-pom-version pom.xml | sed -e "s/-SNAPSHOT//g" -e "s/\\./_/g")
export MAVEN_OPTS="-Xmx512m -XX:MaxPermSize=256m"
mvn -B -Dtag=$tag -DskipTests release:prepare || mvn -B -Dtag=$tag -DskipTests install release:prepare || true
mvn release:perform
war=$PWD/target/checkout/war/target/hudson.war

id=$(show-pom-version target/checkout/pom.xml)
case $id in
*-SNAPSHOT)
  echo Trying to release a SNAPSHOT
  exit 1
  ;;
esac

# TODO: where do we make it available?
# TODO: make JNLP file available

# TODO: resurrect IPS
# publish IPS. The server needs to be restarted for it to see the new package.
# cat war/target/hudson-war-$id.ipstgz | ssh wsinterop.sun.com "cd ips/repository; gtar xvzf -"
# ssh wsinterop.sun.com "cd ips; ./start.sh"

# create and publish debian package
$bin/release-debian.sh $war

# RedHat/OpenSUSE RPM
sudo apt-get install -y createrepo || true
for arch in rpm opensuse; do
  $ws/$arch/build.sh $war
  for rpm in $(find $arch opensuse -name '*.rpm'); do
    ./rpm-sign $(cat ~/.gpg.passphrase) $rpm
  done
  createrepo $arch
  gpg -a --detach-sign --yes --no-use-agent --passphrase-file ~/.gpg.passphrase $arch/repodata/repomd.xml
  cp infradna.com.key $arch/repodata/repomd.xml.key
  for dir in RPMS repodata; do
    rsync -avz --delete $arch/$dir www-data@download.infradna.com:~/download.infradna.com/ich/$arch/$dir
  done
done

echo success
