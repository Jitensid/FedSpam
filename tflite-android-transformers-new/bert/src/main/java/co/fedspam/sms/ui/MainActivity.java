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

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.textfield.TextInputEditText;
import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import co.fedspam.android_transformers.R;
import co.fedspam.sms.flwr.FlowerClient;
import co.fedspam.sms.ml.BertClient;
import co.fedspam.sms.smsReader.DBHandler;
import co.fedspam.sms.smsReader.MessageList;
import co.fedspam.sms.smsReader.SMSReceiver;
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
    private static final String WORK_MANAGER_TAG = "WORK_MANAGER_TAG";
    private static final String IP = "192.168.29.254";
    private static final int PORT = 8999;
    private static final int NUM_THREADS = 4;
    private static final String DEFAULT_LABEL = "Prediction Pending";

    private Handler handler;
//    private BertClient bertClient;
//    private CoordinatorLayout layout;
//    private TextView predictionResult;
//    private TextView resultText;
//    private Button loadDataButton;
//    private Button connectButton;
//    private Button trainButton;
//    private Button ondeviceIntelligenceButton;
    private ListView listView;
    private FlowerClient fc;

    /* GRPC */
    private ManagedChannel channel;

    /* SMS Receiver */
    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_SMS = 0;
    private static final int MY_PERMISSIONS_REQUEST_READ_SMS = 1;
    private static final String SMS_RECEIVED="android.provider.Telephony.SMS_RECEIVED";
    private DBHandler dbHandler = new DBHandler(MainActivity.this);
    TextView messageTV,numberTV,msgClassTV;
    Button getButton;
    SMSReceiver receiver = new SMSReceiver(){
        @Override
        public void onReceive(Context context, Intent intent)
        {
            super.onReceive(context, intent);
            Log.e(TAG, id + " | " + phoneNo + " | " + msg);
            dbHandler.addNewSMS(id, phoneNo, msg, fc.predict(msg), (new Date()).toLocaleString());
        }
    };

    /* ThreadPool Executor */
    protected final ExecutorService executorService = Executors.newFixedThreadPool(NUM_THREADS);
    private final Lock predictionLock = new ReentrantLock();

    @Override
    protected void onResume()
    {
        super.onResume();
        registerReceiver(receiver, new IntentFilter(SMS_RECEIVED));
    }

    @Override
    protected void onDestroy()
    {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

//        TextInputEditText smsText = findViewById(R.id.input_box);
//        loadDataButton = findViewById(R.id.load_data_button);
//        connectButton = findViewById(R.id.connect_button);
//        trainButton = findViewById(R.id.train_button);
//        ondeviceIntelligenceButton = findViewById(R.id.ondeviceIntelligence);
//
//        layout = findViewById(R.id.cord_layout);
//        predictionResult = findViewById(R.id.predition_text);
//        resultText = (TextView) findViewById(R.id.response_textview);
//        resultText.setMovementMethod(new ScrollingMovementMethod());
//
//        loadDataButton.setOnClickListener(view -> loadData(view));
//        connectButton.setOnClickListener(view -> connect(view));
//        trainButton.setOnClickListener(view -> runGRCP(view));
//        ondeviceIntelligenceButton.setOnClickListener(view -> onDeviceIntelligence(view));
//
//        // disable both the buttons
//        connectButton.setEnabled(false);
//        trainButton.setEnabled(false);

        listView = findViewById(R.id.list_view);

        // Setup QA client to and background thread to run inference.
        HandlerThread handlerThread = new HandlerThread("QAClient");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
        fc = new FlowerClient(this);


        //check if permission is granted
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS)!= PackageManager.PERMISSION_GRANTED)
        {
            //check if user denied permission
            if(ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_SMS))
            {
                //user denied
            }
            else
            {
                //ask for allow or deny
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.READ_SMS}, MY_PERMISSIONS_REQUEST_READ_SMS);
            }
        }

        //check if permission is granted
        if(ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS)!= PackageManager.PERMISSION_GRANTED)
        {
            //check if user denied permission
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.RECEIVE_SMS))
            {
                //user denied
            }
            else
            {
                //ask for allow or deny
                ActivityCompat.requestPermissions(this,new String[]{Manifest.permission.RECEIVE_SMS},MY_PERMISSIONS_REQUEST_RECEIVE_SMS);
            }
        }
        new android.os.Handler(Looper.getMainLooper()).postDelayed(
                new Runnable() {
                    public void run() {
                        refreshData();
                        Log.i("tag", "This'll run 2000 milliseconds later");
                    }
                },
                2000);


    }

    //after getting permission
    @Override
    public void onRequestPermissionsResult(int requestcode, String permissions[], int[] grantResults)
    {
        super.onRequestPermissionsResult(requestcode, permissions,grantResults);
        switch(requestcode)
        {
            case MY_PERMISSIONS_REQUEST_RECEIVE_SMS:
            {
                if(grantResults.length>0 && grantResults[0]==PackageManager.PERMISSION_GRANTED)
                {
                    //BroadcastReceiver
                    Toast.makeText(this,"Permitted RECEIVE SMS",Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(this,"Not Permitted RECEIVE SMS",Toast.LENGTH_LONG).show();
                }
            }
            case MY_PERMISSIONS_REQUEST_READ_SMS:
            {
                if(grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    //BroadcastReceiver
                    Toast.makeText(this,"Permitted READ SMS",Toast.LENGTH_LONG).show();
                }
                else
                {
                    Toast.makeText(this,"Not Permitted READ SMS",Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    interface PredictionCallback {
        void onComplete(String predictedLabel);
    }


    private void predictLabelAsync(String msg, PredictionCallback callback){
        executorService.execute(new Runnable() {
            @Override
            public void run() {
                synchronized (this) {
                    String predictedLabel = fc.predict(msg);
                    callback.onComplete(predictedLabel);
                }
            }
        });
    }

    protected ArrayList<SMSData> fetchSMS(int count) {
        // create array of smsData
        ArrayList<SMSData> smsData = new ArrayList<>();

        Cursor cursor = getContentResolver().query(Uri.parse("content://sms/inbox"), null, null, null, null);
        if (cursor.moveToFirst()) {
            // must check the result to prevent exception
            int i = 0;
            do {
                String msgData = "";
                SMSData sms = new SMSData();
                for(int idx=0;idx<cursor.getColumnCount();idx++){
                    String colName = cursor.getColumnName(idx);
                    if (colName.equals("_id")) sms.set_id(Integer.parseInt(cursor.getString(idx)));
                    else if (colName.equals("address")) sms.setSenderName(cursor.getString(idx));
                    else if (colName.equals("date")) sms.setTimeSent(Long.parseLong(cursor.getString(idx)));
                    else if (colName.equals("body")) sms.setMessage(cursor.getString(idx));
                }
                smsData.add(sms);
                if (i > count) break;
                i++;
                // use msgData
            } while (cursor.moveToNext());
        } else {
            // empty box, no SMS
            throw new RuntimeException("SMS are not accessible");
        }

        return smsData;
    }



    protected void refreshData(){
        ArrayList<SMSData> smsArrayList = fetchSMS(50);
        ArrayList<MessageList> msgList = new ArrayList<>();

        for(SMSData sms: smsArrayList){
            // check if this message already exists in data set
            boolean isPresent = dbHandler.isPresent(sms.get_id());
            if (isPresent) {
                String label = dbHandler.getLabel(sms.get_id());
                if (label.equals(DEFAULT_LABEL)) {
                    // predict sms
                    predictLabelAsync(
                            sms.getMessage(),
                            new PredictionCallback() {
                                @Override
                                public void onComplete(String predictedLabel) {
                                    // After getting prediction update Label in database
                                    dbHandler.updateSMSLabel(sms.get_id(), predictedLabel);
                                    Log.i(TAG, sms.getSenderName() + " => " + predictedLabel);
                                }
                            }
                    );
                }
                continue;
            }
            Log.e("SMSListActivity", sms.get_id() + "|" + sms.getSenderName() + "|" + sms.getMessage());

            // add sms into db
            dbHandler.addNewSMS(
                    sms.get_id(),
                    sms.getSenderName(),
                    sms.getMessage(),
                    DEFAULT_LABEL,
                    new Date(sms.getTimeSent()).toLocaleString()
            );

            // predict sms
            predictLabelAsync(
                    sms.getMessage(),
                    new PredictionCallback() {
                        @Override
                        public void onComplete(String predictedLabel) {
                            // After getting prediction update Label in database
                            dbHandler.updateSMSLabel(sms.get_id(), predictedLabel);
                            Log.i(TAG, sms.getSenderName() + " => " + predictedLabel);
                        }
                    }
            );

        }


        ListAdapter listAdapter = new ListAdapter(this, smsArrayList);
        ListView listView = findViewById(R.id.list_view);

        listView.setAdapter(listAdapter);
        listView.setClickable(true);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent i = new Intent(MainActivity.this, SMSDetailsActivity.class);
                SMSData sms = smsArrayList.get(position);
                i.putExtra("id", sms.get_id());
                i.putExtra("sender", sms.getSenderName());
                i.putExtra("message", sms.getMessage());
                i.putExtra("timestamp", sms.getTimeSent());
                i.putExtra("label", "Dummy");
                i.putExtra("flag", false);
                startActivity(i);

            }
        });
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
        executorService.shutdownNow();
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
//        resultText.append("\n" + time + "   " + text);
        Log.i(TAG, time + "   " + text);
    }

    public void loadData(View view){
        int samplesToLoad = 32;

        hideKeyboard(this);
        setResultText("Loading the local training dataset in memory. It will take several seconds.");
//        loadDataButton.setEnabled(false);

        DataLoader dataLoader = new DataLoader(fc, samplesToLoad);

        // load the data on another thread
        new AsyncDataLoad().execute(dataLoader);

    }

    public void onDeviceIntelligence(View view){
//        // mention constraints
//        Constraints.Builder constraintsBuilder = new Constraints.Builder();
//        constraintsBuilder.setRequiredNetworkType(NetworkType.CONNECTED);
////        constraintsBuilder.setRequiresCharging(true);
////        constraintsBuilder.setRequiresBatteryNotLow(true);
////        constraintsBuilder.setRequiresDeviceIdle(true);
//
//        Constraints constraints = constraintsBuilder.build();
//
//        WorkManager workManager = WorkManager.getInstance(this);
//
//        WorkRequest FederatedLearningPeriodicWorkRequest = new PeriodicWorkRequest.Builder(FederatedLearningWorker.class, 15, TimeUnit.MINUTES).setConstraints(constraints).build();
//
//        workManager.getInstance(this).enqueueUniquePeriodicWork(
//                WORK_MANAGER_TAG,
//                ExistingPeriodicWorkPolicy.REPLACE,
//                (PeriodicWorkRequest) FederatedLearningPeriodicWorkRequest
//        );
//
//        Log.i(WORK_MANAGER_TAG, "Added A new WorkManager Instance");

        Intent intent = new Intent(this, SMSListActivity.class);
        startActivity(intent);

    }

    public void connect(View view) {
        channel = ManagedChannelBuilder.forAddress(IP, PORT).maxInboundMessageSize(10 * 1024 * 1024).usePlaintext().build();
        hideKeyboard(this);
//        trainButton.setEnabled(true);
//        connectButton.setEnabled(false);
//        trainButton.setEnabled(true);
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

    private static class DataLoader {
        private FlowerClient flowerClient;
        int maxSamples;

        DataLoader(FlowerClient flowerClient, int maxSamples){
            this.flowerClient = flowerClient;
            this.maxSamples = maxSamples;
        }

        public int getMaxSamples() {
            return maxSamples;
        }

        public FlowerClient getFlowerClient() {
            return flowerClient;
        }
    }

    private class AsyncDataLoad extends  AsyncTask<DataLoader, Void, String>{

        @Override
        protected String doInBackground(DataLoader... dataLoaders) {

            DataLoader dataLoader = dataLoaders[0];

            try {
                dataLoader.getFlowerClient().loadDataQuickly(dataLoader.getMaxSamples());
            } catch (IOException e) {
                e.printStackTrace();
            }

            return "Data is Successfully Loaded";
        }

        @Override
        protected void onPostExecute(String s) {
            setResultText(s);
//            connectButton.setEnabled(true);
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
//            activity.trainButton.setEnabled(false);
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
