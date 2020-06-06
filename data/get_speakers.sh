#!/bin/bash
# Get IDs of all speakers of given sex in train-clean-100

SPEAKERS_FILE=$1
SEX=$2

sed -r -e '/^;.*$/d' < "$SPEAKERS_FILE" | \
awk -F "|" -v sex="$SEX" '{if ($2 ~ sex && $3 ~ "train-clean-100") print$1}'
