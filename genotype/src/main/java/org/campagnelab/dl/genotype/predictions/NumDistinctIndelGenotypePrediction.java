package org.campagnelab.dl.genotype.predictions;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.campagnelab.dl.framework.domains.prediction.Prediction;
import org.campagnelab.dl.genotype.learning.domains.NumDistinctAllelesOutputLayerPrediction;
import org.campagnelab.dl.genotype.learning.domains.predictions.SingleGenotypePrediction;

import java.util.Collections;
import java.util.List;

/**
 * A prediction for models that hae a single
 */
public class NumDistinctIndelGenotypePrediction extends GenotypePrediction {

    public NumDistinctIndelGenotypePrediction(List<Prediction> predictionList) {
        set(predictionList);
    }

    public void set(List<Prediction> predictionList) {
        final Prediction firstPrediction = predictionList.get(0);
        List<Prediction> genoPredList = predictionList.subList(1, 11);
        NumDistinctAllelesOutputLayerPrediction nda = (NumDistinctAllelesOutputLayerPrediction) firstPrediction;
        index = nda.index;
        set(nda, genoPredList.toArray(new SingleGenotypePrediction[genoPredList.size()]));

    }

    public void set(NumDistinctAllelesOutputLayerPrediction numDistinctAlleles, SingleGenotypePrediction[] singleGenotypePredictions) {

        int numAlleles = numDistinctAlleles.predictedValue;

        double predProbability = 0;
        StringBuffer hetGenotype = new StringBuffer();
        ObjectArrayList<SingleGenotypePrediction> list = ObjectArrayList.wrap(singleGenotypePredictions);

        Collections.sort(list, (g1, g2) -> (int) Math.round(1000 * (g2.probabilityIsCalled - g1.probabilityIsCalled)));
        for (SingleGenotypePrediction element : list.subList(0, numAlleles)) {
            if (hetGenotype.length() > 0) {
                hetGenotype.append("/");
            }
            hetGenotype.append(element.predictedSingleGenotype);
            predProbability += element.probabilityIsCalled;
        }
        overallProbability = predProbability / (double) numAlleles;
        predictedGenotype = hetGenotype.toString();
    }


}