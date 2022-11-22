#!/usr/bin/env ruby

if ARGV.length<1
  $stderr.puts "USAGE: #{$0} DAT_FILE"
  exit 2
end
datfile = ARGV[0]

columnNames = [
  :runnum,
  :filenum,
  :eventNumMin,
  :eventNumMax,
  :sector,
  :nElec,
  :nElecFT,
  :fcStart,
  :fcStop,
  :ufcStart,
  :ufcStop,
  :aveLivetime,
]

puts [
  '#filenum',
  'eventNumMin',
  'eventNumMax',
  'fcChargeGated',
  'fcChargeNotGated',
].join ' '

File.readlines(datfile).map(&:split).each do |line|
  dat = columnNames.zip(line).to_h
  next if dat[:sector].to_i != 1
  puts [
    dat[:filenum].to_i,
    dat[:eventNumMin].to_i,
    dat[:eventNumMax].to_i,
    dat[:fcStop].to_f - dat[:fcStart].to_f,
    dat[:ufcStop].to_f - dat[:ufcStart].to_f,
  ].join ' '
end
