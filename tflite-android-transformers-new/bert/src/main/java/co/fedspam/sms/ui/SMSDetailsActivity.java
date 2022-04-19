package co.fedspam.sms.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import co.fedspam.android_transformers.R;
import co.fedspam.sms.smsReader.DBHandler;

public class SMSDetailsActivity  extends AppCompatActivity {

    DBHandler dbHandler = new DBHandler(this);
    private String label;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.sms_details_activity);
        // Get the Intent that started this activity and extract the string


        Intent intent = getIntent();
        int id = intent.getIntExtra("id", -1);
        String sender = intent.getStringExtra("sender");
        String message = intent.getStringExtra("message");
        long timestamp = intent.getLongExtra("timestamp", 0);
        label = dbHandler.getLabel(id); // intent.getStringExtra("label");
        boolean flag = intent.getBooleanExtra("flag", false);

        if (id == -1){
            throw new RuntimeException("Got Invalid Id for " + message);
        }

        // Capture the layout's TextView and set the string as its text
        TextView senderView = findViewById(R.id.sender_name);
        TextView messageBody = findViewById(R.id.message_body);
        TextView predictionView = findViewById(R.id.prediction);
        Button reportSpam = findViewById(R.id.report_spam);
        Button reportHam = findViewById(R.id.report_ham);

        // update db
        reportHam.setOnClickListener(view -> {
            dbHandler.updateSMSLabel(id, "ham");
            label = "ham";
            reload();
        });
        reportSpam.setOnClickListener(view -> {
            dbHandler.updateSMSLabel(id, "spam");
            label = "spam";
            reload();
        });

        senderView.setText(sender);
        messageBody.setText(message);
        predictionView.setText(label + " [" + id + ", " + flag + "]");
    }

    private void reload() {
        finish();
        overridePendingTransition(0, 0);
        startActivity(getIntent());
        overridePendingTransition(0, 0);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}
