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
package co.fedspam.sms.ui;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;

import android.os.Handler;
import android.os.HandlerThread;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.protobuf.ByteString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import co.fedspam.android_transformers.R;
import co.fedspam.sms.flwr.FlowerClient;
import co.fedspam.sms.ml.BertClient;
import flwr.android_client.ClientMessage;
import flwr.android_client.FlowerServiceGrpc;
import flwr.android_client.Parameters;
import flwr.android_client.Scalar;
import flwr.android_client.ServerMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class MainActivity extends AppCompatActivity {
    private static final boolean DISPLAY_RUNNING_TIME = true;
    private static final String TAG = "SpamClassificationActivity";
    private static final String IP = "192.168.29.254";
    private static final int PORT = 8999;

    private Handler handler;
    private BertClient bertClient;
    private CoordinatorLayout layout;
    private TextView predictionResult;
    private TextView resultText;
    private Button loadDataButton;
    private Button connectButton;
    private Button trainButton;
    private FlowerClient fc;

    /* GRPC */
    private ManagedChannel channel;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

        TextInputEditText smsText = findViewById(R.id.input_box);
        loadDataButton = findViewById(R.id.load_data_button);
        connectButton = findViewById(R.id.connect_button);
        trainButton = findViewById(R.id.train_button);

        layout = findViewById(R.id.cord_layout);
        predictionResult = findViewById(R.id.predition_text);
        resultText = (TextView) findViewById(R.id.response_textview);
        resultText.setMovementMethod(new ScrollingMovementMethod());

        loadDataButton.setOnClickListener(view -> loadData(view));
        connectButton.setOnClickListener(view -> connect(view));
        trainButton.setOnClickListener(view -> runGRCP(view));


        // disable both the buttons
        connectButton.setEnabled(false);
        trainButton.setEnabled(false);

        // Setup QA client to and background thread to run inference.
        HandlerThread handlerThread = new HandlerThread("QAClient");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        fc = new FlowerClient(this);
    }

    @Override
    protected void onStart() {
        Log.v(TAG, "onStart");
        super.onStart();
        handler.post(
                () -> {
                    fc.loadDictionary();
                });
    }

    @Override
    protected void onStop() {
        Log.v(TAG, "onStop");
        super.onStop();
        handler.post(() -> fc.unload());
    }

    public static void hideKeyboard(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Activity.INPUT_METHOD_SERVICE);
        View view = activity.getCurrentFocus();
        if (view == null) {
            view = new View(activity);
        }
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    public void setResultText(String text) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");
        String time = dateFormat.format(new Date());
        resultText.append("\n" + time + "   " + text);
    }

    public void loadData(View view){
        int samplesToLoad = 32;

        hideKeyboard(this);
        setResultText("Loading the local training dataset in memory. It will take several seconds.");
        loadDataButton.setEnabled(false);
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                fc.loadData(samplesToLoad);
                setResultText("Training dataset is loaded in memory.");
                connectButton.setEnabled(true);
            }
        }, 1000);

    }

    public void connect(View view) {
        channel = ManagedChannelBuilder.forAddress(IP, PORT).maxInboundMessageSize(10 * 1024 * 1024).usePlaintext().build();
        hideKeyboard(this);
        trainButton.setEnabled(true);
        connectButton.setEnabled(false);
        trainButton.setEnabled(true);
        setResultText("Channel object created. Ready to train!");
    }

    public void runGRCP(View view){
        new GrpcTask(new FlowerServiceRunnable(), channel, this).execute();
    }


    private interface GrpcRunnable {
        void run(FlowerServiceGrpc.FlowerServiceBlockingStub blockingStub, FlowerServiceGrpc.FlowerServiceStub asyncStub, MainActivity activity) throws Exception;
    }

    private static ClientMessage weightsAsProto(ByteBuffer[] weights){
        List<ByteString> layers = new ArrayList<ByteString>();
        for (int i=0; i < weights.length; i++) {
            layers.add(ByteString.copyFrom(weights[i]));
        }
        Parameters p = Parameters.newBuilder().addAllTensors(layers).setTensorType("ND").build();
        ClientMessage.ParametersRes res = ClientMessage.ParametersRes.newBuilder().setParameters(p).build();
        return ClientMessage.newBuilder().setParametersRes(res).build();
    }

    private static ClientMessage fitResAsProto(ByteBuffer[] weights, int training_size){
        List<ByteString> layers = new ArrayList<ByteString>();
        for (int i=0; i < weights.length; i++) {
            layers.add(ByteString.copyFrom(weights[i]));
        }
        Parameters p = Parameters.newBuilder().addAllTensors(layers).setTensorType("ND").build();
        ClientMessage.FitRes res = ClientMessage.FitRes.newBuilder().setParameters(p).setNumExamples(training_size).build();
        return ClientMessage.newBuilder().setFitRes(res).build();
    }

    private static ClientMessage evaluateResAsProto(float accuracy, int testing_size){
        ClientMessage.EvaluateRes res = ClientMessage.EvaluateRes.newBuilder().setLoss(accuracy).setNumExamples(testing_size).build();
        return ClientMessage.newBuilder().setEvaluateRes(res).build();
    }

    private static class FlowerServiceRunnable implements GrpcRunnable {
        private Throwable failed;
        private StreamObserver<ClientMessage> requestObserver;
        @Override
        public void run(FlowerServiceGrpc.FlowerServiceBlockingStub blockingStub, FlowerServiceGrpc.FlowerServiceStub asyncStub, MainActivity activity)
                throws Exception {
            join(asyncStub, activity);
        }

        private void join(FlowerServiceGrpc.FlowerServiceStub asyncStub, MainActivity activity)
                throws InterruptedException, RuntimeException {

            final CountDownLatch finishLatch = new CountDownLatch(1);
            requestObserver = asyncStub.join(
                    new StreamObserver<ServerMessage>() {
                        @Override
                        public void onNext(ServerMessage msg) {
                            handleMessage(msg, activity);
                        }

                        @Override
                        public void onError(Throwable t) {
                            failed = t;
                            finishLatch.countDown();

                            Log.e(TAG, t.getMessage() + " Error Occured!", t);
                        }

                        @Override
                        public void onCompleted() {
                            finishLatch.countDown();
                            Log.e(TAG, "Done");
                        }
                    });
        }

        private void handleMessage(ServerMessage message, MainActivity activity) {

            try {
                ByteBuffer[] weights;
                ClientMessage c = null;

                if (message.hasGetParameters()) {
                    Log.e(TAG, "Handling GetParameters");
                    activity.setResultText("Handling GetParameters message from the server.");

                    weights = activity.fc.getWeights();

                    Log.i("WEIGHTS", "RECEIVED PARAMS FROM SERVER");
                    c = weightsAsProto(weights);
                }
                if (message.hasFitIns()) {

                    Log.e(TAG, "Handling FitIns");
                    activity.setResultText("Handling Fit request from the server.");

                    List<ByteString> layers = message.getFitIns().getParameters().getTensorsList();

                    Log.i("layers", "Got Tensors from getFitIns");

                    Scalar epoch_config = message.getFitIns().getConfigMap().getOrDefault("local_epochs", Scalar.newBuilder().setSint64(1).build());

                    Log.i("epoch_config", "EPOCH CONFIG WORKED");

                    int local_epochs = (int) epoch_config.getSint64();

                    Log.i("local_epochs", "local_epochs");

                    // Our model has 10 layers
                    ByteBuffer[] newWeights = new ByteBuffer[layers.size()] ;
                    for (int i = 0; i < layers.size(); i++) {
                        Log.i("DATA", "MSG" + String.valueOf(i));
                        newWeights[i] = ByteBuffer.wrap(layers.get(i).toByteArray());
                    }

                    Pair<ByteBuffer[], Integer> outputs = activity.fc.fit(newWeights, local_epochs);
                    c = fitResAsProto(outputs.first, outputs.second);
                }
                if (message.hasEvaluateIns()) {
                    Log.e(TAG, "Handling EvaluateIns");
                    activity.setResultText("Handling Evaluate request from the server");

                    List<ByteString> layers = message.getEvaluateIns().getParameters().getTensorsList();

                    // Our model has 10 layers
                    ByteBuffer[] newWeights = new ByteBuffer[layers.size()] ;
                    for (int i = 0; i < layers.size(); i++) {
                        newWeights[i] = ByteBuffer.wrap(layers.get(i).toByteArray());
                    }
                    Pair<Pair<Float, Float>, Integer> inference = activity.fc.evaluate(newWeights);

                    float loss = inference.first.first;
                    float accuracy = inference.first.second;

                    Log.i("LOSS", String.valueOf(loss));
                    Log.i("accuracy", String.valueOf(accuracy));

                    activity.setResultText("Test Accuracy after this round = " + accuracy);

                    int test_size = inference.second;

                    c = evaluateResAsProto(loss, test_size);
                }
                requestObserver.onNext(c);
                activity.setResultText("Response sent to the server");
                c = null;
            }
            catch (Exception e){

                Log.e(e.getClass().getCanonicalName(), e.getClass().getName());

                Log.e(TAG, "ERROR IN ");
                Log.e(TAG, e.getMessage());
            }
        }
    }

    private static class GrpcTask extends AsyncTask<Void, Void, String> {
        private final GrpcRunnable grpcRunnable;
        private final ManagedChannel channel;
        private final MainActivity activityReference;

        GrpcTask(GrpcRunnable grpcRunnable, ManagedChannel channel, MainActivity activity) {
            this.grpcRunnable = grpcRunnable;
            this.channel = channel;
            this.activityReference = activity;
        }

        @Override
        protected String doInBackground(Void... nothing) {
            try {
                grpcRunnable.run(FlowerServiceGrpc.newBlockingStub(channel), FlowerServiceGrpc.newStub(channel), this.activityReference);
                return "Connection to the FL server successful \n";
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                e.printStackTrace(pw);
                pw.flush();
                return "Failed to connect to the FL server \n" + sw;
            }
        }

        @Override
        protected void onPostExecute(String result) {
            MainActivity activity = activityReference;
            if (activity == null) {
                return;
            }
            activity.setResultText(result);
            activity.trainButton.setEnabled(false);
        }
    }

//    private void predictSpam(String query) {
//        query = query.trim();
//        if (query.isEmpty()) {
//            return;
//        }
//
//        // Delete all pending tasks.
//        handler.removeCallbacksAndMessages(null);
//
//        // Hide keyboard and dismiss focus on text edit.
//        InputMethodManager imm =
//                (InputMethodManager) getSystemService(AppCompatActivity.INPUT_METHOD_SERVICE);
//        imm.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
//        View focusView = getCurrentFocus();
//        if (focusView != null) {
//            focusView.clearFocus();
//        }
//
//        Snackbar runningSnackbar =
//                Snackbar.make(layout, "Checking for SPAM...", Snackbar.LENGTH_INDEFINITE);
//        runningSnackbar.show();
//
//
//        // Run TF Lite model to get the answer.
//        String finalQuery = query;
//        handler.post(
//                () -> {
//                    long beforeTime = System.currentTimeMillis();
//                    final float probablity = bertClient.predict(finalQuery);
//                    long afterTime = System.currentTimeMillis();
//                    double totalSeconds = (afterTime - beforeTime) / 1000.0;
//                    runOnUiThread(
//                            () -> {
//                                runningSnackbar.dismiss();
//
//                                String displayMessage = "Message Classified In ";
//                                if (DISPLAY_RUNNING_TIME) {
//                                    displayMessage = String.format("%s %.3f sec.", displayMessage, totalSeconds);
//                                }
//                                Snackbar.make(layout, displayMessage, Snackbar.LENGTH_INDEFINITE).show();
//
//                                // set text to text field
//                                predictionResult.setText(String.format("%s %.3f.", probablity > 0.5 ? "Spam" : "Ham", probablity));
//                            });
//
//                });
//    }
}
