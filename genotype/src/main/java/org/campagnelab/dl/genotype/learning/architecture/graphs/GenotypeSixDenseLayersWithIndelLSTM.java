package org.campagnelab.dl.genotype.learning.architecture.graphs;

import org.campagnelab.dl.framework.architecture.graphs.ComputationGraphAssembler;
import org.campagnelab.dl.framework.domains.DomainDescriptor;
import org.campagnelab.dl.framework.models.ModelPropertiesHelper;
import org.campagnelab.dl.framework.tools.TrainingArguments;
import org.campagnelab.dl.genotype.learning.GenotypeTrainingArguments;
import org.deeplearning4j.nn.conf.ComputationGraphConfiguration;
import org.deeplearning4j.nn.conf.LearningRatePolicy;
import org.deeplearning4j.nn.conf.graph.MergeVertex;
import org.deeplearning4j.nn.conf.graph.rnn.LastTimeStepVertex;
import org.deeplearning4j.nn.conf.inputs.InputType;
import org.deeplearning4j.nn.conf.layers.GravesLSTM;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.deeplearning4j.nn.weights.WeightInit;
import org.nd4j.linalg.lossfunctions.ILossFunction;

/**
 * Created by joshuacohen on 1/12/17.
 */
public class GenotypeSixDenseLayersWithIndelLSTM extends GenotypeAssembler implements ComputationGraphAssembler {
    private final String[] outputNames;
    private static final String[] lstmInputNames = new String[]{"from", "G1", "G2", "G3"};
    private static final WeightInit WEIGHT_INIT = WeightInit.XAVIER;
    private static final LearningRatePolicy LEARNING_RATE_POLICY = LearningRatePolicy.Poly;
    private static final OutputType DEFAULT_OUTPUT_TYPE = OutputType.DISTINCT_ALLELES;
    private final String combined;
    private final OutputType outputType;

    public enum OutputType {
        HOMOZYGOUS,
        DISTINCT_ALLELES,
        COMBINED,
    }

    private GenotypeTrainingArguments arguments;

    public GenotypeSixDenseLayersWithIndelLSTM() {
        this(DEFAULT_OUTPUT_TYPE, false, false);
    }

    public GenotypeSixDenseLayersWithIndelLSTM(OutputType outputType, boolean hasIsVariant) {
        this(outputType, hasIsVariant, false);
    }

    public GenotypeSixDenseLayersWithIndelLSTM(OutputType outputType, boolean hasIsVariant, boolean fixRef) {
        this.outputType = outputType;
        this.hasIsVariant = hasIsVariant;
        combined = fixRef ? "combinedRef" : "combined";
        switch (outputType) {
            case DISTINCT_ALLELES:
                if (hasIsVariant) {
                    outputNames = new String[]{"numDistinctAlleles", "A", "T", "C", "G", "N",
                            "I1", "I2", "I3", "I4", "I5", "metaData", "isVariant"};
                } else {
                    outputNames = new String[]{"numDistinctAlleles", "A", "T", "C", "G", "N",
                            "I1", "I2", "I3", "I4", "I5", "metaData"};
                }
                break;
            case COMBINED:
                if (hasIsVariant) {
                    outputNames = new String[]{combined, "metaData", "isVariant"};
                } else {
                    outputNames = new String[]{combined, "metaData"};
                }
                break;
            case HOMOZYGOUS:
                if (hasIsVariant) {
                    outputNames = new String[]{"homozygous", "A", "T", "C", "G", "N",
                            "I1", "I2", "I3", "I4", "I5", "metaData", "isVariant"};
                } else {
                    outputNames = new String[]{"homozygous", "A", "T", "C", "G", "N",
                            "I1", "I2", "I3", "I4", "I5", "metaData"};
                }
                break;
            default:
                throw new RuntimeException("Output type not recognized");
        }
    }

    private GenotypeTrainingArguments args() {
        return arguments;
    }

    @Override
    public void setArguments(TrainingArguments arguments) {
        this.arguments = ((GenotypeTrainingArguments) arguments);
    }

    private void addOutputLayers(ComputationGraphConfiguration.GraphBuilder build, DomainDescriptor domainDescriptor,
                                 String lastDenseLayerName, int numIn) {
        if (outputType == OutputType.HOMOZYGOUS || outputType == OutputType.DISTINCT_ALLELES) {
            if (outputType == OutputType.DISTINCT_ALLELES) {
                build.addLayer("numDistinctAlleles", new OutputLayer.Builder(domainDescriptor.getOutputLoss("homozygous"))
                        .weightInit(WEIGHT_INIT)
                        .activation("softmax").weightInit(WEIGHT_INIT)
                        .learningRateDecayPolicy(LEARNING_RATE_POLICY)
                        .nIn(numIn)
                        .nOut(domainDescriptor.getNumOutputs("numDistinctAlleles")[0])
                        .build(), lastDenseLayerName);
            } else {
                build.addLayer("homozygous", new OutputLayer.Builder(domainDescriptor.getOutputLoss("homozygous"))
                        .weightInit(WEIGHT_INIT)
                        .activation("softmax").weightInit(WEIGHT_INIT).learningRateDecayPolicy(LEARNING_RATE_POLICY)
                        .nIn(numIn).nOut(11).build(), lastDenseLayerName);
            }
            int endingIndex = hasIsVariant ? outputNames.length - 2 : outputNames.length - 1;
            for (int i = 1; i <= endingIndex; i++) {
                build.addLayer(outputNames[i], new OutputLayer.Builder(domainDescriptor.getOutputLoss(outputNames[i]))
                        .weightInit(WEIGHT_INIT)
                        .activation("softmax").weightInit(WEIGHT_INIT).learningRateDecayPolicy(LEARNING_RATE_POLICY)
                        .nIn(numIn).nOut(2).build(), lastDenseLayerName);
            }
        } else if (outputType == OutputType.COMBINED) {
            build.addLayer(combined, new OutputLayer.Builder(domainDescriptor.getOutputLoss(combined))
                    .weightInit(WEIGHT_INIT)
                    .activation(combined).weightInit(WEIGHT_INIT).learningRateDecayPolicy(LEARNING_RATE_POLICY)
                    .nIn(numIn)
                    .nOut(domainDescriptor.getNumOutputs(combined)[0]).build(), lastDenseLayerName);
        }
        appendMetaDataLayer(domainDescriptor, LEARNING_RATE_POLICY, build, numIn, WEIGHT_INIT, lastDenseLayerName);
        appendIsVariantLayer(domainDescriptor, LEARNING_RATE_POLICY, build, numIn, WEIGHT_INIT, lastDenseLayerName);
    }

    @Override
    public ComputationGraph createComputationalGraph(DomainDescriptor domainDescriptor) {
        int numInputs = domainDescriptor.getNumInputs("input")[0];
        int numLSTMInputs = domainDescriptor.getNumInputs("from")[0];
        int numHiddenNodes = domainDescriptor.getNumHiddenNodes("firstDense");
        int numLSTMHiddenNodes = domainDescriptor.getNumHiddenNodes("lstmLayer");
        FeedForwardDenseLayerAssembler assembler = new FeedForwardDenseLayerAssembler(args());
        assembler.setLearningRatePolicy(LEARNING_RATE_POLICY);
        assembler.initializeBuilder();
        assembler.setInputTypes(getInputTypes(domainDescriptor));
        ComputationGraphConfiguration.GraphBuilder build = assembler.getBuild();
        for (String lstmInputName : lstmInputNames) {
            String lstmInputLayerName = "lstm" + lstmInputName + "Input";
            String lstmInputToFeedForward = lstmInputLayerName;
            build.addLayer(lstmInputLayerName, new GravesLSTM.Builder()
                    .nIn(numLSTMInputs)
                    .nOut(numLSTMHiddenNodes)
                    .activation("softsign")
                    .build(), lstmInputName);
            for (int i = 0; i < args().numLSTMLayers; i++) {
                String lstmPrevious = i == 0 ? lstmInputLayerName :  "lstm" + lstmInputName + "Hidden" + (i - 1);
                String lstmHidden = "lstm" + lstmInputName + "Hidden" + i;
                lstmInputToFeedForward = lstmHidden;
                build.addLayer(lstmHidden, new GravesLSTM.Builder()
                        .nIn(numLSTMHiddenNodes)
                        .nOut(numLSTMHiddenNodes)
                        .activation("softsign")
                        .build(), lstmPrevious);
            }
            build.addVertex("lstm" + lstmInputName + "LastTimeStepVertex", new LastTimeStepVertex(lstmInputName),
                    lstmInputToFeedForward);
        }
        assembler.assemble(numInputs, numHiddenNodes, args().numPreVertexLayers);
        String[] mergeInputs = new String[lstmInputNames.length + 1];
        for (int i = 0; i < lstmInputNames.length; i++) {
            mergeInputs[i] = "lstm" + lstmInputNames[i] + "LastTimeStepVertex";
        }
        mergeInputs[lstmInputNames.length] = assembler.lastLayerName();
        build.addVertex("lstmFeedForwardMerge", new MergeVertex(), mergeInputs);
        int numInputsToDenseAfterMerge = assembler.getNumOutputs() + (lstmInputNames.length  * numLSTMHiddenNodes);
        assembler.assemble(numInputsToDenseAfterMerge, numHiddenNodes,
                args().numLayers, "lstmFeedForwardMerge", args().numPreVertexLayers + 1);
        String lastDenseLayerName = assembler.lastLayerName();
        int numIn = assembler.getNumOutputs();
        addOutputLayers(build, domainDescriptor, lastDenseLayerName, numIn);
        ComputationGraphConfiguration conf = build
                .setOutputs(outputNames)
                .build();
        System.out.println(conf);
        return new ComputationGraph(conf);
    }

    @Override
    public void setNumInputs(String inputName, int... dimension) {

    }

    @Override
    public void setNumOutputs(String outputName, int... dimension) {

    }

    @Override
    public void setNumHiddenNodes(String componentName, int numHiddenNodes) {

    }

    @Override
    public String[] getInputNames() {
        return new String[]{"input", "from", "G1", "G2", "G3"};
    }

    private InputType[] getInputTypes(DomainDescriptor domainDescriptor) {
        String[] inputNames = getInputNames();
        InputType[] inputTypes = new InputType[inputNames.length];
        for (int i = 0; i < inputNames.length; i++) {
            switch(inputNames[i]) {
                case "input":
                    inputTypes[i] = InputType.feedForward(domainDescriptor.getNumInputs(inputNames[i])[0]);
                    break;
                case "from":
                case "G1":
                case "G2":
                case "G3":
                    inputTypes[i] = InputType.recurrent(domainDescriptor.getNumInputs(inputNames[i])[0]);
                    break;
                default:
                    throw new RuntimeException("Invalid input to computation graph");
            }
        }
        return inputTypes;
    }

    @Override
    public String[] getOutputNames() {
        return outputNames;
    }

    @Override
    public String[] getComponentNames() {
        return new String[]{"firstDense", "lstmLayer"};
    }

    @Override
    public void setLossFunction(String outputName, ILossFunction lossFunction) {

    }

    @Override
    public void saveProperties(ModelPropertiesHelper helper) {
        helper.put(this.getClass().getCanonicalName() + ".numLayers", args().numLayers);
        helper.put(this.getClass().getCanonicalName() + ".numLstmLayers", args().numLSTMLayers);
    }
}
