#!/bin/bash
# after finishing analysis in the `QA` subdirectory, this script will call
# qaCut.groovy on the results

if [ -z "$CLASQA" ]; then source env.sh; fi

if [ $# -ne 1 ]; then
  echo "USAGE: $0 [dataset]"
  exit
fi
dataset=$1

qaDir=outmon.${dataset}.qa

mkdir -p $qaDir
rm -r $qaDir
mkdir -p $qaDir

echo "FD part"
for bit in {0..6} 100; do
  run-groovy $CLASQA_JAVA_OPTS qaCut.groovy $dataset false $bit
  qa=$(ls -t outmon.${dataset}/electron_trigger_*QA*.hipo | grep -v epoch | head -n1)
  mv $qa ${qaDir}/$(echo $qa | sed 's/^.*_QA_//g')
done
echo "FT part"

for bit in {6..9} 100; do
  run-groovy $CLASQA_JAVA_OPTS qaCut.groovy $dataset useFT $bit
  qa=$(ls -t outmon.${dataset}/electron_FT_*QA*.hipo | grep -v epoch | head -n1)
  mv $qa ${qaDir}/$(echo $qa | sed 's/^.*_QA_//g')
done

cp QA/qa.${dataset}/qaTree.json $qaDir
echo ""
cat outdat.${dataset}/passFractions.dat
