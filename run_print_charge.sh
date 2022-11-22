#!/bin/bash
for file in outdat/rg*/*; do
  print_charge.rb $file | column -t > `echo $file | sed 's/outdat/&\/cross_check/'`
done
