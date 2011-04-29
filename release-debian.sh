#!/bin/bash -ex
#
# build a debian package from a release build
war=$1

ver=$(unzip -p "$war" META-INF/MANIFEST.MF | grep Implementation-Version | cut -d ' ' -f2 | tr -d '\r' | sed -e "s|-SNAPSHOT||g")
echo version=$ver

dir=$(dirname $0)/../debian

(cat << EOF
jenkins ($ver) unstable; urgency=low

  * See http://jenkins-ci.org/changelog for more details.

 -- Kohsuke Kawaguchi <kkawaguchi@cloudbees.com>  $(date -R)

EOF
cat debian/debian/changelog ) > debian/changelog.tmp
mv debian/changelog.tmp debian/debian/changelog

# build the debian package
sudo apt-get install -y devscripts debhelper || true
cp $war debian/jenkins.war
cd debian
debuild -us -uc -B
rsync ../jenkins_${ver}_all.deb www-data@download.infradna.com:~/download.infradna.com/nectar/debian/binary

# build package index
# see http://wiki.debian.org/SecureApt for more details
mkdir binary > /dev/null 2>&1 || true
mv ../jenkins_${ver}_all.deb binary
sudo apt-get install apt-utils
apt-ftparchive packages binary | tee binary/Packages | gzip -9c > binary/Packages.gz
apt-ftparchive contents binary | gzip -9c > binary/Contents.gz
apt-ftparchive -c debian/release.conf release  binary > binary/Release
# sign the release file
rm binary/Release.gpg || true
gpg --no-use-agent --batch --no-tty --passphrase-file ~/.gpg.passphrase -abs -o binary/Release.gpg binary/Release
rsync binary/Packages.gz binary/Release binary/Release.gpg binary/Contents.gz www-data@download.infradna.com:~/download.infradna.com/nectar/debian/binary


