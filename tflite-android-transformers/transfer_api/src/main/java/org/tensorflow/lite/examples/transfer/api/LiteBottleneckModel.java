/* Copyright 2019 The TensorFlow Authors. All Rights Reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
==============================================================================*/

package org.tensorflow.lite.examples.transfer.api;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

/**
 * A wrapper for TFLite model that generates bottlenecks from images.
 */
class LiteBottleneckModel implements Closeable {
  private static final int FLOAT_BYTES = 4;

  private final LiteModelWrapper modelWrapper;

  LiteBottleneckModel(LiteModelWrapper modelWrapper) {
    this.modelWrapper = modelWrapper;
  }

  /**
   * Passes a single image through the bottleneck model.
   * @param  inputs array of inputIds and inputMask.
   * @param outBottleneck where to store the bottleneck. A new buffer is allocated if null.
   * @return bottleneck data. This is either [outBottleneck], or a newly allocated buffer.
   */
  synchronized ByteBuffer generateBottleneck(Object[] inputs, ByteBuffer outBottleneck) {
    if (outBottleneck == null) {
      outBottleneck = ByteBuffer.allocateDirect(getNumBottleneckFeatures() * FLOAT_BYTES);
    }

    Map<Integer, Object> output = new HashMap<>();
    output.put(0, outBottleneck);

    modelWrapper.getInterpreter().runForMultipleInputsOutputs(inputs, output);
    outBottleneck = (ByteBuffer) output.get(0);
    outBottleneck.rewind();

    return outBottleneck;
  }

  int getNumBottleneckFeatures() {
    return modelWrapper.getInterpreter().getOutputTensor(0).numElements();
  }

  int[] getBottleneckShape() {
    return modelWrapper.getInterpreter().getOutputTensor(0).shape();
  }

  @Override
  public void close() {
    modelWrapper.close();
  }
}
