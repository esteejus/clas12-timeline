// combine a new qaTree.json file with an old one
// - see README.md

import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import Tools
Tools T = new Tools()

def debug = false


if(args.length>=1) dataset = args[0]
def fdFileN = "outdat.$dataset/qaTree.json"
def ftFileN = "outdat.$dataset/qaTreeFT.json"
def FDFile = new File(fdFileN)
def FTFile = new File(ftFileN)
def slurper = new JsonSlurper()
def qaTreeFD = slurper.parse(FDFile)
def qaTreeFT = slurper.parse(FTFile)
def qaTreeMelded = [:]
def defectListFT
def meldList
def meldListOR
def defectMask
def comment
def runQAFT
def fileQAFT
def deleteComment


// loop through new qaTree runs
// run number loop
qaTreeFD.each{ runnum, fileTree ->
  if(debug) println "RUN=$runnum ---------------"
  qaTreeMelded[runnum] = [:]

  // file number loop
  fileTree.each{ filenum, fileQAFD ->
    if(debug) println "\nrun=$runnum file=$filenum"
    qaTreeMelded[runnum][filenum] = [:]
    
    // get QA info from FT qaTree
    runQAFT = qaTreeFT[runnum]
    if(runQAFT!=null) fileQAFT = qaTreeFT[runnum][filenum]
    else fileQAFT = null

    // get the comment from the FT qaTree file, if it exists; if not
    // grab the comment from the FD qaTree file
    if(fileQAFT!=null) comment = T.getLeaf(fileQAFT,['comment'])
    else comment = T.getLeaf(fileQAFD,['comment'])
    if(comment==null) comment=""
    qaTreeMelded[runnum][filenum]['comment'] = comment
    deleteComment = false

    // copy event number range from FD qaTree file
    qaTreeMelded[runnum][filenum]['evnumMin'] = fileQAFD['evnumMin']
    qaTreeMelded[runnum][filenum]['evnumMax'] = fileQAFD['evnumMax']

    // loop through sectors and meld their defect bits
    meldListOR = []
    qaTreeMelded[runnum][filenum]['sectorDefects'] = [:]
    fileQAFD['sectorDefects'].each{ sector, defectListFD ->

      meldList = []

      // meld FD defect bits
      defectListFD.each{ defect ->
        if(defect==T.bit("TotalOutlier")) meldList << defect
        if(defect==T.bit("TerminalOutlier")) meldList << defect
        if(defect==T.bit("MarginalOutlier")) meldList << defect
        if(defect==T.bit("TotalOutlierFT")) meldList << defect
        if(defect==T.bit("TerminalOutlierFT")) meldList << defect
        if(defect==T.bit("MarginalOutlierFT")) meldList << defect

        if(defect==T.bit("LowLiveTime")) meldList << defect
      }

      // meld FT defect bits
      if(fileQAFT!=null) {
        defectListFT = T.getLeaf(fileQAFT,['sectorDefects',sector])
        defectListFT.each{ defect ->
          
        
           if(defect==T.bit("TotalOutlier")) meldList << defect
        if(defect==T.bit("TerminalOutlier")) meldList << defect
        if(defect==T.bit("MarginalOutlier")) meldList << defect
        if(defect==T.bit("TotalOutlierFT")) meldList << defect
        if(defect==T.bit("TerminalOutlierFT")) meldList << defect
        if(defect==T.bit("MarginalOutlierFT")) meldList << defect

        if(defect==T.bit("LowLiveTime")) meldList << defect

        }
      }

meldList.unique()
      // add this sector's meldList to the OR of each sector's meldList,
      // and to the melded tree
      qaTreeMelded[runnum][filenum]['sectorDefects'][sector] = meldList
      meldListOR += meldList


      if(debug) {
        println "s${sector}"
        println "     FD: $defectListFD"
        println "     FT: $defectListFT"
        println "  melded: $meldList"
      }
    } // end sector loop

    // compute defect bitmask
    defectMask = 0
    meldListOR.unique().each { defectMask += (0x1<<it) }
    if(debug) {
      println "--> DEFECT: $defectMask = " + T.printBinary(defectMask)
      println "--> comment: $comment"
    }
    qaTreeMelded[runnum][filenum]['defect'] = defectMask
    if(deleteComment) qaTreeMelded[runnum][filenum]['comment'] = ""

  } // end filenum loop
} // end runnum loop


// output melded qaTree.json
new File("qaTree.json.melded").write(JsonOutput.toJson(qaTreeMelded))
['FD','FT','melded'].each{"./prettyPrint.sh $it".execute()}
