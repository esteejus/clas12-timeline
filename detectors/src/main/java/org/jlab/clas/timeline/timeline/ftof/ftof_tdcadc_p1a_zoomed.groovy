package org.jlab.clas.timeline.timeline.ftof
import java.util.concurrent.ConcurrentHashMap
import org.jlab.groot.data.TDirectory
import org.jlab.groot.data.GraphErrors
import org.jlab.clas.timeline.fitter.FTOFFitter
import org.jlab.groot.data.H1F

class ftof_tdcadc_p1a_zoomed {

def data = new ConcurrentHashMap()

def processDirectory(dir, run) {
  def funclist = []
  def meanlist = []
  def sigmalist = []
  def chi2list = []
  def histlist =   (0..<6).collect{
    def h1 = dir.getObject('/tof/p1a_tdcadc_dt_S'+(it+1))
    def f1 = FTOFFitter.tdcadcdifffit_p1a(h1)
    def peak = f1.getParameter(1)
    def sigma = f1.getParameter(2).abs()
    def minH = h1.getAxis().min()
    def maxH = h1.getAxis().max()
    if (peak>minH && peak<maxH){
        minH = peak - 2.5*sigma
        maxH = peak + 2.5*sigma
        def minBin = h1.getAxis().getBin(minH)
        def maxBin = h1.getAxis().getBin(maxH)
        minH = h1.getAxis().getBinCenter(minBin) - h1.getAxis().getBinWidth(0)*0.5
        maxH = h1.getAxis().getBinCenter(maxBin) + h1.getAxis().getBinWidth(0)*0.5
        def h2 = new H1F("new_" + h1.getName(), "new_" + h1.getTitle(), maxBin-minBin+1, minH, maxH)
        (minBin..maxBin).each{
                h2.setBinContent(it-minBin, h1.getBinContent(it))
        }   
        println(h1.getAxis().getBinCenter(h1.getMaximumBin()))
        println(h2.getAxis().getBinCenter(h2.getMaximumBin()))
        h1 = h2
        f1 = FTOFFitter.tdcadcdifffit_p1a(h1)
    }   


    funclist.add(f1)
    meanlist.add(f1.getParameter(1))
    sigmalist.add(f1.getParameter(2).abs())
    chi2list.add(f1.getChiSquare())
    return h1
  }
  data[run] = [run:run, hlist:histlist, flist:funclist, mean:meanlist, sigma:sigmalist, clist:chi2list]
}



def close() {

  ['mean', 'sigma'].each{ name ->
    TDirectory out = new TDirectory()
    out.mkdir('/timelines')
    (0..<6).each{ sec->
      def grtl = new GraphErrors('sec'+(sec+1))
    grtl.setTitle("p1a t_tdc-t_fadc (" + name +")")
    grtl.setTitleY("p1a t_tdc-t_fadc (" + name +") (ns)")
    grtl.setTitleX("run number")

      data.sort{it.key}.each{run,it->
        if (sec==0){
          out.mkdir('/'+it.run)
        }
        out.cd('/'+it.run)
        out.addDataSet(it.hlist[sec])
        out.addDataSet(it.flist[sec])
        grtl.addPoint(it.run, it[name][sec], 0, 0)
      }
      out.cd('/timelines')
      out.addDataSet(grtl)
    }

    out.writeFile('ftof_tdcadc_time_p1a_zoomed_' + name + '.hipo')
  }
}
}
