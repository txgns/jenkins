#!/bin/sh
# This script is run exactly once for a Hudson plugin
# that we decide to include in ich distribution.
git_svn_check()
{
    git svn --help > /dev/null 2> /dev/null
    if [ $? -ne 0 ]
    then
        echo "Got git svn?"
        exit $status
    fi
}
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
git_svn_check
PLUGIN=$1
#BASE_URL=https://svn.dev.java.net/svn/hudson/trunk/hudson/plugins
BASE_URL=file:///Users/deepk/infradna/local/svnmirror/anonsvn/trunk/hudson/plugins
BIRTH_REV=$(svn log --stop-on-copy $BASE_URL/$PLUGIN | grep ^r|tail -1|sed 's/^r\([1-9][0-9]*\).*/\1/')
git svn clone -r$BIRTH_REV $BASE_URL/$PLUGIN
cd $PLUGIN
git svn fetch
cd ..
`dirname $0`/track-plugin.sh "$PLUGIN" "$BIRTH_REV"
