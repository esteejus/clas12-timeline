// show the contents of specified bank for all events in a hipo file

import org.jlab.io.hipo.HipoDataSource

// ARGUMENTS
def inHipo = "../../data/dst/005032/rec_clas_005032.evio.00060-00064.hipo"
def bankName = "RUN::scaler"
if(args.length>=1) inHipo = args[0]
if(args.length>=2) bankName = args[1]
def bank

def nevents=0
def ntotal=0

reader = new HipoDataSource()
reader.open(inHipo)
while(reader.hasEvent()) {
  event = reader.getNextEvent()
  if(event.hasBank(bankName)) {
    bank = event.getBank(bankName)
    bank.show()
    ntotal++
  }
  nevents++
}

println "nevents=$nevents"
println "ntotal=$ntotal"
