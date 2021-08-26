void CSVtoH1F(TString filename, TString hname, TString title,
    int nbins, float lbin, float ubin, TString xTitle, TString yTitle) {

    fstream file; file.open(filename, ios::in);
    TH1F *hist = new TH1F(hname,title, nbins,lbin,ubin);

    // Loop file for data and fill histogram
    double x,y,z,q;
    while (1) {
        if (!file.good()) break;
        file >> x >> y >> z >> q; hist->Fill(x,q);
    } file.close();

    hist->GetXaxis()->SetTitle(xTitle);
    hist->GetYaxis()->SetTitle(yTitle);
    hist->SetLineColor(kRed); hist->SetFillColor(kRed); hist->Draw("HIST");
}

float *getXLimits(TString filename) {

    fstream file; file.open(filename, ios::in);

    // Loop file for data and fill histogram
    float x,y; 
    float xmin = 0.0; float xmax = 0.0; float nentries = 0.0;
    while (1) {
        if (!file.good()) break;
        else {
            file >> x >> y;
            if (x<xmin) { xmin = x; }
            if (x>xmax) { xmax = x; }
            nentries += 1.0; //NOTE: This relies on there NOT being a trailing newline at the end of the file.
        }
    } file.close();

    static float p[3]; // Static is important!
    p[0] = xmin;
    p[1] = xmax;
    p[2] = nentries;

    return p;
}

void testCSV() {
    TString filename = "logs/jvm_log_6763.dat";
    TString hname    = "h1";
    TString title    = "title";
    TString xTitle   = "event #";
    TString yTitle   = "heap space used (bytes)";
    float *limits    = getXLimits(filename);
    float lbin       = 0.0;
    float ubin       = limits[2];
    int   nbins      = (int)limits[2]/40;
    CSVtoH1F(filename,hname,title,nbins,lbin,ubin,xTitle,yTitle);
}
