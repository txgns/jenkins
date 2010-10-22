#!/bin/sh

# a temporary script to mirror all the plugins to be bundled
BASE_URL=file:///Users/deepk/infradna/local/svnmirror/anonsvn/trunk/hudson/plugins
for plugin in active-directory analysis-core build-timeout copyartifact cvs dashboard-view findbugs git mercurial monitoring parameterized-trigger promoted-builds ssh-slaves subversion warning
do
  echo "cloning $plugin: `svn ls $BASE_URL/$plugin`"
  `dirname $0`/clone-plugin.sh "$plugin"
done

