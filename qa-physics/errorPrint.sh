#!/bin/bash
grep --color -vE '█|═|Physics Division|^     $' /farm_out/`whoami`/clasqa*.err
