#!/bin/bash

# Command line args
SPEAKER_DIR=$1
OUTPUT_DIR=$2


# Global params
OPUS_BITRATE=6


# Conversion functions
convert_opus() {
    opusenc --quiet --bitrate=$OPUS_BITRATE $1 tmp.opus
    opusdec --quiet tmp.opus $2
    rm tmp.opus
}

convert_gsm() {
    sox $1 -r 8000 -c 1 -e gsm $2
}

convert_g711() {
    sox $1 -r 8000 -c 1 -e a-law $2
}

convert_g722() {
    sox $1 -r 16000 -b 16 -e signed-integer tmp.raw
    LD_LIBRARY_PATH=./libg722 ./libg722/test --enc --sln16k tmp.raw tmp.g722
    LD_LIBRARY_PATH=./libg722 ./libg722/test --sln16k tmp.g722 tmp2.raw
    sox -r 16000 -b 16 -e signed-integer tmp2.raw $2
    rm tmp.raw tmp2.raw tmp.g722
}


# Actual script - change conversion method as you wish
mkdir -p $OUTPUT_DIR

for f in `find "$SPEAKER_DIR" -type f -name '*.wav' -printf "%f\n"`; do
    convert_g722 "$SPEAKER_DIR/$f" "$OUTPUT_DIR/$f"
    echo $f
done

