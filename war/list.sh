#!/bin/bash
#
# generate the POM fragment to be put into war/pom.xml from the plugins listed in work/plugins
for f in work/plugins/*.hpi; do
  g=$(unzip -l $f | grep pom.xml | colex 4 | cut -d '/' -f3)
  a=$(unzip -l $f | grep pom.xml | colex 4 | cut -d '/' -f4)
  v=$(show-manifest $f | grep Plugin-Version | colex 2 | tr -d '\t')

  echo "<resolveArtifact type='hpi' groupId='${g}' artifactId='${a}' version='${v}' tofile='\${basedir}/target/generated-resources/WEB-INF/plugins/${a}.hpi' />"
done

