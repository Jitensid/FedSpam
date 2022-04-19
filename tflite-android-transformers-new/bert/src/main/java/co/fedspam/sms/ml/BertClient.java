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
package co.fedspam.sms.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import androidx.annotation.WorkerThread;
import android.util.Log;
import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.Map;
import org.tensorflow.lite.Interpreter;

/** Interface to load TfLite model and provide predictions. */
public class BertClient {
  private static final String TAG = "BertDemo";
  private static final String MODEL_PATH = "model.tflite";
  private static final String DIC_PATH = "vocab.txt";

//  private static final int MAX_ANS_LEN = 32;
  private static final int MAX_QUERY_LEN = 512;
  private static final int MAX_SEQ_LEN = 512;
  private static final boolean DO_LOWER_CASE = true;
//  private static final int PREDICT_ANS_NUM = 5;
  private static final int NUM_LITE_THREADS = 4;

  // Need to shift 1 for outputs ([CLS]).
  private static final int OUTPUT_OFFSET = 1;

  private final Context context;
  private final Map<String, Integer> dic = new HashMap<>();
  private final FeatureConverter featureConverter;
  private Interpreter tflite;

  private static final Joiner SPACE_JOINER = Joiner.on(" ");

  public BertClient(Context context) {
    this.context = context;
    this.featureConverter = new FeatureConverter(dic, DO_LOWER_CASE, MAX_QUERY_LEN, MAX_SEQ_LEN);
  }

  @WorkerThread
  public synchronized void loadModel() {
    try {
      ByteBuffer buffer = loadModelFile(this.context.getAssets());
      Interpreter.Options opt = new Interpreter.Options();
      opt.setNumThreads(NUM_LITE_THREADS);
      tflite = new Interpreter(buffer, opt);
      Log.v(TAG, "TFLite model loaded.");
    } catch (IOException ex) {
      Log.e(TAG, ex.getMessage());
    }
  }

  @WorkerThread
  public synchronized void loadDictionary() {
    try {
      loadDictionaryFile(this.context.getAssets());
      Log.v(TAG, "Dictionary loaded.");
    } catch (IOException ex) {
      Log.e(TAG, ex.getMessage());
    }
  }

  @WorkerThread
  public synchronized void unload() {
    tflite.close();
    dic.clear();
  }

  /** Load tflite model from assets. */
  public MappedByteBuffer loadModelFile(AssetManager assetManager) throws IOException {
    try (AssetFileDescriptor fileDescriptor = assetManager.openFd(MODEL_PATH);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor())) {
      FileChannel fileChannel = inputStream.getChannel();
      long startOffset = fileDescriptor.getStartOffset();
      long declaredLength = fileDescriptor.getDeclaredLength();
      return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }
  }

  /** Load dictionary from assets. */
  public void loadDictionaryFile(AssetManager assetManager) throws IOException {
    try (InputStream ins = assetManager.open(DIC_PATH);
        BufferedReader reader = new BufferedReader(new InputStreamReader(ins))) {
      int index = 0;
      while (reader.ready()) {
        String key = reader.readLine();
        dic.put(key, index++);
      }
    }
  }

  /**
   * Input: Original content and query for the QA task. Later converted to Feature by
   * FeatureConverter. Output: A String[] array of answers and a float[] array of corresponding
   * logits.
   */
  @WorkerThread
  public synchronized float predict(String query) {
    Log.v(TAG, "TFLite model: " + MODEL_PATH + " running...");
    Log.v(TAG, "Convert Feature...");
    Feature feature = featureConverter.convert(query);

    Log.v(TAG, "Set inputs...");
    int[][] inputIds = new int[1][MAX_SEQ_LEN];
    int[][] inputMask = new int[1][MAX_SEQ_LEN];
    float[][] spamProb = new float[1][1];

    for (int j = 0; j < MAX_SEQ_LEN; j++) {
      inputIds[0][j] = feature.inputIds[j];
      inputMask[0][j] = feature.inputMask[j];
    }

    Object[] inputs = {inputIds, inputMask};
    Map<Integer, Object> output = new HashMap<>();
    output.put(0, spamProb);

    Log.v(TAG, "Run inference...");
    tflite.runForMultipleInputsOutputs(new Object[] { inputIds, inputMask }, output);
    
    Log.v(TAG, "Convert answers...");
    float prob[][] = (float[][]) output.get(0);
    Log.v("PREDICTION", Float.toString(prob[0][0]));
    Log.v(TAG, "Finish.");

    return prob[0][0];
  }

}
