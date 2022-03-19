package com.example.smsspam;

import android.content.res.AssetFileDescriptor;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.ml.QaClient;

import org.tensorflow.lite.Interpreter;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.channels.FileChannel;
import java.util.Arrays;
import java.util.HashMap;

public class MainActivity extends AppCompatActivity {

    EditText sms_text_input;
    Button prediction;
    private HashMap<String, Integer> tokenMapping = new HashMap<String, Integer>();
    private static final int Model_input_size = 200;
    private String vocab_file_name = "vocab.txt";
    private String tflite_model_filename = "spam.tflite";

    private static final int MAX_ANS_LEN = 32;
    private static final int MAX_QUERY_LEN = 64;
    private static final int MAX_SEQ_LEN = 384;
    private static final boolean DO_LOWER_CASE = true;
    private static final int PREDICT_ANS_NUM = 5;
    private static final int NUM_LITE_THREADS = 4;

    private static Handler handler;
    private static QaClient qaClient;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sms_text_input = (EditText)findViewById(R.id.sms_text_input);
        prediction = (Button)findViewById(R.id.prediction);
        prediction.setOnClickListener(this::handlePredictionButtonClickEvent);

        // Setup QA client to and background thread to run inference.
        HandlerThread handlerThread = new HandlerThread("QAClient");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        qaClient = new QaClient(this);

        try {
            loadVocabularyFromAssets();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(
                () -> {
                    qaClient.loadModel();
                    qaClient.loadDictionary();
                });
    }

    public void loadVocabularyFromAssets() throws IOException {

        final InputStream vocabTxtFileInputStream = getApplicationContext().getAssets().open(vocab_file_name);

        BufferedReader reader = new BufferedReader(new InputStreamReader(vocabTxtFileInputStream));

        String line = reader.readLine();

        int token_counter = 0;

        while(line != null){
            line = reader.readLine();
            tokenMapping.put(line,token_counter);
            token_counter++;
        }

        tokenMapping.put("", 0);
    }

    public float[][] tokenizeSMSInput(String sms_message_input){
        String[] sms_message_input_original_words = sms_message_input.split("\\s");

        String[] sms_message_tokenized_words = new String[Model_input_size];
        Arrays.fill(sms_message_tokenized_words, "");

        for(int index=0; index < sms_message_input_original_words.length; index++){
            sms_message_tokenized_words[index] = sms_message_input_original_words[index];
        }

        float[] processed_tokens = new float[Model_input_size];

        for(int index = 0; index < processed_tokens.length; index++){
            if(tokenMapping.containsKey(sms_message_tokenized_words[index]))
                processed_tokens[index] = tokenMapping.get(sms_message_tokenized_words[index]).floatValue();
            else{
                processed_tokens[index] = 1;
            }
        }
        float[][] processed_tokens_for_model = new float[1][200];

        processed_tokens_for_model[0] = processed_tokens;
        Log.d("UNPROCESSED STRING", Arrays.toString(sms_message_tokenized_words));
        Log.d("PROCESSED STRING", Arrays.toString(processed_tokens_for_model[0]));

        return processed_tokens_for_model;
    }

    public void makeSMSSpamPrediction(String sms_message_input){

        float[][] processed_sms_message_input_for_model = tokenizeSMSInput(sms_message_input);

        try {
            AssetFileDescriptor fileDescriptor = getApplicationContext().getAssets().openFd(tflite_model_filename);

            FileInputStream inputStream = new  FileInputStream(fileDescriptor.getFileDescriptor());

            FileChannel fileChannel = inputStream.getChannel();

            long startOffset = fileDescriptor.getStartOffset();

            long declaredLength = fileDescriptor.getDeclaredLength();

            Interpreter tflite= new Interpreter(fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength));

            float[][] output = new float[1][1];

            tflite.resizeInput(0, new int[] {1, Model_input_size});

            tflite.allocateTensors();

            tflite.run(processed_sms_message_input_for_model, output);

            Log.d("Model Pred1", String.valueOf(output[0][0]));

            Toast.makeText(getApplicationContext(), "Model Prediction is "+ String.valueOf(output[0][0]), Toast.LENGTH_LONG).show();

            tflite.close();

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void handlePredictionButtonClickEvent(View view){
        String sms_input = this.sms_text_input.getText().toString();

        float[] probability_via_softmax = qaClient.predict(sms_input, "");

        Toast.makeText(getApplicationContext(), "Wdfdf", Toast.LENGTH_LONG);

        if(probability_via_softmax[0] > probability_via_softmax[1]){
            Toast.makeText(getApplicationContext(), "Not Spam " + probability_via_softmax[0], Toast.LENGTH_LONG).show();
        }
        else{
            Toast.makeText(getApplicationContext(), "Spam " + probability_via_softmax[1], Toast.LENGTH_LONG).show();
        }

        this.sms_text_input.setText("");
    }
}