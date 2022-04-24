package co.fedspam.sms.flwr;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.ConditionVariable;
import android.util.Log;
import android.util.Pair;

import androidx.annotation.WorkerThread;
import androidx.lifecycle.MutableLiveData;

import com.opencsv.CSVParser;
import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvValidationException;
import com.univocity.parsers.common.processor.RowListProcessor;
import com.univocity.parsers.csv.CsvParser;
import com.univocity.parsers.csv.CsvParserSettings;

import org.tensorflow.lite.examples.transfer.api.TransferLearningModel;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

import co.fedspam.sms.ml.Feature;
import co.fedspam.sms.ml.FeatureConverter;
import co.fedspam.sms.smsReader.DBHandler;
import co.fedspam.sms.smsReader.MessageList;

public class FlowerClient {

    private TransferLearningModelWrapper tlModel;
    private static final int LOWER_BYTE_MASK = 0xFF;
    private MutableLiveData<Float> lastLoss = new MutableLiveData<>();
    private Context context;
    private final ConditionVariable isTraining = new ConditionVariable();
    private static String TAG = "Flower";
    private int local_epochs = 1;


    /* Bert Config */
    private static final String DIC_PATH = "vocab.txt";
    private static final int MAX_QUERY_LEN = 512;
    private static final int MAX_SEQ_LEN = 512;
    private static final boolean DO_LOWER_CASE = true;
    private static final int NUM_LITE_THREADS = 4;
    // Need to shift 1 for outputs ([CLS]).
    private static final int OUTPUT_OFFSET = 1;
    private final Map<String, Integer> dic = new HashMap<>();
    private final FeatureConverter featureConverter;


    public FlowerClient(Context context) {
        this.tlModel = new TransferLearningModelWrapper(context);
        this.context = context;
        this.featureConverter = new FeatureConverter(dic, DO_LOWER_CASE, MAX_QUERY_LEN, MAX_SEQ_LEN);
    }



    public ByteBuffer[] getWeights() {
        return tlModel.getParameters();
    }

    public Pair<ByteBuffer[], Integer> fit(ByteBuffer[] weights, int epochs) {
        Log.i("MODEL TRAINING","MODEL TRAINING SHOULD BEGIN");
        this.local_epochs = epochs;
        tlModel.updateParameters(weights);
        isTraining.close();
        tlModel.train(this.local_epochs);
        tlModel.enableTraining((epoch, loss) -> setLastLoss(epoch, loss));
        Log.e(TAG ,  "Training enabled. Local Epochs = " + this.local_epochs);
        isTraining.block();
        Log.i("MODEL TRAINING","MODEL TRAINING SHOULD END");
        return Pair.create(getWeights(), tlModel.getSize_Training());
    }

    public Pair<Pair<Float, Float>, Integer> evaluate(ByteBuffer[] weights) {
        tlModel.updateParameters(weights);
        tlModel.disableTraining();
        return Pair.create(tlModel.calculateTestStatistics(), tlModel.getSize_Testing());
    }

    public void setLastLoss(int epoch, float newLoss) {
        if (epoch == this.local_epochs - 1) {
            Log.e(TAG, "Training finished after epoch = " + epoch);
            lastLoss.postValue(newLoss);
            tlModel.disableTraining();
            isTraining.open();
        }
    }

    public void loadData(int maxSamples) {
        try {
            CSVReader reader = new CSVReader(new InputStreamReader(this.context.getAssets().open("data.csv")));
            String[] nextLine;
            int i = 0;
            while ((nextLine = reader.readNext()) != null && i < maxSamples) {
                i++;
                Log.e(TAG, i + "th training image loaded " + nextLine.length);
                addSample(nextLine[0], Integer.parseInt(nextLine[1]), true);
            }

            i = 0;
            while ((nextLine = reader.readNext()) != null && i < maxSamples / 5) {
                i++;
                Log.e(TAG, i + "th training image loaded " + nextLine.length);
                addSample(nextLine[0], Integer.parseInt(nextLine[1]), false);
            }
            reader.close();

        } catch (IOException | CsvValidationException ex) {
            ex.printStackTrace();
        }
    }



    public void loadDataQuickly(int maxSamples) throws IOException {

        CsvParserSettings csvParserSettings = new CsvParserSettings();
        csvParserSettings.setMaxCharsPerColumn(10000);

        CsvParser csvParser = new CsvParser(csvParserSettings);

        // call beginParsing to read records one by one, iterator-style.
        csvParser.beginParsing(new InputStreamReader(this.context.getAssets().open("data.csv")));

        // to store number of rows being iterated
        int rowCounter = 0;

        String[] row;

        while ((row = csvParser.parseNext()) != null) {

            Log.i(TAG, row[0]);
            Log.i(TAG, row[1]);

            // if less than 32 rows then add it to training Data
            if(rowCounter < maxSamples) {
                addSample(row[0], Integer.parseInt(row[1]), true);
             }

            // if more than 32 then add it to testing data
            else if(rowCounter > maxSamples){
                addSample(row[0], Integer.parseInt(row[1]), false);
            }

            // if 2 * maxSamples are read than stop reading data
            if(rowCounter == 2 * maxSamples){
                break;
            }

            rowCounter += 1;
        }

        // You only need to use this if you are not parsing the entire content.
        // But it doesn't hurt if you call it anyway.
        csvParser.stopParsing();

    }

    public void loadDataFromDB(DBHandler dbHandler, int count) throws IOException {
        ArrayList<MessageList> msgList = dbHandler.getTrainData(count);
        int traningSamples = msgList.size();

        Log.i("MESSAGE", String.valueOf(msgList.size()));

        if (traningSamples < 32){
            throw new RuntimeException("Training data less than 32 samples!");
        }
        int i = 0;
        for (MessageList msg: msgList){
            String label = msg.getLabel();
            int labelId = 0;
            if (label.equals("spam")) labelId = 1;
            if (i < traningSamples) {
                addSample(msg.getMessage(), labelId, true);
                addSample(msg.getMessage(),  labelId,false);
            }
            i++;
        }
    }

    private void addSample(String query, int labelId, Boolean isTraining) throws IOException {

        Log.v(TAG, "Convert Feature...");
        Feature feature = featureConverter.convert(query);
        Log.v(TAG, "Set inputs...");
        int[][] inputIds = new int[1][MAX_SEQ_LEN];
        int[][] inputMask = new int[1][MAX_SEQ_LEN];
        for (int j = 0; j < MAX_SEQ_LEN; j++) {
            inputIds[0][j] = feature.inputIds[j];
            inputMask[0][j] = feature.inputMask[j];
        }
        Object[] inputs = {inputIds, inputMask};
        String sampleClass = get_class(labelId);


        // add to the list.
        try {
            this.tlModel.addSample(inputs, sampleClass, isTraining).get();
        } catch (ExecutionException e) {
            throw new RuntimeException("Failed to add sample to model", e.getCause());
        } catch (InterruptedException e) {
            // no-op
        }
    }

    public String predict(String query){
        Log.v(TAG, "Convert Feature...");
        Feature feature = featureConverter.convert(query);
        Log.v(TAG, "Set inputs...");
        int[][] inputIds = new int[1][MAX_SEQ_LEN];
        int[][] inputMask = new int[1][MAX_SEQ_LEN];
        for (int j = 0; j < MAX_SEQ_LEN; j++) {
            inputIds[0][j] = feature.inputIds[j];
            inputMask[0][j] = feature.inputMask[j];
        }

        TransferLearningModel.Prediction res;

        try {
            Object[] inputs = {inputIds, inputMask};
            TransferLearningModel.Prediction[] predictions = this.tlModel.predict(inputs);
            Log.e(TAG, predictions[0].getClassName() + " " + predictions[0].getConfidence());
            res = predictions[0];
        } catch (Exception e) {
            throw new RuntimeException("Failed to add sample to model", e.getCause());
        }

        return res.getClassName();

    }

    public String get_class(int labelId) {
        if (labelId == 0){
            return "ham";
        }
        return "spam";
    }

    /**
     * Normalizes a camera image to [0; 1], cropping it
     * to size expected by the model and adjusting for camera rotation.
     */
    private static float[] prepareImage(Bitmap bitmap)  {
        int modelImageSize = TransferLearningModelWrapper.IMAGE_SIZE;

        float[] normalizedRgb = new float[modelImageSize * modelImageSize * 3];
        int nextIdx = 0;
        for (int y = 0; y < modelImageSize; y++) {
            for (int x = 0; x < modelImageSize; x++) {
                int rgb = bitmap.getPixel(x, y);

                float r = ((rgb >> 16) & LOWER_BYTE_MASK) * (1 / 255.0f);
                float g = ((rgb >> 8) & LOWER_BYTE_MASK) * (1 / 255.0f);
                float b = (rgb & LOWER_BYTE_MASK) * (1 / 255.0f);

                normalizedRgb[nextIdx++] = r;
                normalizedRgb[nextIdx++] = g;
                normalizedRgb[nextIdx++] = b;
            }
        }

        return normalizedRgb;
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
        dic.clear();
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
}