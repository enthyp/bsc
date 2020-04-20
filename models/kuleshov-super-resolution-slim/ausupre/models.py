import tensorflow as tf
from tensorflow.keras import callbacks, initializers, layers, optimizers
from tensorflow.keras import Model


def num_filters(num_layers):
    return [min(512, 2 ** (5 + i)) for i in range(num_layers)]


def kernel_sizes(num_layers):
    return [max(9, 2 ** (6 - i) + 1) for i in range(num_layers)]


def build_model(frame_length, num_layers):
    # dim/layer: 4096, 2048, 1024, 512, 256, 128,  64,  32,
    n_filters = num_filters(num_layers)
    k_sizes = kernel_sizes(num_layers)
    down_layers = []

    input = layers.Input(shape=(frame_length, 1))

    # downsampling layers
    l = input
    for n_f, k_size in list(zip(n_filters, k_sizes))[:-1]:
        l = layers.Conv1D(filters=n_f, kernel_size=k_size, strides=2, padding='same', kernel_initializer='orthogonal')(l)
        l = layers.LeakyReLU(alpha=0.2)(l)
        # if num_layer > 0: x = layers.BatchNormalization(mode=2)(x)
        down_layers.append(l)

    # bottleneck layer
    # TODO: orthogonal init?
    l = layers.Conv1D(filters=n_filters[-1], kernel_size=k_sizes[-1], strides=2,
                      padding='same', kernel_initializer='orthogonal')(l)
    l = layers.Dropout(rate=0.5)(l)
    l = layers.LeakyReLU(alpha=0.2)(l)

    # upsampling layers
    for n_f, k_size, l_in in reversed(list(zip(n_filters, k_sizes, down_layers))):
        l = layers.Conv1D(filters=2 * n_f, kernel_size=k_size, padding='same', kernel_initializer='orthogonal')(l)
        l = layers.Dropout(rate=0.5)(l)
        l = layers.ReLU()(l)
        l = SubPixel1D(l, r=2)
        l = layers.Concatenate(axis=2)([l, l_in])

    # final convolution layer
    l = layers.Conv1D(filters=2, kernel_size=9, padding='same', kernel_initializer=initializers.RandomNormal(stddev=1e-3))(l)
    l = SubPixel1D(l, r=2)

    output = layers.Add()([input, l])
    return Model(inputs=input, outputs=output)


def SubPixel1D(X, r):
    """One-dimensional subpixel upsampling layer."""
    batch_size = tf.shape(X)[0]
    n_splits = X.shape[-1] // r

    splits = tf.split(X, n_splits, axis=-1)
    flattened = [tf.reshape(x, (batch_size, -1)) for x in splits]
    return tf.stack(flattened, axis=2)