import numpy as np
import tensorflow as tf

head = tf.keras.Sequential(
    [
        tf.keras.Input(shape=(768,)),
        tf.keras.layers.Dense(256, activation="relu"),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(32, activation="relu"),
        tf.keras.layers.Dropout(0.2),
        tf.keras.layers.Dense(
            2, activation="softmax", kernel_constraint=None, bias_initializer="zeros"
        ),
    ]
)

new_model_weights = np.load(
    f"round-{10}-weights.npz", encoding="bytes", allow_pickle=True
)

weight_tensor = new_model_weights["arr_0"].item().tensors

dense_1_weights = np.frombuffer(weight_tensor[0], dtype=np.float32).reshape((768, 256))
dense_1_bias = np.frombuffer(weight_tensor[1], dtype=np.float32)

dense_2_weights = np.frombuffer(weight_tensor[2], dtype=np.float32).reshape((256, 32))
dense_2_bias = np.frombuffer(weight_tensor[3], dtype=np.float32)

dense_3_weights = np.frombuffer(weight_tensor[4], dtype=np.float32).reshape((32, 2))
dense_3_bias = np.frombuffer(weight_tensor[5], dtype=np.float32)

head.layers[0].set_weights((dense_1_weights, dense_1_bias))
head.layers[2].set_weights((dense_2_weights, dense_2_bias))
head.layers[4].set_weights((dense_3_weights, dense_3_bias))

head.compile()

head.summary()
