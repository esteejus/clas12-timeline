# clas12-timeline

To download,
```bash
git clone https://github.com/JeffersonLab/clas12-timeline.git
```


# How to submit `clas12_monitoring` to `slurm`
To submit `clas12_monitoring` for each run from specified directory one should run these commands, e.g.:
```bash
./bin/build-all.sh
./bin/run-monitoring.sh   # print usage guide
```

To run it interactively, see the [monitoring subdirectory](monitoring)

##  Timeline
To build,
```bash
./bin/build-all.sh
```

To run, execute following command,

```bash
./bin/run-detectors.sh "run group" "cooking version" "/path/to/monitoring/files/""
```
with the adequate arguments, e.g.,
```bash
./bin/run-detectors.sh rgb pass0v25.18 /volatile/clas12/rg-b/offline_monitoring/pass0/v25.18/
```


## Calibration QA

To run,
```bash
./bin/run-qa.sh TIMELINE
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


# Flowchart

```mermaid
flowchart TB

    classDef script   fill:#8f8,color:black
    classDef data     fill:#ff8,color:black
    classDef misc     fill:#f8f,color:black
    classDef timeline fill:#8ff,color:black

    dst[(DST Files)]:::data
    runMonitoring[bin/run-monitoring.sh<br/><strong>slurm</strong>]:::script
    outMonitoring([plots/plotsRUNNUM/*.hipo]):::misc
    runDetectors[bin/run-detectors.sh]:::script
    outDetectors{{detector timelines}}:::timeline
    runDetectorsQA[bin/run-qa.sh]:::script
    outDetectorsQA{{detector timelines with QA}}:::timeline

    qaPhysics[[qa-physics<br/><strong>slurm</strong>]]:::script
    qadbTimelines{{physics QA timelines}}:::timeline
    qadb([QADB]):::misc

    webTimelines{{timelines on webserver}}:::timeline

    dst --> runMonitoring --> outMonitoring --> runDetectors --> outDetectors --> runDetectorsQA --> outDetectorsQA --> webTimelines
    dst --> qaPhysics --> qadbTimelines --> webTimelines
    qaPhysics --> qadb
```
