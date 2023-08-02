#!/usr/bin/bash

# usage
if [ $# -lt 2 ]; then
  echo """
  USAGE: $0 [VERSION_NAME] [RUN_DIRECTORY]...
         submits the monitoring jobs to the farm batch system via Slurm. Each job corresponds to one RUN_DIRECTORY.


  DESCRIPTION:

    [VERSION_NAME]       version name, defined by the user, used for slurm jobs identification

    [RUN_DIRECTORY]...   One or more directories, each directory corresponds to
                         one run and should contain reconstructed hipo files.
                         - A regexp can be used to specify the list of directories as well
                         - Globbing may also be used, if your shell supports it (e.g. * wildcard)
                         - For each [RUN_DIRECTORY] the directory plots#RUN is created in the working
                           directory. Each 'plots#RUN' directory contains hipo files with monitoring
                           histograms.

  EXAMPLE:
     $0 v1.0.0 /volatile/clas12/rg-a/production/Spring19/mon/recon/*
  """ >&2
  exit 101
fi
ver=$1
shift
rdirs=$*

# constants ############################################################
# dependencies
BINDIR="`dirname $0`"
MONDIR="`realpath $BINDIR/..`"
JARPATH="$MONDIR/monitoring/target/clas12-monitoring-v0.0-alpha.jar"
# executable
EXE=org.jlab.clas12.monitoring.ana_2p2
MAX_NUM_EVENTS=100000000
# slurm settings
SLURM_MEMORY=1500
SLURM_TIME=4:00:00
########################################################################


# test if one jar for clas12-monitoring package exists in the directory
[[ ! -f $JARPATH ]] && echo "---- [ERROR] Problem with jar file for clas12_monitoring package --------" >&2 && echo && exit 100

# test if there is a version name
echo $ver | grep -q "/" && echo "---- [ERROR] version name must not contain / -------" >&2 && echo && exit 100
echo "---- slurm job name will be: $ver"

# make output directories
mkdir -p log plots slurm

# loop over input directories, building the job list
joblist=slurm/job.${ver}.list
> $joblist
for rdir in $rdirs; do

  # check for existence of directory
  echo "---- READ directory $rdir"
  [[ ! -e $rdir ]] && echo "------ [ERROR] the folder $rdir does not exist" >&2 && continue

  # get the run number
  runnum=`basename $rdir | grep -m1 -o -E "[0-9]+"`
  [[ -z "$runnum" ]] && echo "------ [WARNING] unknown run number for directory $rdir" >&2 && continue
  runnum=$((10#$runnum))

  # if output directory exists, skip this run
  plotDir=plots/plots$runnum
  [[ -d $plotDir ]] && echo "------ [WARNING] skipping run $runnum because the directory $plotDir already exists" >&2 && continue

  # get the beam energy
  # FIXME: use a config file or RCDB; this violates DRY with qa-physics/monitorRead.groovy
  beam_energy=`python -c """
beamlist = [
(3861,5673,10.6), (5674, 5870, 7.546), (5871, 6000, 6.535), (6608, 6783, 10.199),
(11620, 11657, 2.182), (11658, 12283, 10.389), (12389, 12444, 2.182), (12445, 12951, 10.389),
(15013,15490, 5.98636), (15533,15727, 2.07052), (15728,15784, 4.02962), (15787,15884, 5.98636),
(16010, 16079, 2.22), (16084, 1e9, 10.54) ]

ret=10.6
for r0,r1,eb in beamlist:
  if $runnum>=r0 and $runnum<=r1:
    ret=eb
    print(ret)
"""`
  echo "------ beam energy = $beam_energy GeV"

  # prepare slurm script
  echo "------ generating $EXE command"
  cmd="""
realpath $rdir/* > plots/$runnum.input &&
cd plots &&
mkdir $plotDir &&
java -DCLAS12DIR=\${COATJAVA}/ -Xmx1024m -cp \${COATJAVA}/lib/clas/*:\${COATJAVA}/lib/utils/*:$JARPATH $EXE $runnum $runnum.input $MAX_NUM_EVENTS $beam_energy"""
  echo $cmd >> $joblist

done

# check if we have any jobs to run
if [ ! -s $joblist ]; then
  echo "[ERROR] there are no jobs to run" >&2
  exit 100
fi

# write slurm script
slurm=slurm/job.${ver}.slurm
cat > $slurm << EOF
#!/bin/sh
#SBATCH --ntasks=1
#SBATCH --job-name=clas12-timeline-monitoring---$ver
#SBATCH --output=log/%x-run-$runnum-%j-%N.out
#SBATCH --error=log/%x-run-$runnum-%j-%N.err
#SBATCH --partition=production
#SBATCH --account=clas12

#SBATCH --mem-per-cpu=$SLURM_MEMORY
#SBATCH --time=$SLURM_TIME

#SBATCH --array=1-$(cat $joblist | wc -l)
#SBATCH --ntasks=1

source /group/clas12/packages/setup.sh
module load clas12/pro
module switch clas12/pro

srun \$(head -n\$SLURM_ARRAY_TASK_ID $joblist | tail -n1)
EOF

echo """
Generated:
  Slurm script: $slurm
  Job list:     $joblist

To run a single job, locally for testing, run any line in $joblist, e.g.,
=========================
$(head -n1 $joblist)
=========================

To submit all jobs to slurm, run:
=========================
sbatch $slurm
=========================
"""
