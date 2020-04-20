import argparse
import os
import tensorflow as tf
from tensorflow.keras.models import load_model

base_path = os.path.dirname(os.path.dirname(__file__))


def main(args):
    # Get model.
    model_path = os.path.join(base_path, args.input)
    output_path = os.path.join(base_path, args.output)
    model = load_model(model_path)

    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    if args.quantize_weights:
        converter.optimizations = [tf.lite.Optimize.DEFAULT]

    # TODO: full quantization using input dynamic range?

    tflite_model = converter.convert()
    open(output_path, "wb+").write(tflite_model)


def parse_args():
    parser = argparse.ArgumentParser()
    parser.add_argument('input')
    parser.add_argument('output')
    parser.add_argument('--quantize_weights', action='store_true')
    return parser.parse_args()


if __name__ == '__main__':
    arguments = parse_args()
    main(arguments)
