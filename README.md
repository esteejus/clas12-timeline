# clas12-timeline

To download,
```bash
git clone https://github.com/JeffersonLab/clas12-timeline.git
```


# How to submit `clas12_monitoring` to `slurm`
To submit clas12_monitoring for each run from specified directory one should run these commands, e.g.:
```bash
./bin/build-all.sh
./bin/slurm-mon12.sh   # print usage guide
```

To run it interactively, see the [monitoring subdirectory](monitoring)

##  Timeline
To build,
```bash
./bin/build-all.sh
```

To run, execute following command,

```bash
./bin/detectors.sh "run group" "cooking version" "/path/to/monitoring/files/""
```
with the adequate arguments, e.g.,
```bash
./bin/detectors.sh rgb pass0v25.18 /volatile/clas12/rg-b/offline_monitoring/pass0/v25.18/
```


## Calibration QA

To run,
```bash
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



