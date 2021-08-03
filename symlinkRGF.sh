#!/bin/bash
# build directory of symlinks to DST directories, for RGF data

# output directory of symlinks:
outdir=../data.RGF


# get list of RGF data directories
mkdir -p tmp
grep -E '^rgf_' datasetList.txt | grep -v 'rgf_2020' | awk '{print $4}' > tmp/rgfList

# build symlinks
while read dstdir; do
  echo "read $dstdir"
  ln -sfv $dstdir/* $outdir/
  rm -v $outdir/README.json
done < tmp/rgfList

# print list of symlinks
ls -l --color $outdir
