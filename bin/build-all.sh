#!/bin/bash
# build clas12-timeline; run with argument 'clean' for a clean build

set -e

wd="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"

dirs=(
  $wd/../monitoring
  $wd/../detectors
)

for d in ${dirs[@]}; do
  pushd $d
  [ "$1" == "clean" ] && mvn clean
  mvn package
  popd
done
