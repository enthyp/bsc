### What I did:
 * installed aiortc:
   - followed: https://github.com/aiortc/aiortc
   - and then `pip install aiortc`
   - it failed while building wheel for av (same problem with `pip install av`)
   - I followed https://tecadmin.net/install-ffmpeg-on-linux/ to install FFMPEG 4.4.2 (I thought it caused some issue) - it did not help...
   - but after isolation of the problem to `av` installation `conda install av -c conda-forge` did the job (from PyPI)
   - conclusion: gotta love whoever makes conda; after conda installation `pip install aiortc` worked like charm, can import
   - right... but was it necessary? 
* aiortc - probably unnecessary. Installed Flask `pip install Flask`
