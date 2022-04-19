package co.fedspam.sms.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Date;

import co.fedspam.android_transformers.R;

public class ListAdapter extends ArrayAdapter<SMSData> {


    public ListAdapter(@NonNull Context context, ArrayList<SMSData> smsDataList) {
        super(context, R.layout.sms_list_item, smsDataList);
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        SMSData smsData = getItem(position);

        if (convertView == null){
            convertView = LayoutInflater.from(getContext()).
                    inflate(
                            R.layout.sms_list_item, parent,false
                    );
        }
        TextView senderView = convertView.findViewById(R.id.sender_name);
        TextView timestampView = convertView.findViewById(R.id.timestamp);
        TextView messageBodyView = convertView.findViewById(R.id.message_body);

        senderView.setText(smsData.getSenderName());
        timestampView.setText(new Date(smsData.getTimeSent()).toLocaleString());
        messageBodyView.setText(String.valueOf(smsData.getMessage()));

        return  convertView;
    }
}
