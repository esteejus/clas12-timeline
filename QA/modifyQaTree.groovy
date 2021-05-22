import groovy.json.JsonSlurper
import groovy.json.JsonOutput
import java.util.Date
import Tools
Tools T = new Tools()


// list of commands and description
def usage = [:]
usage["setBit"] = "setBit: overwrites stored defectBit(s) with specified bit"
usage["addBit"] = "addBit: add specified bit to defectBit(s)"
usage["delBit"] = "delBit: delete specified bit from defectBit(s)"
usage["sectorLoss"] = "sectorLoss: specify a sector loss"
usage["setComment"] = "setComment: change or delete the comment"
usage["addComment"] = "addComment: append a comment"
usage["custom"] = "custom: do a custom action (see code)"
usage["lossFT"] = "lossFT: specify a FT loss"
println("\n\n")


// check arguments and print usage
def cmd
if(args.length>=1) cmd = args[0]
else { 
  println(
  """
  syntax: modify.sh [command] [arguments]\n
List of Commands:
  """)
  usage.each{ println("- "+it.value) }
  println("\ntype any command without arguments for usage for that command\n")
  return
}


// backup qaTree.json
def D = new Date()
("cp qa/qaTree.json qa/qaTree.json."+D.getTime()+".bak").execute()


// open qaTree.json
infile="qa/qaTree.json"
def slurper = new JsonSlurper()
def jsonFile = new File(infile)
def qaTree = slurper.parse(jsonFile)


// subroutine to recompute defect bitmask
def recomputeDefMask = { runnum,filenum ->
  defList = []
  defMask = 0
  (1..6).each{ s -> 
    qaTree["$runnum"]["$filenum"]["sectorDefects"]["$s"].unique()
    defList +=
      qaTree["$runnum"]["$filenum"]["sectorDefects"]["$s"].collect{it.toInteger()}
  }
  defList.unique().each { defMask += (0x1<<it) }
  qaTree["$runnum"]["$filenum"]["defect"] = defMask
}



///////////////////////
// COMMAND EXECUTION //
///////////////////////


if( cmd=="setBit" || cmd=="addBit" || cmd=="delBit") {
  def rnum,fnumL,fnumR
  def bit
  def secList = []
  if(args.length>5) {
    bit = args[1].toInteger()
    rnum = args[2].toInteger()
    fnumL = args[3].toInteger()
    fnumR = args[4].toInteger()
    if(args[5]=="all") secList = (1..6).collect{it}
    else (5..<args.length).each{ secList<<args[it].toInteger() }

    println("run $rnum files ${fnumL}-"+(fnumR==1 ? "END" : fnumR) + 
      " sectors ${secList}: $cmd ${bit}="+T.bitNames[bit])

    println("Enter a comment, if you want, otherwise press return")
    print("> ")
    def cmt = System.in.newReader().readLine()


    qaTree["$rnum"].each { k,v ->
      def qaFnum = k.toInteger()
      if( qaFnum>=fnumL && ( fnumR==1 || qaFnum<=fnumR ) ) {

        if(cmd=="setBit") {
          secList.each{ 
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] = [bit]
          }
        }
        else if(cmd=="addBit") {
          secList.each{
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] += bit
          }
        }
        else if(cmd=="delBit") {
          secList.each{
            qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= bit
          }
        }

        recomputeDefMask(rnum,qaFnum)

        if(cmt.length()>0) qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
      }
    }

  }
  else {
    def helpStr = usage["$cmd"].tokenize(':')[1]
    println(
    """
    SYNTAX: ${cmd} [defectBit] [run] [firstFile] [lastFile] [list_of_sectors]
      -$helpStr
      - set [lastFile] to 1 to denote last file of run
      - use \"all\" in place of [list_of_sectors] to apply to all sectors
      - you will be prompted to enter a comment
    """)
    println("Bit List:\n")
    T.bitDefinitions.size().times {
      println("$it\t" + T.bitNames[it] + "\t" + T.bitDescripts[it] + "\n")
    }
    return
  }
}
else if(cmd=="lossFT") {
  def rnum,fnumL,fnumR
  def secList = []
  if(args.length>4) {
    rnum = args[1].toInteger()
    fnumL = args[2].toInteger()
    fnumR = args[3].toInteger()
    if(args[4]=="all") secList = (1..6).collect{it}
    else (4..<args.length).each{ secList<<args[it].toInteger() }

    println("run $rnum files ${fnumL}-"+(fnumR==1 ? "END" : fnumR) +
      " sectors ${secList}: define sector loss")

    println("Enter a comment, if you want, otherwise press return")
    print("> ")
    def cmt = System.in.newReader().readLine()

    qaTree["$rnum"].each { k,v ->
      def qaFnum = k.toInteger()
      if( qaFnum>=fnumL && ( fnumR==1 || qaFnum<=fnumR ) ) {

        secList.each{
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= T.bit("TotalOutlierFT")
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= T.bit("TerminalOutlierFT")
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= T.bit("MarginalOutlierFT")
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] += T.bit("LossFT")
        }

        recomputeDefMask(rnum,qaFnum)

        if(cmt.length()>0) qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
      }
    }

  }
 else {
    def helpStr = usage["$cmd"].tokenize(':')[1]
    println(
    """
    SYNTAX: ${cmd} [run] [firstFile] [lastFile] [list_of_sectors]
      -$helpStr
      - set [lastFile] to 1 to denote last file of run
      - use \"all\" in place of [list_of_sectors] to apply to all sectors
      - this will set the SectorLoss bit for specified files and sectors;
        it will unset any other relevant bits
      - you will be prompted to enter a comment
    """)
    return
  }
}
else if(cmd=="sectorLoss") {
  def rnum,fnumL,fnumR
  def secList = []
  if(args.length>4) {
    rnum = args[1].toInteger()
    fnumL = args[2].toInteger()
    fnumR = args[3].toInteger()
    if(args[4]=="all") secList = (1..6).collect{it}
    else (4..<args.length).each{ secList<<args[it].toInteger() }

    println("run $rnum files ${fnumL}-"+(fnumR==1 ? "END" : fnumR) + 
      " sectors ${secList}: define sector loss")

    println("Enter a comment, if you want, otherwise press return")
    print("> ")
    def cmt = System.in.newReader().readLine()

    qaTree["$rnum"].each { k,v ->
      def qaFnum = k.toInteger()
      if( qaFnum>=fnumL && ( fnumR==1 || qaFnum<=fnumR ) ) {

        secList.each{ 
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= T.bit("TotalOutlier")
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= T.bit("TerminalOutlier")
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] -= T.bit("MarginalOutlier")
          qaTree["$rnum"]["$qaFnum"]["sectorDefects"]["$it"] += T.bit("SectorLoss")
        }

        recomputeDefMask(rnum,qaFnum)

        if(cmt.length()>0) qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
      }
    }

  }



  else {
    def helpStr = usage["$cmd"].tokenize(':')[1]
    println(
    """
    SYNTAX: ${cmd} [run] [firstFile] [lastFile] [list_of_sectors]
      -$helpStr
      - set [lastFile] to 1 to denote last file of run
      - use \"all\" in place of [list_of_sectors] to apply to all sectors
      - this will set the SectorLoss bit for specified files and sectors;
        it will unset any other relevant bits
      - you will be prompted to enter a comment
    """)
    return
  }
}

else if( cmd=="addComment" || cmd=="setComment") {
  def rnum,fnumL,fnumR
  def secList = []
  if(args.length==4) {
    rnum = args[1].toInteger()
    fnumL = args[2].toInteger()
    fnumR = args[3].toInteger()

    println("run $rnum files ${fnumL}-"+(fnumR==1 ? "END" : fnumR) + ": $cmd")
    if(cmd=="addComment") 
      println("Enter the new comment to be appended")
    else if(cmd=="setComment") 
      println("Enter the new comment, or leave it blank to delete any stored comment")
    print("> ")
    def cmt = System.in.newReader().readLine()
    qaTree["$rnum"].each { k,v ->
      def qaFnum = k.toInteger()
      if( qaFnum>=fnumL && ( fnumR==1 || qaFnum<=fnumR ) ) {
        if (cmd=="addComment") {
          if(!qaTree["$rnum"]["$qaFnum"].containsKey("comment")) {
            qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
          }
          else {
            if(qaTree["$rnum"]["$qaFnum"]["comment"].length()>0)
              qaTree["$rnum"]["$qaFnum"]["comment"] += "; "
            qaTree["$rnum"]["$qaFnum"]["comment"] += cmt
          }
        }
        else if (cmd=="setComment") 
          qaTree["$rnum"]["$qaFnum"]["comment"] = cmt
      }
    }
  }
  else {
    println(
    """
    SYNTAX: ${cmd} [run] [firstFile] [lastFile]
      - set [lastFile] to 1 to denote last file of run
      - you will be prompted to enter the comment
    """)
    return
  }
}

else if( cmd=="custom") {
  // this cmd is useful if you want to do a specific action, while
  // calling this groovy script from another program
  def rnum,fnum
  if(args.length==3) {
    rnum = args[1].toInteger()
    fnum = args[2].toInteger()

    def secList = (1..6).collect{it}
    secList.each{ 
      qaTree["$rnum"]["$fnum"]["sectorDefects"]["$it"] += T.bit("Misc")
    }

    def cmt = "ungated FC charge spike"
    if(!qaTree["$rnum"]["$fnum"].containsKey("comment")) {
      qaTree["$rnum"]["$fnum"]["comment"] = cmt
    }
    else {
      if(qaTree["$rnum"]["$fnum"]["comment"].length()>0)
        qaTree["$rnum"]["$fnum"]["comment"] += "; "
      qaTree["$rnum"]["$fnum"]["comment"] += cmt
    }

    recomputeDefMask(rnum,fnum)

  }
}


else { println("ERROR: unknown command!"); return }


// update qaTree.json
new File("qa/qaTree.json").write(JsonOutput.toJson(qaTree))
"run-groovy parseQaTree.groovy".execute()
