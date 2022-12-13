package acapulco;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import jmetal.core.Operator;
import jmetal.core.Solution;
import jmetal.encodings.variable.Binary;
import jmetal.util.JMException;

/**
 * 
 * The Fix operator checks if complex constraints are violated, and fixes the solution to adhere to them again.
 * The fixing is done in a random fashion, if multiple features can be enabled or disabled to fix a constraint,
 * a random one is chosen.
 * 
 * @author Bob Ruiken
 *
 */
public class aCaPulCO_Fix extends Operator {
	private static final long serialVersionUID = 224678259204149902L;
	
	private final boolean useLookupTable = true;
	
	public static int NumberOfFixes = 0;
	public static int SmartFixes = 0;
	
	private Map<Integer, List<Integer>> fixableRuleIndices;
	
	private final aCaPulCO_Mutation mutation;
	private final List<List<Integer>> complexConstraints;
	private final List<Integer> trueOptionalFeatures;
	
	private final Map<Integer, Integer> feature2ActivationRule;
	private final Map<Integer, Integer> feature2DeactivationRule;
	
	private final Random random;
	
	public aCaPulCO_Fix(
			HashMap<String, Object> parameters, 
			aCaPulCO_Mutation mutation,
			Map<Integer, Integer> feature2ActivationRule,
			Map<Integer, Integer> feature2DeactivationRule,
			List<List<Integer>> complexConstraints,
			List<Integer> trueOptionalFeatures) {
		super(parameters);
		this.mutation = mutation;
		this.complexConstraints = complexConstraints;
		this.feature2ActivationRule = feature2ActivationRule;
		this.feature2DeactivationRule = feature2DeactivationRule;
		this.trueOptionalFeatures = trueOptionalFeatures;
		this.random = new Random();
		this.fixableRuleIndices = new HashMap<>();
	}

	/**
	 * The execute function of the fix operator checks if complex constraints
	 * are violated, and fixes the solution to adhere to them again.
	 */
	@Override
	public Object execute(Object object) throws JMException {
		Solution solution = (Solution) object;
		Binary config = (Binary) solution.getDecisionVariables()[0];
		
		Map<Integer, Set<Integer>> attemptedFixes = new HashMap<>();
		
		List<Integer> violatedConstraint = getViolatedConstraint(config);
		while (violatedConstraint != null) {
			int ruleIndex;
			if (useLookupTable) {
				int configDecimal = Integer.parseInt(config.toString(), 2);
				
				if (!attemptedFixes.containsKey(configDecimal)) {
					attemptedFixes.put(configDecimal, new HashSet<>());
				}
				
				if (!this.fixableRuleIndices.containsKey(configDecimal)) {
					loadFixableRuleIndices(violatedConstraint, config, configDecimal);
				}
				
				List<Integer> fixableIndices = new ArrayList<Integer>(this.fixableRuleIndices.get(configDecimal)); 
				fixableIndices.removeAll(attemptedFixes.get(configDecimal));
				
				if (fixableIndices.size() > 0) {
					ruleIndex = fixableIndices.get(0);
					SmartFixes++;
				} else {
					ruleIndex = this.fixableRuleIndices.get(configDecimal).get(random.nextInt(this.fixableRuleIndices.get(configDecimal).size()));
				}
				
				attemptedFixes.get(configDecimal).add(ruleIndex);				
			} else {
				ruleIndex = ruleToApply(violatedConstraint, config);
			}
			
			mutation.applyCpcoRuleToSolution(solution, ruleIndex);
			config = (Binary) solution.getDecisionVariables()[0];
			violatedConstraint = getViolatedConstraint(config);
			
			NumberOfFixes++;
		};
		return solution;
	}
	
	/**
	 * Find a rule index to apply, to fix the violated constraint.
	 * Note that this process is random, if multiple rules can be applied to fix the operator,
	 * a random one is chosen.
	 * @param constraint The violated constraint.
	 * @param config The current config.
	 * @return Rule index to apply, in order to fix the violated constraint.
	 */
	private int ruleToApply(List<Integer> constraint, Binary config) {
		List<Integer> switchable = getSwitchableFeatures(constraint);
				
		// we should assert that there is always at least 1 way to fix the violated constraint
		assert switchable.size() > 0;
		
		int featureIdxToSwitch = switchable.get(random.nextInt(switchable.size()));
		boolean isEnabled = config.getIth(featureIdxToSwitch);
		
		if (isEnabled) {
			return feature2DeactivationRule.get(featureIdxToSwitch + 1);
		} else {
			return feature2ActivationRule.get(featureIdxToSwitch + 1);
		}
	}
	
	private void loadFixableRuleIndices(List<Integer> constraint, Binary config, int configDec) {
		List<Integer> switchable = getSwitchableFeatures(constraint);
				
		// we should assert that there is always at least 1 way to fix the violated constraint
		assert switchable.size() > 0;
				
		List<Integer> ruleIndices = new ArrayList<>();
		this.fixableRuleIndices.put(configDec, ruleIndices);
		for (int i = 0; i < switchable.size(); i++) {
			int featureIdxToSwitch = switchable.get(i);
			boolean isEnabled = config.getIth(featureIdxToSwitch);
			
			if (isEnabled) {
				ruleIndices.add(feature2DeactivationRule.get(featureIdxToSwitch + 1));
			} else {
				ruleIndices.add(feature2ActivationRule.get(featureIdxToSwitch + 1));
			}
		}
	}
	
	/**
	 * Get the feature indices that are can be switched in order to satisfy the given constraint.
	 * @param constraint The constraint to satisfy.
	 * @return A list of feature indices that can be switched.
	 */
	private List<Integer> getSwitchableFeatures(List<Integer> constraint) {
		List<Integer> result = new ArrayList<Integer>();
		
		for (int i=0; i < constraint.size(); i++) {
			int featureIdx = Math.abs(constraint.get(i)) - 1;
			if (trueOptionalFeatures.contains(featureIdx)) {
				result.add(featureIdx);
			}
		}
		
		return result;
	}
	
	/**
	 * Get a complex constraint that is violated, if there is one.
	 * @param b The solution to check.
	 * @return null if there is no violated constraint, otherwise the violated constraint.
	 */
	private List<Integer> getViolatedConstraint(Binary b) {
		for (List<Integer> constraint : complexConstraints) {
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
				return constraint;
			}
		}
		return null;
	}
}
