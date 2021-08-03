#!/bin/bash
# copy qaTree.json from ../outdat.$dataset, so we can start the QA
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [dataset] [optional: path to qaTree.json] [optional: options for parseQaTree.groovy]"
  echo ""
  echo "- to see parseQaTree options: $0 [dataset] -h"
  echo "                         and: $0 [dataset] -l"
  echo ""
  echo "- for manual QA: $0 [dataset] -cnds=user_comment"
  exit
fi
dataset=$1
mkdir -p qa.${dataset}
rm -r qa.${dataset}
mkdir -p qa.${dataset}
qatree=../outdat.${dataset}/qaTree.json
opts=""
if [ $# -ge 2 ]; then
  opts=${@:2}
  if [ -f $2 ]; then
    qatree=$2
    if [ $# -gt 2 ]; then
      opts=${@:3}
    fi
  fi
fi
cp -v $qatree qa.${dataset}/qaTree.json
rm qa
ln -sv qa.${dataset} qa
run-groovy $CLASQA_JAVA_OPTS parseQaTree.groovy $opts
echo ""
echo "imported $qatree to local area, and parsed"
echo "view qa/qaTable.dat for human readable version"
