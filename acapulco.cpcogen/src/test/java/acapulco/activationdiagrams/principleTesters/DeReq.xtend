package acapulco.activationdiagrams.principleTesters

import acapulco.activationdiagrams.PrincipleTester
import acapulco.model.Feature
import java.util.Set
import acapulco.rulesgeneration.activationdiagrams.ActivationDiagramNode
import acapulco.featuremodel.FeatureModelHelper
import static org.junit.Assert.assertTrue

class DeReq extends PrincipleTester {

	override checkPrincipleApplies(Feature f, Set<ActivationDiagramNode> activationDiagram,
		extension FeatureModelHelper featureModelHelper) {
		val fd = activationDiagram.findDeactivationOf(f)
		val consequences = fd.consequences.collectFeatureDecisions

		assertTrue('''All features requiring «f.name» must be deactivated.''', f.deactivableCTCFeaturesForDeactivateF.
			forall [ requiredFeature |
				consequences.exists[deactivationOf(requiredFeature)]
			])
	}

}