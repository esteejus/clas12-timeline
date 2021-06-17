import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
sectors = (0..<6)
def sec = { int i -> i+1 }
myDirs = sectors.collect{ new TDirectory() }
// myDirs.each{print(it);print("\n")}
runnum = 0
def defineGraph = { det,name,ytitle ->
//TODO: Added: option for looping 'FD' sectors or not
  if (det.contains('FD') || det.equals('')) { //default plot to sectors
    sectors.collect {
      def g = new GraphErrors(name+"_${runnum}"+(detectors.contains(det) ? "_"+det : "")+"_"+sec(it))
      def gT = ytitle+" vs. file number "+det+" -- Sector "+sec(it)
      g.setTitle(gT)
      g.setTitleY(ytitle)
      g.setTitleX("file number")
      return g
    }
  } // if (det.contains('FD'))
  else {
    def g = new GraphErrors(name+"_${runnum}_"+det)
    def gT = ytitle+" vs. file number "+det
    g.setTitle(gT)
    g.setTitleY(ytitle)
    g.setTitleX("file number")
    return g
  }
}
graphs = defineGraph("FD","NAME","YTITLE")
sectors.each{print(myDirs[it]);print("\n");myDirs[it].mkdir("/run");myDirs[it].cd("/run");myDirs[it].addDataSet(graphs[it])}

// write hipo files for each detector
outHipoN = sectors.collect{ "${it}_.hipo" }
sectors.each{
  File outHipoFile = new File(outHipoN[it])
  if(outHipoFile.exists()) outHipoFile.delete()
  myDirs[it].writeFile(outHipoN[it])
}
