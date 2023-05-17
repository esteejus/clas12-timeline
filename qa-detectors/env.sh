#!/bin/bash

if [ -z "${BASH_SOURCE[0]}" ]; then
  export CALIBQA=$(dirname $(realpath $0))
else
  export CALIBQA=$(dirname $(realpath ${BASH_SOURCE[0]}))
fi

export TIMELINEDIR="/u/group/clas/www/clas12mon/html/hipo"
JYPATH="${JYPATH}:${CALIBQA}/src/"
export JYPATH=$(echo $JYPATH | sed 's/^://')
export CALIBQA_JAVA_OPTS="-Djava.awt.headless=true"

echo "--- Environment ---"
echo "CALIBQA = $CALIBQA"
echo "TIMELINEDIR = $TIMELINEDIR"
echo "JYPATH = $JYPATH"
echo "CALIBQA_JAVA_OPTS = $CALIBQA_JAVA_OPTS"
echo "-------------------"
