package co.fedspam.sms.background;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.work.ListenableWorker;
import androidx.work.WorkRequest;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.google.protobuf.ByteString;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import co.fedspam.sms.flwr.FlowerClient;
import co.fedspam.sms.grpcConnection.GrpcTaskConnect;
import co.fedspam.sms.smsReader.DBHandler;
import co.fedspam.sms.ui.MainActivity;
import flwr.android_client.ClientMessage;
import flwr.android_client.FlowerServiceGrpc;
import flwr.android_client.Parameters;
import flwr.android_client.Scalar;
import flwr.android_client.ServerMessage;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.stub.StreamObserver;

public class FederatedLearningWorker extends Worker {
    private static final String WORK_MANAGER_TAG = "WORK_MANAGER_TAG";

    private static final String IP = "192.168.29.254";

    private static final int PORT = 8999;

    private final int samplesForTraining = 32;

    private static final String TAG = "TAG";

    private FlowerClient flowerClient;

    private DBHandler dbHandler;

    public FederatedLearningWorker(Context appContext, WorkerParameters workerParameters){
        super(appContext, workerParameters);
        dbHandler = new DBHandler(appContext);
    }

    @SuppressLint("WrongThread")
    @NonNull
    @Override
    public Result doWork() {

        flowerClient = new FlowerClient(getApplicationContext());

        Log.i(WORK_MANAGER_TAG, "Dictionary Loaded Started");

        // vocab loaded
        flowerClient.loadDictionary();

        Log.i(WORK_MANAGER_TAG, "Dictionary Loaded Ended");

        Log.i(WORK_MANAGER_TAG, "Data Loading Begin");
        // training and testing data loaded
        try {
            flowerClient.loadDataFromDB(dbHandler, samplesForTraining);
        } catch (IOException e) {
            e.printStackTrace();
        }

        Log.i(WORK_MANAGER_TAG, "Data Loading Ended");

        // set up a GRPC Connection
        ManagedChannel channel = ManagedChannelBuilder.forAddress(IP, PORT).maxInboundMessageSize(10 * 1024 * 1024).usePlaintext().build();

        Log.i(WORK_MANAGER_TAG, "Connection Created");

        new GrpcTaskConnect(channel, flowerClient).startGRPCTask();

        Log.i(WORK_MANAGER_TAG, "1 Round Complete");

        // after model is updated unload everything
        flowerClient.unload();

        Log.i(WORK_MANAGER_TAG, "Everything is unloaded");

        return null;
    }
}
