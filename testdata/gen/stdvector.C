// from https://root.cern.ch/doc/master/hvector_8C_source.html
/// \file
/// \ingroup tutorial_tree
/// \notebook
/// Write and read STL vectors in a tree.
///
/// \macro_image
/// \macro_code
///
/// \author The ROOT Team

#include <vector>

#include "TFile.h"
#include "TTree.h"
#include "TCanvas.h"
#include "TFrame.h"
#include "TH1F.h"
#include "TBenchmark.h"
#include "TRandom.h"
#include "TSystem.h"

void write()
{

	TFile *f = TFile::Open("stdvector.root","RECREATE");

	if (!f) { return; }

	std::vector<float> vpx;
	std::vector<float> vpy;
	std::vector<float> vpz;
	std::vector<float> vrand;

	// Create a TTree
	TTree *t = new TTree("tvec","Tree with vectors");
	t->Branch("vpx",&vpx);
	t->Branch("vpy",&vpy);
	t->Branch("vpz",&vpz);
	t->Branch("vrand",&vrand);

	gRandom->SetSeed(2072019);
	for (Int_t i = 0; i < 10; i++) {
		Int_t npx = (Int_t)(gRandom->Rndm(1)*15);

		vpx.clear();
		vpy.clear();
		vpz.clear();
		vrand.clear();

		for (Int_t j = 0; j < npx; ++j) {

			Float_t px,py,pz;
			gRandom->Rannor(px,py);
			pz = px*px + py*py;
			Float_t random = gRandom->Rndm(1);

			vpx.emplace_back(px);
			vpy.emplace_back(py);
			vpz.emplace_back(pz);
			vrand.emplace_back(random);

		}
		
		t->Fill();
	}
	f->Write();

	delete f;
}

void stdvector()
{
	write();
}