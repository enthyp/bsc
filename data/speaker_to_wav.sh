#!/bin/bash
# Applied to all speakers to have .wav instead of .flac files

SPEAKER_DIR=$1
OUTPUT_DIR=$2

mkdir -p $OUTPUT_DIR

for d in `ls $SPEAKER_DIR`; do
    for f in $SPEAKER_DIR/$d/*.flac; do
        echo $f;
        FILENAME=${f##*/}; 
        sox $f $OUTPUT_DIR/${FILENAME//.flac/.wav}; 
    done
    yes | rm -r $SPEAKER_DIR/$d
done
