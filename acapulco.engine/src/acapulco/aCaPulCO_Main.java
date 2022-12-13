package acapulco;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.EPackage.Registry;
import org.eclipse.emf.ecore.EcorePackage;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.ecore.xmi.impl.XMIResourceImpl;

import acapulco.algorithm.instrumentation.ToolInstrumenter;
import acapulco.algorithm.termination.StoppingCondition;
import acapulco.engine.HenshinFileReader;
import acapulco.engine.variability.ConfigurationSearchOperator;
import acapulco.tool.executor.AbstractExecutor;
import jmetal.core.Algorithm;
import jmetal.core.Problem;
import jmetal.core.SolutionSet;
import jmetal.core.Variable;
import jmetal.encodings.variable.Binary;

public class aCaPulCO_Main extends AbstractExecutor {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) throws Exception {
		String fm = "testdata\\linux\\linux.dimacs";
		StoppingCondition sc = StoppingCondition.EVOLUTIONS;
		Integer sv = 50;
		boolean debug = true;
		boolean includeFixOperator = false;

		aCaPulCO_Main main = new aCaPulCO_Main();

		if (args.length > 0) {
			fm = args[0] + ".dimacs";
			sc = args[1].equals("EVOLUTIONS") ? StoppingCondition.EVOLUTIONS : StoppingCondition.TIME;
			sv = Integer.parseInt(args[2]);
			debug = Boolean.parseBoolean(args[3]);
		}
		if (args.length > 4) {
			includeFixOperator = Boolean.parseBoolean(args[4]);
		}

		for (int i = 0; i < 60; i++) {
			System.out.println("Run " + i);
			main.run(fm, sc, sv, debug, includeFixOperator);
		}

//		int exitCode = new CommandLine(new aCaPulCO_Main()).execute(args);
//		System.exit(exitCode);
	}

	@Override
	public void run() {
		String fm = featureModel;
		StoppingCondition sc = stoppingCondition;
		Integer sv = stoppingValue;
		Boolean debug = debugMode;
		boolean includeFixOperator = fixOperatorMode;
		run(fm, sc, sv, debug, includeFixOperator);
	}

	public void run(String fm, StoppingCondition sc, Integer sv, boolean debug, boolean includeFixOperator) {
		String rules = fm + ".cpcos";
		Map<String, Integer> featureName2index = readFeatureNameMapFromFile(fm);
		List<ConfigurationSearchOperator> operators = readOperatorsFromDirectory(rules, featureName2index);
		run(fm, sc, sv, debug, includeFixOperator, operators);
	}

	public void run(String fm, StoppingCondition sc, Integer sv, boolean debug, boolean includeFixOperator,
			List<ConfigurationSearchOperator> operators) {
		String augment = fm + ".augment";
		String dead = fm + ".dead";
		String mandatory = fm + ".mandatory";
		String seed = fm + ".richseed";
		String complex = fm + ".complex";

		aCaPulCO_Problem p = null;
		Algorithm a = null;
		SolutionSet pop = null;
		try {
			aCaPulCO_Mutation.DEBUG_MODE = debug;
			p = new aCaPulCO_Problem(fm, augment, mandatory, dead, seed, complex);
			ToolInstrumenter toolInstrumenter = new ToolInstrumenter(p.getNumberOfObjectives(),
					p.getNumberOfConstraints(), "ACAPULCO", "acapulco-results", 1);
			
			List<List<Integer>> complexConstraints;
			if (includeFixOperator) {
				complexConstraints = p.getComplexConstraints();
			} else {
				complexConstraints = new ArrayList<List<Integer>>();
			}
			
			a = new aCaPulCO_SettingsIBEA(p).configure(toolInstrumenter, sc, sv, fm,
					p.getNumFeatures(), p.getConstraints(), operators, complexConstraints);
			
			long start = System.currentTimeMillis();
			pop = a.execute();
			long timeTaken = System.currentTimeMillis() - start;
			System.out.println("Took " + timeTaken + "ms");
			if (includeFixOperator) {
				System.out.println("Amount of times fix was needed: " + aCaPulCO_Fix.NumberOfFixes);
				System.out.println("Non random fixes: " + aCaPulCO_Fix.SmartFixes);
			}
		} catch (Exception e) {
			e.printStackTrace(System.out);
		}

		System.out.println("******* END OF RUN! SOLUTIONS: ***");
			
		if (pop == null) {
			System.out.println("Population is null, algorithm run failed!");
			return;
		}
		
		for (int i = 0; i < pop.size(); i++) {
			for (int j = 0; j < 5; j++) {
				System.out.print(pop.get(i).getObjective(j) + " ");
			}
			System.out.println("");
		}
	}

	public static int numViolatedConstraints(Binary b) {

		// IVecInt v = bitSetToVecInt(b);
		int s = 0;
		for (List<Integer> constraint : aCaPulCO_Problem.constraints) {
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

	public static int numViolatedConstraints(Binary b, HashSet<Integer> blacklist) {

		// IVecInt v = bitSetToVecInt(b);
		int s = 0;
		for (List<Integer> constraint : aCaPulCO_Problem.constraints) {
			boolean sat = false;

			for (Integer i : constraint) {
				int abs = (i < 0) ? -i : i;
				boolean sign = i > 0;
				if (b.getIth(abs - 1) == sign) {
					sat = true;
				} else {
					blacklist.add(abs);
				}
			}
			if (!sat) {
				s++;
			}

		}

		return s;
	}

	public static int numViolatedConstraints(boolean[] b) {
		int s = 0;
		for (List<Integer> constraint : aCaPulCO_Problem.constraints) {

			boolean sat = false;

			for (Integer i : constraint) {
				int abs = (i < 0) ? -i : i;
				boolean sign = i > 0;
				if (b[abs - 1] == sign) {
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

	private Map<String, Integer> readFeatureNameMapFromFile(String fm) {
		HashMap<String, Integer> result = new HashMap<String, Integer>();

		BufferedReader objReader = null;
		try {
			String line;
			objReader = new BufferedReader(new FileReader(fm));
			boolean done = false;
			while (!done && (line = objReader.readLine()) != null) {
				if (line.startsWith("c")) {
					String[] lineSplit = line.split(" ");
					result.put(lineSplit[2], Integer.parseInt(lineSplit[1]));
				} else {
					done = true;
				}
			}

		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (objReader != null)
					objReader.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}
		return result;
	}

	public List<ConfigurationSearchOperator> readOperatorsFromDirectory(String rulesPath,
			Map<String, Integer> featureName2index) {
		List<ConfigurationSearchOperator> result = new ArrayList<>();
		File rulesDirectory = new File(rulesPath);
		for (File f : rulesDirectory.listFiles()) {
			if (f.getName().endsWith(".hen")) {
				ConfigurationSearchOperator operator = HenshinFileReader
						.readConfigurationSearchOperatorFromFile(f.getPath(), featureName2index);
				result.add(operator);
			}
		}
		return result;
	}

}
