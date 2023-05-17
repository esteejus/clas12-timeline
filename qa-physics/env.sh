#!/bin/bash

export CLASQA=$(dirname $(realpath $0))
export CLASQAWWW="/u/group/clas/www/clas12mon/html/hipo"
JYPATH="${JYPATH}:${CLASQA}"
export JYPATH=$(echo $JYPATH | sed 's/^://')
export CLASQA_JAVA_OPTS="-Djava.awt.headless=true"

echo "Environment:"
env | grep -w COATJAVA
env | grep -w CLASQA
env | grep -w CLASQAWWW
env | grep -w JYPATH
env | grep -w CLASQA_JAVA_OPTS
