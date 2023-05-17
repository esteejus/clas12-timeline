# clas12-timeline

To download,
```
git clone https://github.com/JeffersonLab/clas12-timeline.git
```


# How to submit `clas12_monitoring` to slurm
To submit clas12_monitoring for each run from specified directory one should run these commands, e.g.:
<pre>
./bin/build-all.sh
<b>./bin/slurm-mon12.sh</b> version_name /volatile/clas12/rg-a/production/Spring19/mon/recon/[0-9][0-9][0-9][0-9][0-9][0-9]
</pre>

The `slurm-mon12.sh` usage:
<pre>
USAGE: ./bin/slurm-mon12.sh VERSION_NAME RUN_DIRECTORY[...]
       submits the monitoring jobs to the farm batch system via Slurm. Each job corresponds to one RUN_DIRECTORY.

<b>EXAMPLE: ./bin/slurm-mon12.sh version_name /volatile/clas12/rg-a/production/Spring19/mon/recon/[0-9][0-9][0-9][0-9][0-9][0-9]</b>

OPTIONS:
  VERSION_NAME         version name, defined by the user, used for slurm jobs identification
  RUN_DIRECTORY[...]   One or more directories, each directory corresponds to one run and should contain reconstructed hipo files.
                       The regexp can be used to specify the list of directories as well.
                       For each RUN_DIRECTORY the directory <b>plots#RUN</b> is created in the working directory.
                       Each `plots` directory contains hipo files with monitoring histograms.
</pre>

To run it interactively, see the [monitoring subdirectory](monitoring)

##  Timeline
To build,
```
./bin/build-all.sh
```

To run, execute following command,

```
./bin/detectors.sh "run group" "cooking version" "/path/to/monitoring/files/""
```
with the adequte arguments, e.g.)
```
./bin/detectors.sh rgb pass0v25.18 /volatile/clas12/rg-b/offline_monitoring/pass0/v25.18/
```


## Calibration QA

To run,
```
./bin/qa.sh TIMELINE
```
where `TIMELINE` is either the URL, for example,
```
https://clas12mon.jlab.org/rga/pass1/version3/tlsummary
```
or the relative path to the timeline, which for this example would be `rga/pass1/version3`. The output
URL containing QA timelines will be printed at the end of the script output; for this example, it will be
```
https://clas12mon.jlab.org/rga/pass1/version3_qa/tlsummary
```

See [further details](qa-detectors/README.md) for more information.

## Physics QA and QADB

See [documentation here](qa-physics).



