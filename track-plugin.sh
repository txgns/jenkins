#!/bin/sh 

# Used to track the branches/tags of an upstream community hudson plugin in svn
# Should be called while doing initial fetch and each subsequent fetch
usage()
{
  echo "tracks upstream svn repository branches for a given plugin"
  echo "Usage: $0 plugin-name birth-revision"
}
if [ $# -ne 2 ]
then
  usage
  exit 1
else
  usage
fi
PLUGIN=$1
BIRTH_REV=$2
TAG_URL=https://svn.dev.java.net/svn/hudson/tags
cd $PLUGIN
for v in $(svn ls $TAG_URL | grep "^$PLUGIN-1[_.][0-9]*" | grep -v rc | sed -e "s|$PLUGIN-\(1[_.].*\)/|\1|g")
do
  echo $PLUGIN version: $v
  hasBranch=$(grep '\[svn-remote' .git/config | grep $v | wc -l)
  if [ $hasBranch -ne 0 ]; then
    echo $v is already tracked
    continue
  fi

  echo Tracking $v
  cat >> .git/config << EOF
 [svn-remote "$v"]
   url = https://hudson.dev.java.net/svn/hudson
   fetch = tags/$PLUGIN-$v:refs/remotes/$PLUGIN-$v

EOF
 git svn fetch -r $BIRTH_REV $v
done
