#!/bin/bash
# trim a directory of HIPO files, each to specified number of events

set -e

if [ $# -ne 3 ]; then
  echo """
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

[ ! -d $inputTopDir ] && echo "ERROR: [TOP-LEVEL DIRECTORY]=$inputTopDir does not exist" >&2 && exit 100
mkdir -pv $outputTopDir

inputList=$(find $inputTopDir -name "*.hipo")

for inputFile in $inputList; do
  outputSubDir=$outputTopDir/$(dirname $(realpath $inputFile --relative-to $inputTopDir))
  outputFile=$outputSubDir/$(basename $inputFile)
  echo """[+] TRIM:
    input:  $inputFile
    output: $outputFile
  """
  mkdir -pv $outputSubDir
  echo hipo-utils -filter \
    -n $nEvents \
    -o $outputFile \
    $inputFile
done
