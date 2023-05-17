#!/bin/bash
# pretty print a part of qaTree.json (specify tree path as arguments)
run-groovy $CLASQA_JAVA_OPTS ../jprint.groovy qa/qaTree.json $*
