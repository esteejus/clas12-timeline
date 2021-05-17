#!/bin/bash

if [ -z "$CLASQA" ]; then source env.sh; fi

if [ $# -ne 1 ];then echo "USAGE: $0 [dataset]"; exit; fi
dataset=$1

# setup error filtered execution function
errlog="errors.log"
> $errlog
function sep { printf '%70s\n' | tr ' ' -; }
function exe { 
  sep
  echo "EXECUTE: $*"
  sep
  sep >> $errlog
  echo "$* errors:" >> $errlog
  $* 2>>$errlog
}

# organize the data into datasets
exe ./datasetOrganize.sh $dataset

# produce chargeTree.json
exe run-groovy $CLASQA_JAVA_OPTS buildChargeTree.groovy $dataset

# loop over datasets
# trigger electrons monitor
exe run-groovy $CLASQA_JAVA_OPTS qaPlot.groovy $dataset
exe run-groovy $CLASQA_JAVA_OPTS qaCut.groovy $dataset
# FT electrons
exe run-groovy $CLASQA_JAVA_OPTS qaPlot.groovy $dataset FT
exe run-groovy $CLASQA_JAVA_OPTS qaCut.groovy $dataset FT
#melding the two tables cp outdat.rgk7/qaTree.json ./qaTree.json.old && cp outdat.rgk7/qaTreeFT.json ./qaTree.json.new : exe run-groovy $CLASQA_JAVA_OPTS QA/meld/meld.groovy 

# meld FT and FD json files from outdt.rgk7/qaTree.json
exe run-groovy mergeFTandFD.groovy $dataset

# general monitor
exe run-groovy $CLASQA_JAVA_OPTS monitorPlot.groovy $dataset
# deploy timelines to dev www
exe ./deployTimelines.sh $dataset $dataset
# print errors (filtering out hipo logo contamination)
sep
echo "TIMELINE GENERATION COMPLETE"
grep -vE '█|═|Physics Division|^     $' $errlog
rm $errlog
