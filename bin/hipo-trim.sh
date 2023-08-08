#!/bin/bash

set -e

# arguments
if [ $# -ne 3 ]; then
  echo """
  Search a directory for HIPO files, trim each to specified number of events,
  and output the trimmed files to a specified directory

  USAGE: $0 [TOP-LEVEL DIRECTORY] [OUTPUT DIRECTORY] [NUM EVENTS]
  - finds all HIPO files in [TOP-LEVEL DIRECTORY] and trims them
    to have [NUM EVENTS] events
  - the output will be in [OUTPUT DIRECTORY], with the same file tree
  """ >&2
  exit 101
fi
inputTopDir=$(realpath $1)
outputTopDir=$(realpath $2)
nEvents=$3

# checks and preparation
[ ! -d $inputTopDir ] && echo "ERROR: [TOP-LEVEL DIRECTORY]=$inputTopDir does not exist" >&2 && exit 100
[ $nEvents -lt 1 ] && echo "ERROR: [NUM EVENTS] should be greater than zero" >&2 && exit 100
mkdir -pv $outputTopDir


# find input HIPO files
for inputFile in $(find $inputTopDir -name "*.hipo"); do

  # create output file name
  outputSubDir=$outputTopDir/$(dirname $(realpath $inputFile --relative-to $inputTopDir))
  outputFile=$outputSubDir/$(basename $inputFile)
  echo """[+] TRIM:
    input:  $inputFile
    output: $outputFile
  """

  # if the output file exists, `hipo-utils -filter` will fail
  if [ -f $outputFile ]; then
    echo """
    ERROR: this output file already exists...
    SUGGESTION: remove [OUTPUT DIRECTORY] using the following command;
                BE SURE IT IS CORRECT BEFORE YOU RUN IT!

      rm -r $outputTopDir

    """ >&2
    exit 100
  fi
  mkdir -pv $outputSubDir

  # trim the input file
  hipo-utils -filter \
    -b "*::*"      \
    -n $nEvents    \
    -o $outputFile \
    $inputFile

done
