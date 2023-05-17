/**
*
* Fitter package for Forward
*
* Writer: Sangbaek Lee, Andrey Kim
*
**/
package org.jlab.clas.timeline.fitter
import org.jlab.groot.data.H1F
import org.jlab.groot.math.F1D


class ForwardFitter{
	static F1D fit(H1F h1) {
	    def f1 = new F1D("fit:"+h1.getName(), "[amp]*gaus(x,[mean],[sigma])", -20.0, 10.0);
        double hAmp  = h1.getBinContent(h1.getMaximumBin());
        double hMean = h1.getAxis().getBinCenter(h1.getMaximumBin());
        double hRMS  = h1.getRMS(); //ns
        double rangeMin = (hMean - (3*hRMS));
        double rangeMax = (hMean + (3*hRMS));
        f1.setRange(rangeMin, rangeMax);
        f1.setParameter(0, hAmp);
        f1.setParameter(1, hMean);
        f1.setParameter(2, hRMS);
		MoreFitter.fit(f1,h1,"LQ");

		def makefit = {func->
			hMean = func.getParameter(1)
			hRMS = func.getParameter(2).abs()
			func.setRange(hMean-2.5*hRMS,hMean+2.5*hRMS)
			MoreFitter.fit(func,h1,"Q")
			return [func.getChiSquare(), (0..<func.getNPars()).collect{func.getParameter(it)}]
		}

		def fits1 = (0..20).collect{makefit(f1)}
		def bestfit = fits1.sort()[0]
		f1.setParameters(*bestfit[1])
		//makefit(f1)

		return f1
 	}
} 
