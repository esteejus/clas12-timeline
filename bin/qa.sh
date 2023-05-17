#!/bin/bash
# runs calibration QA on specified timeline directory
# - output directory path will be the same as timeline directory path, with "_qa"
#   appended to the directory name
# - dataset name is determined automatically and added to `datasetList.txt`

# arguments
if [ -z "$CALIBQA" ]; then
  source $(dirname $(realpath ${BASH_SOURCE[0]}))/../qa-detectors/env.sh
fi
if [ $# -lt 1 ]; then
  echo "USAGE: $0 [URL (or path) of timelines directory]"
  echo " - if specifying a path, it must be relative to TIMELINEDIR = $TIMELINEDIR"
  exit
fi

# define dataset, and input/output timelines directories
indir=$(echo $1 | sed 's/\/$//g')
indir=$(echo $indir | sed 's/^http.*clas12mon\.jlab\.org\///g')
indir=$(echo $indir | sed 's/\/tlsummary$//g')
outdir=${indir}_qa
dataset=$(echo $indir | sed 's/\//_/g')
echo "dataset = $dataset"
echo "indir = $indir"
echo "outdir = $outdir"
echo "$dataset $indir $outdir" >> ${CALIBQA}/datasetList.txt

# run the qa
${CALIBQA}/util/runQA.sh $dataset
