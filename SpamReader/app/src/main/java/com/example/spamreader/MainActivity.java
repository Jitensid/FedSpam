package com.example.spamreader;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private static final int MY_PERMISSIONS_REQUEST_RECEIVE_SMS = 0;
    private static final String SMS_RECEIVED="android.provider.Telephony.SMS_RECEIVED";
    private DBHandler dbHandler = new DBHandler(MainActivity.this);;
    TextView messageTV,numberTV,msgClassTV;
    Button getButton;
    MyReceiver receiver = new MyReceiver(){
        @Override
        public void onReceive(Context context, Intent intent)
        {
            super.onReceive(context,intent);
            dbHandler.addNewSMS(msg,msgClass);
            messageTV.setText(msg);
            numberTV.setText(phoneNo);
            msgClassTV.setText(msgClass);
        }
    };

    @Override
    protected void onResume()
    {
        super.onResume();
        registerReceiver(receiver,new IntentFilter(SMS_RECEIVED));
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
        setContentView(R.layout.activity_main);
        messageTV = findViewById(R.id.message);
        numberTV = findViewById(R.id.number);
        msgClassTV = findViewById(R.id.msgClass);
        getButton = findViewById(R.id.getButton);

        getButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // opening a new activity via a intent.
                ArrayList<MsgList> msglist = dbHandler.readMessages();
                for(int i=0;i<msglist.size();i++)
                {
                    MsgList msg = msglist.get(i);
                    String message = msg.getMessage();
                    String label = msg.getLabel();
                    Log.i(message,message);
                    Log.i(label,label);
                }
            }
        });

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
    }

    //after getting permission
    @Override
    public void onRequestPermissionsResult(int requestcode, String permissions[], int[] grantResults)
    {
        super.onRequestPermissionsResult(requestcode,permissions,grantResults);
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
        }
    }
}