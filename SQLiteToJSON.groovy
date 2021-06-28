import groovy.json.JsonOutput

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
if(args.length>=1) dataset = args[0]
def out_dir = "outdat."+dataset+"/"
def db_path = out_dir+dataset+".db"
if(args.length>=2) db_path = args[1]; out_dir = './'
//--------------------------------------------------------------------------

//--------------------------------------------------------------------------
// OPEN SQL DATABASE CONNECTION AND TABLE AND FILL QA TREE
def sectors = 0..6
def sql
try { sql = Sql.newInstance("jdbc:sqlite:"+db_path, "org.sqlite.JDBC")
} catch (SQLException e) {
  println "*** ERROR ***  Database ${dataset} does not exist."
  e.printStackTrace()
  System.exit(0)
}
def db
def qaTree = [:]
try {  sql.eachRow("select * from "+dataset) {

    // Check entries
    if (!qaTree.keySet().contains(it.run)) { qaTree[it.run] = [:] }
    if (!qaTree[it.run].keySet().contains(it.filenum)) {
        qaTree[it.run][it.filenum] = ['evnumMin':it.evmin,'evnumMax':it.evmax,'comment':it.comment,'defect':0,'livetime':it.livetime]
    }
    
    if (!qaTree[it.run][it.filenum].keySet().contains(it.detector)) { qaTree[it.run][it.filenum][it.detector] = ['defect':0] }
    if (!qaTree[it.run][it.filenum][it.detector].keySet().contains(it.pid)) { qaTree[it.run][it.filenum][it.detector][it.pid] = ['defect':0,'evnumMin':it.evmin,'evnumMax':it.evmax,'comment':it.comment] }
    if (!qaTree[it.run][it.filenum][it.detector][it.pid].keySet().contains('defects'))  { qaTree[it.run][it.filenum][it.detector][it.pid]['defects'] = 0 }
    if (!qaTree[it.run][it.filenum][it.detector][it.pid].keySet().contains('sectorDefects'))  { qaTree[it.run][it.filenum][it.detector][it.pid]['sectorDefects'] = sectors.collectEntries{s->[s+1,0]} }

    // Add entries
    if (qaTree[it.run][it.filenum][it.detector][it.pid]['defects']==0 && it.sector==0 && it.defect!=0) { qaTree[it.run][it.filenum][it.detector][it.pid]['defect'] = it.defect }
    if (qaTree[it.run][it.filenum][it.detector][it.pid]['sectorDefects'][it.sector]==0 && it.sector!=0 && it.sectordefect!=0) { qaTree[it.run][it.filenum][it.detector][it.pid]['sectorDefects'][it.sector] = it.sectordefect }
    if (it.defect>0 || it.sectordefect>0) {
        qaTree[it.run][it.filenum][it.detector][it.pid]['defect'] = 1
        qaTree[it.run][it.filenum][it.detector]['defect'] = 1
        qaTree[it.run][it.filenum]['defect'] = 1
    }
} } catch (SQLException e) {
  println "*** ERROR *** Could not open table ${dataset}."
  e.printStackTrace()
  System.exit(0)
}
//--------------------------------------------------------------------------

//--------------------------------------------------------------------------
// SORT AND WRITE OUT QATREE
qaTree.each { qaRun, qaRunTree -> qaRunTree.sort{it.key.toInteger()} }
qaTree.sort()
new File(out_dir+"qaTreeFromSQL.json").write(JsonOutput.toJson(qaTree))
println "Wrote SQLite database to outdat.${dataset}/qaTreeFromSQL.json"
//--------------------------------------------------------------------------
