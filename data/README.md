# Scripts
 - `convert.sh` - convert all files from a single directory using given codec
 - `get_speakers.sh` - retrieves IDs of all speakers of given sex from LibriSpeech metafiles
 - `make_dataset.sh` - converts all speakers of given sex using `convert.sh`
 - `speaker_to_wav.sh` - converts all files for given speaker from WAV to FLAC - necessary one-time preprocessing

# Codecs

How to install different codecs.

### Opus:
`sudo apt-get install opus-tools`

### G.722
Build from [this repo](https://github.com/sippy/libg722).

### G.711 a-law
Available in SoX out of the box.

### GSM full-rate
Available in SoX out of the box.


# Notes
 * approx. 3GB (50hrs) of male recordings, same for female
 * 
