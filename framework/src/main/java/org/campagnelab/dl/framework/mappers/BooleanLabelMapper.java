package org.campagnelab.dl.framework.mappers;

import org.nd4j.linalg.api.ndarray.INDArray;

import java.util.function.Predicate;

/**
 * A functional label mapper for boolean values. Support epsilon.
 * Created by fac2003 on 12/23/16.
 */
public class BooleanLabelMapper<RecordType> implements LabelMapper<RecordType> {
    public static final int IS_TRUE = 0;
    public static final int IS_FALSE = 1;
    private final float epsilon;
    private Predicate<RecordType> predicate;

    public BooleanLabelMapper(Predicate<RecordType> predicate) {

        this(predicate, 0);
    }

    /**
     * Define a boolean mapper with predicate and epsilon. Epsilon controls how much label smoothing is applied.
     * An epsilon of zero indicates certainty in the label. Small
     * epsilons indicate a some possibility that the alternative is true.
     * See Deep Learning, Goodfellow, Bengio and Courville, 1st edition, p 236.
     *
     * @param predicate
     * @param epsilon
     */
    public BooleanLabelMapper(Predicate<RecordType> predicate, float epsilon) {
        this.predicate = predicate;
        this.epsilon = epsilon;
    }


    @Override
    public int numberOfLabels() {
        return 2;
    }

    int[] indices = new int[]{0, 0};

    @Override
    public void mapLabels(RecordType record, INDArray labels, int indexOfRecord) {
        indices[0] = indexOfRecord;

        for (int labelIndex = 0; labelIndex < numberOfLabels(); labelIndex++) {
            indices[1] = labelIndex;
            labels.putScalar(indices, produceLabel(record, labelIndex));
        }
    }


    @Override
    public float produceLabel(RecordType record, int labelIndex) {
        switch (labelIndex) {
            case IS_TRUE:
                return predicate.test(record) ? 1 - epsilon : epsilon;
            case IS_FALSE:
                return !predicate.test(record) ? 1 - epsilon : epsilon;
            default:
                throw new RuntimeException("Label index out of range, must be 0 or 1: " + labelIndex);
        }

    }

    /**
     * The default implementation returns a 1 dimension.
     *
     * @return
     */
    @Override
    public MappedDimensions dimensions() {
        return new MappedDimensions(numberOfLabels());
    }

    @Override
    public boolean hasMask() {
        return false;
    }

    @Override
    public void maskLabels(RecordType record, INDArray mask, int indexOfRecord) {

    }

    @Override
    public boolean isMasked(RecordType record, int featureIndex) {
        return false;
    }

    @Override
    public void prepareToNormalize(RecordType record, int indexOfRecord) {

    }

}
