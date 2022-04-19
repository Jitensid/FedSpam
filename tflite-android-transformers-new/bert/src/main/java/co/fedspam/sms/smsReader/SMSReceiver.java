package co.fedspam.sms.smsReader;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;
import android.widget.Toast;

public class SMSReceiver extends BroadcastReceiver {
    private static final String SMS_RECEIVED="android.provider.Telephony.SMS_RECEIVED";
    private static final String TAG="SmaBroadcastReceiver";
    protected String msg="";
    protected String phoneNo="";
    protected int id = -1;
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG,"Intent Received"+intent.getAction());
        if(intent.getAction() == SMS_RECEIVED)
        {
            Bundle dataBundle = intent.getExtras();
            if(dataBundle!=null)
            {
                //create PDU
                Object[] mypdu = (Object[])dataBundle.get("pdus");
                final SmsMessage[] message= new SmsMessage[mypdu.length];

                for(int i=0;i< mypdu.length;i++)
                {
                    if(Build.VERSION.SDK_INT>Build.VERSION_CODES.M)
                    {
                        String format = dataBundle.getString("format");

                        message[i] = SmsMessage.createFromPdu((byte[])mypdu[i], format);
                    }
                    else
                    {
                        message[i] = SmsMessage.createFromPdu((byte[]) mypdu[i]);
                    }
                    msg = message[i].getMessageBody();
                    phoneNo = message[i].getOriginatingAddress();
                    id = message[i].getIndexOnIcc();
                }
                Log.e(TAG, id + " | " + phoneNo + " | " + msg);
                Toast.makeText(context,"Message : "+msg+"\n"+"Number:"+phoneNo+"\n"+"Class:"+"\n",Toast.LENGTH_LONG).show();
            }
        }
    }}
