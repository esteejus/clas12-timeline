#!/bin/bash

# arguments
if [ -z "$CALIBQA" ]; then
  echo "ERROR: please source env vars"
  exit
fi
datasetList=${CALIBQA}/datasetList.txt

if [ $# -lt 1 ]; then
  echo "USAGE: $0 [dataset]"
  echo " - datasets are defined in datasetList.txt:"
  cat $datasetList
  exit
fi
dataset=$1


# get timeline directory names
indir=$(grep $dataset $datasetList | tail -n1 | awk '{print $2}')
outdir=$(grep $dataset $datasetList | tail -n1 | awk '{print $3}')
if [ -z "$indir" ]; then
  echo "ERROR: dataset not found in datasetList.txt"
  exit
fi
indir="${TIMELINEDIR}/${indir}"
outdir="${TIMELINEDIR}/${outdir}"


# prepare output directory
mkdir -p $outdir; rm -r $outdir; mkdir -p $outdir
cp -rvL ${indir}/* ${outdir}/


# apply QA bounds
run-groovy $CALIBQA_JAVA_OPTS ${CALIBQA}/util/applyBounds.groovy $dataset


# cleanup output directory
echo ""
echo "CLEANUP $outdir"
for f in `find $outdir -name "*.hipo" -print | grep '_QA.hipo'`; do
  g=`echo $f | sed 's/_QA\.hipo/.hipo/g'`
  rm -v $g
  echo "...since we have $f"
done


# print URL
${CALIBQA}/util/url.sh $dataset

