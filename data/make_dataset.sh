#!/bin/bash
# Apply conversion to ALL speakers of given sex 

SEX=$1
OUTPUT_DIR=$2

./get_speakers.sh train-clean-100/LibriSpeech/SPEAKERS.TXT "$SEX" | \
    sed -e 's/[[:space:]]*$//g' | \  # TODO: may not be necessary now (change in get_speakers.sh)
    xargs -I{} ./convert.sh train-clean-100/LibriSpeech/train-clean-100/{} "$OUTPUT_DIR"/{}
