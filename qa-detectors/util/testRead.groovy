import org.jlab.groot.data.TDirectory
def hipoFile = "/u/group/clas/www/clas12mon/html/hipo/rga/pass0/v2.2.52/rf/rftime_electron_FD_mean.hipo"
def inTdir = new TDirectory()
// File inTdirFile
// inTdirFile = new File(hipoFile)
inTdir.readFile(hipoFile)
