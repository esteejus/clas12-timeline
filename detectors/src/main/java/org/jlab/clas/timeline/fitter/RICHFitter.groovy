package org.jlab.clas.timeline.fitter
import org.jlab.groot.fitter.DataFitter
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D


class RICHFitter {
  static F1D timefit(H1F h1) {
    def f1 = new F1D("fit:"+h1.getName(), "[amp]*gaus(x,[mean],[sigma])+[p0]", -1.0, 1.0)
    def hAmp  = h1.getBinContent(h1.getMaximumBin());
    def hMean = h1.getAxis().getBinCenter(h1.getMaximumBin())
    def hRMS = Math.min(h1.getRMS(),0.44)

    f1.setParameter(0, hAmp)
    f1.setParameter(1, hMean)
    f1.setParameter(2, hRMS)
    f1.setParameter(3, 0)

    def makefits = {func->
      hRMS = func.getParameter(2).abs()
      func.setRange(hMean-3.0*hRMS, hMean+3.0*hRMS)
      DataFitter.fit(func,h1,"Q")
      return [func.getChiSquare(), (0..<func.getNPars()).collect{func.getParameter(it)}]
    }
    def fits1 = (0..10).collect{makefits(f1)}

    def f2 = new F1D("fit:"+h1.getName(), "[amp]*gaus(x,[mean],[sigma])+[p0]+[p1]*x+[p2]*x*x",-0.2,0.2);

    fits1.sort()[0][1].eachWithIndex{par,ipar->
      f2.setParameter(ipar, par)
    }

    def fits2 = (0..10).collect{makefits(f2)}

    def bestfit = fits2.sort()[0]
    f2.setParameters(*bestfit[1])

    return f2
  }
}
