import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D
import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Tools
Tools T = new Tools()

//--------------------------------------------------------------------------
// SQLite3 IMPORTS AND DEPENDENCIES
@Grapes([
 @Grab(group='org.xerial',module='sqlite-jdbc',version='3.7.2'),
 @GrabConfig(systemClassLoader=true)
])
 
import java.sql.*
import org.sqlite.SQLite
import groovy.sql.Sql
//--------------------------------------------------------------------------

//--------------------------------------------------------------------------
// ARGUMENTS:
dataset = 'inbending1'
def detectors = ["eCFD","eFT","CD","FD","FT"]
def implementedDetectors = ['CD','FD','FT'] // for particle loops, DIS electrons are treated separately
qaBit = -1 // if positive, produce QA timeline based on QA/qa.${dataset}/qaTree.json

if(args.length>=1) dataset = args[0]

if(args.length>=2 && args[1].startsWith("-ds=")) {
  detectors = [] // reset map
  for (det in args[1].split('=')[1].split(',')) { detectors.add(det) }
}
if(args.length>=2 && args[1].startsWith("-addDs=")) { for (det in args[1].split('=')[1].split(',')) { detectors.add(det) } }
detectors.unique() // Make sure you don't double count

if(args.length>=3) qaBit = args[2].toInteger()
//--------------------------------------------------------------------------

//--------------------------------------------------------------------------
// CREATE SQL DATABASE AND TABLE
def db_path = "outdat."+dataset+"/"+dataset+".db" //TODO: Add option to manually specify path to db
def sql = Sql.newInstance("jdbc:sqlite:"+db_path, "org.sqlite.JDBC")
def force = true // force overwrite of database
if (force) sql.execute("drop table if exists "+dataset)
try { sql.execute("create table "+dataset+
      " (id integer, run integer, filenum integer,"+
      " evmin integer, evmax integer, livetime integer,"+
      "detector string, pid integer,"+
      "defect integer, sector integer, sectordefect integer, comment string)")
} catch (SQLException e) {
  println "*** ERROR ***  Database ${dataset} already exists."
  e.printStackTrace()
  System.exit(0)
}
def db
try { db = sql.dataSet(dataset) 
} catch (SQLException e) {
  println "*** ERROR *** Could not create dataset ${dataset}."
  e.printStackTrace()
  System.exit(0)
}
def id = 0 // global counter for entries added to database
//--------------------------------------------------------------------------

// vars and subroutines
def sectors = 0..<6
def sec = { int i -> i+1 }
def runnum, filenum, sector, epoch, evnumMin, evnumMax
def gr
def jPrint = { name,object -> new File(name).write(JsonOutput.toJson(object)) }

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
pidMap[0]    = ['undefined','undefined']

def passingFractions = [:]
for (det in detectors) { passingFractions[det] = [:] }

// read epochs list file
def epochFile = new File("epochs.${dataset}.txt")
if(!(epochFile.exists())) {
  System.err << "WARNING: using epochs.default.txt\n"
  epochFile = new File("epochs.default.txt")
}
//if(!(epochFile.exists())) throw new Exception("epochs.${dataset}.txt not found")

def getEpoch = { r,s ->
  //return 1 // (for testing single-epoch mode)
  def lb,ub
  def e = -1
  epochFile.eachLine { line,i ->
    (lb,ub) = line.tokenize(' ').collect{it.toInteger()}
    if(r>=lb && r<=ub) e=i
  }
  if(e<0) throw new Exception("run $r sector $s has unknown epoch")
  return e
}
def lowerBound, upperBound
def getEpochBounds = { e ->
  epochFile.eachLine { line,i ->
    if(i==e) (lowerBound,upperBound) = line.tokenize(' ').collect{it.toInteger()}
  }
}

// build map of (runnum,filenum) -> (evnumMin,evnumMax)
def dataFile = new File("outdat.${dataset}/data_table.dat")
def tok
def evnumTree = [:] // TODO: Modify how this file should be written
if(!(dataFile.exists())) throw new Exception("data_table.dat not found")
dataFile.eachLine { line ->
  tok = line.tokenize(' ')
  runnum = tok[0].toInteger()
  filenum = tok[1].toInteger()
  evnumMin = tok[2].toInteger()
  evnumMax = tok[3].toInteger()
  if(!evnumTree.containsKey(runnum))
    evnumTree[runnum] = [:]
  if(!evnumTree[runnum].containsKey(filenum)) {
    evnumTree[runnum][filenum] = [
      "evnumMin":evnumMin,
      "evnumMax":evnumMax
    ]
  }
}

// define qaTree
def qaTree // [runnum][filenum] -> defects enumeration
def slurper
def jsonFile
if(qaBit>=0) {
  slurper = new JsonSlurper()
  jsonFile = new File("QA/qa.${dataset}/qaTree.json")
  qaTree = slurper.parse(jsonFile)
}
else qaTree = [:]

// open hipo file
detectors.each{ det ->
  def inTdir = new TDirectory()
  inTdir.readFile("outmon.${dataset}/monitor"+det+".hipo")
  def inList = inTdir.getCompositeObjectList(inTdir)

  // Set sector list to just [1] if no sectors (minimal code change)
  def hasSectors = false
  if (det.contains('FD')) { sectors = 0..<6; hasSectors = true; }
  else { sectors = [0]; hasSectors = false; }
  userPIDList = []

  if (!det.startsWith('e')) inList.each { obj -> //TODO Think about what this does with FC and LT graphs...
    if(obj.contains("/grA_PID")) { userPIDList.add(obj.tokenize('_')[1].split('PID')[1].toInteger()) }
  } //NOTE: PID[Lund pid] should be second, check qaPlot.groovy
  if (userPIDList.size()==0) userPIDList = [0] // for particle specific "detectors" like 'eCFD/eFD'
  userPIDList.unique()//IMPORTANT!
  userPIDList.each{ pid ->

    // define 'ratioTree', a tree with the following structure
    /* 
      { 
        sector1: {
          epoch1: [ list of N/F values ]
          epoch2: [ list of N/F values ]
        }
        sector2: {
          epoch1: [ list of N/F values ]
          epoch2: [ list of N/F values ]
          epoch3: [ list of N/F values ]
        }
        ...
      }
    // - also define 'cutTree', which follows the same structure as 'ratioTree', but
    //   with the leaves [ list of N/F values ] replaced with [ cut boundaries (map) ]
    // - also define 'epochPlotTree', with leaves as maps of plots
    */
    def ratioTree = [:]
    def cutTree = [:]
    def epochPlotTree = [:]

    // initialize sector branches
    sectors.each{ 
      ratioTree.put((hasSectors ? sec(it) : it),[:])
      cutTree.put((hasSectors ? sec(it) : it),[:])
      epochPlotTree.put((hasSectors ? sec(it) : it),[:])
    }

    // loop over 'grA' graphs (of N/F vs. filenum), filling ratioTree leaves
    def (minA,maxA) = [100000,0]
    inList.each { obj ->
      if(obj.contains("/grA_") && (pid!=0 ? obj.contains("PID${pid}") : true)) { //TODO: Could make this nicer...
        // get runnum and sector
        runnum = 0; sector = 0;
        if (hasSectors) (runnum,sector) = obj.tokenize('_')[-2..-1].collect{ it.toInteger() } //NOTE: check qaPlot.groovy, runnum and sector (if applicable) should always be last in naming
        else { runnum = obj.tokenize('_')[-1].toInteger(); sector = 0; } //NOTE: Curly braces are important
        if(sector<0||sector>6) throw new Exception("bad sector number $sector")

        // get epoch num, then initialize epoch branch if needed
        epoch = getEpoch(runnum,sector)
        if(ratioTree[sector][epoch]==null) {
          ratioTree[sector].put(epoch,[])
          cutTree[sector].put(epoch,[:])
          epochPlotTree[sector].put(epoch,[:])
        }

        // append N/F values to the list associated to this (sector,epoch)
        // also determine minimum and maximum values of N/F
        gr = inTdir.getObject(obj)
        gr.getDataSize(0).times { i -> 
          def val = gr.getDataY(i)
          minA = val < minA ? val : minA
          maxA = val > maxA ? val : maxA
          ratioTree[sector][epoch].add(val)
        }
        //ratioTree[sector][epoch].add(runnum) // useful for testing
      }
    }
    //println T.pPrint(ratioTree)

    // subroutine for calculating median of a list
    def median = { d ->
      if(d.size()==0) {
        System.err << "WARNING: attempt to calculate median of an empty list\n"
        return -10000
      }
      d.sort()
      def m = d.size().intdiv(2)
      d.size() % 2 ? d[m] : (d[m-1]+d[m]) / 2
    }

    // establish cut lines using 'cutFactor' x IQR method, and fill cutTree
    // - note: for the FT electrons, it seems that N/F has a long tail toward
    //   lower values, so cutLo is forced to be lower 
    //TODO: Could move cutFactor to list at beginning that you can set from command line for each detector
    def cutFactor = 4.0 //cutFactors[det][pid]!=null ? cutFactors[det][pid] : 4.0
    def mq,lq,uq,iqr,cutLo,cutHi
    sectors.each { s ->

      sectorIt = (hasSectors ? sec(s) : s)
      ratioTree[sectorIt].each { epochIt,ratioList ->

        mq = median(ratioList) // middle quartile
        lq = median(ratioList.findAll{it<mq}) // lower quartile
        uq = median(ratioList.findAll{it>mq}) // upper quartile
        iqr = uq - lq // interquartile range
        cutLo = lq - cutFactor * iqr // lower QA cut boundary
        cutHi = uq + cutFactor * iqr // upper QA cut boundary

        if(!hasSectors) cutLo = lq - 1 * cutFactor * iqr // override FT low cut to be lower
        
        cutTree[sectorIt][epochIt]['mq'] = mq
        cutTree[sectorIt][epochIt]['lq'] = lq
        cutTree[sectorIt][epochIt]['uq'] = uq
        cutTree[sectorIt][epochIt]['iqr'] = iqr
        cutTree[sectorIt][epochIt]['cutLo'] = cutLo
        cutTree[sectorIt][epochIt]['cutHi'] = cutHi
      }
    }
    //jPrint("cuts.${dataset}.json",cutTree) // output cutTree to JSON
    //println T.pPrint(cutTree)

    // vars and subroutines for splitting graphs into "good" and "bad", 
    // i.e., "pass QA cuts" and "outside QA cuts", respectively
    def grA,grA_good,grA_bad
    def grN,grN_good,grN_bad
    def grF,grF_good,grF_bad
    def grT,grT_good,grT_bad
    def histA_good, histA_bad
    def nGood,nBad
    def nGoodTotal = 0
    def nBadTotal = 0
    def copyTitles = { g1,g2 ->
      g2.setTitle(g1.getTitle())
      g2.setTitleX(g1.getTitleX())
      g2.setTitleY(g1.getTitleY())
    }
    def copyPoint = { g1,g2,i ->
      g2.addPoint(g1.getDataX(i),g1.getDataY(i),g1.getDataEX(i),g1.getDataEY(i))
    }
    def splitGraph = { g ->
      def gG,gB
      gG = new GraphErrors(g.getName())
      gB = new GraphErrors(g.getName()+":red")
      copyTitles(g,gG)
      copyTitles(g,gB)
      gB.setMarkerColor(2)
      return [gG,gB]
    }

    // define 'epoch plots', which are time-ordered concatenations of all the plots, 
    // and put them in the epochPlotTree
    def defineEpochPlot = { name,ytitle,s,e ->
      def g = new GraphErrors("${name}_s${s}_e${e}")
      if(!hasSectors) g.setTitle(ytitle+" vs. file index -- epoch $e")
      else      g.setTitle(ytitle+" vs. file index -- Sector $s, epoch $e")
      g.setTitleY(ytitle)
      g.setTitleX("file index")
      return splitGraph(g) // returns list of plots ['good','bad']
    }
    def insertEpochPlot = { map,name,plots ->
      map.put(name+"_good",plots[0])
      map.put(name+"_bad",plots[1])
    }
    //TODO: Modified naming scheme
    def particleT = "${det} ${pidMap[pid][0]}"
    if (det.startsWith('e')) particleT = !hasSectors ? "Forward Tagger Electron" : "Trigger Electron"
    sectors.each { s ->
      sectorIt = (hasSectors ? sec(s) : s)
      ratioTree[sectorIt].each { epochIt,ratioList ->
        insertEpochPlot(epochPlotTree[sectorIt][epochIt],
          "grA",defineEpochPlot("grA_epoch","${particleT} N/F",sectorIt,epochIt))
        insertEpochPlot(epochPlotTree[sectorIt][epochIt],
          "grN",defineEpochPlot("grN_epoch","Number ${particleT}s N",sectorIt,epochIt))
        insertEpochPlot(epochPlotTree[sectorIt][epochIt],
          "grF",defineEpochPlot("grF_epoch","Faraday cup charge F [nC]",sectorIt,epochIt))
        insertEpochPlot(epochPlotTree[sectorIt][epochIt],
          "grT",defineEpochPlot("grT_epoch","Live Time",sectorIt,epochIt))
      }
    }

    // define output hipo files and outliers list
    def outHipoQA = new TDirectory()
    def outHipoEpochs = new TDirectory()
    def outHipoA = new TDirectory()
    def outHipoN = new TDirectory()
    def outHipoF = new TDirectory()
    def outHipoT = new TDirectory()
    def outHipoSigmaN = new TDirectory()
    def outHipoSigmaF = new TDirectory()
    def outHipoRhoNF = new TDirectory()

    // define QA timeline title and name
    def qaTitle, qaName
    if(qaBit>=0) {
      if(qaBit==100) {
        qaTitle = ":: Fraction of files with any defect"
        qaName = "Any_Defect"
      }
      else {
        qaTitle = ":: Fraction of files with " + T.bitDescripts[qaBit]
        qaName = T.bitNames[qaBit]
      }
    } else {
      qaTitle = ":: AUTOMATIC QA RESULT: Fraction of files with any defect"
      qaName = "Automatic_Result"
    }

    // define timeline graphs
    def defineTimeline = { title,ytitle,name ->
      sectors.collect { s ->
        if( (hasSectors && !(name == "F" || name == "LT")) || ( (name == "F" || name == "LT" || !hasSectors) && s==0 ) ) {//TODO: Modified condition
          def gN = !hasSectors ? "${name}_${det}_${pid}" : "${name}_${det}_${pid}_sector_"+sec(s)
          gN = (name == "F" || name == "LT") ? "${name}" : gN
          def g = new GraphErrors(gN)
          g.setTitle(title)
          g.setTitleY(ytitle)
          g.setTitleX("run number")
          return g
        }
      }.findAll()
    }
    def TLqa = defineTimeline("${particleT} ${qaTitle}","","QA")
    def TLA = defineTimeline("Number of ${particleT}s N / Faraday Cup Charge F","N/F","A")
    def TLN = defineTimeline("Number of ${particleT}s N","N","N")
    def TLF = defineTimeline("Accumulated Faraday Cup Charge [mC]","Charge","F")
    def TLT = defineTimeline("Live Time","Live Time","LT")
    def TLsigmaN = defineTimeline("${particleT} Yield sigmaN / aveN","sigmaN/aveN","sigmaN")
    def TLsigmaF = defineTimeline("Faraday Cup Charge sigmaF / aveF","sigmaF/aveF","sigmaF")
    def TLrhoNF = defineTimeline("Correlation Coefficient rho_{NF}","rho_{NF}","rhoNF")

    def TLqaEpochs
    if(!hasSectors) {
      TLqaEpochs = new GraphErrors("epoch_${det}")
      TLqaEpochs.setTitle("click the point to load graphs")
      TLqaEpochs.addPoint(1.0,1.0,0,0)
    }
    else {
      TLqaEpochs = new GraphErrors("epoch_"+(det.startsWith('e') ? det[1..<det.size()] : det)+"_sectors") //TODO: Not sure about this still...
      TLqaEpochs.setTitle("choose a sector")
      TLqaEpochs.setTitleX("sector")
      sectors.each{ TLqaEpochs.addPoint(sec(it),1.0,0,0) }
    }

    // other subroutines
    def lineMedian, lineCutLo, lineCutHi
    def elineMedian, elineCutLo, elineCutHi
    def buildLine = { graph,lb,ub,name,val ->
      new F1D(graph.getName()+":"+name,Double.toString(val),lb-3,ub+3)
    }
    def addEpochPlotPoint = { plotOut,plotIn,i,r ->
      def f = plotIn.getDataX(i) // filenum
      def n = r + f/5000.0 // "file index"
      plotOut.addPoint(n,plotIn.getDataY(i),0,0)
    }
    def writeHipo = { hipo,outList -> outList.each{ hipo.addDataSet(it) } }
    def addGraphsToHipo = { hipoFile ->
      hipoFile.mkdir("/${runnum}")
      hipoFile.cd("/${runnum}")
      writeHipo(
        hipoFile,
        [
          grA_good,grA_bad,
          grN_good,grN_bad,
          grF_good,grF_bad,
          grT_good,grT_bad,
          histA_good,histA_bad,
          lineMedian, lineCutLo, lineCutHi
        ]
      )
    }

    // subroutine for projecting a graph onto the y-axis as a histogram
    def buildHisto = { graph,nbins,binmin,binmax ->

      // expand histogram range a bit so the projected histogram is padded
      def range = binmax - binmin
      binmin -= 0.05*range
      binmax += 0.05*range

      // set the histogram names and titles
      // assumes the graph name is 'gr._.*' (regex syntax) and names the histogram 'gr.h_.*'
      def histN = graph.getName().replaceAll(/^gr./) { graph.getName().replaceAll(/_.*$/,"h") }
      def histT = graph.getTitle().replaceAll(/vs\. file number/,"distribution")

      // define histogram and set formatting
      def hist = new H1F(histN,histT,nbins,binmin,binmax)
      hist.setTitleX(graph.getTitleY())
      hist.setLineColor(graph.getMarkerColor())

      // project the graph and return the histogram
      graph.getDataSize(0).times { i -> hist.fill(graph.getDataY(i)) }
      return hist
    }

    // subroutines for calculating means and variances of lists
    def listMean = { valList, wgtList ->
      def numer = 0.0
      def denom = 0.0
      valList.eachWithIndex { val,i ->
        numer += wgtList[i] * val
        denom += wgtList[i]
      }
      return denom>0 ? numer/denom : 0
    }
    def listCovar = { Alist, Blist, wgtList, muA, muB ->
      def numer = 0.0
      def denom = 0.0
      Alist.size().times { i ->
        numer += wgtList[i] * (Alist[i]-muA) * (Blist[i]-muB)
        denom += wgtList[i]
      }
      return denom>0 ? numer/denom : 0
    }
    def listVar = { valList, wgtList, mu ->
      return listCovar(valList,valList,wgtList,mu,mu)
    }

    // subroutine to convert a graph into a list of values
    def listA, listN, listF, listT, listOne, listWgt
    def graph2list = { graph ->
      def lst = []
      graph.getDataSize(0).times { i -> lst.add(graph.getDataY(i)) }
      return lst
    }
      
    // loop over runs, apply the QA cuts, and fill 'good' and 'bad' graphs
    def muN, muF
    def varN, varF 
    def totN, totF, totA, totU, totT
    def totFacc = sectors.collect{0}
    def reluncN, reluncF
    def NF,NFerrH,NFerrL,LT
    def valN,valF,valA
    def defectList = []
    def badfile
    inList.each { obj ->
      if(obj.contains("/grA_") && (pid!=0 ? obj.contains("PID${pid}") : true)) {

        // get runnum, sector, epoch
        runnum = 0; sector = 0;
        if (hasSectors) (runnum,sector) = obj.tokenize('_')[-2..-1].collect{ it.toInteger() }
        else { runnum = obj.tokenize('_')[-1].toInteger(); sector = 0; } //NOTE: Curly braces are important here.
        epoch = getEpoch(runnum,sector)
        if(qaBit<0 && !qaTree.containsKey(runnum)) qaTree[runnum] = [:]

        // if using the FT, only loop over sector 1 (no sectors-dependence for FT)
        if( hasSectors || (!hasSectors && sector==0)) {

          // get all the graphs and convert to value lists
          grA = inTdir.getObject(obj)
          grN = inTdir.getObject(obj.replaceAll("grA","grN"))
          grF = inTdir.getObject("/${runnum}/grF_${det}_${runnum}") //NOTE: check formatting from qaPlot.groovy
          grT = inTdir.getObject("/${runnum}/grT_${det}_${runnum}")
          listA = graph2list(grA)
          listN = graph2list(grN)
          listF = graph2list(grF)
          listT = graph2list(grT)
          listOne = []
          listA.size().times{listOne<<1}

          // decide whether to enable livetime weighting
          listWgt = listOne // disable
          //listWgt = listT // enable

          // get totals
          totN = listN.sum()
          totF = listF.sum()
          totA = totN/totF
          totU = 0
          listF.size().times{ totU += listF[it] / listT[it] }
          totT = totF / totU

          // accumulated charge (units converted nC -> mC)
          // - should be same for all sectors
          totFacc[sector-1] += totF/1e6 // (same for all sectors)

          // get mean, and variance of N and F
          muN = listMean(listN,listWgt)
          muF = listMean(listF,listWgt)
          varN = listVar(listN,listWgt,muN)
          varF = listVar(listF,listWgt,muF)

          // calculate Pearson correlation coefficient
          covarNF = listCovar(listN,listF,listWgt,muN,muF)
          corrNF = covarNF / (varN*varF)

          // calculate uncertainties of N and F relative to the mean
          reluncN = Math.sqrt(varN) / muN
          reluncF = Math.sqrt(varF) / muF

          // assign Poisson statistics error bars to graphs of N, F, and N/F
          // - note that N/F error uses Pearson correlation determined from the full run's 
          //   covariance(N,F)
          grA.getDataSize(0).times { i ->
            valN = grN.getDataY(i)
            valF = grF.getDataY(i)
            grN.setError(i,0,Math.sqrt(valN))
            grF.setError(i,0,Math.sqrt(valF))
            grA.setError(i,0,
              (valN/valF) * Math.sqrt(
                1/valN + 1/valF - 2 * corrNF * Math.sqrt(valN*valF) / (valN*valF)
              )
            )
          }

          // split graphs into good and bad
          (grA_good,grA_bad) = splitGraph(grA)
          (grN_good,grN_bad) = splitGraph(grN)
          (grF_good,grF_bad) = splitGraph(grF)
          (grT_good,grT_bad) = splitGraph(grT)

          // loop through points in grA and fill good and bad graphs
          grA.getDataSize(0).times { i -> 

            filenum = grA.getDataX(i).toInteger()

            // DETERMINE DEFECT BITS, or load them from modified qaTree.json
            badfile = false
            if(qaBit<0) {

              if(!qaTree[runnum].containsKey(filenum)) {
                qaTree[runnum][filenum] = [:]
                qaTree[runnum][filenum]['evnumMin'] = evnumTree[runnum][filenum]['evnumMin']
                qaTree[runnum][filenum]['evnumMax'] = evnumTree[runnum][filenum]['evnumMax']
                qaTree[runnum][filenum]['comment'] = ""
                qaTree[runnum][filenum]['defect'] = 0
                qaTree[runnum][filenum]['livetime'] = 0//TODO: Added
                qaTree[runnum][filenum]['sectorDefects'] = sectors.collectEntries{s->[sec(s),[]]}
                qaTree[runnum][filenum]['defects'] = []
              }
              if (!qaTree[runnum][filenum].containsKey(det)) {
                qaTree[runnum][filenum][det] = [:]
                qaTree[runnum][filenum][det]['defect'] = 0
              }
              if (!qaTree[runnum][filenum][det].containsKey(pid)) {
                qaTree[runnum][filenum][det][pid] = [:]
                qaTree[runnum][filenum][det][pid]['evnumMin'] = evnumTree[runnum][filenum]['evnumMin']
                qaTree[runnum][filenum][det][pid]['evnumMax'] = evnumTree[runnum][filenum]['evnumMax']
                qaTree[runnum][filenum][det][pid]['comment'] = ""
                qaTree[runnum][filenum][det][pid]['defect'] = 0
                qaTree[runnum][filenum][det][pid]['defects'] = []
                if (hasSectors) {
                  qaTree[runnum][filenum][det][pid]['sectorDefects'] = sectors.collectEntries{s->[sec(s),0]}//TODO: Change to zero from empty list
                }
              }

              // get variables needed for checking for defects
              NF = grA.getDataY(i)
              NFerrH = NF + grA.getDataEY(i)
              NFerrL = NF - grA.getDataEY(i)
              cutLo = cutTree[sector][epoch]['cutLo']
              cutHi = cutTree[sector][epoch]['cutHi']
              LT = grT.getDataY(i)

              defectList = []
              // set outlier bit
              if( NF<cutLo || NF>cutHi ) {
                if( NFerrH>cutLo && NFerrL<cutHi ) {
                  defectList.add(T.bit("MarginalOutlier"))
                } else if( i==0 || i+1==grA.getDataSize(0) ) {
                  defectList.add(T.bit("TerminalOutlier"))
                } else {
                  defectList.add(T.bit("TotalOutlier"))
                }
              }
              // set FC bit
              def livetime_defect = 0
              if( LT<0.9 ) {
                qaTree[runnum][filenum]['defects'].add(T.bit("LowLiveTime"))
                qaTree[runnum][filenum][det][pid]['defects'].add(T.bit("LowLiveTime"))
                livetime_defect = (Integer)T.bit("LowLiveTime")
                qaTree[runnum][filenum]['livetime'] = livetime_defect//TODO: Added
              }

              // insert in qaTree
              def evmin = evnumTree[runnum][filenum]['evnumMin']
              def evmax = evnumTree[runnum][filenum]['evnumMax']
              def defects = ( defectList.size()==0 ? 0 : defectList.sum() ) //.collect{2**it} //NOTE: Currently defectList should only have one entry
              if (hasSectors) {
                qaTree[runnum][filenum][det][pid]['sectorDefects'][sector] = defectList.collect()
                if (qaTree[runnum][filenum][det][pid]['defect']==0 && defectList.sum()>0) { qaTree[runnum][filenum][det][pid]['defect'] = 1 }
                if (qaTree[runnum][filenum][det]['defect']==0 && defectList.sum()>0) { qaTree[runnum][filenum][det]['defect'] = 1 }
                if (qaTree[runnum][filenum]['defect']==0 && defectList.sum()>0) { qaTree[runnum][filenum]['defect'] = 1 }
                db.add(id:id,run:runnum,filenum:filenum,evmin:evmin,evmax:evmax,livetime:livetime_defect,
                  detector:det,pid:pid,defect:0,sector:sector,sectordefect:defects,comment:"")
                id++
              } else {
                qaTree[runnum][filenum][det][pid]['defects'] = defectList.collect()
                if (qaTree[runnum][filenum][det][pid]['defect']==0 && defectList.sum()>0) { qaTree[runnum][filenum][det][pid]['defect'] = 1 }
                if (qaTree[runnum][filenum][det]['defect']==0 && defectList.sum()>0) { qaTree[runnum][filenum][det]['defect'] = 1 }
                if (qaTree[runnum][filenum]['defect']==0 && defectList.sum()>0) { qaTree[runnum][filenum]['defect'] = 1 }
                db.add(id:id,run:runnum,filenum:filenum,evmin:evmin,evmax:evmax,livetime:livetime_defect,
                detector:det,pid:pid,defect:defects,sector:sector,sectordefect:0,comment:"")
                id++
              }

              badfile = defectList.size() > 0
            }
            else {
              // lookup defectList for this sector
              if(qaBit==100) { // bad if not perfect
                badfile = qaTree["$runnum"]["$filenum"]['sectorDefects']["$sector"].size() > 0 //TODO: Update this...
              } else { // bad only if defectList includes qaBit
                if(qaTree["$runnum"]["$filenum"]['sectorDefects']["$sector"].size()>0) {
                  badfile = qaBit in qaTree["$runnum"]["$filenum"]['sectorDefects']["$sector"]
                }
              }
            }

            // send points to "good" or "bad" graphs
            if(badfile) {
              copyPoint(grA,grA_bad,i)
              copyPoint(grN,grN_bad,i)
              copyPoint(grF,grF_bad,i)
              copyPoint(grT,grT_bad,i)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grA_bad'],grA,i,runnum)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grN_bad'],grN,i,runnum)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grF_bad'],grF,i,runnum)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grT_bad'],grT,i,runnum)
            } else {
              copyPoint(grA,grA_good,i)
              copyPoint(grN,grN_good,i)
              copyPoint(grF,grF_good,i)
              copyPoint(grT,grT_good,i)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grA_good'],grA,i,runnum)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grN_good'],grN,i,runnum)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grF_good'],grF,i,runnum)
              addEpochPlotPoint(epochPlotTree[sector][epoch]['grT_good'],grT,i,runnum)
            }
          }

          // fill histograms
          histA_good = buildHisto(grA_good,250,minA,maxA)
          histA_bad = buildHisto(grA_bad,250,minA,maxA)

          // define lines
          lowerBound = grA.getDataX(0)
          upperBound = grA.getDataX(grA.getDataSize(0)-1)
          lineMedian = buildLine(
            grA,lowerBound,upperBound,"median",cutTree[sector][epoch]['mq'])
          lineCutLo = buildLine(
            grA,lowerBound,upperBound,"cutLo",cutTree[sector][epoch]['cutLo'])
          lineCutHi = buildLine(
            grA,lowerBound,upperBound,"cutHi",cutTree[sector][epoch]['cutHi'])

          // write graphs to hipo file
          addGraphsToHipo(outHipoQA)
          addGraphsToHipo(outHipoA)
          addGraphsToHipo(outHipoN)
          addGraphsToHipo(outHipoF)
          addGraphsToHipo(outHipoT)
          addGraphsToHipo(outHipoSigmaN)
          addGraphsToHipo(outHipoSigmaF)
          addGraphsToHipo(outHipoRhoNF)

          // fill timeline points
          nGood = grA_good.getDataSize(0)
          nBad = grA_bad.getDataSize(0)
          nGoodTotal += nGood
          nBadTotal += nBad
          TLqa[sector-1].addPoint(
            runnum,
            nGood+nBad>0 ? nBad/(nGood+nBad) : 0,
            0,0
          )
          TLA[sector-1].addPoint(runnum,totA,0,0)
          TLN[sector-1].addPoint(runnum,totN,0,0)
          if (sector==0) TLF[0].addPoint(runnum,totFacc[sector-1],0,0)
          if (sector==0) TLT[0].addPoint(runnum,totT,0,0)
          TLsigmaN[sector-1].addPoint(runnum,reluncN,0,0)
          TLsigmaF[sector-1].addPoint(runnum,reluncF,0,0)
          TLrhoNF[sector-1].addPoint(runnum,corrNF,0,0)
        }
      }
    }

    // assign defect masks
    qaTree.each { qaRun, qaRunTree -> 
      qaRunTree.each { qaFile, qaFileTree ->
        def defList = []
        def defMask = 0
        qaFileTree["sectorDefects"].each { qaSec, qaDefList ->
          defList += qaDefList.collect{it.toInteger()}
        }
        defList.unique().each { defMask += (0x1<<it) }
        qaTree[qaRun][qaFile]["defect"] = defMask
      }
    }

    // write epoch plots to hipo file
    sectors.each { s ->
      sectorIt = (hasSectors ? sec(s) : s)

      if( hasSectors || (!hasSectors && sectorIt==1)) {
        outHipoEpochs.mkdir("/${sectorIt}")
        outHipoEpochs.cd("/${sectorIt}")
        epochPlotTree[sectorIt].each { epochIt,map ->

          getEpochBounds(epochIt) // sets lower(upper)Bound
          elineMedian = buildLine(
            map['grA_good'],lowerBound,upperBound,"median",cutTree[sectorIt][epochIt]['mq'])
          elineCutLo = buildLine(
            map['grA_good'],lowerBound,upperBound,"cutLo",cutTree[sectorIt][epochIt]['cutLo'])
          elineCutHi = buildLine(
            map['grA_good'],lowerBound,upperBound,"cutHi",cutTree[sectorIt][epochIt]['cutHi'])

          histA_good = buildHisto(map['grA_good'],500,minA,maxA)
          histA_bad = buildHisto(map['grA_bad'],500,minA,maxA)

          writeHipo(outHipoEpochs,map.values())
          writeHipo(outHipoEpochs,[histA_good,histA_bad])
          writeHipo(outHipoEpochs,[elineMedian,elineCutLo,elineCutHi])
        }
      }
    }

    // write timelines to output hipo files
    def particleN
    def writeTimeline = { tdir,timeline,title,once=false ->
      tdir.mkdir("/timelines")
      tdir.cd("/timelines")
      if(once) {
        def name = timeline[0].getName().replaceAll(/_sector.*$/,"")
        timeline[0].setName(name)
        tdir.addDataSet(timeline[0])
      }
      else {
        timeline.each { tdir.addDataSet(it) }
      }
      def outHipoName = "outmon.${dataset}/${title}.hipo"
      File outHipoFile = new File(outHipoName)
      if(outHipoFile.exists()) outHipoFile.delete()
      tdir.writeFile(outHipoName)
    }
    particleN = "${pidMap[pid][1]}_${det}"
    if (det.startsWith('e')) particleN = "electron_" + (!hasSectors ? "FT" : "trigger") //TODO: Could just use the 'eCFD/eFT' detector names here...
    writeTimeline(outHipoQA,TLqa,"${particleN}_yield_QA_${qaName}",false)
    writeTimeline(outHipoA,TLA,"${particleN}_yield_normalized_values")
    writeTimeline(outHipoN,TLN,"${particleN}_yield_values")
    writeTimeline(outHipoSigmaN,TLsigmaN,"${particleN}_yield_stddev")
    if(hasSectors) {
      writeTimeline(outHipoF,TLF,"faraday_cup_charge",true)
      writeTimeline(outHipoT,TLT,"live_time",true)
      writeTimeline(outHipoSigmaF,TLsigmaF,"faraday_cup_stddev",true)
    }
    writeTimeline(outHipoRhoNF,TLrhoNF,"faraday_cup_vs_${particleN}_yield_correlation",true)

    outHipoEpochs.mkdir("/timelines")
    outHipoEpochs.cd("/timelines")
    outHipoEpochs.addDataSet(TLqaEpochs)
    outHipoName = "outmon.${dataset}/${particleN}_yield_QA_epoch_view.hipo"
    File outHipoEpochsFile = new File(outHipoName)
    if(outHipoEpochsFile.exists()) outHipoEpochsFile.delete()
    outHipoEpochs.writeFile(outHipoName)

    // print total QA passing fractions
    def PF = nGoodTotal / (nGoodTotal+nBadTotal)
    def FF = 1-PF
    if(qaBit<0) {
      println "\nQA cut overall passing fraction for detector ${det} and PID ${pid}: $PF";
      passingFractions[det][pid] = "$PF"
    }
    else {
      def PFfile = new File("outdat.${dataset}/passFractions${det}"+(det.startsWith('e') ? "" : "_PID${pid}")+".dat")
      def PFfileWriter = PFfile.newWriter(qaBit>0?true:false)
      def PFstr = qaBit==100 ? "Fraction of golden files (no defects): $PF" :
                              "Fraction of files with "+T.bitDescripts[qaBit]+": $FF"
      PFfileWriter << PFstr << "\n"
      PFfileWriter.close()
    }
  } //userPIDList.each
}//detectors.each

// sort qaTree and output to json file
//println T.pPrint(qaTree)
qaTree.each { qaRun, qaRunTree -> qaRunTree.sort{it.key.toInteger()} }
qaTree.sort()
new File("outdat.${dataset}/qaTree.json").write(JsonOutput.toJson(qaTree))

// Print out files fractions passed by pid/detector
def header = ["Detector","PID","# Files passing"]; printf(sprintf(' %1$-10s %2$-10s %3$-10s\n', header))
println " ---------------------------------------------------------------------------- "
for (det in passingFractions.keySet()) { for (pid in passingFractions[det].keySet()) {
    def row = [det, (det.startsWith('e') ? 11 : Integer.toString(pid)), passingFractions[det][pid]];
    printf(sprintf(' %1$-10s %2$-10s %3$-10s\n', row))
} }
