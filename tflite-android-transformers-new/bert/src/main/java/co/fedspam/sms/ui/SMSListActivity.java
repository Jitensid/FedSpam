package co.fedspam.sms.ui;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Date;

import co.fedspam.android_transformers.R;
import co.fedspam.sms.smsReader.DBHandler;

public class SMSListActivity extends AppCompatActivity {

    private DBHandler dbHandler = new DBHandler(this);

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

        for(SMSData sms: smsArrayList){
            // check if this message already exists in data set
            boolean isPresent = dbHandler.isPresent(sms.get_id());
            if (isPresent) continue;
//            Log.e("SMSListActivity", sms.get_id() + "|" + sms.getSenderName() + "|" + sms.getMessage());
            // add sms into db
            dbHandler.addNewSMS(sms.get_id(), sms.getSenderName(), sms.getMessage(),"Dummy", new Date(sms.getTimeSent()).toLocaleString());
        }


        ListAdapter listAdapter = new ListAdapter(SMSListActivity.this, smsArrayList);
        ListView listView = findViewById(R.id.list_view);

        listView.setAdapter(listAdapter);
        listView.setClickable(true);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                Intent i = new Intent(SMSListActivity.this, SMSDetailsActivity.class);
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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main_activity);

//        String[][] _data = {
//                {"Rudresh" , "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.", "7:58 PM"},
//                {"Rudresh" , "Lorem Ipsum is simply dummy text of the printing and typesetting industry. Lorem Ipsum has been the industry's standard dummy text ever since the 1500s, when an unknown printer took a galley of type and scrambled it to make a type specimen book. It has survived not only five centuries, but also the leap into electronic typesetting, remaining essentially unchanged. It was popularised in the 1960s with the release of Letraset sheets containing Lorem Ipsum passages, and more recently with desktop publishing software like Aldus PageMaker including versions of Lorem Ipsum.", "7:58 PM"}
//        };

        // opening a new activity via a intent.
       refreshData();

    }
}
