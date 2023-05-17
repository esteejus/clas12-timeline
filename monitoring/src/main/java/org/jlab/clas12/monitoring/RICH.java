package org.jlab.clas12.monitoring;
import java.io.*;
import java.util.*;

import org.jlab.groot.math.*;
import org.jlab.groot.data.H1F;
import org.jlab.groot.data.H2F;
import org.jlab.groot.math.F1D;
import org.jlab.groot.fitter.DataFitter;
import org.jlab.io.base.DataBank;
import org.jlab.io.base.DataEvent;
import org.jlab.io.hipo.HipoDataSource;
import org.jlab.groot.fitter.ParallelSliceFitter;
import org.jlab.groot.graphics.EmbeddedCanvas;
import org.jlab.groot.data.GraphErrors;
import org.jlab.groot.data.TDirectory;
import org.jlab.clas.physics.Vector3;
import org.jlab.clas.physics.LorentzVector;
import org.jlab.groot.base.GStyle;
import org.jlab.utils.groups.IndexedTable;
import org.jlab.detector.calib.utils.CalibrationConstants;
import org.jlab.detector.calib.utils.ConstantsManager;
import org.jlab.groot.base.DatasetAttributes;
import org.jlab.groot.base.Attributes;
import org.jlab.groot.math.StatNumber;
import org.jlab.groot.ui.PaveText;
import org.jlab.groot.data.IDataSet;
import org.jlab.groot.base.PadAttributes;
import java.util.ArrayList;

public class RICH{
    boolean userTimeBased, write_volatile;
    int runNum;
    boolean[] trigger_bits;
    public float EBeam;

    public static final int nPMTS = 391, nANODES = 64, NTBITS = 32;
    public int BINWINDOW;
    public PaveText statBox = null;
    public PadAttributes attr1;

    public H2F H_dt_channel;
    public H1F H_dt, H_FWHM, H_ProjY, H_RMS;
    public H1F[] H_dt_PMT;
    
    public ArrayList<H1F> H_dt_PMT_AL;

    public int eventN;

    public RICH(int reqR, float reqEb, boolean reqTimeBased, boolean reqwrite_volatile){
	runNum = reqR;userTimeBased=reqTimeBased;
	write_volatile = reqwrite_volatile;
	EBeam = reqEb;
	BINWINDOW = 14;
	
	
	//statBox = new PaveText(2);
	//attr1 = new PadAttributes();

	trigger_bits = new boolean[NTBITS];

	String histitle = String.format("RICH  T_meas - T_calc Photons");
	H_dt = new H1F(String.format("H_RICH_dt"),histitle,500,-150,50);
	H_dt.setTitle(histitle);
	H_dt.setTitleX("T_meas - T_calc (ns)");
	H_dt.setTitleY("counts");

	histitle = String.format("RICH Delta T vs channel");
	H_dt_channel = new H2F(String.format("H_RICH_dt_channel"),histitle,nPMTS*nANODES,0.5,0.5+nPMTS*nANODES, 500, -150, 50);
	H_dt_channel.setTitle(histitle);
	H_dt_channel.setTitleX("channel");
	H_dt_channel.setTitleY("T_meas - T_calc (ns)");

	histitle = String.format("RICH Full-Width at Half Maximum");
	H_FWHM = new H1F(String.format("H_RICH_FWHM"),histitle,nPMTS,0.5,0.5+nPMTS);
	H_FWHM.setTitle(histitle);
	H_FWHM.setTitleX("PMT number");
	H_FWHM.setTitleY("FWHM of (T_meas - T_calc) (ns)");

	histitle = String.format("RICH RMS within BINWINDOW bins around the Max");
	H_RMS = new H1F(String.format("H_RICH_RMS"),histitle,nPMTS,0.5,0.5+nPMTS);
	H_RMS.setTitle(histitle);
	H_RMS.setTitleX("PMT number");
	H_RMS.setTitleY("RMS of (T_meas - T_calc) (ns)");

	histitle = String.format("Projection Y Test");
	H_ProjY = new H1F(String.format("H_ProjY"),histitle,500,-150,50);
	H_ProjY.setTitle(histitle);
	H_ProjY.setTitleX("dT (ns)");
	H_ProjY.setTitleY("events");
	
	H_dt_PMT = new H1F[nPMTS];	    
    }
    public void getPhotons(DataBank part, DataBank hadr, DataBank phot, DataBank hits){
	float p_min = 2.0f;
	int pmt, anode, absChannel;
	double DTimeCorr;

	for (int j = 0; j< phot.rows(); j++) {
	    int part_hypo = phot.getInt("hypo", j);
	    int pindex = phot.getInt("pindex", j);
	    int pid = part.getInt("pid", pindex);
	    Vector3 P3 = new Vector3(part.getFloat("px", pindex),part.getFloat("py", pindex),part.getFloat("pz", pindex));

	    //System.out.println(String.format("j="+j+"  hypo="+part_hypo+"   pid="+pid));
	    
	    if ( ( (pid == 11) || (java.lang.Math.abs(pid) == 211) ) && (part_hypo == pid)  && (P3.mag() >= p_min) ) {

		/* channel info */
		pmt = phot.getInt("pmt", j);
		anode = phot.getInt("anode", j);
		absChannel = anode + (pmt-1)*nANODES;
		DTimeCorr = phot.getFloat("dtime", j);
		
		H_dt.fill(DTimeCorr);
		H_dt_channel.fill(absChannel, DTimeCorr);	

	    }

	}

    }    

    public void FillFWHMHistogram() {
	int npeakMin = 20;

	H_FWHM.reset();
	H_RMS.reset();
	H2F H_dt_channel_rb = H_dt_channel.rebinX(nANODES);
	H_dt_PMT_AL = H_dt_channel_rb.getSlicesX();
	for (int p=0; p<nPMTS; p++) {
	    H_dt_PMT[p] = H_dt_PMT_AL.get(p);
	    H_dt_PMT[p].setTitle(String.format("dT, PMT=%d",p+1));
	    H_dt_PMT[p].setTitleX("dT (ns)");
	    H_dt_PMT[p].setTitleY("counts");
	    H_dt_PMT[p].setOptStat("1111111");
	    int binM = (int) H_dt_PMT[p].getMaximumBin();
	    int binL = binM - BINWINDOW/2;
	    int binH = binM + BINWINDOW/2;
	    float rms = 0;
	    float fwhm = 0;

	    int nentries = getHistoEntries(H_dt_PMT[p]);
	    //System.out.println(String.format("PMT "+p+" ne="+nentries));

	    if (nentries >= npeakMin) {
		rms =  getRMS(H_dt_PMT[p], binL, binH);
		fwhm = getFWHM(H_dt_PMT[p]);
	    }
	    H_FWHM.fill(p+1,fwhm);
	    H_RMS.fill(p+1,rms);
	}
		
    }

    public void processEvent(DataEvent event){
	if(event.hasBank("RUN::config")){
	    DataBank confbank = event.getBank("RUN::config");
	    long TriggerWord = confbank.getLong("trigger",0);
	    for (int i = NTBITS-1; i >= 0; i--) {trigger_bits[i] = (TriggerWord & (1 << i)) != 0;} 
	    DataBank eventBank=null, partBank = null, hadrBank = null, photBank = null, hitBank = null;
	    if(userTimeBased){
		if(event.hasBank("REC::Event")) eventBank = event.getBank("REC::Event");
		if(event.hasBank("REC::Particle"))partBank = event.getBank("REC::Particle");
	    }
	    if(!userTimeBased){
		if(event.hasBank("REC::Event"))eventBank = event.getBank("REC::Event");
		if(event.hasBank("RECHB::Particle"))partBank = event.getBank("RECHB::Particle");
	    }


	    eventN = confbank.getInt("event", 0);

	    //if(event.hasBank("RICH::hadrons")) hadrBank = event.getBank("RICH::hadrons");
	    //if(event.hasBank("RICH::photons")) photBank = event.getBank("RICH::photons");
	    //if(event.hasBank("RICH::hits")) hitBank = event.getBank("RICH::hits");

	    if(event.hasBank("RICH::Hit")) hitBank = event.getBank("RICH::Hit");
	    if(event.hasBank("RICH::Ring")) photBank = event.getBank("RICH::Ring");
	    if(event.hasBank("RICH::Particle")) hadrBank = event.getBank("RICH::Particle");

	    //if( (trigger_bits[1] || trigger_bits[2] || trigger_bits[3] || trigger_bits[4] || trigger_bits[5] || trigger_bits[6]) && partBank!=null)e_part_ind = makeElectron(partBank);
	    if (eventBank!=null) {
		if ((eventBank.rows() > 0) && (confbank.rows() > 0)) {
		    if(partBank!=null && hadrBank!=null && photBank!=null && hitBank!=null) {getPhotons(partBank,hadrBank,photBank,hitBank);}
		}
	    }
	}
    }
    public void plot() {
	EmbeddedCanvas can_RICH  = new EmbeddedCanvas();
	can_RICH.setSize(3500,10000);
	can_RICH.divide(1,5);
	can_RICH.setAxisTitleSize(18);
	can_RICH.setAxisFontSize(18);
	can_RICH.setTitleSize(18);
	can_RICH.cd(0);can_RICH.draw(H_dt);
	can_RICH.cd(1);can_RICH.draw(H_dt_channel);
	can_RICH.cd(2);can_RICH.draw(H_FWHM);
	can_RICH.cd(3);can_RICH.draw(H_dt_PMT[80]);
	can_RICH.cd(4);can_RICH.draw(H_RMS);
	if(runNum>0){
	    if(!write_volatile)can_RICH.save(String.format("plots"+runNum+"/RICH.png"));
	    if(write_volatile)can_RICH.save(String.format("/volatile/clas12/rga/spring18/plots"+runNum+"/RICH.png"));
	    System.out.println(String.format("saved plots"+runNum+"/RICH.png"));
	}
	else{
	    can_RICH.save(String.format("plots/RICH.png"));
	    System.out.println(String.format("saved plots/RICH.png"));
	}
	EmbeddedCanvas can_RICH_PMT  = new EmbeddedCanvas();
	can_RICH_PMT.setSize(10000,4000);
	can_RICH_PMT.divide(20,20);
	can_RICH_PMT.setAxisTitleSize(18);
	can_RICH_PMT.setAxisFontSize(18);
	can_RICH_PMT.setTitleSize(18);
	for(int ic=0;ic<nPMTS;ic++){
	    can_RICH_PMT.cd(ic);can_RICH_PMT.draw(H_dt_PMT[ic]); 
	    //can_RICH_PMT.getPad(ic).attr1.statBox.setStatBoxOffsetY(1);
	    //can_RICH_PMT.getPad(ic).attr1.statBox.setStatBoxOffsetY(0);
	    //can_RICH_PMT.getPad(ic).setStatBoxOffsetY(1);
	}
	if(runNum>0){
	    if(!write_volatile)can_RICH_PMT.save(String.format("plots"+runNum+"/RICH_PMT_DeltaT.png"));
	    if(write_volatile)can_RICH_PMT.save(String.format("/volatile/clas12/rga/spring18/plots"+runNum+"/RICH_PMT_DeltaT.png"));
	    System.out.println(String.format("saved plots"+runNum+"/RICH_PMT_DeltaT.png"));
	}
	else{
	    can_RICH_PMT.save(String.format("plots/RICH_PMT_DeltaT.png"));
	    System.out.println(String.format("saved plots/RICH_PMT_DeltaT.png"));
	}

    }
    public static void main(String[] args) {
	System.setProperty("java.awt.headless", "true");
	GStyle.setPalette("kRainBow");
	int count = 0;
	int runNum = 0;
	boolean useTB = true;
	boolean useVolatile = false;
	String filelist = "list_of_files.txt";
	if(args.length>0)runNum=Integer.parseInt(args[0]);
	if(args.length>1)filelist = args[1];
	int maxevents = 20000000;
	if(args.length>2)maxevents=Integer.parseInt(args[2]);
	float Eb =10.2f;//10.6f;
	if(args.length>3)Eb=Float.parseFloat(args[3]);
	if(args.length>4)if(Integer.parseInt(args[4])==0)useTB=false;
	RICH ana = new RICH(runNum,Eb,useTB,useVolatile);
	List<String> toProcessFileNames = new ArrayList<String>();
	File file = new File(filelist);
	Scanner read;
	try {
	    read = new Scanner(file);
	    do { 
		String filename = read.next();
		toProcessFileNames.add(filename);

	    }while (read.hasNext());
	    read.close();
	}catch(IOException e){ 
	    e.printStackTrace();
	}   
	int progresscount=0;int filetot = toProcessFileNames.size();
	for (String runstrg : toProcessFileNames) if( count<maxevents ){
		progresscount++;
		System.out.println(String.format(">>>>>>>>>>>>>>>> %s",runstrg));
		File varTmpDir = new File(runstrg);
		if(!varTmpDir.exists()){System.out.println("FILE DOES NOT EXIST");continue;}
		System.out.println("READING NOW "+runstrg);
		HipoDataSource reader = new HipoDataSource();
		reader.open(runstrg);
		int filecount = 0;
		while(reader.hasEvent()&& count<maxevents ) { 
		    DataEvent event = reader.getNextEvent();
		    ana.processEvent(event);
		    filecount++;count++;
		    if(count%100000 == 0) System.out.println(count/1000 + "k events (this is RICH analysis in "+runstrg+") ; progress : "+progresscount+"/"+filetot);
		}   
		reader.close();
	    }   
	System.out.println("Total : " + count + " events");
	ana.FillFWHMHistogram();
	ana.FillFWHMHistogram();
	ana.plot();
	ana.write();
    }   

    public void write() {
	TDirectory dirout = new TDirectory();
	dirout.mkdir("/RICH/");
	dirout.cd("/RICH/");
	dirout.addDataSet(H_dt,H_dt_channel);
	dirout.addDataSet(H_FWHM);
	dirout.addDataSet(H_RMS);

	if(!write_volatile){
	    if(runNum>0)dirout.writeFile("plots"+runNum+"/out_RICH"+runNum+".hipo");
	    else dirout.writeFile("plots/out_RICH.hipo");
	}
    }
    public float getFWHM(H1F h){
	    double halfmax = h.getBinContent(h.getMaximumBin())/2;
	    int nbins = h.getXaxis().getNBins();
	    /* Calculate the FWHM */
	    int bin1 = 0;
	    int bin2 = 0;
	    /* The first bin above halfmax */
	    for (int c=0;c<nbins;c++) {
		if (h.getBinContent(c)>=halfmax) {bin1=c; break;}
	    }
	    /* The last bin above halfmax */
	    for (int c=nbins-1;c>-1;c--) {
		if (h.getBinContent(c)>=halfmax) {bin2=c; break;}
	    }	
	    double fwhm = h.getDataX(bin2) - h.getDataX(bin1) + 1;
	    return (float) fwhm;
    }

    public float getRMS(H1F h, int binL, int binH){
	float rms=0.f;
	float avg=0.f;


	/* First get the average within BINWINDOW */
	int Ntot = 0;
	for (int c=binL;c<=binH;c++) {
	    avg += h.getBinContent(c)*(float)h.getDataX(c);
	    rms += h.getBinContent(c)*(float)h.getDataX(c)*(float)h.getDataX(c);
	    Ntot += (int)h.getBinContent(c);
	}

	if (Ntot!=0) avg = avg/Ntot;

	/* Calculate the RMS */
	rms = (float)Math.sqrt(rms/(Ntot-1) - avg*avg*Ntot/(Ntot-1));

	return rms;

    }



    public int getHistoEntries(H1F h){
        int entries = 0;
        for(int loop = 0; loop < h.getAxis().getNBins(); loop++){
            entries += (int) h.getBinContent(loop);
        }
        return entries;
    }

}
