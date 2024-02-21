/*******************************************************************************
 * Copyright 2024 Stefan Holm
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package ch.wsl.sustfor.sorsim.controller;

import java.util.ArrayList;
import java.util.List;

import ch.wsl.sustfor.baumschaft.base.BaumSchaftformFunktion;
import ch.wsl.sustfor.baumschaft.base.Baumart;
import ch.wsl.sustfor.baumschaft.base.BaumschaftDefinition;
import ch.wsl.sustfor.sorsim.gui.util.StatusHandler;
import ch.wsl.sustfor.sorsim.model.SortimentVorgabenListe;
import ch.wsl.sustfor.sorsim.model.SortimentsStueck;
import ch.wsl.sustfor.sorsim.model.SortimentsStueck.ReststueckKategorie;
import ch.wsl.sustfor.sorsim.model.SortimentsVorgabe;
import ch.wsl.sustfor.sorsim.model.SortimentsVorgabe.CodeLaengenKlassen;
import ch.wsl.sustfor.sorsim.model.SortimentsVorgabe.SortimentsStueckPositionierung;


/**
 * 
 * @author Stefan Holm (Portierung nach Java)
 * @author Vinzenz Erni (Original in VB.NET)
 *
 */
public class BaumlistenSortimentierer {

	private final SortimentVorgabenListe svListe = new SortimentVorgabenListe(); 
    private final List<SortimentsStueck> sortimentStueckListe = new ArrayList<>();
    private final List<BaumschaftDefinition> bsDefListe = new ArrayList<>();
    private BaumSchaftformFunktion schaftformFkt;
    
    private final double PRUEFSCHRITTWEITE_AM_BAUMSCHAFT_M = 0.1;
    
    public void selectSchaftformFunktion(BaumSchaftformFunktion schaftformFunktion) {
    	this.schaftformFkt = schaftformFunktion;
    }
    
    public void sortimentieren(
    		final CodeLaengenKlassen codeLaenge,
            final double dFuerSortimenteZuVerwendenderSchaftanteil, 
            final double dAbzopfDurchmesserOhneRinde_cm,
            final double dStockhoehe_cm) {   
    	
    	// -----------------------------------
        // ZWECK
        // 
        // bvoBerechnungWEK -> Spezialausgabe f�r Projekt Wald-Energieholz-Karte (WEK) von C. Rosset. 

        // Teilt die in BaumSchaftDefinitionListe enthaltenen Baumsch�fte 
        // in Sortimente gem�ss den in SortimentsVorgabenListe enthaltenen SortimentsVorgaben ein 
        // und legt die ermittelten SortimentsSt�cke in der SortimentsStueckListe ab.
        // 
        // VORAUSSETZUNGEN
        // SortimentsVorgabenListe und BaumSchaftDefinitionListe sind erstellt und eingef�llt.
        //
        // OPTIONEN
        // Falls //bvoAmStammfussBeginnen//, werden die Sortimente vom Schaftfuss beginnend nach oben fortschreitend, 
        //       sonst vom Stammende beginnend, nach unten fortschreitend zugewiesen.
        // Falls //bvoSortimenteMoeglichstKurzHalten//, werden die Sortimente so kurz wie m�glich, 
        //       sonst so lang wie m�glich ausgehalten.
        // Falls //bvoAnzahlStueckBeiDenSortimentenGeltenJeBaum//,
        //       stehen alle Sortimente mit der gesamten Anzahl St�ck bei jedem Baum von neuem zur Verf�gung,
        //       sonst wird jedes Sortiment insgesamt nur so oft erstellt, wie Anzahl St�ck gefordert sind.
        //
        // SEITEN-EFFEKTE
        // Die Daten der Sortimentsvorgaben in der SortimentsVorgabenListe werden dem Ablauf entsprechend modifiziert.
        // (Anzahl St�ck, Positionen am Stamm)
        //
        // ZUM ABLAUF
        // Die Sortimente werden in der Reihenfolge erstellt, wie sie aktuell in der SortimentsVorgabenListe 
        // enthalten (sortiert) sind. Es wird versucht m�glichst viele der geforderten St�cke 
        // einer Sortimentsvorgabe (SV) zu erstellen, bevor zur n�chsten SV gewechselt wird.
        // Konnte von einer SV im ersten Versuch kein Sortimentsst�ck erstellt werden, 
        // wird diese SV beim gleichen Baum kein zweites Mal versucht, 
        // auch nicht an Positionen weiter oben am Stamm! 
        // -> Ist das sinnvoll so?
        //
        // ----------------------------------- 
    	 
        if (dFuerSortimenteZuVerwendenderSchaftanteil < 0 || dFuerSortimenteZuVerwendenderSchaftanteil > 1) {
        	throw new RuntimeException("Ung�ltiger Wert f�r zu verwendenden Schaftanteil");
        }
        
        // Sortimentsvorgaben-Liste sortieren
        svListe.sort();

        // Alle B�ume sortimentieren   
        for (int i=0; i<bsDefListe.size(); i++) {
        	StatusHandler.publishProgress((0.0 + i) / bsDefListe.size());
        	baumSortimentieren(
        			bsDefListe.get(i),
        			codeLaenge,
        			dFuerSortimenteZuVerwendenderSchaftanteil,
        			dAbzopfDurchmesserOhneRinde_cm,
        			dStockhoehe_cm);
        }
        StatusHandler.publishProgress(0); //done
    }

    private static class AktuelleSchaftPosition_m {
    	public double oben;
        public double unten;
    }
    
    private void baumSortimentieren(
    		final BaumschaftDefinition bsd,
    		final CodeLaengenKlassen codeLaenge,
            final double dFuerSortimenteZuVerwendenderSchaftanteil, 
            final double dAbzopfDurchmesserOhneRinde_cm,
            final double dStockhoehe_cm) {
    	
        final SortimentStueckInBaumschaftEinpassen ssibse = new SortimentStueckInBaumschaftEinpassen(bsd, schaftformFkt);
        final AktuelleSchaftPosition_m aktuelleSchaftPosition_m = getInitialSchaftPosition(bsd, dStockhoehe_cm, dFuerSortimenteZuVerwendenderSchaftanteil);

        while (checkNextSchaftposition(
        			bsd,
            		codeLaenge,
                    dAbzopfDurchmesserOhneRinde_cm,
                    dStockhoehe_cm,
                    ssibse,
                    aktuelleSchaftPosition_m)); // repeat as long as checkNextSchaftposition() returns true
        
        reststueckObenBestimmenOberhalbLetztemSortimentstueck(ssibse, bsd, dAbzopfDurchmesserOhneRinde_cm, codeLaenge);
        reststueckObenBestimmenFallsSchaftanteilKleinerEins(ssibse, bsd, dAbzopfDurchmesserOhneRinde_cm);
    }
    
    private AktuelleSchaftPosition_m getInitialSchaftPosition(BaumschaftDefinition bsd, double dStockhoehe_cm, double dFuerSortimenteZuVerwendenderSchaftanteil) {
        final AktuelleSchaftPosition_m aktuelleSchaftPosition_m = new AktuelleSchaftPosition_m();
    	
        aktuelleSchaftPosition_m.unten = 0;
        if (dStockhoehe_cm >= 0) {
        	aktuelleSchaftPosition_m.unten = dStockhoehe_cm / 100;
        }
        aktuelleSchaftPosition_m.oben = bsd.getSchaftLaenge_m() * dFuerSortimenteZuVerwendenderSchaftanteil;
        bsd.setFuerSortimenteZuVerwendenderSchaftanteil(dFuerSortimenteZuVerwendenderSchaftanteil);
        if (bsd.getBaumart().isLaubholz() && bsd.getKronenansatzHoeheAnteil() < 1) {
        	//bei Laubb�umen kronenh�he ber�cksichtigen
        	aktuelleSchaftPosition_m.oben = bsd.getSchaftLaenge_m() * bsd.getKronenansatzHoeheAnteil();
            bsd.setFuerSortimenteZuVerwendenderSchaftanteil(bsd.getKronenansatzHoeheAnteil());
        }
        
        return aktuelleSchaftPosition_m;
    }
    
    private boolean checkNextSchaftposition(
    		final BaumschaftDefinition bsd,
    		final CodeLaengenKlassen codeLaenge,
            final double dAbzopfDurchmesserOhneRinde_cm,
            final double dStockhoehe_cm,
            final SortimentStueckInBaumschaftEinpassen ssibse,
            final AktuelleSchaftPosition_m aktuelleSchaftPosition_m) {

        double dLaengenZugabe_m = 0;
        boolean isSortimentsStueckErstellt = false;
        boolean isRestStueckErstellt = false;
        
        double dPositionZopfDrmAmSchaft_m = 0;
    	if (dAbzopfDurchmesserOhneRinde_cm >= 0) { // F�r diesen Schaft allf�llige Abzopfposition ermitteln
    		dPositionZopfDrmAmSchaft_m = bsd.getHoehe_m(dAbzopfDurchmesserOhneRinde_cm, false, 0.05);
    	}

    	for (int currentSvIndex=0; currentSvIndex < svListe.size(); currentSvIndex++) {
    		SortimentsVorgabe sv = svListe.get(currentSvIndex).clone(); // clone n�tig, da Position am Stamm danach ver�ndert wird.
    		
    		if (isSortimentsvorgabeOk(sv, bsd, codeLaenge) == false) {
    			continue; // check next SortimentsVorgabe
    		}

    		if (dAbzopfDurchmesserOhneRinde_cm >= 0) {
    			// Obere Schaftpos. auf Abzopfdurchm. setzen!
    			aktuelleSchaftPosition_m.oben = Math.min(aktuelleSchaftPosition_m.oben, dPositionZopfDrmAmSchaft_m);
    		}

    		// Sortimentsvorgaben mit tempor�ren, aktuell g�ltigen Infos versehen
    		updateSortimentsvorgabenWithTempInfo(ssibse, sv, aktuelleSchaftPosition_m, dStockhoehe_cm);

    		// Versuch, Sortimentsst�ck einzupassen
    		isSortimentsStueckErstellt = false;
    		boolean isSortimentEinpassenOk = (ssibse.sortimentEinpassen(sortimentStueckListe.size() + 1, getReststueckKategorie(sv)) == null);
    		if (isSortimentEinpassenOk) {
    			sortimentStueckListe.add(ssibse.getSortimentStueck());
    			ssibse.getSortimentsVorgaben().reduceAnzahlStueck(); // muss wegen clone() auf beiden Instanzen aufgerufen werden.
    			svListe.get(currentSvIndex).reduceAnzahlStueck(); // muss wegen clone() auf beiden Instanzen aufgerufen werden.

    			// L�ngenzugabe ermitteln
    			dLaengenZugabe_m = getLaengenzugabe_m(ssibse.getSortimentStueck());

    			isSortimentsStueckErstellt = true;  
    			if( CodeLaengenKlassen.Restholz.equals(sv.getLaengenKlassenCode()) ) {
            		isRestStueckErstellt = true;
            	}
    			break;
    		}

            // Sortimentsst�ck nicht erstellt
            dLaengenZugabe_m = 0;
        }
    	return isWeiterSortimentieren(aktuelleSchaftPosition_m, ssibse, isSortimentsStueckErstellt, isRestStueckErstellt, dLaengenZugabe_m);
    }
    
    private boolean isSortimentsvorgabeOk(SortimentsVorgabe sv, BaumschaftDefinition bsDef, CodeLaengenKlassen codeLaenge) {
		//Check, ob weitere St�cke m�glich bei aktueller Sortimentsvorgabe
		if (sv.getAnzahlStueck() <= 0) {
			return false; 
		}
		
		//Check, ob Baumart ok
        if (bsDef.getBaumart() != sv.getBaumart()) {
        	return false;
        }
		
		//Check, ob Laengenklasse ok
        boolean isLangenKlasseOk = codeLaenge.name().contains( sv.getLaengenKlassenCode().name() ) || CodeLaengenKlassen.Restholz.equals(sv.getLaengenKlassenCode());
        if ( ! isLangenKlasseOk ) {
        	if (bsDef.getBaumart().isLaubholz() && CodeLaengenKlassen.L1.equals(sv.getLaengenKlassenCode()) && !CodeLaengenKlassen.Restholz.equals(codeLaenge) ) {
        		//Bei Laubb�umen automatisch L1 verwenden, ausser es wurde explizit Restholz gew�hlt
        		isLangenKlasseOk = true;
        	} else {
        		//Falsche L�ngenklasse -> n�chste SV pr�fen
        		return false;
        	}
        }
        return true;
    }
    
    private void updateSortimentsvorgabenWithTempInfo(SortimentStueckInBaumschaftEinpassen ssibse, SortimentsVorgabe sv, AktuelleSchaftPosition_m aktuelleSchaftPosition_m, double dStockhoehe_cm) {
		ssibse.setSortimentsVorgaben(sv);
		if (dStockhoehe_cm >= 0) {
    		sv.setPositionAmStammUnten_m(dStockhoehe_cm / 100);
		}
		sv.setPositionAmStammOben_m(round( Math.min(aktuelleSchaftPosition_m.oben, sv.getPositionAmStammOben_m()) , 2));
		sv.setPositionAmStammUnten_m(round( Math.max(aktuelleSchaftPosition_m.unten, sv.getPositionAmStammUnten_m()) , 2));
		if (sv.getLaengenIntervall_m() < 0) {
			throw new RuntimeException("L�ngen-Intervall muss positiv sein!");
		}
    }
    
    private ReststueckKategorie getReststueckKategorie(SortimentsVorgabe sv) {
		if (CodeLaengenKlassen.Restholz.equals(sv.getLaengenKlassenCode())) {
    		if (sv.getPositionierung() == SortimentsStueckPositionierung.ZuOberst) {
    			return ReststueckKategorie.Kat1unten;
    		}
    		else {
				return ReststueckKategorie.Kat1;
    		}
    	}
		return null;
    }
    
    private boolean isWeiterSortimentieren(
    		final AktuelleSchaftPosition_m aktuelleSchaftPosition_m,
    		final SortimentStueckInBaumschaftEinpassen ssibse,
    		final boolean isSortimentsStueckErstellt,
    		final boolean isRestStueckErstellt,
    		final double dLaengenZugabe_m) {
    	
    	if (isRestStueckErstellt) {
    		//Falls letztes Sortimentst�ck Restholz war, ist der aktuelle Baum fertig sortimentiert
    		return false;
    	}
    	else {
        	//Neue Positionen am Stamm definieren
        	updateSchaftPositionToNextPosition(aktuelleSchaftPosition_m, ssibse, isSortimentsStueckErstellt, dLaengenZugabe_m);
            
            // Neue Positionen pr�fen
            double dSchaftAbschnitt_m = round(aktuelleSchaftPosition_m.oben - aktuelleSchaftPosition_m.unten, 2);
            if (dSchaftAbschnitt_m < 0) {
            	return false; //Neuer Schaftabschnitt ist zu kurz 
            }
            else {
                return true; // weiter sortimentieren
            }
    	}
    }
    
    private void updateSchaftPositionToNextPosition(
    		final AktuelleSchaftPosition_m aktuelleSchaftPosition_m,
    		final SortimentStueckInBaumschaftEinpassen ssibse,
    		final boolean isSortimentsStueckErstellt,
    		final double dLaengenZugabe_m) {

        if (isSortimentsStueckErstellt) {
            double dLaengeDerPositionsAenderung = ssibse.getSortimentStueck().getLaenge_m() + dLaengenZugabe_m;
            
            // Neue Positionen definieren (abh�ngig davon, ob das letzte erfolgreich eingepasste SortSt�ck oben oder unten positioniert war
            if (ssibse.getSortimentsVorgaben().getPositionierung() == SortimentsStueckPositionierung.ZuUnterst) {
            	aktuelleSchaftPosition_m.unten = ssibse.getSortimentsVorgaben().getPositionAmStammUnten_m() + dLaengeDerPositionsAenderung;
            } 
            else {
//            	dAktuelleSchaftPositionOben_m = ssibse.getSortimentsVorgaben().getPositionAmStammOben_m() - dLaengeDerPositionsAenderung;
            	aktuelleSchaftPosition_m.oben = ssibse.getSortimentStueck().getPositionDerBasisAmStamm_m() - dLaengenZugabe_m;
            }
        } else {
            double dLaengeDerPositionsAenderung = PRUEFSCHRITTWEITE_AM_BAUMSCHAFT_M;

            // Neue Positionen definieren (immer von unten her)
            aktuelleSchaftPosition_m.unten += dLaengeDerPositionsAenderung;
        	//FIXME: das macht keinen sinn f�r sortimentSt�cke, die zuOberst eingepasst werden sollen
        	// -> Kann insbesondere zu Fehlern f�hren, wenn sich zuUnterst / zuOberst abwechseln!

//          // Neue Positionen definieren 
//          if (ssibse.getSortimentsVorgaben().getPositionierung() == ESortimentsStueckPositionierung.ZuUnterst) {
//          	dAktuelleSchaftPositionUnten_m += dLaengeDerPositionsAenderung;
//          } else {
//          	dAktuelleSchaftPositionOben_m -= dLaengeDerPositionsAenderung;
//          }
        }
    }
    
    private void reststueckObenBestimmenOberhalbLetztemSortimentstueck(SortimentStueckInBaumschaftEinpassen ssibse, BaumschaftDefinition bsDef, double dAbzopfDurchmesserOhneRinde_cm, CodeLaengenKlassen codeLaenge) {
        //Falls Positionierung zuOberst: Restst�ck zwischen ZopfPos vom obersten SortimentSt�ck und Schaftende bestimmen!
        if ( ! CodeLaengenKlassen.Restholz.equals(codeLaenge) && ssibse.getSortimentsVorgaben().getPositionierung() == SortimentsStueckPositionierung.ZuOberst) {      	
        	// 1. Position des Restst�ckes bestimmen            	
        	//Obere Position vom Restst�ck bestimmen
        	double dPositionSchaftanteil = bsDef.getSchaftLaenge_m() * bsDef.getFuerSortimenteZuVerwendenderSchaftanteil();
        	double dPositionAbzopfDrm = bsDef.getSchaftLaenge_m();            			
        	if (dAbzopfDurchmesserOhneRinde_cm >= 0) {
        		dPositionAbzopfDrm = bsDef.getHoehe_m(dAbzopfDurchmesserOhneRinde_cm,false, 0.05);
        		if (dPositionAbzopfDrm < 0) {
        			dPositionAbzopfDrm = 0;
        		}
        	}
        	double dPositionReststueckEnde = Math.min(dPositionSchaftanteil, dPositionAbzopfDrm);
        	double dPositionReststueckStart = getStartPosReststueckOberhalbVomOberstenSortimentstueck(bsDef);
        
        	// 2. Restst�ck erstellen
        	//Passende Sortimentsvorgabe suchen
        	SortimentsVorgabe sv = getSortimentsVorgabeRestholz(bsDef.getBaumart());
    		ssibse.setSortimentsVorgaben(sv.clone()); // clone n�tig, da Position am Stamm danach ver�ndert wird.
         	
    		// Sortimentsvorgaben mit tempor�ren, aktuell g�ltigen Infos versehen
    		ssibse.getSortimentsVorgaben().setPositionAmStammOben_m(dPositionReststueckEnde);
    		ssibse.getSortimentsVorgaben().setPositionAmStammUnten_m(dPositionReststueckStart);

    		// Versuch, Sortimentsst�ck einzupassen
    		boolean isSortimentEinpassenOk = ssibse.sortimentEinpassen(sortimentStueckListe.size() + 1, ReststueckKategorie.Kat1oben) == null;
    		if (isSortimentEinpassenOk) {
    			sortimentStueckListe.add(ssibse.getSortimentStueck());
    		} else {
    			//Restst�ck kann nicht erstellt werden
    			// -> oberstes Sortimentst�ck geht bereits bis an Schaftende
//    			throw new RuntimeException("Restst�ck konnte nicht erstellt werden!\n" + fehlerMeldung);
    		}            	
        }
    }
    
    private double getStartPosReststueckOberhalbVomOberstenSortimentstueck(BaumschaftDefinition bsDef) {
    	for (int i=sortimentStueckListe.size()-1; i>=0; i--) {
    		// oberstes Sortimentst�ck ist das mit der kleinsten Position in der ssListe
    		if (sortimentStueckListe.get(i).getBsDef().getId() != bsDef.getId() || i==0) {
    			//neuer baum -> vorheriges sortimentsSt�ck ist das richtige
    			SortimentsStueck ssTemp = sortimentStueckListe.get(i+1);
    			if (i==0) {
    				ssTemp = sortimentStueckListe.get(0); //Sonderfall erstes Element
    			}
    			if (ssTemp.getBsDef().getId() != bsDef.getId()) {
    				throw new RuntimeException("Fehler: Falsches Sortimentst�ck"); //This should never happen!!
    			}
    			
       			// L�ngenzugabe ermitteln
    			double zugabe_m = getLaengenzugabe_m(ssTemp);
    			return ssTemp.getPositionDerBasisAmStamm_m() + ssTemp.getLaenge_m() + zugabe_m;
    		}
    	}
    	throw new RuntimeException("Fehler: Sortimentst�ck nicht gefunden"); //This should never happen!! //TODO: kann passieren, falls nur nach resst�cken sortiert wird??
    }
    
    private double getLaengenzugabe_m(SortimentsStueck ss) {
		double zugabe_m;
		if (ss.getSortimentsVorgabe().getLaengenZugabe_Prozent() > 0) {
			zugabe_m = (ss.getSortimentsVorgabe().getLaengenZugabe_Prozent() / 100) * ss.getLaenge_m();
		} 
		else {
			zugabe_m = ss.getSortimentsVorgabe().getLaengenZugabe_cm() / 100;
		}
		return zugabe_m;
    }
    
    private void reststueckObenBestimmenFallsSchaftanteilKleinerEins(SortimentStueckInBaumschaftEinpassen ssibse, BaumschaftDefinition bsDef, double dAbzopfDurchmesserOhneRinde_cm) {
        //Falls zu verwendender Schaftanteil < 1: Restst�ck bestimmen
        if (bsDef.getFuerSortimenteZuVerwendenderSchaftanteil() < 1 || dAbzopfDurchmesserOhneRinde_cm > 0) {
        	//Untere Position vom Restst�ck bestimmen
        	double dPositionSchaftanteil = bsDef.getSchaftLaenge_m() * bsDef.getFuerSortimenteZuVerwendenderSchaftanteil();
        	double dPositionAbzopfDrm = bsDef.getSchaftLaenge_m();            			
        	if (dAbzopfDurchmesserOhneRinde_cm >= 0) {
        		dPositionAbzopfDrm = bsDef.getHoehe_m(dAbzopfDurchmesserOhneRinde_cm,false, 0.05);
        		if (dPositionAbzopfDrm < 0) {
        			dPositionAbzopfDrm = 0;
        		}
        	}
        	double dPositionReststueckStart = Math.min(dPositionSchaftanteil, dPositionAbzopfDrm);
        	
        	//Passende Sortimentsvorgabe suchen
        	SortimentsVorgabe sv = getSortimentsVorgabeRestholz(bsDef.getBaumart());
    		ssibse.setSortimentsVorgaben(sv.clone()); // clone n�tig, da Position am Stamm danach ver�ndert wird.
         	
    		// Sortimentsvorgaben mit tempor�ren, aktuell g�ltigen Infos versehen
    		ssibse.getSortimentsVorgaben().setPositionAmStammOben_m(bsDef.getSchaftLaenge_m());
    		ssibse.getSortimentsVorgaben().setPositionAmStammUnten_m(dPositionReststueckStart);

    		// Versuch, Sortimentsst�ck einzupassen
    		boolean isSortimentEinpassenOk = (ssibse.sortimentEinpassen(sortimentStueckListe.size() + 1, ReststueckKategorie.Kat2) == null);
    		if (isSortimentEinpassenOk) {
    			sortimentStueckListe.add(ssibse.getSortimentStueck());
    		} else {
    			throw new RuntimeException("Restst�ck konnte nicht erstellt werden!");
    		}
        }
    }
    
    private SortimentsVorgabe getSortimentsVorgabeRestholz(Baumart baumart) {
     	for (SortimentsVorgabe sv : svListe) {
            boolean bLangenKlasseOk = CodeLaengenKlassen.Restholz.equals(sv.getLaengenKlassenCode());
            boolean bBaumartOk = baumart == sv.getBaumart();
            
            if (bLangenKlasseOk && bBaumartOk) {
        		return sv;
            }
     	}
     	throw new RuntimeException("keine passende Sortimentsvorgabe gefunden"); // this should never happen
    }
    
    private double round(double input, int anzahlStellen) {
    	double factor = Math.pow(10, anzahlStellen);
    	return Math.round(input * factor) / factor;    	
    } 

    public SortimentVorgabenListe getSvListe() {
		return svListe;
	}

	public List<BaumschaftDefinition> getBsDefListe() {
		return bsDefListe;
	}

	public List<SortimentsStueck> getSsListe() {
		return sortimentStueckListe;
	}
	
	public void clearSortimentsStueckListe() {
		getSsListe().clear();
	}
}
