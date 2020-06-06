#!/bin/bash

SPEAKER_DIR=$1
OUTPUT_DIR=$2
BITRATES=(6 48)

mkdir -p $OUTPUT_DIR

for br in "${BITRATES[@]}"; do
    for f in `find "$SPEAKER_DIR" -type f -name '*.wav' -printf "%f\n"`; do
        OPUS_ROOT=$OUTPUT_DIR/$br-${f//.wav/}
        opusenc --quiet --bitrate=$br "$SPEAKER_DIR/$f" "$OPUS_ROOT.opus" 
        opusdec --quiet "$OPUS_ROOT.opus" "$OPUS_ROOT.wav"
        rm "$OPUS_ROOT.opus"
        echo $f
    done
done

