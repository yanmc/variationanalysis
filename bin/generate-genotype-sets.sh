#!/usr/bin/env bash
. `dirname "${BASH_SOURCE[0]}"`/common.sh

if [ "$#" -ne 4 ]; then
   echo "Argument missing. expected arguments memory_size goby_alignment vcf goby_genome"
   exit 1;
fi

if [ -e configure.sh ]; then
 echo "Loading configure.sh"
 source configure.sh
fi

memory_requirement=$1
#!/usr/bin/env bash
. `dirname "${BASH_SOURCE[0]}"`/setup.sh
ALIGNMENTS=$1
VCF=$2
GENOME=$3
DATE=`date +%Y-%m-%d`
echo ${DATE} >DATE.txt

case ${ALIGNMENTS} in *.bam) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .bam`;; esac
case ${ALIGNMENTS} in *.sam) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .sam`;; esac
case ${ALIGNMENTS} in *.cram) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .cram`;; esac
case ${ALIGNMENTS} in *.entries) OUTPUT_PREFIX=`basename ${ALIGNMENTS} .entries`;; esac
OUTPUT_PREFIX="${OUTPUT_PREFIX}-${DATE}"
echo "Will write results to ${OUTPUT_PREFIX}"

if [ -z "${DELETE_TMP}" ]; then
    DELETE_TMP="false"
    echo "DELETE_TMP set to ${DELETE_TMP}. Change the variable with export to clear the working directory."
fi
rm -rf tmp
mkdir -p tmp

goby ${memory_requirement} vcf-to-genotype-map ${VCF} \
  -o tmp/variants.varmap
dieIfError "Cannot create varmap"

export SBI_GENOME=${GENOME}
export OUTPUT_BASENAME=tmp/genotype_full

parallel-genotype-sbi.sh 10g ${ALIGNMENTS}
export OUTPUT_BASENAME=${OUTPUT_PREFIX}

add-true-genotypes.sh ${memory_requirement} -m tmp/variants.varmap \
  -i tmp/genotype_full.sbi \
  -o tmp/genotype_full_called \
  --genome ${SBI_GENOME} |tee add-true-genotypes.log
dieIfError "Failed to annotate true variants"

randomize.sh ${memory_requirement} -i tmp/genotype_full_called.sbi \
  -o tmp/genotype_full_called_randomized -b 100000 -c 100 |tee randomize.log
dieIfError "Failed to randomize"

split.sh ${memory_requirement} -i tmp/genotype_full_called_randomized.sbi \
  -f 0.8 -f 0.1 -f 0.1 \
  -o "${OUTPUT_BASENAME}-" \
   -s train -s test -s validation
dieIfError "Failed to split"

# subset the validation sample, throwing out many reference matching sites (to speed
# up performance evaluation for early stopping):
mv "${OUTPUT_BASENAME}-validation.sbi" "${OUTPUT_BASENAME}-validation-all.sbi"
mv "${OUTPUT_BASENAME}-validation.sbip" "${OUTPUT_BASENAME}-validation-all.sbip"

add-true-genotypes.sh ${memory_requirement} -m tmp/variants.varmap \
  -i "${OUTPUT_BASENAME}-validation-all.sbi" \
  -o "${OUTPUT_BASENAME}-validation" \
  --genome ${GENOME} --ref-sampling-rate 0.01 |tee add-true-genotypes-downsampling-ref.log
dieIfError "Failed to reduce validation set"

if [ ${DELETE_TMP} = "true" ]; then
   rm -rf tmp
fi

export DATASET="${OUTPUT_BASENAME}-"