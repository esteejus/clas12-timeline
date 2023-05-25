import groovy.json.JsonSlurper
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
if(args.length<1) {
  println("ChargeTreeToSQLite.groovy [dataset] [chargeTree_path]")
  System.exit(0)
}
if(args.length>=1) dataset = args[0]
def out_dir = "QA/qa."+dataset+"/" //NOTE: KEEP THIS THE SAME AS THE DEFAULT PATH IN JSONTOSQLITE.GROOVY.  //TODO: CHECK THESE PATHS AND MAYBE CLEAN ALL THIS UP A BIT.
def chargeTree_path = "outdat.${dataset}/chargeTree.json"
if(args.length>=2) qaTree_path = args[1]; out_dir = './'
def db_path = out_dir+dataset+"_FROM_JSON.db"
//--------------------------------------------------------------------------

//--------------------------------------------------------------------------
// CREATE SQL DATABASE AND TABLE
// def db_path = "outdat."+dataset+"/"+dataset+".db" //TODO: Add option to manually specify path to db //NOTE: Not needed here.
def sql = Sql.newInstance("jdbc:sqlite:"+db_path, "org.sqlite.JDBC")
def tablename = dataset+'_chargeTree'
def force = false // force overwrite of database table
if (force) sql.execute("drop table if exists "+tablename)
try { sql.execute("create table "+tablename+
      " (id integer, run integer, filenum integer,"+
      " fcChargeMin double, fcChargeMax double,"+
      " ufcChargeMin double, ufcChargeMax double,"+
      " livetime double, nElec integer, sector integer)")
} catch (SQLException e) {
  println "*** WARNING ***  Database table ${tablename} already exists."
  e.printStackTrace()
  // System.exit(0) //NOTE: Don't exit here since for FT option you should just update existing database.
}
def db
try { db = sql.dataSet(tablename)
} catch (SQLException e) {
  println "*** ERROR *** Could not open dataset ${tablename}."
  e.printStackTrace()
  System.exit(0)
}
def db_id = sql.rows("select count(*) as nrows from "+tablename)[0]["nrows"] // global counter for entries added to database table
//--------------------------------------------------------------------------

// define qaTree
def slurper = new JsonSlurper()
def jsonFile = new File(chargeTree_path)
def chargeTree = slurper.parse(jsonFile) // [runnum][filenum] -> defects enumeration

// Add chargeTree data to database
chargeTree.each { chargeRun, chargeRunTree ->
  chargeRunTree.each { chargeFile, chargeFileTree ->
    def nElec = chargeFileTree["nElec"]
    db.add(id:db_id,
        run:chargeRun,
        filenum:chargeFile,
        fcChargeMin:chargeFileTree['fcChargeMin'],
        fcChargeMax:chargeFileTree['fcChargeMax'],
        ufcChargeMin:chargeFileTree['ufcChargeMin'],
        ufcChargeMax:chargeFileTree['ufcChargeMax'],
        nElec_sec1:nElec[0],
        nElec_sec2:nElec[1],
        nElec_sec3:nElec[2],
        nElec_sec4:nElec[3],
        nElec_sec5:nElec[4],
        nElec_sec6:nElec[5],
        comment:""
        )
    db_id += 1
  }
}

//--------------------------------------------------------------------------
println "Converted JSON file to SQLite database in:\n\t${db_path}"
