package co.fedspam.sms.grpcConnection;

import android.os.AsyncTask;
import android.util.Log;
import android.util.Pair;

import com.google.protobuf.ByteString;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

import co.fedspam.sms.flwr.FlowerClient;
import flwr.android_client.ClientMessage;
import flwr.android_client.FlowerServiceGrpc;
import flwr.android_client.Parameters;
import flwr.android_client.Scalar;
import flwr.android_client.ServerMessage;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;

public class GrpcTaskConnect {

    private static String TAG = "JITEN";

    private ManagedChannel channel;
    private FlowerClient flowerClient;

    public GrpcTaskConnect(ManagedChannel channel, FlowerClient flowerClient){
        this.channel = channel;
        this.flowerClient = flowerClient;
    }

    public void startGRPCTask(){
        new GrpcTask(new FlowerServiceRunnable(this.flowerClient), this.channel).execute();
    }

    private interface GrpcRunnable {
        void run(FlowerServiceGrpc.FlowerServiceBlockingStub blockingStub, FlowerServiceGrpc.FlowerServiceStub asyncStub) throws Exception;
    }

    private static class GrpcTask extends AsyncTask<Void, Void, String> {
        private final GrpcRunnable grpcRunnable;
        private final ManagedChannel channel;

        GrpcTask(GrpcRunnable grpcRunnable, ManagedChannel channel) {
            this.grpcRunnable = grpcRunnable;
            this.channel = channel;
        }

        @Override
        protected String doInBackground(Void... nothing) {
            try {
                grpcRunnable.run(FlowerServiceGrpc.newBlockingStub(channel), FlowerServiceGrpc.newStub(channel));
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
            Log.i(TAG, TAG);
        }
    }

    private static class FlowerServiceRunnable implements GrpcRunnable {
        private Throwable failed;
        private StreamObserver<ClientMessage> requestObserver;

        private FlowerClient flowerClient;

        FlowerServiceRunnable(FlowerClient flowerClient){
            this.flowerClient = flowerClient;
        }

        @Override
        public void run(FlowerServiceGrpc.FlowerServiceBlockingStub blockingStub, FlowerServiceGrpc.FlowerServiceStub asyncStub)
                throws Exception {
            join(asyncStub);
        }

        private void join(FlowerServiceGrpc.FlowerServiceStub asyncStub)
                throws InterruptedException, RuntimeException {

            final CountDownLatch finishLatch = new CountDownLatch(1);
            requestObserver = asyncStub.join(
                    new StreamObserver<ServerMessage>() {
                        @Override
                        public void onNext(ServerMessage msg) {
                            handleMessage(msg);
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

        private void handleMessage(ServerMessage message) {

            try {
                ByteBuffer[] weights;
                ClientMessage c = null;

                if (message.hasGetParameters()) {
                    Log.e(TAG, "Handling GetParameters");

                    weights = this.flowerClient.getWeights();

                    Log.i("WEIGHTS", "RECEIVED PARAMS FROM SERVER");
                    c = weightsAsProto(weights);
                }
                if (message.hasFitIns()) {

                    Log.e(TAG, "Handling FitIns");

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

                    Pair<ByteBuffer[], Integer> outputs = this.flowerClient.fit(newWeights, local_epochs);

                    c = fitResAsProto(outputs.first, outputs.second);
                }
                if (message.hasEvaluateIns()) {
                    Log.e(TAG, "Handling EvaluateIns");

                    List<ByteString> layers = message.getEvaluateIns().getParameters().getTensorsList();

                    // Our model has 10 layers
                    ByteBuffer[] newWeights = new ByteBuffer[layers.size()] ;
                    for (int i = 0; i < layers.size(); i++) {
                        newWeights[i] = ByteBuffer.wrap(layers.get(i).toByteArray());
                    }
                    Pair<Pair<Float, Float>, Integer> inference = this.flowerClient.evaluate(newWeights);

                    float loss = inference.first.first;
                    float accuracy = inference.first.second;

                    Log.i("LOSS", String.valueOf(loss));
                    Log.i("accuracy", String.valueOf(accuracy));

                    int test_size = inference.second;

                    c = evaluateResAsProto(loss, test_size);
                }
                requestObserver.onNext(c);
                c = null;
            }
            catch (Exception e){

                Log.e(e.getClass().getCanonicalName(), e.getClass().getName());

                Log.e(TAG, "ERROR IN ");
                Log.e(TAG, e.getMessage());
            }
        }
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

}

