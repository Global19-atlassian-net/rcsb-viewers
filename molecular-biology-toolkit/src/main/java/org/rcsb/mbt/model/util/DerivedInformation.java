/*
 * BioJava development code
 *
 * This code may be freely distributed and modified under the
 * terms of the GNU Lesser General Public Licence. This should
 * be distributed with the code. If you do not have a copy,
 * see:
 *
 * http://www.gnu.org/copyleft/lesser.html
 *
 * Copyright for this code is held jointly by the individual
 * authors. These should be listed in @author doc comments.
 *
 * For more information on the BioJava project and its aims,
 * or to join the biojava-l mailing list, visit the home page
 * at:
 *
 * http://www.biojava.org/
 *
 * This code was contributed from the Molecular Biology Toolkit
 * (MBT) project at the University of California San Diego.
 *
 * Please reference J.L. Moreland, A.Gramada, O.V. Buzko, Qing
 * Zhang and P.E. Bourne 2005 The Molecular Biology Toolkit (MBT):
 * A Modular Platform for Developing Molecular Visualization
 * Applications. BMC Bioinformatics, 6:21.
 *
 * The MBT project was funded as part of the National Institutes
 * of Health PPG grant number 1-P01-GM63208 and its National
 * Institute of General Medical Sciences (NIGMS) division. Ongoing
 * development for the MBT project is managed by the RCSB
 * Protein Data Bank(http://www.pdb.org) and supported by funds
 * from the National Science Foundation (NSF), the National
 * Institute of General Medical Sciences (NIGMS), the Office of
 * Science, Department of Energy (DOE), the National Library of
 * Medicine (NLM), the National Cancer Institute (NCI), the
 * National Center for Research Resources (NCRR), the National
 * Institute of Biomedical Imaging and Bioengineering (NIBIB),
 * the National Institute of Neurological Disorders and Stroke
 * (NINDS), and the National Institute of Diabetes and Digestive
 * and Kidney Diseases (NIDDK).
 *
 * Created on 2007/02/08
 *
 */ 
package org.rcsb.mbt.model.util;

import java.util.Vector;
import java.util.ListIterator;

import org.rcsb.mbt.model.Atom;
import org.rcsb.mbt.model.Chain;
import org.rcsb.mbt.model.Residue;
import org.rcsb.mbt.model.Structure;
import org.rcsb.mbt.model.StructureMap;
import org.rcsb.mbt.model.Residue.Classification;
import org.rcsb.mbt.model.StructureComponentRegistry.ComponentType;


/**
 * DerivedInformation is a class that encapsulates methods that produce
 * information derivable from data in the source file. Example: secondary
 * structures.
 * <P>
 * This implementation is based on the one in Molscript by Per Kraulis and the
 * original paper by Kabsch and Sander Kabsch W and Sander C, "Dictionary of
 * Protein Secondary Structure: Pattern Recognition of Hydrogen-Bonded and
 * Geometrical Features" Byopolymers, Vol. 22, pp 2577-2637 (1983).
 * <P>
 * 
 * @author Apostol Gramada
 */
public class DerivedInformation
{
	private StructureMap structureMap;

	private final float hBondCutoffDistance = 8.0f;

	private final float energyFactor = 332.0f;

	private final float charge1 = 0.42f;

	private final float charge2 = 0.20f;

	private char[] ssFlags = null;

	private int[][] chains = null;

	/**
	 * Creates a DerivedInformation object from a structure and a StructureMap
	 * object.
	 */
	public DerivedInformation(final Structure structure, final StructureMap structureMap) {
		this.structureMap = structureMap;
	}

	/**
	 * Sets the residue flags according to the extended classification in the
	 * Kabsch-Sander algorithm.
	 */
	private Object[] setSsExtendedFlags() {
		Vector<BondInfo> bondList = null; // Candidate H bonds
		
	try {
		int aaCount = 0;
		int resCount;
		int[] resPointers;
		int[] coHBonds;
		int[] hnHBonds;
		double[] coEnergy;
		double[] hnEnergy;
		byte[] pattern;
		int[] beta1;
		int[] beta2;

		resCount = this.structureMap.getResidueCount();
		// char[] ssFlags = null;
		this.ssFlags = new char[Math.max(resCount, 2)];
		beta1 = new int[resCount];
		beta2 = new int[resCount];

		// Possible hbonding patterns
		//
		final byte HBONDS_3TURN = 1;
		final byte HBONDS_4TURN = 2;
		final byte HBONDS_5TURN = 4;
		final byte HBONDS_ANTIPARA = 8;
		final byte HBONDS_PARA = 16;

		// First, figure out how many aa we have among all residues listed in
		// the StructureMap
		//
		// Iterate over residues, check whether aa and set flags a la Molscript
		//
		Residue residue = null;
		for (int i = 0; i < resCount; i++) {
			residue = this.structureMap.getResidue(i);

			if (residue.getClassification() == Residue.Classification.AMINO_ACID) { // Is
																				// aa
				aaCount++;
				this.ssFlags[i] = ' ';
			} else {
				this.ssFlags[i] = '-'; // Non aa
			}

			// Initialize some fields with this occasion
			//
			beta1[i] = beta2[i] = -1;
		}

		// Initialize different data arrays: hbonds, pointers to original
		// residues, pattern fields
		//
		coHBonds = new int[aaCount];
		hnHBonds = new int[aaCount];
		resPointers = new int[aaCount];
		pattern = new byte[aaCount];
		coEnergy = new double[aaCount];
		hnEnergy = new double[aaCount];

		// Sets the pointers into the initial residue array
		//
		int aaIndex = -1;
		for (int i = 0; i < resCount; i++) {
			if (this.structureMap.getResidue(i).getClassification() == Residue.Classification.AMINO_ACID) {

				aaIndex++;
				resPointers[aaIndex] = i;
			}
		}

		// Working space for atoms needed to calculate the hbond energy.
		//
		final double[] cCoord1 = new double[3];
		final double[] nCoord1 = new double[3];
		final double[] oCoord1 = new double[3];
		final double[] hCoord1 = new double[3];

		final double[] cCoord2 = new double[3];
		final double[] nCoord2 = new double[3];
		final double[] oCoord2 = new double[3];
		final double[] hCoord2 = new double[3];

		final double[] cCoordM1 = new double[3];
		final double[] oCoordM1 = new double[3];

		// Assign the hydrogen bonds. First, select from the aaCount^2
		// candidates only those within
		// a certain distance threshold and at least 3 aa apart. Use an octree
		// to perform this selection.
		// 
		// Ideally, I would like to totally incorporate the H bond detection in
		// a method of the octree class.
		// However, for the moment I don't have the right solution to doing
		// this.
		//
		Atom atom1 = new Atom();
		final Vector<Integer> chainStarts = new Vector<Integer>();
		final Vector<Integer> chainEnds = new Vector<Integer>();
		final double[][] caCoord = new double[aaCount][3];
		int resStart, resEnd, resCa;
		final OctreeAtomItem[] treeData = new OctreeAtomItem[aaCount];
		String previousChainId = "NONE";
		for (int i = 0; i < aaCount; i++) {
			final Residue r = this.structureMap.getResidue(resPointers[i]);
			atom1 = r.getAlphaAtom();
			if (atom1 == null) {
				System.err.println("AA " + resPointers[i]
						+ "  Does not seem to have a CA atom ");
				// use a random atom as the backbone atom...
				atom1 = r.getAtom(0);
			}
			caCoord[i][0] = atom1.coordinate[0];
			caCoord[i][1] = atom1.coordinate[1];
			caCoord[i][2] = atom1.coordinate[2];
		    treeData[i] = new OctreeAtomItem(atom1, i);
			if (!atom1.chain_id.equals(previousChainId)) {
				chainStarts.add(new Integer(resPointers[i]));
				if (i > 0) {
					chainEnds.add(new Integer(resPointers[i - 1]));
				}
				previousChainId = new String(atom1.chain_id);
			}
			// System.out.println( "" + i + " " + resPointers[i] + " " +
			// caCoord[i][0] + " " + caCoord[i][1] + " " + caCoord[i][2] );

			// Take this opportunity to initialize some fields. H-bonds are set
			// to -1 initially,
			// which is "no bond from this residue"
			//
			coHBonds[i] = -1;
			hnHBonds[i] = -1;
			coEnergy[i] = 1.0E10f;
			hnEnergy[i] = 1.0E10f;
		}
		chainEnds.add(new Integer(aaCount - 1));

		chainStarts.trimToSize();
		chainEnds.trimToSize();
		this.chains = new int[chainStarts.size()][2];
		// chainBreaks = new int[chainStarts.size()];
		final ListIterator<Integer> startIterator = chainStarts.listIterator();
		final ListIterator<Integer> endIterator = chainEnds.listIterator();
		int chn = 0;
		while (startIterator.hasNext()) {
			this.chains[chn][0] = (startIterator.next()).intValue();
			this.chains[chn][1] = (endIterator.next()).intValue();
			chn++;
		}

		// Build the octree
		//
		final float[] margin = new float[3];
		margin[0] = margin[1] = margin[2] = 0.0f;
		final Octree tree = new Octree(3, treeData, margin);
		try {
			tree.build();
		} catch (final ExcessiveDivisionException e) {
			System.err.println(e.toString());
		}
		// boolean existMultipleItems = tree.existMultipleItems();

		// Get the list of candidate H bonds
		//
		bondList = tree.getHBondInfoVector(this.hBondCutoffDistance);
		// bondList = tree.getHBonds( hBondCutoffDistance );
		bondList.trimToSize();
		// System.out.println( " Size of the Potential bond list: " +
		// bondList.size() );

		final ListIterator<BondInfo> iterator = bondList.listIterator();

//		double time1, time2, time;
//		time = 0.0;
//		int strCalls = 0;

		int atIded;

		// Now, select only the bonds that satisfy the energy < -0.5 kcal/mol
		// criterion
		//
		double energy;
		BondInfo bondData;
		int index1, index2;
		double distance;
		int previousIndex1 = -1;
		boolean rem1, rem2, cFound, oFound;
		// Residue residue = null;
		while (iterator.hasNext()) {
			bondData = iterator.next();
			index1 = bondData.index1;
			index2 = bondData.index2;
			/*
			 * if( (index2 - index1) < 3 ) { iterator.remove(); continue; }
			 */
			rem1 = false;
			rem2 = false;

			// Get the coordinates needed to calculate the energy of the
			// hydrogen bond between the two residues
			// pointed at by index1 and index2
			//
			// index1 data (if not already calculated)
			//
			residue = this.structureMap.getResidue(resPointers[index1]);
			resStart = 0;
			resEnd = residue.getAtomCount() - 1;
			resCa = residue.getAlphaAtomIndex();
			if (index1 != previousIndex1) {
				atIded = 0;
				for (int j = resStart; (atIded < 1) && (j <= resEnd); j++) {
//					time1 = System.currentTimeMillis();
					atom1 = residue.getAtom(j);
//					strCalls++;
//					time2 = System.currentTimeMillis();
//					time += (time2 - time1);
					if (atom1.name.equals("N")) {
						nCoord1[0] = atom1.coordinate[0];
						nCoord1[1] = atom1.coordinate[1];
						nCoord1[2] = atom1.coordinate[2];
						atIded++;
					}
				}

				atIded = 0;
				cFound = false;
				oFound = false;
				for (int j = resCa + 1; (atIded < 2) && (j <= resEnd); j++) {
//					time1 = System.currentTimeMillis();
					atom1 = residue.getAtom(j);
//					strCalls++;
//					time2 = System.currentTimeMillis();
//					time += (time2 - time1);
					if (atom1.name.equals("C") && (!cFound)) {
						cCoord1[0] = atom1.coordinate[0];
						cCoord1[1] = atom1.coordinate[1];
						cCoord1[2] = atom1.coordinate[2];
						atIded++;
						cFound = true;
					}
					if (atom1.name.equals("O") && (!oFound)) {
						oCoord1[0] = atom1.coordinate[0];
						oCoord1[1] = atom1.coordinate[1];
						oCoord1[2] = atom1.coordinate[2];
						atIded++;
						oFound = true;
					}
				}

				// Need to infer the position of the H atom from the position of
				// the closest C=O bond
				// since it is not given in the pdb file
				//
				if (index1 == 0) { // No previous amino to look for trans
									// configuration accross the peptide bond
					this.diff(hCoord1, oCoord1, cCoord1);
					this.normalize(hCoord1, hCoord1);
					this.scale(hCoord1, hCoord1, 1.008f);
					this.add(hCoord1, nCoord1, hCoord1);
				} else {
					residue = this.structureMap.getResidue(resPointers[index1 - 1]);
					resStart = 0;
					resEnd = residue.getAtomCount() - 1;
					resCa = residue.getAlphaAtomIndex();
					atIded = 0;
					cFound = false;
					oFound = false;
					for (int j = resCa + 1; (atIded < 2) && (j <= resEnd); j++) {
//						time1 = System.currentTimeMillis();
						atom1 = residue.getAtom(j);
//						strCalls++;
//						time2 = System.currentTimeMillis();
//						time += (time2 - time1);
						if (atom1.name.equals("C") && (!cFound)) {
							cCoordM1[0] = atom1.coordinate[0];
							cCoordM1[1] = atom1.coordinate[1];
							cCoordM1[2] = atom1.coordinate[2];
							atIded++;
							cFound = true;
						}
						if (atom1.name.equals("O") && (!oFound)) {
							oCoordM1[0] = atom1.coordinate[0];
							oCoordM1[1] = atom1.coordinate[1];
							oCoordM1[2] = atom1.coordinate[2];
							atIded++;
							oFound = true;
						}
					}

					distance = this.dist(cCoordM1, nCoord1);
					if (distance <= 2.0) {
						this.diff(hCoord1, cCoordM1, oCoordM1);
					} else {
						this.diff(hCoord1, oCoord1, cCoord1);
					}
					this.normalize(hCoord1, hCoord1);
					this.scale(hCoord1, hCoord1, 1.008f);
					this.add(hCoord1, nCoord1, hCoord1);
				}
				previousIndex1 = index1;
			}

			// index2 data
			//
			residue = this.structureMap.getResidue(resPointers[index2]);
			resStart = 0;
			resEnd = residue.getAtomCount() - 1;
			resCa = residue.getAlphaAtomIndex();
			atIded = 0;
			for (int j = resStart; (atIded < 1) && (j <= resEnd); j++) {
//				time1 = System.currentTimeMillis();
				atom1 = residue.getAtom(j);
//				strCalls++;
//				time2 = System.currentTimeMillis();
//				time += (time2 - time1);
				if (atom1.name.equals("N")) {
					nCoord2[0] = atom1.coordinate[0];
					nCoord2[1] = atom1.coordinate[1];
					nCoord2[2] = atom1.coordinate[2];
					atIded++;
				}
			}

			atIded = 0;
			cFound = false;
			oFound = false;
			for (int j = resCa + 1; (atIded < 2) && (j <= resEnd); j++) {
//				time1 = System.currentTimeMillis();
				atom1 = residue.getAtom(j);
//				strCalls++;
//				time2 = System.currentTimeMillis();
//				time += (time2 - time1);
				if (atom1.name.equals("C") && (!cFound)) {
					cCoord2[0] = atom1.coordinate[0];
					cCoord2[1] = atom1.coordinate[1];
					cCoord2[2] = atom1.coordinate[2];
					atIded++;
					cFound = true;
				}
				if (atom1.name.equals("O") && (!oFound)) {
					oCoord2[0] = atom1.coordinate[0];
					oCoord2[1] = atom1.coordinate[1];
					oCoord2[2] = atom1.coordinate[2];
					atIded++;
					oFound = true;
				}
			}

			// Need to infer the position of the H atom from the position of the
			// closest C=O bond
			// since it is not given in the pdb file
			//
			residue = this.structureMap.getResidue(resPointers[index2 - 1]);
			resStart = 0;
			resEnd = residue.getAtomCount() - 1;
			resCa = residue.getAlphaAtomIndex();

			atIded = 0;
			cFound = false;
			oFound = false;
			for (int j = resCa + 1; (atIded < 2) && (j <= resEnd); j++) {
//				time1 = System.currentTimeMillis();
				atom1 = residue.getAtom(j);
//				strCalls++;
//				time2 = System.currentTimeMillis();
//				time += (time2 - time1);
				if (atom1.name.equals("C") && (!cFound)) {
					cCoordM1[0] = atom1.coordinate[0];
					cCoordM1[1] = atom1.coordinate[1];
					cCoordM1[2] = atom1.coordinate[2];
					atIded++;
					cFound = true;
				}
				if (atom1.name.equals("O")) {
					oCoordM1[0] = atom1.coordinate[0];
					oCoordM1[1] = atom1.coordinate[1];
					oCoordM1[2] = atom1.coordinate[2];
					atIded++;
					oFound = true;
				}
			}

			distance = this.dist(cCoordM1, nCoord2);
			if (distance <= 2.0) {
				this.diff(hCoord2, cCoordM1, oCoordM1);
			} else {
				this.diff(hCoord2, oCoord2, cCoord2);
			}
			this.normalize(hCoord2, hCoord2);
			this.scale(hCoord2, hCoord2, 1.008f);
			this.add(hCoord2, nCoord2, hCoord2);

			// System.out.println( " " + index1 + " " + index2 );

			// Finally, calculate the energy associated with a potential
			// hydrogen bond between aas at index1 and index2.
			// If > -0.5 reject the bond and remove it from the bondlist.
			// Otherwise, sets the corresponding bonding flags.
			//
			energy = 1.0f / this.dist(oCoord1, nCoord2) + 1.0f
					/ this.dist(cCoord1, hCoord2) - 1.0f / this.dist(oCoord1, hCoord2)
					- 1.0f / this.dist(cCoord1, nCoord2);
			energy *= this.energyFactor * this.charge1 * this.charge2;
			if ((energy < -0.5) & (energy < coEnergy[index1])) {
				coHBonds[index1] = index2;
				hnHBonds[index2] = index1;
				coEnergy[index1] = energy;
			} else {
				rem1 = true;
			}

			energy = 1.0f / this.dist(nCoord1, oCoord2) + 1.0f
					/ this.dist(hCoord1, cCoord2) - 1.0f / this.dist(hCoord1, oCoord2)
					- 1.0f / this.dist(nCoord1, cCoord2);
			energy *= this.energyFactor * this.charge1 * this.charge2;
			if ((energy < -0.5) & (energy < hnEnergy[index1])) {
				hnHBonds[index1] = index2;
				coHBonds[index2] = index1;
				hnEnergy[index1] = energy;
			} else {
				rem2 = true;
			}

			if (rem1 & rem2) {
				iterator.remove();
			}
		}

		/*
		 * System.out.println( "Bonds and HBonds pointers" ); for( int i = 0; i <
		 * aaCount; i++ ) { System.out.println( " " + i + " " + coHBonds[i] + " " +
		 * hnHBonds[i] + " " + resPointers[i]); }
		 */

		// ///////////////////////////////////////////////////////////////////////////////////////
		// Setting the main n-turn and bridge pattern flags
		// ---------------------------------------------------------------------------------------
		// This closely follows the implementation in Molscript
		// ---------------------------------------------------------------------------------------
		// ///////////////////////////////////////////////////////////////////////////////////////
		// Set the n-turn pattern flags
		//
		int seqDist;
		for (int i = 0; i < aaCount; i++) {
			if (coHBonds[i] > -1) {
				seqDist = resPointers[coHBonds[i]] - resPointers[i];
				switch (seqDist) {
				case 3:
					pattern[i] |= HBONDS_3TURN;
					break;
				case 4:
					pattern[i] |= HBONDS_4TURN;
					break;
				case 5:
					pattern[i] |= HBONDS_5TURN;
					break;
				default:
					break;
				}
			}
		}

		// Set the anti-parallel bridge pattern flags
		//
		// Case 1
		//
		for (int i = 0; i < aaCount; i++) {
			if (coHBonds[i] > -1) {
				if (coHBonds[coHBonds[i]] == i) {
					pattern[i] |= HBONDS_ANTIPARA;
					if (beta1[resPointers[i]] == -1) {
						beta1[resPointers[i]] = resPointers[coHBonds[i]];
					} else if (beta1[resPointers[i]] != resPointers[coHBonds[i]]) {
						beta2[resPointers[i]] = resPointers[coHBonds[i]];
					}
					pattern[coHBonds[i]] |= HBONDS_ANTIPARA;
					if (beta1[resPointers[coHBonds[i]]] == -1) {
						beta1[resPointers[coHBonds[i]]] = resPointers[i];
					} else if (beta1[resPointers[coHBonds[i]]] != resPointers[i]) {
						beta2[resPointers[coHBonds[i]]] = resPointers[i];
					}
				}
			}
		}
		//
		// Case 2
		//
		int index3;
		for (int i = 0; i < aaCount - 2; i++) {
			if (coHBonds[i] > -1) {
				index2 = hnHBonds[i + 2];
				if (index2 > -1) {
					if ((resPointers[coHBonds[i]] - resPointers[index2]) == 2) {
						index2++;
						index3 = i + 1;
						pattern[index3] |= HBONDS_ANTIPARA;
						if (beta1[resPointers[index3]] == -1) {
							beta1[resPointers[index3]] = resPointers[index2];
						} else if (beta1[resPointers[index3]] != resPointers[index2]) {
							beta2[resPointers[index3]] = resPointers[index2];
						}
						pattern[index2] |= HBONDS_ANTIPARA;
						if (beta1[resPointers[index2]] == -1) {
							beta1[resPointers[index2]] = resPointers[index3];
						} else if (beta1[resPointers[index2]] != resPointers[index3]) {
							beta2[resPointers[index2]] = resPointers[index3];
						}
					}
				}
			}
		}

		// Set the parallel bridge pattern flags
		//
		// Case 1
		//
		for (int i = 1; i < aaCount; i++) {
			index2 = coHBonds[i - 1];
			if ((index2 > -1) && (coHBonds[index2] > -1)) {
				if ((resPointers[coHBonds[index2]] - resPointers[i]) == 1) {
					pattern[i] |= HBONDS_PARA;
					if (beta1[resPointers[i]] == -1) {
						beta1[resPointers[i]] = resPointers[index2];
					} else if (beta1[resPointers[i]] != resPointers[index2]) {
						beta2[resPointers[i]] = resPointers[index2];
					}
					pattern[index2] |= HBONDS_PARA;
					if (beta1[resPointers[index2]] == -1) {
						beta1[resPointers[index2]] = resPointers[i];
					} else if (beta1[resPointers[index2]] != resPointers[i]) {
						beta2[resPointers[index2]] = resPointers[i];
					}
				}
			}
		}
		//
		// Case 2
		//
		for (int i = 0; i < aaCount; i++) {
			if ((hnHBonds[i] > -1) & (coHBonds[i] > -1)) {
				if ((resPointers[coHBonds[i]] - resPointers[hnHBonds[i]]) == 2) {
					index2 = hnHBonds[i] + 1;
					pattern[i] |= HBONDS_PARA;
					if (beta1[resPointers[i]] == -1) {
						beta1[resPointers[i]] = resPointers[index2];
					} else if (beta1[resPointers[i]] != resPointers[index2]) {
						beta2[resPointers[i]] = resPointers[index2];
					}
					pattern[index2] |= HBONDS_PARA;
					if (beta1[resPointers[index2]] == -1) {
						beta1[resPointers[index2]] = resPointers[i];
					} else if (beta1[resPointers[index2]] != resPointers[i]) {
						beta2[resPointers[index2]] = resPointers[i];
					}
				}
			}
		}

		// 
		// Swap beta1/beta2 to insure that any given one points toward the same
		// side.
		//
		boolean swap;
		int dist;
		for (int i = 1; i < aaCount; i++) {
			swap = false;
			if (beta1[resPointers[i]] > -1) {
				index2 = i - 1;
				if (beta1[resPointers[index2]] > -1) {
					dist = Math.abs(beta1[resPointers[index2]]
							- beta1[resPointers[i]]);
					swap = (dist > 2);
				} else if (beta2[resPointers[index2]] > -1) {
					dist = Math.abs(beta2[resPointers[index2]]
							- beta1[resPointers[i]]);
					swap = (dist <= 2);
				} else if (i > 1) {
					index2 = i - 2;
					if (beta1[resPointers[index2]] > -1) {
						dist = Math.abs(beta1[resPointers[index2]]
								- beta1[resPointers[i]]);
						swap = (dist > 2);
					} else if (beta2[resPointers[index2]] > -1) {
						dist = Math.abs(beta2[resPointers[index2]]
								- beta1[resPointers[i]]);
						swap = (dist <= 2);
					}
				}
			}

			if (swap) {
				index2 = beta1[resPointers[i]];
				beta1[resPointers[i]] = beta2[resPointers[i]];
				beta2[resPointers[i]] = index2;
			}
		}

		/*
		 * System.out.println( " Patterns for " + aaCount + " aa" ); for( int i =
		 * 0; i < aaCount; i++ ) { if( ( pattern[i] & HBONDS_4TURN ) > 0 ) {
		 * System.out.println( " " + i + " h " ); } else { System.out.println( " " +
		 * i ); } }
		 */

		// ///////////////////////////////////////////////////////////////////////////////////////
		// Start assigning extended SS symbols based on the patterns set above
		// ---------------------------------------------------------------------------------------
		// ///////////////////////////////////////////////////////////////////////////////////////
		//
		// 4-helices
		//
		char ss = 'h';
		for (int i = 0; i < aaCount - 1; i++) {
			index2 = i + 1;
			if (((pattern[i] & HBONDS_4TURN) > 0)
					&& ((pattern[index2] & HBONDS_4TURN) > 0)) {
				this.ssFlags[resPointers[index2]] = ss;
				ss = Character.toUpperCase(ss);
				this.ssFlags[resPointers[index2 + 1]] = ss;
				if (index2 < aaCount - 2) {
					this.ssFlags[resPointers[index2 + 2]] = ss;
				}
				if (index2 < aaCount - 3) {
					this.ssFlags[resPointers[index2 + 3]] = ss;
				}
			} else {
				ss = 'h';
			}
		}

		//
		// strands, beta1 pass
		//
		int dist1;
		for (int i = 0; i < aaCount; i++) {
			if (beta1[resPointers[i]] > -1) {
				ss = 'e';
				for (int j = i + 1; j < aaCount; j++) {
					if (beta1[resPointers[j]] > -1) {
						dist = 2;
					} else {
						j++;
						if (j >= aaCount) {
							break;
						}
						if (beta1[resPointers[j]] > -1) {
							dist = 3;
						} else {
							j++;
							if (j >= aaCount) {
								break;
							}
							if (beta1[resPointers[j]] <= -1) {
								break;
							}
							dist = 2;
						}
					}
					dist1 = Math.abs(beta1[resPointers[i]]
							- beta1[resPointers[j]]);
					if (dist1 <= dist) {
						for (int k = i; k <= j; k++) {
							switch (this.ssFlags[resPointers[k]]) {
							case ' ':
							case 'e':
								this.ssFlags[resPointers[k]] = ss;
							case 'E':
								ss = 'E';
								break;
							default:
								break;
							}
						}
					}
					i = j;
				}
			}
		}
		//
		// strands, beta2 pass
		//
		for (int i = 0; i < aaCount; i++) {
			if (beta2[resPointers[i]] > -1) {
				ss = 'e';
				for (int j = i + 1; j < aaCount; j++) {
					if (beta2[resPointers[j]] > -1) {
						dist = 2;
					} else {
						j++;
						if (j >= aaCount) {
							break;
						}
						if (beta2[resPointers[j]] > -1) {
							dist = 3;
						} else {
							j++;
							if (j >= aaCount) {
								break;
							}
							if (beta2[resPointers[j]] <= -1) {
								break;
							}
							dist = 2;
						}
					}
					dist1 = Math.abs(beta2[resPointers[i]]
							- beta2[resPointers[j]]);
					if (dist1 <= dist) {
						for (int k = i; k <= j; k++) {
							switch (this.ssFlags[resPointers[k]]) {
							case ' ':
							case 'e':
								this.ssFlags[resPointers[k]] = ss;
							case 'E':
								ss = 'E';
								break;
							default:
								break;
							}
						}
					}
					i = j;
				}
			}
		}
		//
		// 5-helices
		//
		ss = 'i';
		for (int i = 0; i < aaCount; i++) {
			if (((pattern[i] & HBONDS_5TURN) > 0)
					&& ((pattern[i + 1] & HBONDS_5TURN) > 0)) {
				for (int j = 1; j <= 5; j++) {
					index2 = i + j;
					if (this.ssFlags[resPointers[index2]] == ' ') {
						this.ssFlags[resPointers[index2]] = ss;
						ss = 'I';
					}
				}
			} else {
				ss = 'i';
			}
		}
		//
		// 3-helices
		//
		ss = 'g';
		for (int i = 0; i < aaCount; i++) {
			if (((pattern[i] & HBONDS_3TURN) > 0)
					&& ((pattern[i + 1] & HBONDS_3TURN) > 0)) {
				for (int j = 1; j <= 3; j++) {
					index2 = i + j;
					if (this.ssFlags[resPointers[index2]] == ' ') {
						this.ssFlags[resPointers[index2]] = ss;
						ss = 'G';
					}
				}
			} else {
				ss = 'g';
			}
		}
		//
		// Convert singlet 'G' or 'I' into 't'
		//
		char ss1;
		for (int i = 0; i < aaCount; i++) {
			switch (this.ssFlags[resPointers[i]]) {
			case 'g':
			case 'G':
			case 'i':
			case 'I':
				ss = java.lang.Character.toUpperCase(this.ssFlags[resPointers[i]]);
				ss1 = java.lang.Character
						.toUpperCase(this.ssFlags[resPointers[i - 1]]);
				if (i > 0) {
					swap = (ss != ss1);
				} else {
					swap = true;
				}
				ss1 = java.lang.Character
						.toUpperCase(this.ssFlags[resPointers[i + 1]]);
				if (i < aaCount - 1) {
					swap = swap && (ss != ss1);
				}
				if (swap) {
					this.ssFlags[resPointers[i]] = 't';
				}
				break;
			default:
				break;
			}
		}
		//
		// Single 5-turns
		//
		for (int i = 0; i < aaCount; i++) {
			if ((pattern[i] & HBONDS_5TURN) > 0) {
				if (i > 0) {
					swap = !((pattern[i - 1] & HBONDS_5TURN) > 0);
				} else {
					swap = true;
				}
				swap = swap && (!((pattern[i + 1] & HBONDS_5TURN) > 0));
				if (swap) {
					ss = 't';
					for (int j = 1; j <= 5; j++) {
						index2 = i + j;
						if (this.ssFlags[resPointers[index2]] == ' ') {
							this.ssFlags[resPointers[index2]] = ss;
							ss = Character.toUpperCase(ss);
						}
					}
				}
			}
		}
		//
		// Single 4-turns
		//
		for (int i = 0; i < aaCount; i++) {
			if ((pattern[i] & HBONDS_4TURN) > 0) {
				if (i > 0) {
					swap = !((pattern[i - 1] & HBONDS_4TURN) > 0);
				} else {
					swap = true;
				}
				swap = swap && (!((pattern[i + 1] & HBONDS_4TURN) > 0));
				if (swap) {
					ss = 't';
					for (int j = 1; j <= 4; j++) {
						index2 = i + j;
						if (this.ssFlags[resPointers[index2]] == ' ') {
							this.ssFlags[resPointers[index2]] = ss;
							ss = Character.toUpperCase(ss);
						}
					}
				}
			}
		}
		//
		// Single 3-turns
		//
		for (int i = 0; i < aaCount; i++) {
			if ((pattern[i] & HBONDS_3TURN) > 0) {
				if (i > 0) {
					swap = !((pattern[i - 1] & HBONDS_3TURN) > 0);
				} else {
					swap = true;
				}
				swap = swap && (!((pattern[i + 1] & HBONDS_3TURN) > 0));
				if (swap) {
					ss = 't';
					for (int j = 1; j <= 3; j++) {
						index2 = i + j;
						if (this.ssFlags[resPointers[index2]] == ' ') {
							this.ssFlags[resPointers[index2]] = ss;
							ss = Character.toUpperCase(ss);
						}
					}
				}
			}
		}

		
//		 System.out.println( " SS Flags for residues: " + resCount ); for( int
//		 i = 0; i < resCount; i++ ) { System.out.println( " " + i + " " +
//		 ssFlags[i] ); }
		

		bondList.trimToSize();

} catch(final Exception e) { e.printStackTrace();}
		return bondList.toArray();
	}

	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	// Get Methods //
	// ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
	//

	/**
	 * Return the char array of the residue flags.
	 */
//	private char[] getSsFlags() {
//		return this.ssFlags;
//	}

	/**
	 * Return the number of aa chains found in this structure.
	 */
//	private int getNumberOfChains() {
//		return this.chains.length;
//	}

	/**
	 * Return the index number associated with a given fragment (represented as
	 * pair of two integers, the start and the end of the fragment).
	 */
//	private int getChain(final int[] fragment) {
//		for (int i = 0; i < this.chains.length; i++) {
//			if (fragment[0] < this.chains[i][1]) {
//				return i;
//			}
//		}
//		return 100000;
//	}

	/**
	 * Generate the fragments and their associated conformation types.
	 * <P>
	 * Here, the fragments generated share in general connecting residues.
	 */
//	public boolean getFragments(final Vector<int[]> fragments, final Vector<ComponentType> conformationType) {
//
//		if (this.chains.length == 0) {
//			// System.err.println( " No chains in this structure " );
//			return false;
//		}
//
//		// ssFlags is the array of characters denoting the extended secondary
//		// structure flags
//		//
//		// char[] ssFlags = new char[resCount];
//		// setSsExtendedFlags( ssFlags );
//		if (this.ssFlags == null) {
//			System.out
//					.println("getFragments: null ssFlags. One should first run setSsExtendedFlags on this class");
//		}
//
//		this.reinterpretSsFlags();
//
//		// We are done, output the list of secondary structures
//		//
//		/*
//		 * for( int i = 0; i < ssFlags.length; i++ ) { System.out.println( " " +
//		 * i + " " + ssFlags[i] ); }
//		 */
//		int[] fragment;
//		ComponentType confType = ComponentType.UNDEFINED_CONFORMATION;
//		int start = 0;
//		char ss = this.ssFlags[0];
//		if (ss == '-') {
//			start = -1;
//		}
//		char ss1;
//		int ssIndex = 0;
//		boolean newChain = false;
//		int chnIndex = 0;
//		int chn = this.chains[chnIndex][0];
//		for (int i = 0; i < this.ssFlags.length; i++) {
//			newChain = (i == chn);
//			// System.out.println( " " + newChain + " " + ssFlags[i] + " " + ss
//			// );
//			if (newChain) {
//				if (chnIndex < this.chains.length - 1) {
//					chn = this.chains[++chnIndex][0];
//				}
//				ss = this.ssFlags[i];
//				start = i;
//				continue;
//			}
//
//			if ((this.ssFlags[i] != ss) || (i == this.ssFlags.length - 1) // Should we
//																// check for
//																// this? It
//																// appears as
//																// needed
//			) {
//				fragment = new int[2];
//				switch (ss) {
//				case '-':
//					start = i;
//					break;
//
//				case ' ':
//					confType = ComponentType.COIL;
//					fragment[0] = start;
//					ss1 = Character.toUpperCase(this.ssFlags[i]);
//					switch (ss1) {
//					case '-':
//						fragment[1] = i - 1;
//						start = -1;
//						break;
//					case 'T':
//						fragment[1] = i - 1;
//						start = i - 1;
//						break;
//					case 'H':
//					case 'E':
//						fragment[1] = i;
//						start = i;
//						break;
//					}
//					fragments.add(fragment);
//					conformationType.add(confType);
//					ssIndex++;
//					break;
//
//				case 'T':
//					confType = ComponentType.TURN;
//					fragment[0] = start;
//					ss1 = Character.toUpperCase(this.ssFlags[i]);
//					switch (ss1) {
//					case '-':
//						fragment[1] = i - 1;
//						start = -1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					case ' ':
//					case 'H':
//					case 'E':
//						fragment[1] = i;
//						start = i;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					}
//					ssIndex++;
//					break;
//
//				case 'H':
//					confType = ComponentType.HELIX;
//					fragment[0] = start;
//					ss1 = Character.toUpperCase(this.ssFlags[i]);
//					switch (ss1) {
//					case '-':
//						fragment[1] = i - 1;
//						start = -1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					case ' ':
//					case 'T':
//						fragment[1] = i - 1;
//						start = i - 1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					case 'H':
//					case 'E':
//						fragment[1] = i - 1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						ssIndex++;
//
//						fragment = new int[2];
//						fragment[0] = i - 1;
//						fragment[1] = i;
//						confType = ComponentType.TURN;
//						start = i;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					}
//					ssIndex++;
//					break;
//
//				case 'E':
//					confType = ComponentType.STRAND;
//					fragment[0] = start;
//					ss1 = Character.toUpperCase(this.ssFlags[i]);
//					switch (ss1) {
//					case '-':
//						fragment[1] = i - 1;
//						start = -1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					case ' ':
//					case 'T':
//						fragment[1] = i - 1;
//						start = i - 1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					case 'H':
//					case 'E':
//						fragment[1] = i - 1;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						ssIndex++;
//
//						fragment = new int[2];
//						fragment[0] = i - 1;
//						fragment[1] = i;
//						confType = ComponentType.TURN;
//						start = i;
//						fragments.add(fragment);
//						conformationType.add(confType);
//						break;
//					}
//					ssIndex++;
//					break;
//				}
//				ss = Character.toUpperCase(this.ssFlags[i]);
//			}
//		}
//
//		if (fragments.size() == 0) {
//			return false;
//		}
//
//		this.openDistanceGaps(fragments, conformationType);
//
//		/*
//		 * System.out.println( "\nFragments after removing distance gaps" ); fIt =
//		 * fragments.listIterator(); cIt = conformationType.listIterator();
//		 * while( fIt.hasNext() ) { frag = (int[])fIt.next(); cType = (String)
//		 * cIt.next(); System.out.println( cType + " from " + frag[0] + " to " +
//		 * frag[1] ); }
//		 */
//
//		return true;
//	}

	/**
	 * Generate the fragments and their associated main conformation types.
	 * Here, the fragments generated DO NOT share connecting residues.
	 */
	private boolean getDisjointFragments(final Vector<int[]> fragments,
			final Vector<ComponentType> conformationType) {

		if (this.chains.length == 0) {
			return false;
		}

		// ssFlags is the array of characters denoting the extended secondary
		// structure flags
		//
		// char[] ssFlags = new char[resCount];
		// setSsExtendedFlags( ssFlags );
		if (this.ssFlags == null) {
			System.out
					.println("getFragments: null ssFlags. One should first run setSsExtendedFlags on this class");
		}

		this.reinterpretSsFlags();
		
//		 System.err.println( "Chains" ); for( int i = 0; i < chains.length;
//		 i++ ) { System.err.println( chains[i][0] + " " + chains[i][1] ); }
		
		// We are done, output the list of secondary structures
		//
		
//		 System.out.println( "Ss Flags after reinterpretation " ); for( int i =
//		 0; i < ssFlags.length; i++ ) { System.out.println( " " + i + " " +
//		 ssFlags[i] ); }
		 

		int[] fragment;
		ComponentType confType = ComponentType.UNDEFINED_CONFORMATION;
		int start = 0;
		char ss = this.ssFlags[0];
		if (ss == '-') {
			start = -1;
		}
		char ss1;
		int ssIndex = 0;
		boolean newChain = false;
		boolean endChain = false;
		int chnIndex = 0;
		int chn = this.chains[chnIndex][0];
		int endOfChainIndex = 0;
		int lastIndex = 0;
		for (int i = 0; i < this.ssFlags.length; i++) {
			endChain = (i == endOfChainIndex);
//if(fragments.size() == 32) {
//	System.out.flush();
//}
			newChain = (i == chn);
			if (newChain) {
				endOfChainIndex = this.chains[chnIndex][1];
				if (chnIndex < this.chains.length - 1) {
					chn = this.chains[++chnIndex][0];
				}
				ss = this.ssFlags[i];
				start = i;
				continue;
			}

			// if( i > endOfChainIndex ) continue;

			if ((this.ssFlags[i] != ss) || (endChain)) {
				fragment = new int[2];
				switch (ss) {
				case '-':
					start = i;
					break;

				case ' ':
					confType = ComponentType.COIL;
					fragment[0] = start;
					ss1 = Character.toUpperCase(this.ssFlags[i]);
					switch (ss1) {
					case '-':
						fragment[1] = i - 1;
						if (fragment[1] > start) {
							fragments.add(fragment);
							conformationType.add(confType);
							ssIndex++;
						}
						start = -1;
						break;
					case 'T':
						fragment[1] = i - 1;
						fragments.add(fragment);
						conformationType.add(confType);
						ssIndex++;
						start = i;
						break;
					case 'H':
					case 'E':
						fragment[1] = i - 1;
						fragments.add(fragment);
						conformationType.add(confType);
						ssIndex++;
						start = i;
						break;
					case ' ':
						if (endChain) {
							fragment[1] = i;
							fragments.add(fragment);
							conformationType.add(confType);
							ssIndex++;
							start = i + 1;
						}
						break;
					}
					break;

				case 'T':
					confType = ComponentType.TURN;
					fragment[0] = start;
					ss1 = Character.toUpperCase(this.ssFlags[i]);
					switch (ss1) {
					case '-':
						if (lastIndex < (i - 1)) {
							fragment[1] = i - 1;
							fragments.add(fragment);
							conformationType.add(confType);
						}
						start = -1;
						break;
					case 'T':
						if (endChain) {
							fragment[1] = i;
							start = i;
							fragments.add(fragment);
							conformationType.add(confType);
						}
						break;
					case ' ':
					case 'H':
					case 'E':
						fragment[1] = i - 1;
						start = i;
						fragments.add(fragment);
						conformationType.add(confType);
						break;
					}
					ssIndex++;
					break;

				case 'H':
					confType = ComponentType.HELIX;
					fragment[0] = start;
					ss1 = Character.toUpperCase(this.ssFlags[i]);
					switch (ss1) {
					case '-':
						if (lastIndex < (i - 1)) {
							fragment[1] = i - 1;
							fragments.add(fragment);
							conformationType.add(confType);
						}
						start = -1;
						break;
					case 'H':
						if (endChain) {
							fragment[1] = i;
							start = i;
							fragments.add(fragment);
							conformationType.add(confType);
						}
						break;
					case ' ':
					case 'T':
						fragment[1] = i - 1;
						start = i;
						fragments.add(fragment);
						conformationType.add(confType);
						break;
					// case 'H':
					case 'E':
						fragment[1] = i - 1;
						fragments.add(fragment);
						conformationType.add(confType);
						ssIndex++;

						start = i;
						break;
					}
					ssIndex++;
					break;

				case 'E':
					confType = ComponentType.STRAND;
					fragment[0] = start;
					ss1 = Character.toUpperCase(this.ssFlags[i]);
					switch (ss1) {
					case '-':
						if (lastIndex < (i - 1)) {
							fragment[1] = i - 1;
							fragments.add(fragment);
							conformationType.add(confType);
						}
						start = -1;
						break;
					case ' ':
					case 'T':
						fragment[1] = i - 1;
						start = i;
						fragments.add(fragment);
						conformationType.add(confType);
						break;
					case 'H':
					case 'E':
						fragment[1] = i - 1;
						fragments.add(fragment);
						conformationType.add(confType);
						ssIndex++;

						start = i;
						break;
					}
					ssIndex++;
					break;
				}
				lastIndex = fragment[1];
				ss = Character.toUpperCase(this.ssFlags[i]);
			}
		}

		// It may happen that the last residue in the chain has a new
		// conformation type. This is tipically a one residue coil or a turn.
		// We should include it.
		if ((start == this.ssFlags.length - 1)
				&& (this.ssFlags[this.ssFlags.length - 1] == ' ')) {
			fragment = new int[2];
			fragment[0] = start;
			fragment[1] = this.ssFlags.length - 1;
			fragments.add(fragment);
			conformationType.add(ComponentType.COIL);
		}

		// It may happen that the last residue in the chain has a new
		// conformation type. This is tipically a one residue coil or a turn.
		// We should include it.
		if ((start == this.ssFlags.length - 1)
				&& (Character.toUpperCase(this.ssFlags[this.ssFlags.length - 1]) == 'T')) {
			fragment = new int[2];
			fragment[0] = start;
			fragment[1] = this.ssFlags.length - 1;
			fragments.add(fragment);
			conformationType.add(ComponentType.COIL); // Too
																		// short
																		// for a
																		// turn,
																		// make
																		// it a
																		// coil
		}

		if (fragments.size() == 0) {
			return false;
		}

		this.openDistanceGaps(fragments, conformationType);

		/*
		 * System.out.println( "\nFragments after removing distance gaps" ); fIt =
		 * fragments.listIterator(); cIt = conformationType.listIterator();
		 * while( fIt.hasNext() ) { frag = (int[])fIt.next(); cType = (String)
		 * cIt.next(); System.out.println( cType + " from " + frag[0] + " to " +
		 * frag[1] ); }
		 */

		return true;
	}

	/**
	 * Produces a reinterpretation of the Ss Flags for the purpose of a coarser
	 * classification.
	 */
	private void reinterpretSsFlags() {
		// Reinterpretation.
		// This follows very closely the code in Molscript
		//
		for (int i = 0; i < this.ssFlags.length; i++) {
			switch (this.ssFlags[i]) {
			case 'i':
			case 'I':
			case 'b':
			case 'B':
				this.ssFlags[i] = ' ';
				break;
			case 'g':
				this.ssFlags[i] = 'h';
				break;
			case 'G':
				this.ssFlags[i] = 'H';
				break;
			default:
				break;
			}
		}

		// Convert a single residue coil following or before a turn into a turn
		//
		if ((this.ssFlags[0] == ' ') && (Character.toUpperCase(this.ssFlags[1]) == 'T')) {
			this.ssFlags[0] = 'T';
		}

		for (int i = 1; i < this.ssFlags.length - 2; i++) {
			if (((this.ssFlags[i] == ' ')
					&& (Character.toUpperCase(this.ssFlags[i - 1]) == 'T') && (this.ssFlags[i + 1] != ' '))
					|| ((this.ssFlags[i] == ' ') && (Character
							.toUpperCase(this.ssFlags[i + 1]) == 'T'))) {
				this.ssFlags[i] = 'T';
			}
		}

		if ((this.ssFlags[this.ssFlags.length - 1] == ' ')
				&& (Character.toUpperCase(this.ssFlags[this.ssFlags.length - 2]) == 'T')) {
			this.ssFlags[this.ssFlags.length - 1] = 'T';
		}

		// Convert single turn starts between turns
		//
		for (int i = 1; i < this.ssFlags.length; i++) {
			if ((this.ssFlags[i] == 't') && (this.ssFlags[i - 1] == 'T')) {
				this.ssFlags[i] = 'T';
			}
		}

		// Remove helices and strands shorter than 3
		//
		int start = 0;
		int count = 1;
		char ss = Character.toUpperCase(this.ssFlags[0]);
		for (int i = 0; i < this.ssFlags.length; i++) {
			if (this.ssFlags[i] == ss) {
				count++;
			} else {
				if (((ss == 'H') || (ss == 'E')) && (count < 3)) {
					for (int j = start; j < i; j++) {
						this.ssFlags[j] = ' ';
					}
				}
				start = i;
				ss = Character.toUpperCase(this.ssFlags[i]);
				count = 1;
			}
		}

		// Convert single aa between ligands to a ligand
		//
		for (int i = 1; i < this.ssFlags.length - 1; i++) {
			if (this.ssFlags[i] != '-') {
				if ((this.ssFlags[i - 1] == '-') && (this.ssFlags[i + 1] == '-')) {
					this.ssFlags[i] = '-';
				}
			}
		}
	}

	// Split a coil into two disjunct parts when big distance gaps exist between
	// consecutive CA atoms.
	// Sometimes data is missing and this result in a big distance between
	// (nonconsecutive now) CA atoms.
	// Example 1b25
	// Typically this is a coil but this doesn't matter
	//
	private void openDistanceGaps(final Vector<int[]> fragments, final Vector<ComponentType> conformationType) {
		final float distCutOff = 5.1f;
		int[] newFrag;
		int[] frag = null;
		int[] adjacentFrag;
		int startIndex;
		int newStartIndex;
		Atom caAtom = new Atom();
		int resCount;
		int fragIndex = 0;
		ComponentType conf = null;
		final double[] coords1 = new double[3];
		final double[] coords2 = new double[3];
		Residue residue = null;
		boolean gap = false;
		fragments.trimToSize();
		conformationType.trimToSize();

		frag = fragments.get(fragIndex);
		while (frag != null) {
			gap = false;
			startIndex = frag[0];
			residue = this.structureMap.getResidue(startIndex);
			caAtom = residue.getAlphaAtom();
			if(caAtom == null) {	// if no ca atom, use a random atom...
				caAtom = residue.getAtom(0);
			}
			coords1[0] = caAtom.coordinate[0];
			coords1[1] = caAtom.coordinate[1];
			coords1[2] = caAtom.coordinate[2];
			resCount = frag[1] - frag[0] + 1;
			newStartIndex = fragIndex + 1;
			for (int i = 1; i < resCount; i++) {
				residue = this.structureMap.getResidue(i + startIndex);
				caAtom = residue.getAlphaAtom();
				if(caAtom == null) {	// if no ca atom, use a random atom...
					caAtom = residue.getAtom(0);
				}
				coords2[0] = caAtom.coordinate[0];
				coords2[1] = caAtom.coordinate[1];
				coords2[2] = caAtom.coordinate[2];
				if (this.dist(coords1, coords2) <= distCutOff) {
					coords1[0] = coords2[0];
					coords1[1] = coords2[1];
					coords1[2] = coords2[2];
					continue;
				} else {
					// An unusually big distance to the previous residue, there
					// is probably a gap in the data
					// Remove it from the fragment and split this fragment into
					// 2 separate fragments if enough long.
					//
					gap = true;
					conf = conformationType.elementAt(fragIndex);
					frag[1] = startIndex + i - 1;

					if (((frag[1] - frag[0]) <= 1) && (!gap)
					// && (fragIndex != 0)
					) { // Too short, extend the previous if connected and
						// discard

						adjacentFrag = fragments.get(fragIndex - 1);
						if (adjacentFrag[1] >= frag[0] - 1) {
							adjacentFrag[1] = frag[1];
							fragments.remove(fragIndex);
							conformationType.remove(fragIndex);
							newStartIndex = fragIndex;
						} else {
							// fragments.remove( fragIndex );
							// conformationType.remove( fragIndex );
							// newStartIndex = fragIndex;
							conformationType.set(fragIndex, ComponentType.NONE);
						}
					}

					// Insert a "NONE" conformation type between the two
					// segments
					//
					newFrag = new int[2];
					newFrag[0] = frag[1];
					newFrag[1] = startIndex + i;
					// Do not actually insert it, since it conflicts with the
					// RangeMap mechanism
					// fragments.insertElementAt( newFrag, newStartIndex );
					// conformationType.insertElementAt( "NONE", newStartIndex
					// );
					// newStartIndex++;

					if (i < resCount - 1) { // Enough long for a new fragment
						newFrag = new int[2];
						newFrag[0] = startIndex + i;
						newFrag[1] = resCount + startIndex - 1;
						fragments.add(newStartIndex, newFrag);
						conformationType.insertElementAt(conf, newStartIndex);
					} else {
						if (fragIndex + 1 < fragments.size()) {
							adjacentFrag = fragments.get(fragIndex + 1);
							adjacentFrag[0] = startIndex + i;
						}
					}
					break;
				}
			}
			if (fragIndex < fragments.size() - 1) {
				fragIndex++;
				frag = fragments.get(fragIndex);
			} else {
				frag = null;
			}
		}

		// We need to remove helices and strands shorter than 2 that may have
		// resulted from opening distance-based gaps
		//
		fragIndex = 0;
		frag = fragments.get(fragIndex);
		while (frag != null) {
			resCount = frag[1] - frag[0] + 1;
			if (resCount < 3) {
				if ((conformationType.elementAt(fragIndex) == ComponentType.HELIX)
						|| (conformationType.elementAt(fragIndex) == ComponentType.STRAND)) {
					conformationType.set(fragIndex,
							ComponentType.COIL);
				}
			}

			if (fragIndex < fragments.size() - 1) {
				fragIndex++;
				frag = fragments.get(fragIndex);
			} else {
				frag = null;
			}
		}

		return;
	}

	/**
	 * Given a RangeMap object, it sets its Value according to the set of
	 * Secondary Structures as derived via the Kabsch-Sander algorithm.
	 */
	public void setConformationType(final Vector<Residue> residues) {
		final Vector<int[]> fragmentRanges = new Vector<int[]>();
		final Vector<ComponentType> confTypes = new Vector<ComponentType>();
		this.setSsExtendedFlags();
		this.getDisjointFragments(fragmentRanges, confTypes);
		// getFragments( fragments, confTypes );
		fragmentRanges.trimToSize();
		confTypes.trimToSize();
		// System.err.println( "Generating Range Map: " + fragments.size() );
		int[] frag;
		int globalIndex;
		ComponentType cType;
		String chainId;
		Residue residue = null;
		Chain chain;
		for (int i = 0; i < fragmentRanges.size(); i++) {
			frag = fragmentRanges.elementAt(i);
			cType = confTypes.elementAt(i);
			residue = residues.elementAt(frag[0]);
			chainId = residue.getChainId();
//			 System.err.println( "Fragment from " + frag[0] + " to " + frag[1]
//			 + " Type " + cType + " chainId " + chainId );
			chain = this.structureMap.getChain(chainId);
			globalIndex = chain.getResidueIndex(residue);
			chain.setFragmentRange(globalIndex, globalIndex + frag[1] - frag[0],
					cType);

		}
		
		setNucleicAcids(residues);
	}
	
	private void setNucleicAcids(Vector<Residue> residues) {
		int start = -1;
		int end = -1;
		String startChainId = "";
		boolean isNucleicAcid = false;

		for (Residue r: residues) {
			if (! r.getChainId().equals(startChainId) || residues.indexOf(r) == residues.size()-1) {
				if (start != -1 && end != -1) {
					Chain chain = this.structureMap.getChain(startChainId);
					chain.setFragmentRange(start, end, ComponentType.STRAND);
					start = -1;
					end = -1;
					isNucleicAcid = false;
				}
			}
			if (r.getClassification() == Classification.NUCLEIC_ACID) {
				String chainId = r.getChainId();
				Chain chain = this.structureMap.getChain(chainId);
			    int index = chain.getResidueIndex(r);
				if (isNucleicAcid == false) {
					isNucleicAcid = true;
					startChainId = chainId;
				    start = index;
				}
				end = index;
			} 
			
		}
	}

	private final void add(final double[] sum, final double[] v1, final double[] v2) {
		sum[0] = v1[0] + v2[0];
		sum[1] = v1[1] + v2[1];
		sum[2] = v1[2] + v2[2];
	}

	private final void diff(final double[] difference, final double[] v1, final double[] v2) {
		// Assume the two floats have the same length = 3
		//
		difference[0] = v1[0] - v2[0];
		difference[1] = v1[1] - v2[1];
		difference[2] = v1[2] - v2[2];
	}

//	private final float length(final float[] v) {
//		float len = 0.0f;
//		len += v[0] * v[0];
//		len += v[1] * v[1];
//		len += v[2] * v[2];
//		len = (float) Math.sqrt(len);
//
//		return len;
//	}

	private final double length(final double[] v) {
		double len = 0.0f;
		len += v[0] * v[0];
		len += v[1] * v[1];
		len += v[2] * v[2];
		len = Math.sqrt(len);

		return len;
	}

//	private final void scale(final float[] scaled, final float[] v, final float s) {
//		scaled[0] = v[0] * s;
//		scaled[1] = v[1] * s;
//		scaled[2] = v[2] * s;
//	}

	private final void scale(final double[] scaled, final double[] v, final double s) {
		scaled[0] = v[0] * s;
		scaled[1] = v[1] * s;
		scaled[2] = v[2] * s;
	}

	private final double dist(final double[] v1, final double[] v2) {
		// Assume the two floats have the same length = 3
		//
		double dist = 0.0f;
		double tmp;
		tmp = v1[0] - v2[0];
		dist += tmp * tmp;
		tmp = v1[1] - v2[1];
		dist += tmp * tmp;
		tmp = v1[2] - v2[2];
		dist += tmp * tmp;
		dist = (float) Math.sqrt(dist);

		return dist;
	}

	private final void normalize(final double[] normed, final double[] v) {
		final double len = this.length(v);
		this.scale(normed, v, 1 / len);
	}

//	private void clearStructure() {
//		this.structureMap = null;
//	}
}
