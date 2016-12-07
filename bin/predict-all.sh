#!/usr/bin/env bash
. `dirname "${BASH_SOURCE[0]}"`/setup.sh

INPUT=${memory_requirement}
MODELS=models/*
MODEL_TIMES=`grep -v Tag model-conditions.txt | cut -d" " -f 6|awk '{print "models/"$1}' `
parallel -j4 predict.sh 10g -l bestAUC -f -i ${INPUT} -m ::: ${MODEL_TIMES}