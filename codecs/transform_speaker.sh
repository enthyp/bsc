#!/bin/bash

SPEAKER_DIR=$1
OUTPUT_DIR=$2
BITRATES=(6 48)

mkdir -p $OUTPUT_DIR

for br in "${BITRATES[@]}"; do
    for d in `ls $SPEAKER_DIR`; do
        for f in `find "$SPEAKER_DIR/$d" -type f -name '*.flac' -printf "%f\n"`; do
            OPUS_ROOT=$OUTPUT_DIR/$br-${f//.flac/}
            opusenc --quiet --bitrate=$br "$SPEAKER_DIR/$d/$f" "$OPUS_ROOT.opus" 
            opusdec --quiet "$OPUS_ROOT.opus" "$OPUS_ROOT.wav"
            rm "$OPUS_ROOT.opus"
            echo $f
        done
    done
done
