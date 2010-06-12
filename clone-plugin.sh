#!/bin/sh
# This script is run exactly once for a Hudson plugin
# that we decide to include in ich distribution.
usage()
{
  echo "clones upstream svn repository as a git repository"
  echo "Usage: $0 plugin-name"
}
if [ $# -ne 1 ]
then
  usage
  exit 1
else
  usage
fi
PLUGIN=$1
BASE_URL=https://svn.dev.java.net/svn/hudson/trunk/hudson/plugins
BIRTH_REV=$(svn log --stop-on-copy $BASE_URL/$PLUGIN | grep ^r|tail -1|sed 's/^r\([1-9][0-9]*\).*/\1/')
git svn clone -r$BIRTH_REV $BASE_URL/$PLUGIN
cd $PLUGIN
git svn fetch
cd ..
track-plugin.sh $PLUGIN $BIRTH_REV
