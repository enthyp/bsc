# Apply trained model to a single recording.
import argparse
import numpy as np
import os
import tensorflow as tf
import time
from scipy.io import wavfile
from tensorflow.keras.models import load_model

base_path = os.path.dirname(os.path.dirname(__file__))


# Get data.
def get_audio(filepath):
    audio_path = os.path.join(base_path, filepath)
    _, audio = wavfile.read(audio_path)
    return audio.astype(np.float32)


def get_frames(sample, frame_length):
    frames = [sample[s:s + frame_length] for s in range(0, len(sample) - frame_length, frame_length)]
    return np.stack(frames)[:, :, np.newaxis]


# Apply model.
def apply_model(filepath, sample, frame_length):
    model_path = os.path.join(base_path, filepath)
    model = load_model(model_path)

    frames = get_frames(sample, frame_length)
    return model.predict(frames)


def apply_converted_model(filepath, sample, frame_length):
    model_path = os.path.join(base_path, filepath)
    interpreter = tf.lite.Interpreter(model_path=model_path)
    interpreter.allocate_tensors()

    # Get input and output tensors.
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()

    # Infer.
    frames = get_frames(sample, frame_length)
    out = []

    for f in frames:
        interpreter.set_tensor(input_details[0]['index'], f[np.newaxis, :, :])
        interpreter.invoke()
        out.append(interpreter.get_tensor(output_details[0]['index']))

    return np.stack(out)


# TODO: overlap frames? LIMIT VALUES TO [~-30000, ~30000]???

def main(args):
    audio = get_audio(args.input)

    s = time.time()
    if args.converted:
        out = apply_converted_model(args.model_path, audio, args.frame_length)
    else:
        out = apply_model(args.model_path, audio, args.frame_length)
    print(time.time() - s)

    out = out.reshape(-1, 1).astype(np.int16)
    wavfile.write(args.output, args.sample_rate, out)


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('--frame_length', default=4096)
    parser.add_argument('--sample_rate', default=16000)
    parser.add_argument('--converted', action='store_true')
    parser.add_argument('model_path')
    parser.add_argument('input')
    parser.add_argument('output')
    return parser.parse_args()


if __name__ == '__main__':
    arguments = parse_args()
    main(arguments)
