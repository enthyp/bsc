import glob
import numpy as np
import os
from scipy.io import wavfile
from tensorflow.keras import callbacks, optimizers

from ausupre import models


# Prepare training data.
def split_sample(sample, frame_length):
    step = frame_length // 2
    return [sample[s:s + frame_length] for s in range(0, len(sample) - frame_length, step)]


tmp_frame_length = 512
original_files = glob.glob('./speaker_27/27-*.wav')
compressed = [f.replace('/27', '/6-27') for f in original_files]

original_arrays = [wavfile.read(f)[1] for f in original_files]
compressed_arrays = [wavfile.read(f)[1] for f in compressed]

x_train_list = []
y_train_list = []

for o_arr, c_arr in zip(original_arrays, compressed_arrays):
    x_samples = split_sample(o_arr, tmp_frame_length)
    y_samples = split_sample(c_arr, tmp_frame_length)
    x_train_list.extend(x_samples)
    y_train_list.extend(y_samples)

x_train = np.stack(x_train_list)
y_train = np.stack(y_train_list)


# Train the model.
checkpoint_path = "training_3/cp.ckpt"
checkpoint_dir = os.path.dirname(checkpoint_path)

# Create a callback that saves the model's weights
cp_callback = callbacks.ModelCheckpoint(filepath=checkpoint_path,
                                        save_weights_only=True,
                                        verbose=1)

model = models.build_model(tmp_frame_length, 3)
model.compile(optimizer=optimizers.Adam(clipvalue=1.),
              loss='mean_squared_error')

history = model.fit(x_train,
                    y_train,
                    batch_size=128,
                    epochs=100,
                    validation_data=(x_train, y_train),
                    callbacks=[cp_callback])

print(history)
