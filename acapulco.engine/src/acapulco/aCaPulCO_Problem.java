package acapulco;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import jmetal.core.Problem;
import jmetal.core.Solution;
import jmetal.core.Variable;
import jmetal.encodings.variable.Binary;
import jmetal.util.JMException;

public class aCaPulCO_Problem extends Problem {

    public static String fm;
    private String augment;
    public static int numFeatures;
    private int numConstraints;
    public static List<List<Integer>> constraints;
    public static List<List<Integer>> complexConstraints;
    private double[] usability;
    private double[] battery;
    private double[] footprint;
    private static int n = 0;
    private List<Integer> mandatoryFeaturesIndices, deadFeaturesIndices;
    public static List<Integer> featureIndicesAllowedFlip;
    private int[] seed;

    
    private static final int N_VARS = 1, N_OBJS = 5;

    public aCaPulCO_Problem(String fm, String augment, String mandatory, String dead, String seedfile, String complex) throws Exception {
        this.numberOfVariables_ = N_VARS;
        this.numberOfObjectives_ = N_OBJS;
        this.numberOfConstraints_ = 0;
        this.fm = fm;
        this.augment = augment;
        loadFM(fm, augment, complex);
        loadMandatoryDeadFeaturesIndices(mandatory, dead);
        loadSeed(seedfile);
        this.solutionType_ = new aCaPulCO_BinarySolution(this, numFeatures, fm,mandatoryFeaturesIndices, deadFeaturesIndices, seed, new ArrayList<>(), new ArrayList<>(), constraints);
    }

    public List<List<Integer>> getConstraints() {
        return constraints;
    }
    
    public List<List<Integer>> getComplexConstraints() {
        return complexConstraints;
    }

    @Override
    public void evaluate(Solution sltn) throws JMException {
        Variable[] vars = sltn.getDecisionVariables();
        Binary bin = (Binary) vars[0];

        int unselected = 0;
        double usability_ = 0.0;
        double battery_ = 0.0;
        double footprint_ = 0.0;

        for (int i = 0; i < bin.getNumberOfBits(); i++) {

            boolean b = bin.getIth(i);

            if (!b) {
                unselected++;
            } else {
                usability_ += usability[i];
                battery_ += battery[i];
                footprint_ += footprint[i];
            }

        }

        sltn.setObjective(0, numViolatedConstraints(bin));
        sltn.setObjective(1, unselected);
        sltn.setObjective(2, 0 - usability_); // maximize
        sltn.setObjective(3, battery_);
        sltn.setObjective(4, footprint_);
        
//        String str ="";
//        for (int i=0;i<5;i++) 
//        	str+= sltn.getObjective(i) + " ";
//        System.out.println(str);

    }

    public String getFm() {
        return fm;
    }

    public int getNumFeatures() {
        return numFeatures;
    }

    
    
    
    public int numViolatedConstraints(Binary b) {

        //IVecInt v = bitSetToVecInt(b);
        int s = 0;
        for (List<Integer> constraint : constraints) {
            boolean sat = false;

            for (Integer i : constraint) {
                int abs = (i < 0) ? -i : i;
                boolean sign = i > 0;
                if (b.getIth(abs - 1) == sign) {
                    sat = true;
                    break;
                }
            }
            if (!sat) {
                s++;
            }

       }

        return s;
    }

    public void loadFM(String fm, String augment, String complex) throws Exception {
        BufferedReader in = new BufferedReader(new FileReader(fm));
        String line;
        while ((line = in.readLine()) != null) {
            line = line.trim();

            if (line.startsWith("p")) {
                StringTokenizer st = new StringTokenizer(line, " ");
                st.nextToken();
                st.nextToken();
                numFeatures = Integer.parseInt(st.nextToken());
                numConstraints = Integer.parseInt(st.nextToken());
                constraints = new ArrayList<List<Integer>>(numConstraints);
            }

            if (!line.startsWith("c") && !line.startsWith("p") && !line.isEmpty()) {
                StringTokenizer st = new StringTokenizer(line, " ");
                List<Integer> constraint = new ArrayList<Integer>(st.countTokens() - 1);
                while (st.hasMoreTokens()) {
                    int i = Integer.parseInt(st.nextToken());
                    if (i != 0) {
                        constraint.add(i);
                    }
                }
                constraints.add(constraint);
            }
        }
        in.close();

        in = new BufferedReader(new FileReader(complex));
        complexConstraints = new ArrayList<List<Integer>>();
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("c") && !line.startsWith("p") && !line.isEmpty()) {
                StringTokenizer st = new StringTokenizer(line, " ");
                List<Integer> constraint = new ArrayList<Integer>(st.countTokens() - 1);
                while (st.hasMoreTokens()) {
                    int i = Integer.parseInt(st.nextToken());
                    if (i != 0) {
                        constraint.add(i);
                    }
                }
                if (!constraints.contains(constraint)) {
                	complexConstraints.add(constraint);
                }
            }
        }
        in.close();

        usability = new double[numFeatures];
        battery = new double[numFeatures];
        footprint = new double[numFeatures];

        in = new BufferedReader(new FileReader(augment));
        while ((line = in.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("#")) {
                StringTokenizer st = new StringTokenizer(line, " ");
                int featIndex = Integer.parseInt(st.nextToken()) - 1;
                usability[featIndex] = Double.parseDouble(st.nextToken());
                battery[featIndex] = Double.parseDouble(st.nextToken());
                footprint[featIndex] = Double.parseDouble(st.nextToken());
            }
        }
    }

    public void loadMandatoryDeadFeaturesIndices(String mandatory, String dead) throws Exception {

        mandatoryFeaturesIndices = new ArrayList<Integer>(numFeatures);
        deadFeaturesIndices = new ArrayList<Integer>(numFeatures);
        featureIndicesAllowedFlip = new ArrayList<Integer>(numFeatures);

        BufferedReader in = new BufferedReader(new FileReader(mandatory));
        String line;
        while ((line = in.readLine()) != null) {
            if (!line.isEmpty()) {
                int i = Integer.parseInt(line) - 1;
                mandatoryFeaturesIndices.add(i);
            }

        }
        in.close();
        
        in = new BufferedReader(new FileReader(dead));
        while ((line = in.readLine()) != null) {
            if (!line.isEmpty()) {
                int i = Integer.parseInt(line) - 1;
                deadFeaturesIndices.add(i);
            }
        }
        in.close();
        
         for (int i = 0; i < numFeatures; i++) {
            if (! mandatoryFeaturesIndices.contains(i) && !deadFeaturesIndices.contains(i))
                featureIndicesAllowedFlip.add(i);
            
        }

    }
    
    public void loadSeed(String seedfile) throws Exception{
        seed = new int[numFeatures];
        
        BufferedReader in = new BufferedReader(new FileReader(seedfile));
        String line;
        while ((line = in.readLine()) != null) {
            line.trim();
            StringTokenizer st = new StringTokenizer(line, " ");
            while(st.hasMoreElements()){
                int i = Integer.parseInt(st.nextToken());
                int iAbs = (i>0)?i:-i;
                seed[iAbs-1] = (i>0)?1:-1;
            }
        }
        in.close();
    }

}
