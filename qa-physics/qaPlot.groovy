/* create hipo file with plots of N, F, N/F, etc. vs. file number, for each run
 * - this starts to build the structure of the 'timeline' hipo file
 */

import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors

//----------------------------------------------------------------------------------
// ARGUMENTS:
def dataset = 'inbending1'
//TODO: Added: map of detectors to loop over
def detectors = ["eCFD","eFT","CD","FD","FT"]
def implementedDetectors = ['CD','FD','FT'] // for particle loops, DIS electrons are treated separately
if(args.length>=1) dataset = args[0]
//TODO: Added: parse command line options to modify list of detectors to loop over
if(args.length>=2 && args[1].startsWith("-ds=")) {
  detectors = [] // reset map
  for (det in args[1].split('=')[1].split(',')) { detectors.add(det) }
}
if(args.length>=2 && args[1].startsWith("-addDs=")) { for (det in args[1].split('=')[1].split(',')) { detectors.add(det) } }
detectors.toUnique() // Make sure you don't double count, .toUnique() is for string lists!
//----------------------------------------------------------------------------------

// define vars and subroutines
def pid_e = 11
def sectors = 0..<6
def sec = { int i -> i+1 }
def tok
int r=0
def runnum, filenum, eventNumMin, eventNumMax, sector
def nElec, nElecFT
def fcStart, fcStop
def ufcStart, ufcStop
def aveLivetime
def fcCharge
def ufcCharge
def trigRat
def errPrint = { str -> System.err << "ERROR in run ${runnum}_${filenum}: "+str+"\n" }
def pidMap   = [:]
pidMap[11]   = ['e-','electron']
pidMap[22]   = ['γ','photon']
pidMap[211]  = ['π+','pip']
pidMap[-211] = ['π-','pim']
pidMap[111]  = ['π0','pi0']
pidMap[321]  = ['K+','kp']
pidMap[-321] = ['K-','km']
pidMap[310]  = ['K0','k0']
pidMap[2212] = ['p+','proton']
pidMap[2112] = ['n','neutron']

//TODO: Added: defined variables for additional particles in detectors: structure is nParticles[ detector, nElec or [pids:nPid] ]
def nParticles = [:]; for (det in detectors) { nParticles.put(det,[:]) }

// define graphs
def defineGraph = { det,name,ytitle ->
//TODO: Added: check for looping FD sectors or not
  if (det.contains('FD') && detectors.contains(det)) { //default plot to sectors
    sectors.collect {
      def g = new GraphErrors(name+"_"+det+"_${runnum}_"+sec(it)) //NOTE: This formatting is useful for qaCut.groovy
      def gT = ytitle+" vs. file number -- Sector "+sec(it)
      g.setTitle(gT)
      g.setTitleY(ytitle)
      g.setTitleX("file number")
      return g
    }
  } // if (det.contains('FD') && ...)
  else {
    def g = new GraphErrors(name+(detectors.contains(det) ? "_"+det : det)+"_${runnum}") //NOTE: kind of a hack, just pass '_'+det for grF and grT
    def gT = ytitle+" vs. file number " //NOTE: Make sure det is included in ytitle when calling function where applicable
    g.setTitle(gT)
    g.setTitleY(ytitle)
    g.setTitleX("file number")
    return g
  }
}
def grF = [:]
def grT = [:] // grA, grN, are absorbed into 'eCFD/eFT' since we include FC normalized and unnormalized particle counts for each detector
def grU = [:]//NOTE: ADDED 5/17/23

//TODO: Added: define additional particle graphs: structure is grNP[ detector, [pids:[grA,grN]] ]
def grNP = [:]; for (det in detectors) { grNP.put(det,[:]) }

// define output hipo file
def outHipo = detectors.collect{ new TDirectory() }
"mkdir -p outmon.${dataset}".execute()
//TODO: Added: loop over detector and particle lists now
def outHipoN = detectors.collect{ "outmon.${dataset}/monitor"+it+".hipo" }
def writeHipo = { index, o -> o.each{ outHipo[index].addDataSet(it) } }
def writePlots = { run -> (0..<detectors.size()).each{
    println "write run $run"
    outHipo[it].mkdir("/${run}")
    outHipo[it].cd("/${run}")

    writeHipo(it,grF[detectors[it]])
    writeHipo(it,grT[detectors[it]]) // grA and grN are now included in eCFD and eFT detectors
    writeHipo(it,grU[detectors[it]])//NOTE: ADDED 5/17/23

    // TODO: Added: write additional particle graphs: structure is grNP[ detector, [pids:[grA,grN]] ] just pid=11 for eCFD/eFT
    for (maplist in grNP[detectors[it]].values()) {
      if (detectors[it].contains('FD')) { // account for sector lists in FD
        for (grlist in maplist) { for (gr in grlist) { for (sect in gr) { writeHipo(it,sect) } } }
      } else { for (grlist in maplist) { for (gr in grlist) { writeHipo(it,gr) } } }
    } // for(maplist in grNP[...])
  } //(0..<detectors.size()).each
} // writePlots()

// open data_table.dat
def dataFile = new File("outdat.${dataset}/data_table.dat")
def runnumTmp = 0
def initialized = false
//TODO: Removed: set titles for electron graphs
if(!(dataFile.exists())) throw new Exception("data_table.dat not found")
dataFile.eachLine { line ->

  // read columns of data_table.dat (in order left-to-right)
  tok = line.tokenize(' ')
  r=0
  runnum = tok[r++].toInteger()
  filenum = tok[r++].toInteger()
  eventNumMin = tok[r++].toInteger()
  eventNumMax = tok[r++].toInteger()
  sector = tok[r++].toInteger()
  nElec = tok[r++].toBigDecimal() // could use nParticles['eCFD'][pid_e][sector-1] = nElec
  nElecFT = tok[r++].toBigDecimal() // could use nParticles['eFT'][pid_e][sector-1] = nElecFT
  fcStart = tok[r++].toBigDecimal()
  fcStop = tok[r++].toBigDecimal()
  ufcStart = tok[r++].toBigDecimal()
  ufcStop = tok[r++].toBigDecimal()
  aveLivetime = tok.size()>11 ? tok[r++].toBigDecimal() : -1 //NOTE: ADDED 5/17/23. MODIFIED livetime->aveLivetime
  // TODO: Added: tokenize routine for additional particles
  particlesSize = tok.size()>12 ? tok[r++].toInteger() : 0
  counter = 0
  particles = []
  // initialize counter arrays and graphs from first line...could be nicer...
  if(!initialized) {
    (0..<particlesSize).each{ particles.add(tok[r+(implementedDetectors.size()+1)*it].toInteger()) }
    (0..<detectors.size()).each{
      outHipo[it].mkdir("/${runnum}")
      outHipo[it].cd("/${runnum}")
      det = detectors[it]
      if (det.startsWith('e')) {
        nParticles[det].put(pid_e,[]); grNP[det].put(pid_e,[])
      }
      else {
        for (pid in particles) {
          nParticles[det].put(pid,[]); grNP[det].put(pid,[])
        }
      }
    }
    particles = []
    initialized = true
  }
  while (counter<particlesSize) {
    pid = tok[r++].toInteger()
    particles.add(pid)
    implementedDetectors.each{ if (!it.startsWith('e')) { //NOTE: failsafe check here since 'eCFD/eFT' should not be in implementedDetectors 
      if (it.contains('FD')) { nParticles[it][pid][sector-1] = tok[r++].toBigDecimal() } 
      else { nParticles[it][pid] = tok[r++].toBigDecimal() } //NOTE: Make sure the number of detector entries is the same in the file as here
    } }//NOTE: DIS electrons treated separately
    counter++
  }

  // if the run number changed, write filled graphs, then start new graphs
  if(runnum!=runnumTmp) {
    if(runnumTmp>0) writePlots(runnumTmp)
    //TODO: Added: define graphs for additional particles
    (0..<detectors.size()).each{
      det = detectors[it]
      outHipo[it].mkdir("/${runnum}")
      outHipo[it].cd("/${runnum}")

      grF[detectors[it]] = defineGraph('_'+detectors[it],"grF","Faraday cup charge F [nC]") //NOTE: empty string detector argument means graph will be split into sectors
      grT[detectors[it]] = defineGraph('_'+detectors[it],"grT","Live Time")
      grU[detectors[it]] = defineGraph('_'+detectors[it],"grU","Ungated Faraday Cup charge F [nC]")
      
      if (det.startsWith('e')) {
        grNP[det][pid_e] = [ defineGraph(det,"grA","${det[1..<det.size()]} DIS Electrons Normalized Yield N/F"),
        defineGraph(det,"grN","${det[1..<det.size()]} DIS Electrons Yield N") ]
      }
      else {
        for (pid in particles) grNP[det][pid] = [ defineGraph(det,"grA_PID${pid}","${det} ${pidMap[pid][0]} Normalized Yield N/F"),
        defineGraph(det,"grN_PID${pid}","${det} ${pidMap[pid][0]} Yield N") ]//TODO: DEBUGGING CHANGED gr*_PID${pid} -> pidMap[pid]
      }
    }
    runnumTmp = runnum
  }

  // FC calculations
  fcCharge = fcStop - fcStart
  ufcCharge = ufcStop - ufcStart
  liveTime = ufcCharge!=0 ? fcCharge/ufcCharge : 0

  // DIS electrons
  trigRat = fcCharge!=0 ? nElec/fcCharge : 0
  trigRatFT = fcCharge!=0 ? nElecFT/fcCharge : 0

  // choose which livetime to plot and use for QA cut "LowLiveTime" //NOTE: ADDED 5/17/23. THIS ENTIRE BLOCK of 4 lines.
  livetime = aveLivetime // average `livetime`, directly from scaler bank
  //livetime = livetimeFromFCratio // from gated/ungated FC charge
  //println "LIVETIME: aveLivetime, livetimeFromFCratio, diff = ${aveLivetime}, ${livetimeFromFCratio}, ${aveLivetime-livetimeFromFCratio}"


  // Add points to graphs
  s = sector-1
  if(s<0||s>5) { errPrint("bad sector number $sector") }
  else {
    detectors.each{
      if (s==0) { //NOTE: Don't double count over sectors
        grF[it].addPoint(filenum,fcCharge,0,0)
        grT[it].addPoint(filenum,liveTime,0,0)
        grU[it].addPoint(filenum,ufcCharge,0,0)//NOTE: ADDED 5/17/23
      }
      if (it.equals('eCFD')) {
          grNP[it][pid_e][0][s].addPoint(filenum,trigRat,0,0)
          grNP[it][pid_e][1][s].addPoint(filenum,nElec,0,0)
      }
      if (it.equals('eFT') && s==0) { // currently same for all sectors...
          grNP[it][pid_e][0].addPoint(filenum,trigRatFT,0,0)
          grNP[it][pid_e][1].addPoint(filenum,nElecFT,0,0)
      }
      if (!it.startsWith('e')) {
        if (it.contains('FD')) { // acount for sectors
            for (pid in particles) {
              grNP[it][pid][0][s].addPoint(filenum,(fcCharge!=0 ? nParticles[it][pid][s]/fcCharge : 0),0,0)
              grNP[it][pid][1][s].addPoint(filenum,nParticles[it][pid][s],0,0)
            } //for
        } else if (s==0) {
          for (pid in particles) {
            grNP[it][pid][0].addPoint(filenum,(fcCharge!=0 ? nParticles[it][pid]/fcCharge : 0),0,0)
            grNP[it][pid][1].addPoint(filenum,nParticles[it][pid],0,0)
          } //for
        } //else
      } //else
    } //detectors.each
  } //else

} // eo loop through data_table.dat
writePlots(runnum) // write last run's graphs

// write hipo files for each detector
(0..<detectors.size()).each{
  File outHipoFile = new File(outHipoN[it])
  if(outHipoFile.exists()) outHipoFile.delete()
  outHipo[it].writeFile(outHipoN[it])
}
