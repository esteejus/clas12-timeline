#!/bin/bash
# (called by meld.sh)
#run-groovy $CLASQA_JAVA_OPTS ../../jprint.groovy qaTree.json.$1 > qaTree.json.${1}.pprint
run-groovy $CLASQA_JAVA_OPTS ../parseQaTree.groovy qaTree.json.$1
