// a more general monitor, for things like <sinPhiH> or helicity
// - this reads DST files or skim files
// - can be run on slurm
// - note: search for 'CUT' to find which cuts are applied

import org.jlab.io.hipo.HipoDataSource
import org.jlab.clas.physics.Particle
import org.jlab.clas.physics.Vector3
import org.jlab.groot.data.H1F
import org.jlab.groot.data.H2F
import org.jlab.groot.data.TDirectory
import org.jlab.clas.physics.LorentzVector
import org.jlab.clas.physics.Vector3
import org.jlab.detector.base.DetectorType
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.lang.Math.*
import Tools // (make sure `.` is in $CLASSPATH)
import java.lang.management.* //TODO: added
Tools T = new Tools()

// OPTIONS
def segmentSize = 10000 // number of events in each segment
def inHipoType = "dst" // options: "dst", "skim"\

//TODO: Added list of particle to count
def particles = [211,-211,22] // options: -addPs=pid1,pid2,... adds to current list
                              //          -ps=pid1,pid2,... modifies current list


// ARGUMENTS
def inHipo = "skim/skim4_5052.hipo" // directory of DST files, or a single SKIM file
if(args.length==0) { println "Usage: run-groovy monitorRead.groovy [hipofile] [hipotype] [particle pidlist option]"; return;}
if(args.length>=1) inHipo = args[0]
if(args.length>=2) inHipoType = args[1]

//TODO: Added command line options to modify list of particles to count
if(args.length>=3 && args[2].startsWith("-ps=")) particles = args[2].split('=')[1].split(',')
if(args.length>=3 && args[2].startsWith("-addPs=")) particles.addAll(arg.split('=')[1].split(','))
particles = particles.collect{ el -> el.toInteger() } // Argument values are strings!
particles.unique() // Make sure you don't have multiple identical entries

// get hipo file names
def inHipoList = []
if(inHipoType=="dst") {
  def inHipoDirObj = new File(inHipo)
  def inHipoFilter = ~/.*\.hipo/
  inHipoDirObj.traverse( type: groovy.io.FileType.FILES, nameFilter: inHipoFilter ) {
    if(it.size()>0) inHipoList << inHipo+"/"+it.getName()
  }
  inHipoList.sort()
  if(inHipoList.size()==0) {
    System.err << "ERROR: no hipo files found in this directory\n"
    return
  }
}
else if(inHipoType=="skim") { inHipoList << inHipo }
else {
  System.err << "ERROR: unknown inHipoType setting\n"
  return
}

// get runnum
def runnum
if(inHipoType=="skim") {
  if(inHipo.contains('postprocess'))
    runnum = inHipo.tokenize('.')[-2].tokenize('/')[-1].toInteger()
  else
    runnum = inHipo.tokenize('.')[-2].tokenize('_')[-1].toInteger()
}
else if(inHipoType=="dst") {
  runnum = inHipo.tokenize('/')[-1].toInteger()
}
println "runnum=$runnum"


//////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////
// RUN GROUP DEPENDENT SETTINGS //////////////////////////

def RG = "unknown"
if(runnum>=4763 && runnum<=5001) RG="RGA" // early period
else if(runnum>=5032 && runnum<=5262) RG="RGA" // inbending1
else if(runnum>=5300 && runnum<=5666) RG="RGA" // inbending1 + outbending
else if(runnum>=5674 && runnum<=6000) RG="RGK" // 6.5+7.5 GeV
else if(runnum>=6120 && runnum<=6604) RG="RGB" // spring 19
else if(runnum>=6616 && runnum<=6783) RG="RGA" // spring 19
else if(runnum>=11093 && runnum<=11300) RG="RGB" // fall 19
else if(runnum>=11323 && runnum<=11571) RG="RGB" // winter 20
else if(runnum>=12210 && runnum<=12951) RG="RGF" // spring+summer 20
else System.err << "WARNING: unknown run group; using default run-group-dependent settings (see monitorRead.groovy)\n"
println "rungroup = $RG"

// helFlip: if true, REC::Event.helicity has opposite sign from reality
def helFlip = false
if(RG=="RGA") helFlip = true
else if(RG=="RGB") {
  helFlip = true
  if(runnum>=11093 && runnum<=11283) helFlip = false // fall, 10.4 GeV period only
  else if(runnum>=11323 && runnum<=11571) helFlip = false // winter
};
else if(RG=="RGK") helFlip = false
else if(RG=="RGF") helFlip = true

// beam energy
// - hard-coded; could instead get from RCDB, but sometimes it is incorrect
def EBEAM = 10.6041 // RGA default
if(RG=="RGA") {
  if(runnum>=6616 && runnum<=6783) EBEAM = 10.1998 // spring 19
  else EBEAM = 10.6041
}
else if(RG=="RGB") {
  if(runnum>=6120 && runnum<=6399) EBEAM = 10.5986 // spring
  else if(runnum>=6409 && runnum<=6604) EBEAM = 10.1998 // spring
  else if(runnum>=11093 && runnum<=11283) EBEAM = 10.4096 // fall
  else if(runnum>=11284 && runnum<=11300) EBEAM = 4.17179 // fall BAND_FT
  else if(runnum>=11323 && runnum<=11571) EBEAM = 10.3894 // winter (RCDB may still be incorrect)
  else System.err << "ERROR: unknown beam energy\n"
}
else if(RG=="RGK") {
  if(runnum>=5674 && runnum<=5870) EBEAM = 7.546
  else if(runnum>=5875 && runnum<=6000) EBEAM = 6.535
  else System.err << "ERROR: unknown beam energy\n"
}
else if(RG=="RGF") {
  if     (runnum>=12210 && runnum<=12388) EBEAM = 10.389 // RCDB may still be incorrect
  else if(runnum>=12389 && runnum<=12443) EBEAM =  2.186 // RCDB may still be incorrect
  else if(runnum>=12444 && runnum<=12951) EBEAM = 10.389 // RCDB may still be incorrect
  else System.err << "ERROR: unknown beam energy\n"
}

// gated FC charge determination
// - 0: use workaround method: ungated FC charge * average livetime
//      - needed if cooked with 6.5.3 or below, or without the recharge option
// - 1: use RUN::scaler:fcupgated - should be ok if data cooked with recharge option
// - 2: use REC::Event:beamCharge - useful if RUN::scaler is unavailable
def FCmode = 1
if(RG=="RGA") {
  FCmode = 0 // fall inbending+outbending
  if(runnum>=6616 && runnum<=6783) { // spring19
    FCmode=1; // default
    if(runnum==6724) FCmode=0; // fcupgated charge spike in file 230
  };
}
else if(RG=="RGB") {
  FCmode = 1 // default
  if( runnum in [6263, 6350, 6599, 6601, 11119] ) FCmode=0 // fcupgated charge spikes
}
else if(RG=="RGK") FCmode = 0
else if(RG=="RGF") FCmode = 0

// FC attenuation fix
// RGB runs <6400 had wrong attenuation, need to use
// fc -> fc*9.96025
// (this is programmed in below, but mentioned here for documentation)

//////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////
//////////////////////////////////////////////////////////



// prepare output table for electron count and FC charge
"mkdir -p outdat".execute()
def datfile = new File("outdat/data_table_${runnum}.dat")
def datfileWriter = datfile.newWriter(false)

//TODO: Added for JVM monitoring
"mkdir -p logs".execute()
def logfile = new File("logs/jvm_log_${runnum}.dat")
def logfileWriter = logfile.newWriter(false)
logfileWriter.close() //NOTE: Overwrites existing log file
def logcounter = 0


// property lists
def partList = [ 'pip', 'pim' ]
def helList = [ 'hp', 'hm' ]
def heluList = [ 'hp', 'hm', 'hu' ]


// build tree 'histTree', for storing histograms
def histTree = [:]

T.buildTree(histTree,'helic',[
  ['sinPhi'],
  partList,
  helList
],{ new H1F() })

T.buildTree(histTree,'helic',[
  ['dist']
],{ new H1F() })

T.buildTree(histTree,'helic',[
  ['distGoodOnly']
],{ new H1F() })

T.buildTree(histTree,'DIS',[
  ['Q2','W','x','y']
],{ new H1F() })

T.buildTree(histTree,"DIS",[
  ['Q2VsW']
],{ new H2F() })

T.buildTree(histTree,"inclusive",[
  partList,
  ['p','pT','z','theta','phiH']
],{ new H1F() })

/*
println("---\nhistTree:"); 
T.printTree(histTree,{T.leaf.getClass()});
println("---")
*/


// subroutine to build a histogram
def buildHist(histName, histTitle, propList, runn, nb, lb, ub, nb2=0, lb2=0, ub2=0) {

  def propT = [ 
    'pip':'pi+',
    'pim':'pi-', 
    'hp':'hel+',
    'hm':'hel-',
    'hu':'hel?'
  ]

  def pn = propList.join('_')
  def pt = propList.collect{ propT.containsKey(it) ? propT[it] : it }.join(' ')
  if(propList.size()>0) { pn+='_'; }

  def sn = propList.size()>0 ? '_':''
  def st = propList.size()>0 ? ' ':''
  def hn = "${histName}_${pn}${runn}"
  def ht = "${pt} ${histTitle}"

  if(nb2==0) return new H1F(hn,ht,nb,lb,ub)
  else return new H2F(hn,ht,nb,lb,ub,nb2,lb2,ub2)
}

// define variables
def event
def pidList = []
def particleBank
def FTparticleBank
def configBank
def eventBank
def calBank
def scalerBank
def pipList = []
def pimList = []
def eleList = []
def disElectron
def disEleFound
def eleSec
def eventNum
def eventNumList = []
def eventNumMin, eventNumMax
def segmentNum
def segmentDev
def helicity
def helStr
def helDefined
def phi
def runnumTmp = -1
def reader
def evCount
def segment
def segmentTmp = -1
def nbins
def sectors = 0..<6
def nElec = sectors.collect{0}
def nElecFT = 0
def LTlist = []
def FClist = []
def UFClist = []
def rellumG, rellumU
def detIdEC = DetectorType.ECAL.getDetectorId()
def Q2
def W
def nu
def x,y,z
def p,pT,theta,phiH
def countEvent
def caseCountNtrigGT1 = 0
def caseCountNFTwithTrig = 0
def nElecTotal

//DEBUGGING: Moving stuff around
// subroutine to write out to hipo file
def outHipo = new TDirectory()
outHipo.mkdir("/$runnum")
outHipo.cd("/$runnum")

//----------------------
// event loop
//----------------------
evCount = 0
inHipoList.each { inHipoFile ->

//TODO: Added
def particle = [:]
def pFound = [:]
for (pid in particles) { particle.put(pid,[]); pFound.put(pid,false) }

//TODO: Added
def pSec = [:] // just for FD currently...
for (pid in particles) { pSec.put(pid,-1) } // set sector to -1 as default not sure if this will mess things up

//TODO: Added
def nParticlesCD = [:]
def nParticlesFD = [:]
def nParticlesFT = [:]
for (pid in particles) { nParticlesCD.put(pid,0); nParticlesFD.put(pid,sectors.collect{0}); nParticlesFT.put(pid,0) }

//TODO: Added
def pcaseCountNCDGT1 = [:]
def pcaseCountNFDGT1 = [:]
def pcaseCountNFT = [:]
for (pid in particles) { pcaseCountNCDGT1.put(pid,0); pcaseCountNFDGT1.put(pid,0); pcaseCountNFT.put(pid,0) }
def npTotal = [:]

//TODO: Added
for (pid in particles) { npTotal.put(pid,0) }
def pInCD = [:]
def pInFD = [:]
def pInFT = [:]
for (part in particles) { pInCD.put(part,false); pInFD.put(part,false); pInFT.put(part,false) }

// lorentz vectors
def vecBeam = new LorentzVector(0, 0, EBEAM, EBEAM)
def vecTarget = new LorentzVector(0, 0, 0, 0.938)
def vecEle = new LorentzVector()
def vecH = new LorentzVector()
def vecQ = new LorentzVector()
def vecW = new LorentzVector()


// subroutine to increment the number of counted electrons
def disElectronInTrigger
def disElectronInFT
def countTriggerElectrons = { eleRows,eleParts ->

  // reset some vars
  def Emax = 0
  def Etmp
  disElectronInTrigger = false
  disElectronInFT = false
  def nTrigger = 0
  def nFT = 0
  disEleFound = false

  // loop over electrons from REC::Particle
  if(eleRows.size()>0) {
    eleRows.eachWithIndex { row,ind ->

      def status = particleBank.getShort('status',row)
      def chi2pid = particleBank.getFloat('chi2pid',row)

      // TRIGGER ELECTRONS (FD or CD) CUT
      // - must have status<0 and FD or CD bit(s) set
      // - must have |chi2pid|<3
      // - must appear in ECAL, to obtain sector
      if( status<0 &&
          ( Math.abs(status/1000).toInteger() & 0x2 || 
            Math.abs(status/1000).toInteger() & 0x4 ) &&
          Math.abs(chi2pid)<3
      ) {

        // get sector
        def eleSecTmp = (0..calBank.rows()).collect{
          ( calBank.getShort('pindex',it).toInteger() == row &&
            calBank.getByte('detector',it).toInteger() == detIdEC ) ?
            calBank.getByte('sector',it).toInteger() : null
        }.find()

        // CUT for electron: sector must be defined
        if(eleSecTmp!=null) {

          nTrigger++ // count how many trigger electrons we looked at

          // CUT for electron: choose maximum energy electron (for triggers)
          // - choice is from both trigger and FT electron sets (see below)
          Etmp = eleParts[ind].e()
          if(Etmp>Emax) {
            Emax = Etmp
            eleSec = eleSecTmp
            disElectronInTrigger = true
            disElectronInFT = false
            disElectron = eleParts[ind]
          }

        } else {
          System.err << "WARNING: found electron with unknown sector\n"
        }
      }


      // FT trigger electrons
      // - REC::Particle:status has FT bit
      // - must also appear in RECFT::Particle with status<0 and FT bit
      // - must have E > 300 MeV
      if( Math.abs(status/1000).toInteger() & 0x1 ) {
        if( FTparticleBank.rows() > row ) {
          def FTpid = FTparticleBank.getInt('pid',row)
          def FTstatus = FTparticleBank.getShort('status',row)
          if( FTpid==11 && 
              FTstatus<0 && 
              Math.abs(FTstatus/1000).toInteger() & 0x1 &&
              eleParts[ind].e() > 0.3
          ) {

            nFT++ // count how many FT electrons we looked at

            // CUT for electron: maximum energy electron (for FT)
            // - choice is from both trigger and FT electron sets (see above)
            Etmp = eleParts[ind].e()
            if(Etmp>Emax) {
              Emax = Etmp
              disElectronInFT = true
              disElectronInTrigger = false
              disElectron = eleParts[ind]
            }

          }
        }
      }


    } // eo loop through REC::Particle
  } // eo if nonempty REC::Particle


  // calculate DIS kinematics and increment counters
  if(disElectronInTrigger || disElectronInFT) {

    
    // - calculate DIS kinematics
    // calculate Q2
    vecQ.copy(vecBeam)
    vecEle.copy(disElectron.vector())
    vecQ.sub(vecEle) 
    Q2 = -1*vecQ.mass2()

    // calculate W
    vecW.copy(vecBeam)
    vecW.add(vecTarget)
    vecW.sub(vecEle)
    W = vecW.mass()

    // calculate x and y
    nu = vecBeam.e() - vecEle.e()
    x = Q2 / ( 2 * 0.938272 * nu )
    y = nu / EBEAM


    // CUT for electron: Q2 cut
    //if(Q2<2.5) return


    // - increment counters, and set `disEleFound`
    if(disElectronInTrigger && disElectronInFT) { // can never happen (failsafe)
      System.err << "ERROR: disElectronInTrigger && disElectronInFT == 1; skip event\n"
      return
    }
    else if(disElectronInTrigger) {
      nElec[eleSec-1]++
      disEleFound = true
    }
    else if(disElectronInFT) {
      nElecFT++
      disEleFound = true
    }

    // increment 'case counters' (for studying overlap of trigger/FT cuts)
    // - case where there are more than one trigger electron in FD
    if(disElectronInTrigger && nTrigger>1)
      caseCountNtrigGT1 += nTrigger-1 // count number of unanalyzed extra electrons
    // - case where disElectron is in FT, but there are trigger electrons in FD
    if(disElectronInFT && nTrigger>0)
      caseCountNFTwithTrig += nTrigger // count number of unanalyzed trigger (FD) electrons

  }

}


// subroutine which returns a list of Particle objects of a certain PID
def findParticles = { pid ->

  // get list of bank rows and Particle objects corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  def particleList = rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }
  //println "pid=$pid  found in rows $rowList"

  //TODO: Moved this functionality to countParticles method
  // if looking for electrons, also count the number of trigger electrons,
  // and find the DIS electron
  //if(pid==11) countTriggerElectrons(rowList,particleList)

  // return list of Particle objects
  return particleList
}

// subroutine which returns a list of Particle objects of a certain PID
def countParticles = { pid ->

  // get list of bank rows and Particle objects corresponding to this PID
  def rowList = pidList.findIndexValues{ it == pid }.collect{it as Integer}
  def particleList = rowList.collect { row ->
    new Particle(pid,*['px','py','pz'].collect{particleBank.getFloat(it,row)})
  }
  //println "pid=$pid  found in rows $rowList"

  // if looking for electrons, also count the number of trigger electrons,
  // and find the DIS electron
  if(pid==11) { countTriggerElectrons(rowList,particleList);  return particleList }

  // reset some vars
  pInCD.put(pid,false)
  pInFD.put(pid,false)
  pInFT.put(pid,false)
  def nCD = 0
  def nFD = 0
  def nFT = 0
  pFound.put(pid,false)
  
  if(rowList.size()>0) {
    rowList.eachWithIndex { row,ind ->

      // Reset for arbitrary particles since you can have several per event unlike scattered electron
      pInCD.put(pid,false)
      pInFD.put(pid,false)
      pInFT.put(pid,false)
      pFound.put(pid,false)

      def status = particleBank.getShort('status',row)
      def chi2pid = particleBank.getFloat('chi2pid',row) //TODO: not used currently

      // PARTICLES (CD) CUT
      // - must CD bit set
      if( Math.abs(status/1000).toInteger() & 0x4 ) {

          nCD++ // count how many central detector particles we looked at

          pInCD.put(pid,true)
          pInFT.put(pid,false)
          particle.get(pid).add(particleList[ind])

      } // CD Particles

      // PARTICLES (FD) CUT
      // - must FD bit set
      // - must appear in ECAL, to obtain sector
      if( Math.abs(status/1000).toInteger() & 0x2) {

        // get sector
        def pSecTmp = (0..calBank.rows()).collect{
          ( calBank.getShort('pindex',it).toInteger() == row /*&&
            calBank.getByte('detector',it).toInteger() == detIdEC*/ ) ? //TODO: Not detIdEC but just cal?
            calBank.getByte('sector',it).toInteger() : null
        }.find()

        // CUT for particles: sector must be defined
        if(pSecTmp!=null) {

          nFD++ // count how many forward/central detector particles we looked at

          pSec.put(pid,pSecTmp)
          pInFD.put(pid,true)
          pInFT.put(pid,false)
          particle.get(pid).add(particleList[ind])

        } else {
          // System.err << "WARNING: found particle with unknown sector, pid = ${pid}\n" //TODO: Commented out since you get a lot of these....
        }
      } // FD Particles

      // FT Particles
      // - REC::Particle: status has FT bit
      // - must also appear in RECFT::Particle with FT bit (NOTE: CURRENTLY NOT REQUIRED) (Don't think it changed anything either...)
      if( Math.abs(status/1000).toInteger() & 0x1 ) {
        if( FTparticleBank.rows() > row ) {
          def FTpid = FTparticleBank.getInt('pid',row)
          def FTstatus = FTparticleBank.getShort('status',row)
          if( (FTpid==pid && Math.abs(FTstatus/1000).toInteger() & 0x1) || pid!=9999 ) {

            nFT++ // count how many FT particles we looked at

            pInFT.put(pid,true)
            particle.get(pid).add(particleList[ind])
          }
        }
      } // FT Particles

      // Increment counters
      if(pInCD.get(pid) || pInFD.get(pid) || pInFT.get(pid)) {

        // TODO: Not sure if this actually applies for all particles...
        // - increment counters, and set `pFound`
        if((pInCD.get(pid) || pInFD.get(pid)) && pInFT.get(pid)) { // can never happen (failsafe) except for photons?
          if (pid!=22) System.err << "ERROR: pInCFD[" << pid << "] && pInFT[" << pid << "] == 1; skip particle\n"
          return;
        }
        else if(pInCD.get(pid)) {
          nParticlesCD[pid]++ // NOTE: For some reason trying .get(pid)++ does not work...
          //pFound.put(pid,true)
        }
        else if(pInFD.get(pid)) {
          nParticlesFD.get(pid)[pSec.get(pid)-1]++
          //pFound.put(pid,true)
        }
        else if(pInFT.get(pid)) {
          nParticlesFT[pid]++ // NOTE: For some reason trying .get(pid)++ does not work...
          //pFound.put(pid,true)
        }

        pFound.put(pid,true)

        //TODO: Not really sure if all the following is necessary...needs to be refined...

        // // increment 'case counters' (for studying overlap of CD/FD/FT cuts)
        // // - case where there are more than one particle in CD
        // if(pInCD.get(pid) && nCD>1)
        //   pcaseCountNCDGT1.get[pid] += nCD // count number of unanalyzed trigger (FD) electrons
        // // - case where there are more than one particle in FD
        // if(pInFD.get(pid) && nFD>1)
        //   pcaseCountNFDGT1[pid] += nFD-1 // count number of unanalyzed extra electrons
        // // - case where particle is in FT, but there are trigger electrons in FD
        // if(pInFT.get(pid) && (nCD+nFD)>0)
        //   pcaseCountNFT.get[pid] += nFD + nCD // count number of unanalyzed trigger (FD) electrons

      }

    } // eo loop through REC::Particle
  } // eo if nonempty REC::Particle

  // return list of Particle objects
  return particleList
}

// subroutine to calculate hadron (pion) kinematics, and fill histograms
// note: needs to have some kinematics defined (vecQ,Q2,W), and helStr
def fillHistos = { list, partN ->
  list.each { part ->

    // calculate z
    vecH.copy(part.vector())
    z = T.lorentzDot(vecTarget,vecH) / T.lorentzDot(vecTarget,vecQ)

    // CUT for pions: particle z
    if(z>0.3 && z<1) {

      // calculate momenta, theta, phiH
      p = vecH.p()
      pT = Math.hypot( vecH.px(), vecH.py() )
      theta = vecH.theta()
      phiH = T.planeAngle( vecQ.vect(), vecEle.vect(), vecQ.vect(), vecH.vect() )

      // CUT for pions: if phiH is defined
      if(phiH>-10000) {

        // fill histograms
        if(helDefined) {
          histTree['helic']['sinPhi'][partN][helStr].fill(Math.sin(phiH))
        }
        histTree['inclusive'][partN]['p'].fill(p)
        histTree['inclusive'][partN]['pT'].fill(pT)
        histTree['inclusive'][partN]['z'].fill(z)
        histTree['inclusive'][partN]['theta'].fill(theta)
        histTree['inclusive'][partN]['phiH'].fill(phiH)

        // tell event counter that this event has at least one particle added to histos
        countEvent = true
      }
    }
  }
}

// // subroutine to write out to hipo file
// def outHipo = new TDirectory()
// outHipo.mkdir("/$runnum")
// outHipo.cd("/$runnum")
def histN,histT
def writeHistos = {

  // get event number range
  eventNumMin = eventNumList.min()
  eventNumMax = eventNumList.max()

  // proceed only if there are data to write
  if(eventNumList.size()>0) {

    // get segment number
    if(inHipoType=="skim") {
      // segment number is average event number; include standard deviation
      // of event number as well
      segmentNum = Math.round( eventNumList.sum() / eventNumList.size() )
      segmentDev = Math.round(Math.sqrt( 
       eventNumList.collect{ n -> Math.pow((n-segmentNum),2) }.sum() / 
       (eventNumList.size()-1)
      ))
      print "eventNum ave=$segmentNum dev=$segmentDev "
      print "min=$eventNumMin max=$eventNumMax\n"
    }
    else if(inHipoType=="dst") {
      // segment number is the DST 5-file number; standard devation is irrelevant here
      // and set to 0 for compatibility with downstream code
      segmentNum = segmentTmp
      segmentDev = 0
    }

    // loop through histTree, adding histos to the hipo file;
    // note that the average event number is appended to the name
    T.exeLeaves( histTree, {
      histN = T.leaf.getName() + "_${segmentNum}_${segmentDev}"
      histT = T.leaf.getTitle() + " :: segment=${segmentNum}"
      T.leaf.setName(histN)
      T.leaf.setTitle(histT)
      outHipo.addDataSet(T.leaf)
    })
    //println "write histograms:"; T.printTree(histTree,{T.leaf.getName()})

    // get FC charge
    def ufcStart
    def ufcStop
    if(UFClist.size()>0) {
      ufcStart = UFClist.min()
      ufcStop = UFClist.max()
    } else {
      System.err << "WARNING: empty UFClist for run=${runnum} file=${segmentNum}\n"
      ufcStart = 0
      ufcStop = 0
    }

    def fcStart
    def fcStop
    LTlist.removeAll{it<0} // remove undefined livetime values
    def aveLivetime = LTlist.size()>0 ? LTlist.sum() / LTlist.size() : 0
    if(FCmode==0) {
      fcStart = ufcStart * aveLivetime // workaround method
      fcStop = ufcStop * aveLivetime // workaround method
    } else if(FCmode==1 || FCmode==2) {
      if(FClist.size()>0) {
        fcStart = FClist.min()
        fcStop = FClist.max()
      } else {
        System.err << "WARNING: empty FClist for run=${runnum} file=${segmentNum}\n"
        fcStart = 0
        fcStop = 0
      }
    }
    if(fcStart>fcStop || ufcStart>ufcStop) {
      System.err << "WARNING: faraday cup start > stop for" <<
        " run=${runnum} file=${segmentNum}\n"
    }

    // RGB attenuation correction
    if(RG=="RGB" && runnum<6400) {
      fcStart *= 9.96025
      fcStop *= 9.96025
      ufcStart *= 9.96025
      ufcStop *= 9.96025
    }

    // write number of electrons and FC charge to datfile
    sectors.each{ sec ->
      datfileWriter << [ runnum, segmentNum ].join(' ') << ' '
      datfileWriter << [ eventNumMin, eventNumMax ].join(' ') << ' '
      datfileWriter << [ sec+1, nElec[sec], nElecFT ].join(' ') << ' '
      datfileWriter << [ fcStart, fcStop, ufcStart, ufcStop, aveLivetime ].join(' ') << ' '
      //TODO: Added
      // Add length before
      datfileWriter << particles.size() << ' ' // don't forget space on end
      for (part in particles) { datfileWriter << [ part, nParticlesCD.get(part), nParticlesFD.get(part)[sec], nParticlesFT.get(part) ].join(' ') << ' ' }
      datfileWriter << '\n' // TODO: Hopefully this does not mess up tokenizing later on...
    }

    // print some stats
    /*
    nElecTotal = nElec*.value.sum()
    println "\nnumber of trigger electrons: $nElecTotal" 
    println """number of electrons that satisified FD trigger cuts, but were not analyzed...
    ...because they had subdominant E: $caseCountNtrigGT1
    ...because there was a higher-E electron satisfying FT cuts: $caseCountNFTwithTrig""" 
    caseCountNtrigGT1=0
    caseCountNFTwithTrig=0
    */
  }
  else {
    System.err << "WARNING: empty segment (segmentTmp=$segmentTmp)\n"
    System.err << " if all segments in a run are empty, there will be more errors later!"
  }

  // reset number of trigger electrons counter and FC lists
  nElec = sectors.collect{0}
  nElecFT = 0
  UFClist = []
  FClist = []
  LTlist = []
  eventNumList.clear()
  //TODO: Added reset particle counts for CD/FD/FT
  nParticlesCD = [:]
  nParticlesFD = [:]
  nParticlesFT = [:]
  for (pid in particles) { nParticlesCD.put(pid,0); nParticlesFD.put(pid,sectors.collect{0}); nParticlesFT.put(pid,0) }
}

// //----------------------
// // event loop
// //----------------------
// evCount = 0
// inHipoList.each { inHipoFile ->

  //TODO: added
  logfileWriter = logfile.newWriter(true) //NOTE: true means you are appending!

  // open skim/DST file
  reader = new HipoDataSource()
  reader.open(inHipoFile)

  // if DST file, set segment number to 5-file number
  if(inHipoType=="dst")
    segment = inHipoFile.tokenize('.')[-2].tokenize('-')[0].toInteger()

  // EVENT LOOP
  while(reader.hasEvent()) {
    //if(evCount>100000) break // limiter
    event = reader.getNextEvent()

    // get required banks
    particleBank = event.getBank("REC::Particle")
    eventBank = event.getBank("REC::Event")
    configBank = event.getBank("RUN::config")
    // get additional banks
    FTparticleBank = event.getBank("RECFT::Particle")
    calBank = event.getBank("REC::Calorimeter")
    scalerBank = event.getBank("RUN::scaler")

    // get list of PIDs, with list index corresponding to bank row
    pidList = (0..<particleBank.rows()).collect{ particleBank.getInt('pid',it) }
    //println "pidList = $pidList"

    // update segment number, if reading skim file
    if(inHipoType=="skim") segment = (evCount/segmentSize).toInteger()

    // if segment number changed, write out filled histos 
    // and/or create new histos
    if(segment!=segmentTmp) {

      // if this isn't the first segment, and if we are reading a skim file,
      // write out filled histograms; note that if reading a dst file, this
      // subroutine is instead called at the end of the event loop
      if(segmentTmp>=0 && inHipoType=="skim") writeHistos()

      // define new histograms
      nbins = 50
      T.exeLeaves( histTree.helic.sinPhi, {
        T.leaf = buildHist('helic_sinPhi','sinPhiH',T.leafPath,runnum,nbins,-1,1) 
      })
      histTree.helic.dist = buildHist('helic_dist','helicity',[],runnum,3,-1,2)
      histTree.helic.distGoodOnly = buildHist('helic_distGoodOnly','helicity (with electron cuts)',[],runnum,3,-1,2)
      histTree.DIS.Q2 = buildHist('DIS_Q2','Q^2',[],runnum,2*nbins,0,12)
      histTree.DIS.W = buildHist('DIS_W','W',[],runnum,2*nbins,0,6)
      histTree.DIS.x = buildHist('DIS_x','x',[],runnum,2*nbins,0,1)
      histTree.DIS.y = buildHist('DIS_y','y',[],runnum,2*nbins,0,1)
      histTree.DIS.Q2VsW = buildHist('DIS_Q2VsW','Q^2 vs W',[],runnum,nbins,0,6,nbins,0,12)

      T.exeLeaves( histTree.inclusive, {
        def lbound=0
        def ubound=0
        if(T.key=='p') { lbound=0; ubound=10 }
        else if(T.key=='pT') { lbound=0; ubound=4 }
        else if(T.key=='z') { lbound=0; ubound=1 }
        else if(T.key=='theta') { lbound=0; ubound=Math.toRadians(90.0) }
        else if(T.key=='phiH') { lbound=-3.15; ubound=3.15 }
        T.leaf = buildHist('inclusive','',T.leafPath,runnum,nbins,lbound,ubound)
      })

      // print the histogram names and titles
      /*
      if(segmentTmp==-1) {
        println "---\nhistogram names and titles:"
        T.printTree(histTree,{ T.leaf.getName() +" ::: "+ T.leaf.getTitle() })
        println "---"
      }
      */

      // update tmp number
      segmentTmp = segment
    }

    // get helicity and fill helicity distribution
    if(event.hasBank("REC::Event")) helicity = eventBank.getByte('helicity',0)
    else helicity = 0 // (undefined)
    if(helFlip) helicity *= -1 // flip helicity (needed for RGA pass1)
    switch(helicity) {
      case 1:  helStr='hp'; helDefined=true; break
      case -1: helStr='hm'; helDefined=true; break
      default: helDefined = false; helicity = 0; break
    }
    histTree.helic.dist.fill(helicity)

    // get FC charge
    if(scalerBank.rows()>0) {
      UFClist << scalerBank.getFloat("fcup",0) // ungated charge
      LTlist << scalerBank.getFloat("livetime",0) // livetime
      if(FCmode==1) {
        FClist << scalerBank.getFloat("fcupgated",0) // gated charge
      }
    }
    if(FCmode==2 && eventBank.rows()>0) {
      FClist << eventBank.getFloat("beamCharge",0) // gated charge
    }

    // get electron list, and increment the number of trigger electrons
    // - also finds the DIS electron, and calculates x,Q2,W,y,nu
    eleList = countParticles(11) // (`eleList` is unused) //TODO: Moved functionality from findParticles method

    //TODO: Added
    particlesList = particles.collect{ part -> countParticles(part) } // particlesList is unused

    // CUT: if a dis electron was found (see countTriggerElectrons)
    if(disEleFound) {

      if(disElectronInTrigger)
        histTree.helic.distGoodOnly.fill(helicity)

      // CUT for pions: Q2 and W and y and helicity
      if( Q2>1 && W>2 && y<0.8 && helDefined) {

        // get lists of pions
        pipList = findParticles(211)
        pimList = findParticles(-211)

        // calculate pion kinematics and fill histograms
        // countEvent will be set to true if a pion is added to the histos 
        countEvent = false
        fillHistos(pipList,'pip')
        fillHistos(pimList,'pim')

        if(countEvent) {

          // fill event-level histograms
          histTree.DIS.Q2.fill(Q2)
          histTree.DIS.W.fill(W)
          histTree.DIS.x.fill(x)
          histTree.DIS.y.fill(y)
          histTree.DIS.Q2VsW.fill(W,Q2)

          // increment event counter
          evCount++
          if(evCount % 100000 == 0) println "found $evCount events which contain a pion"

        }
      }
    }

    // add eventNum to the list of this segment's event numbers
    eventNum = BigInteger.valueOf(configBank.getInt('event',0))
    eventNumList.add(eventNum)

    //TODO: Added
    def mem = ManagementFactory.memoryMXBean
    def heapUsage = mem.heapMemoryUsage
    def nonHeapUsage = mem.nonHeapMemoryUsage
    def collectionTime = ManagementFactory.garbageCollectorMXBeans.collect{ gc -> gc.collectionTime }.sum()
    logfileWriter << [logcounter,heapUsage.used,nonHeapUsage.used,collectionTime].join(' ') << "\n"
    logcounter ++;

  } // end event loop
  reader.close()
  logfileWriter.close()//TODO: Added

  // write histograms to hipo file, and then set them to null for garbage collection
  segmentTmp = segment
  writeHistos()

  // close reader
  reader = null
  //System.gc()
} // end loop over hipo files

// write outHipo file
outHipoN = "outmon/monitor_${runnum}.hipo"
File outHipoFile = new File(outHipoN)
if(outHipoFile.exists()) outHipoFile.delete()
outHipo.writeFile(outHipoN)
if(inHipoType=="dst") datfileWriter.close()
//TODO: Added for development purposes...
else datfileWriter.close()

//TODO: added
logfileWriter.close()
