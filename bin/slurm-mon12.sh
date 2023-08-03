#!/usr/bin/bash


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



# usage
if [ $# -lt 2 ]; then
  echo """
  USAGE: $0 [VERSION_NAME] [OPTIONS]... [RUN_DIRECTORY]...
         runs the monitoring jobs, either on the farm or locally

    [VERSION_NAME]       REQUIRED: version name, defined by the user, used for
                         slurm jobs identification

    [OPTIONS]...         OPTIONAL: optional keywords to change settings
                         - if you specify none, the default will generate a
                           slurm job script, one job per specified
                           [RUN_DIRECTORY], and a printout of the corrsponding
                           \`sbatch\` command for user execution
                         - any number of the following keywords may be used:

                         rundir    assume the specified [RUN_DIRECTORY] contains
                                   subdirectories named as just run numbers; jobs
                                   will be created for each of them; it is not
                                   recommended to use wildcards for this option

                         single    run only the first job, locally; useful for
                                   testing before submitting jobs to slurm

                         series    run all jobs locally, one at a time; useful
                                   for testing on systems without slurm

                         submit    submit the slurm jobs, rather than just
                                   printing the \`sbatch\` command


    [RUN_DIRECTORY]...   REQUIRED: One or more directories, each directory corresponds to
                         one run and should contain reconstructed hipo files.
                         - A regexp can be used to specify the list of directories as well,
                           if your shell supports it
                         - Globbing may also be used, if your shell supports it (e.g. * wildcard)
                         - For each [RUN_DIRECTORY] the directory plots[RUNNUM] is created in the working
                           directory. Each 'plots[RUNNUM]' directory contains hipo files with monitoring
                           histograms.

  EXAMPLES:

  $  $0 v1.0.0 submit rundir /volatile/mon
       -> submit slurm jobs for all numerical subdirectories of /volatile/mon/,
          where each subdirectory should be a run number; this is the most common usage

  $  $0 v1.0.0 /volatile/mon/*
       -> generate the slurm script to run on all subdirectories of
          /volatile/mon/ no matter their name

  $  $0 v1.0.0 single /volatile/mon/run*
       -> run on the first directory named run[RUNNUM], where [RUNNUM] is a run number

  """ >&2
  exit 101
fi

# get version number
ver=$1
shift

# get options
modeRundir=0
modeSingle=0
modeSeries=0
modeSubmit=0
while [ 1 ]; do
  case $1 in
    rundir) modeRundir=1; shift ;;
    single) modeSingle=1; shift ;;
    series) modeSeries=1; shift ;;
    submit) modeSubmit=1; shift ;;
    *) break ;;
  esac
done

# get run directories
if [ $# -eq 0 ]; then
  echo "ERROR: no run directories specified" >&2
  exit 100
fi
rdirs=""
if [ $modeRundir -eq 1 ]; then
  for topdir in $*; do
    for subdir in $(ls $topdir | grep -E "[0-9]+"); do
      rdirs+=$(echo "$topdir/$subdir " | sed 's;//;/;g')
    done
  done
else
  rdirs=$*
fi

# print arguments
echo """
Settings:
========================
VERSION_NAME = $ver
OPTIONS = {
  rundir => $modeRundir,
  single => $modeSingle,
  series => $modeSeries,
  submit => $modeSubmit,
}
RUN_DIRECTORIES = ["""
for rdir in $rdirs; do
  echo "  $rdir,"
done
echo """]
========================
"""


# test if one jar for clas12-monitoring package exists in the directory
[[ ! -f $JARPATH ]] && echo "---- [ERROR] Problem with jar file for clas12_monitoring package --------" >&2 && echo && exit 100

# test if there is a version name
echo $ver | grep -q "/" && echo "---- [ERROR] version name must not contain / -------" >&2 && echo && exit 100
slurmJobName=clas12-timeline-monitoring-$ver
echo "---- slurm job name will be: $slurmJobName"

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
echo \"RUN $runnum\" &&
realpath $rdir/* > plots/$runnum.input &&
mkdir -p $plotDir &&
pushd plots &&
java -DCLAS12DIR=\${COATJAVA}/ -Xmx1024m -cp \${COATJAVA}/lib/clas/*:\${COATJAVA}/lib/utils/*:$JARPATH $EXE $runnum $runnum.input $MAX_NUM_EVENTS $beam_energy;
popd"""
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
#SBATCH --job-name=$slurmJobName
#SBATCH --output=log/%x-%A_%a.out
#SBATCH --error=log/%x-%A_%a.err
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
"""


# execute the job(s)
singleScript=$slurm.single.sh
if [ $modeSingle -eq 1 -o $modeSeries -eq 1 ]; then
  echo "#!/bin/bash" > $singleScript
  echo "set -e" >> $singleScript
  if [ $modeSingle -eq 1 ]; then
    echo "RUNNING ONE SINGLE JOB LOCALLY:"
    echo "================================================"
    head -n1 $joblist >> $singleScript
  else
    echo "RUNNING ALL JOBS SEQUENTIALLY, LOCALLY:"
    echo "================================================"
    cat $joblist >> $singleScript
  fi
  chmod u+x $singleScript
  $singleScript
  exit $?
elif [ $modeSubmit -eq 1 ]; then
  sbatch $slurm
  echo "JOBS SUBMITTED!"
else
  echo """
  To submit all jobs to slurm, run:
  =========================
  sbatch $slurm
  =========================
  """
fi
