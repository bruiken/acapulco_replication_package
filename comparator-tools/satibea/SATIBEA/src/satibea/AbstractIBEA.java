/*
 * Author : Christopher Henard (christopher.henard@uni.lu)
 * Date : 01/03/14
 * Copyright 2013 University of Luxembourg – Interdisciplinary Centre for Security Reliability and Trust (SnT)
 * All rights reserved
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package satibea;

import jmetal.core.*;
import jmetal.util.JMException;
import jmetal.util.Ranking;
import jmetal.util.comparators.DominanceComparator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import acapulco.algorithm.instrumentation.ToolInstrumenter;

/**
 *
 * @author chris
 */
public abstract class AbstractIBEA extends Algorithm {

    protected int print_time = 0;

    protected long stoppingValue;

    /**
     * Defines the number of tournaments for creating the mating pool
     */
    public static final int TOURNAMENTS_ROUNDS = 1;

    /**
     * Stores the value of the indicator between each pair of solutions into the
     * solution set
     */
    private List<List<Double>> indicatorValues_;

    /**
     *
     */
    private double maxIndicatorValue_;

	protected ToolInstrumenter toolInstrumenter;

    /**
     * Constructor. Create a new IBEA instance
     *
     * @param problem Problem to solve
     */
    public AbstractIBEA(Problem problem, long stoppingValue, ToolInstrumenter toolInstrumenter) {
        super(problem);
        this.stoppingValue = stoppingValue;
        this.toolInstrumenter = toolInstrumenter;
    } // Spea2

    /**
     * calculates the hypervolume of that portion of the objective space that is
     * dominated by individual a but not by individual b
     */
    double calcHypervolumeIndicator(Solution p_ind_a,
            Solution p_ind_b,
            int d,
            double maximumValues[],
            double minimumValues[]) {
        double a, b, r, max;
        double volume = 0;
        double rho = 2.0;

        r = rho * (maximumValues[d - 1] - minimumValues[d - 1]);
        max = minimumValues[d - 1] + r;

        a = p_ind_a.getObjective(d - 1);
        if (p_ind_b == null) {
            b = max;
        } else {
            b = p_ind_b.getObjective(d - 1);
        }

        if (d == 1) {
            if (a < b) {
                volume = (b - a) / r;
            } else {
                volume = 0;
            }
        } else {
            if (a < b) {
                volume = calcHypervolumeIndicator(p_ind_a, null, d - 1, maximumValues, minimumValues)
                        * (b - a) / r;
                volume += calcHypervolumeIndicator(p_ind_a, p_ind_b, d - 1, maximumValues, minimumValues)
                        * (max - b) / r;
            } else {
                volume = calcHypervolumeIndicator(p_ind_a, p_ind_b, d - 1, maximumValues, minimumValues)
                        * (max - b) / r;
            }
        }

        return (volume);
    }

    /**
     * This structure store the indicator values of each pair of elements
     */
    public void computeIndicatorValuesHD(SolutionSet solutionSet,
            double[] maximumValues,
            double[] minimumValues) {
        SolutionSet A, B;
        // Initialize the structures
        indicatorValues_ = new ArrayList<List<Double>>();
        maxIndicatorValue_ = -Double.MAX_VALUE;

        for (int j = 0; j < solutionSet.size(); j++) {
            A = new SolutionSet(1);
            A.add(solutionSet.get(j));

            List<Double> aux = new ArrayList<Double>();
            for (int i = 0; i < solutionSet.size(); i++) {
                B = new SolutionSet(1);
                B.add(solutionSet.get(i));

                int flag = (new DominanceComparator()).compare(A.get(0), B.get(0));

                double value = 0.0;
                if (flag == -1) {
                    value = -calcHypervolumeIndicator(A.get(0), B.get(0), problem_.getNumberOfObjectives(), maximumValues, minimumValues);
                } else {
                    value = calcHypervolumeIndicator(B.get(0), A.get(0), problem_.getNumberOfObjectives(), maximumValues, minimumValues);
                }
        //double value = epsilon.epsilon(matrixA,matrixB,problem_.getNumberOfObjectives());

                //Update the max value of the indicator
                if (Math.abs(value) > maxIndicatorValue_) {
                    maxIndicatorValue_ = Math.abs(value);
                }
                aux.add(value);
            }
            indicatorValues_.add(aux);
        }
    } // computeIndicatorValues

    /**
     * Calculate the fitness for the individual at position pos
     */
    public void fitness(SolutionSet solutionSet, int pos) {
        double fitness = 0.0;
        double kappa = 0.05;

        for (int i = 0; i < solutionSet.size(); i++) {
            if (i != pos) {
                fitness += Math.exp((-1 * indicatorValues_.get(i).get(pos) / maxIndicatorValue_) / kappa);
            }
        }

        solutionSet.get(pos).setFitness(fitness);

    }

    /**
     * Calculate the fitness for the entire population.
     *
     */
    public void calculateFitness(SolutionSet solutionSet) {
        // Obtains the lower and upper bounds of the population
        double[] maximumValues = new double[problem_.getNumberOfObjectives()];
        double[] minimumValues = new double[problem_.getNumberOfObjectives()];

        for (int i = 0; i < problem_.getNumberOfObjectives(); i++) {
            maximumValues[i] = -Double.MAX_VALUE; // i.e., the minus maxium value
            minimumValues[i] = Double.MAX_VALUE; // i.e., the maximum value
        }

        for (int pos = 0; pos < solutionSet.size(); pos++) {
            for (int obj = 0; obj < problem_.getNumberOfObjectives(); obj++) {
                double value = solutionSet.get(pos).getObjective(obj);
                if (value > maximumValues[obj]) {
                    maximumValues[obj] = value;
                }
                if (value < minimumValues[obj]) {
                    minimumValues[obj] = value;
                }
            }
        }

        computeIndicatorValuesHD(solutionSet, maximumValues, minimumValues);
        for (int pos = 0; pos < solutionSet.size(); pos++) {
            fitness(solutionSet, pos);
        }
    }

    /**
     * Update the fitness before removing an individual
     */
    public void removeWorst(SolutionSet solutionSet) {

        // Find the worst;
        double worst = solutionSet.get(0).getFitness();
        int worstIndex = 0;
        double kappa = 0.05;

        for (int i = 1; i < solutionSet.size(); i++) {
            if (solutionSet.get(i).getFitness() > worst) {
                worst = solutionSet.get(i).getFitness();
                worstIndex = i;
            }
        }

        //if (worstIndex == -1) {
        //    System.out.println("Yes " + worst);
        //}
        //System.out.println("Solution Size "+solutionSet.size());
        //System.out.println(worstIndex);
        // Update the population
        for (int i = 0; i < solutionSet.size(); i++) {
            if (i != worstIndex) {
                double fitness = solutionSet.get(i).getFitness();
                fitness -= Math.exp((-indicatorValues_.get(worstIndex).get(i) / maxIndicatorValue_) / kappa);
                solutionSet.get(i).setFitness(fitness);
            }
        }

        // remove worst from the indicatorValues list
        indicatorValues_.remove(worstIndex); // Remove its own list
        Iterator<List<Double>> it = indicatorValues_.iterator();
        while (it.hasNext()) {
            it.next().remove(worstIndex);
        }

        // remove the worst individual from the population
        if (solutionSet.get(worstIndex).getObjective(0) == 0) {
        	// JABIER commented
            //System.out.print("Removed-" + print_time + "-: ");
            //Variable v = solutionSet.get(worstIndex).getDecisionVariables()[0];
            //for (int j = 0; j < solutionSet.get(worstIndex).getNumberOfObjectives(); j++) {
            //    System.out.print(solutionSet.get(worstIndex).getObjective(j) + " ");
            //}
            //System.out.println("");
        }
        solutionSet.remove(worstIndex);
    } // removeWorst

    /**
     * Runs of the IBEA algorithm.
     *
     * @return a <code>SolutionSet</code> that is a set of non dominated
     * solutions as a result of the algorithm execution
     * @throws ClassNotFoundException 
     * @throws JMException
     */
    public abstract SolutionSet execute() throws JMException, ClassNotFoundException; 
} // Spea2
