
package com.example.ml;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import androidx.annotation.WorkerThread;
import android.util.Log;
import android.widget.Toast;

import com.google.common.base.Joiner;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.flex.FlexDelegate;

/** Interface to load TfLite model and provide predictions. */
public class QaClient {
  private static final String TAG = "BertDemo";
  private static final String MODEL_PATH = "bert_spam_model.tflite";
  private static final String DIC_PATH = "bert_spam_vocab.txt";

  private static final int MAX_ANS_LEN = 32;
  private static final int MAX_QUERY_LEN = 512;
  private static final int MAX_SEQ_LEN = 512;
  private static final boolean DO_LOWER_CASE = true;
  private static final int PREDICT_ANS_NUM = 2;
  private static final int NUM_LITE_THREADS = 4;

  // Need to shift 1 for outputs ([CLS]).
  private static final int OUTPUT_OFFSET = 1;

  private final Context context;
  private final Map<String, Integer> dic = new HashMap<>();
  private final FeatureConverter featureConverter;
  private Interpreter tflite;

  private static final Joiner SPACE_JOINER = Joiner.on(" ");

  public QaClient(Context context) {
    this.context = context;
    this.featureConverter = new FeatureConverter(dic, DO_LOWER_CASE, MAX_QUERY_LEN, MAX_SEQ_LEN);
  }

  @WorkerThread
  public synchronized void loadModel() {
    try {
      FlexDelegate delegate = new FlexDelegate();

      ByteBuffer buffer = loadModelFile(this.context.getAssets());
      Interpreter.Options opt = new Interpreter.Options().addDelegate(delegate);
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
  public synchronized float[] predict(String query, String content) {
    Log.v(TAG, "TFLite model: " + MODEL_PATH + " running...");
    Log.v(TAG, "Convert Feature...");
    Feature feature = featureConverter.convert(query, content);

    Log.v(TAG, "Set inputs...");
    int[][] inputIds = new int[1][MAX_SEQ_LEN];
    int[][] inputMask = new int[1][MAX_SEQ_LEN];
//    int[][] segmentIds = new int[1][MAX_SEQ_LEN];
    float[][] startLogits = new float[1][2];
    float[][] endLogits = new float[1][2];

    for (int j = 0; j < MAX_SEQ_LEN; j++) {
      inputIds[0][j] = feature.inputIds[j];
      inputMask[0][j] = feature.inputMask[j];
//      segmentIds[0][j] = feature.segmentIds[j];
    }

    Object[] model_inputs = {inputMask, inputIds};
    Map<Integer, Object> output = new HashMap<>();
    output.put(0, startLogits);
//    output.put(1, endLogits);

    Log.v(TAG, "Run inference...");
    tflite.runForMultipleInputsOutputs(model_inputs, output);
//    tflite.run(model_inputs, output);

    float [][] myoutput = (float[][]) output.get(0);

    for(int index = 0; index < myoutput[0].length; index++){
      Log.v(TAG, String.valueOf(myoutput[0][index]));
    }

    float[] probability_via_softmax = softmax(myoutput[0]);

    Log.v(TAG,"Softmax" +  Arrays.toString(probability_via_softmax));

    Log.v(TAG, "Convert answers...");
    Log.v(TAG, "Finish.");

    return probability_via_softmax;

  }

  private float[] softmax(float[] logits){
    float[] ans = new float[2];

    float[] exponents = new float[2];

    float exponents_sum = 0;
    
    for(int index=0;index<logits.length;index++){
       exponents[index] = (float) Math.exp(logits[index]);
       exponents_sum += exponents[index];
    }
    
    for(int index=0;index<logits.length;index++){
      ans[index] = exponents[index] / exponents_sum;
    }

    return ans;
  }

}
