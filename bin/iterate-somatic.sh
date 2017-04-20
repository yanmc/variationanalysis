#!/usr/bin/env bash
. `dirname "${BASH_SOURCE[0]}"`/common.sh
FEATURE_MAPPER=$1
GPU=$2
if [ "$#" -eq 3 ]; then
  NETWORK_ARCHITECTURE=$3
  NETWORK_ARCHITECTURE_OPTION="--net-architecture ${NETWORK_ARCHITECTURE}"
else
  NETWORK_ARCHITECTURE_OPTION=""
fi

if [ -e configure.sh ]; then
 echo "Loading configure.sh"
 source configure.sh
fi

if [ "$#" -lt 2 ]; then
   echo "Argument missing. You must provide a feature mapper classname to use in the iteration."
   exit 1;
fi
if [ -z "${LEARNING_RATE+set}" ]; then
    LEARNING_RATE="50"
    echo "LEARNING_RATE set to ${LEARNING_RATE}. Change the variable to switch the learning rate."
fi
if [ -z "${TRAINING_OPTIONS+set}" ]; then
    TRAINING_OPTIONS=" "
    echo "TRAINING_OPTIONS set to ${TRAINING_OPTIONS}. Change the variable to switch training options (regularization, etc.)."
fi

if [ -z "${MINI_BATCH_SIZE+set}" ]; then
    MINI_BATCH_SIZE="128"
    echo "MINI_BATCH_SIZE set to ${MINI_BATCH_SIZE}. Change the variable to switch the mini-batch-size."
fi
if [ -z "${DATASET+set}" ]; then
    DATASET="NA12878-random-labeled-"
    echo "DATASET set to ${DATASET}. Change the variable to switch dataset."
fi
if [ -z "${GPU+set}" ]; then
    GPU="3"
    echo "GPU set to ${GPU}. Change the integer to switch to another GPU card."
fi
if [ -z "${TRAIN_SUFFIX+set}" ]; then
    TRAIN_SUFFIX="train"
    echo "TRAIN_SUFFIX set to ${TRAIN_SUFFIX}. Change the variable to switch the training set suffix."
fi
if [ -z "${VAL_SUFFIX+set}" ]; then
    VAL_SUFFIX="validation"
    echo "VAL_SUFFIX set to ${VAL_SUFFIX}. Change the variable to switch the validation suffix."
fi
if [ -z "${EVALUATION_METRIC_NAME+set}" ]; then
      EVALUATION_METRIC_NAME="AUC+F1"
      echo "EVALUATION_METRIC_NAME set to ${EVALUATION_METRIC_NAME}. Change the variable to switch the performance metric used to control early stopping."
fi
if [ -z "${PREDICT_OPTIONS+set}" ]; then
      PREDICT_OPTIONS=" "
      echo "PREDICT_OPTIONS set to ${PREDICT_OPTIONS}. Change the variable to switch the options for predict."
fi
if [ ! -e "${DATASET}${VAL_SUFFIX}.sbi" ]; then
    echo "The validation set was not found: ${DATASET}${VAL_SUFFIX}.sbi  "
       exit 1;
fi
if [ ! -e "${DATASET}${TRAIN_SUFFIX}.sbi" ]; then
    echo "The training set was not found: ${DATASET}${TRAIN_SUFFIX}.sbi  "
       exit 1;
fi
if [ ! -e "${DATASET}test.sbi" ]; then
        echo "The test set was not found: ${DATASET}test.sbi  "
           exit 1;
fi

echo "Iteration for FEATURE_MAPPER=${FEATURE_MAPPER}"

export FORCE_PLATFORM=native
#rm ${DATASET}${TRAIN_SUFFIX}.sbi ${DATASET}${VAL_SUFFIX}*cf
train-somatic.sh 10g -t ${DATASET}${TRAIN_SUFFIX}.sbi -v ${DATASET}${VAL_SUFFIX}.sbi \
       --mini-batch-size ${MINI_BATCH_SIZE}  -r ${LEARNING_RATE} ${TRAINING_OPTIONS} \
       --feature-mapper ${FEATURE_MAPPER}  ${NETWORK_ARCHITECTURE_OPTION} \
       --build-cache-then-stop
dieIfError "Failed to map features with CPU build."

OUTPUT_FILE=output-${RANDOM}.log
unset FORCE_PLATFORM
resetPlatform


    train-somatic.sh 10g -t ${DATASET}${TRAIN_SUFFIX}.sbi -v ${DATASET}${VAL_SUFFIX}.sbi \
          --mini-batch-size ${MINI_BATCH_SIZE} -r ${LEARNING_RATE} \
          ${TRAINING_OPTIONS} \
          --feature-mapper ${FEATURE_MAPPER} \
          --random-seed 90129 \
          --early-stopping-measure ${EVALUATION_METRIC_NAME} \
          --early-stopping-num-epochs 10 --gpu-device ${GPU} \
          ${NETWORK_ARCHITECTURE_OPTION} | tee ${OUTPUT_FILE}
dieIfError "Failed to train model with CUDA GPU build."
set -x
MODEL_DIR=`grep "model directory:" ${OUTPUT_FILE}  |cut -d " " -f 3`
MODEL_TIME=`basename ${MODEL_DIR}`
rm ${MODEL_TIME}-best${EVALUATION_METRIC_NAME}*-genotypes.vcf
rm ${MODEL_TIME}-best${EVALUATION_METRIC_NAME}-*.bed

predict.sh 10g -m ${MODEL_DIR} -l best${EVALUATION_METRIC_NAME} -f \
    -i ${DATASET}test.sbi ${PREDICT_OPTIONS} --mini-batch-size ${MINI_BATCH_SIZE}
dieIfError "Failed to predict statistics."
