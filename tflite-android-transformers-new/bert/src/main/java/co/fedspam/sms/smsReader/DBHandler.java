package co.fedspam.sms.smsReader;


import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.util.ArrayList;

public class DBHandler extends SQLiteOpenHelper {

    // creating a constant variables for our database.
    // below variable is for our database name.
    private static final String DB_NAME = "SMSDB";

    // below int is our database version
    private static final int DB_VERSION = 1;

    // below variable is for our table name.
    private static final String TABLE_NAME = "messages";

    // below variable is for our message column
    private static final String MSG_COL = "message";

    // below variable is for our label column
    private static final String LABEL_COL = "label";

    private static final String SENDER_COL = "sender";
    private static final String TIMESTAMP_COL = "timestamp";
    private static final String FLAG_COL = "already_trained";
    private static final String ID_COL = "id";

    // creating a constructor for our database handler.
    public DBHandler(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    // below method is for creating a database by running a sqlite query
    @Override
    public void onCreate(SQLiteDatabase db) {
        // on below line we are creating
        // an sqlite query and we are
        // setting our column names
        // along with their data types.
        String query = "CREATE TABLE " + TABLE_NAME + " (" + ID_COL + " INTEGER," + SENDER_COL + " TEXT,"
                + MSG_COL + " TEXT,"
                + LABEL_COL + " TEXT," + TIMESTAMP_COL + " TEXT," + FLAG_COL + " TEXT)";

        // at last we are calling a exec sql
        // method to execute above sql query
        db.execSQL(query);
    }

    // this method is use to add new course to our sqlite database.
    public void addNewSMS(int id, String sender, String message, String label, String timestamp) {

        // on below line we are creating a variable for
        // our sqlite database and calling writable method
        // as we are writing data in our database.
        SQLiteDatabase db = this.getWritableDatabase();

        // on below line we are creating a
        // variable for content values.
        ContentValues values = new ContentValues();

        // on below line we are passing all values
        // along with its key and value pair.
        values.put(ID_COL, id);
        values.put(SENDER_COL, sender);
        values.put(MSG_COL, message);
        values.put(LABEL_COL, label);
        values.put(TIMESTAMP_COL, timestamp);
        values.put(FLAG_COL, "false"); // default False

        // after adding all values we are passing
        // content values to our table.
        db.insert(TABLE_NAME, null, values);

        // at last we are closing our
        // database after adding database.
        db.close();
    }

    public void updateSMSFlag(int id, boolean flag) {

        // calling a method to get writable database.
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // on below line we are passing all values
        // along with its key and value pair.
        values.put(FLAG_COL, String.valueOf(flag));

        // on below line we are calling a update method to update our database and passing our values.
        // and we are comparing it with name of our course which is stored in original name variable.
        db.update(TABLE_NAME, values, ID_COL + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public boolean isPresent(int id){
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursorMessages = db.rawQuery(
                String.format("SELECT EXISTS(SELECT 1 FROM %s WHERE %s=%s)", TABLE_NAME, ID_COL, id), null
        );
        cursorMessages.moveToFirst();
        int res = Integer.parseInt(cursorMessages.getString(0));
        Log.e("DBHandler", res + " " + (res != 0));
        return (res != 0);
    }

    public String getLabel(int id) {
        SQLiteDatabase db = this.getReadableDatabase();
        Cursor cursorMessages = db.rawQuery(String.format("SELECT %s FROM %s WHERE %s = %d", LABEL_COL, TABLE_NAME, ID_COL, id), null);
        cursorMessages.moveToFirst();
        return cursorMessages.getString(0);
    }

    public void updateSMSLabel(int id, String label) {

        // calling a method to get writable database.
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues values = new ContentValues();

        // on below line we are passing all values
        // along with its key and value pair.
        values.put(LABEL_COL, label);

        // on below line we are calling a update method to update our database and passing our values.
        // and we are comparing it with name of our course which is stored in original name variable.
        db.update(TABLE_NAME, values, ID_COL + "=?", new String[]{String.valueOf(id)});
        db.close();
    }

    public ArrayList<MessageList> getTrainData(int count) {
        // on below line we are creating a
        // database for reading our database.
        SQLiteDatabase db = this.getReadableDatabase();

        // on below line we are creating a cursor with query to read data from database.
        //
        Cursor cursorMessages = db.rawQuery(String.format("SELECT * FROM %s WHERE %s = false LIMIT %d", TABLE_NAME, FLAG_COL, count), null);

        // on below line we are creating a new array list.
        ArrayList<MessageList> messageArrayList = new ArrayList<>();
        // moving our cursor to first position.
        if (cursorMessages.moveToFirst()) {
            do {
                // on below line we are adding the data from cursor to our array list.
                messageArrayList.add(new MessageList(
                        Integer.parseInt(cursorMessages.getString(0)),
                        cursorMessages.getString(1),
                        cursorMessages.getString(2),
                        cursorMessages.getString(3),
                        cursorMessages.getString(4),
                        Boolean.parseBoolean(cursorMessages.getString(5))
                ));
            } while (cursorMessages.moveToNext());
            // moving our cursor to next.
        }
        // at last closing our cursor
        // and returning our array list.
        cursorMessages.close();
        return messageArrayList;
    }

    public ArrayList<MessageList> readMessages() {
        // on below line we are creating a
        // database for reading our database.
        SQLiteDatabase db = this.getReadableDatabase();

        // on below line we are creating a cursor with query to read data from database.
        Cursor cursorMessages = db.rawQuery("SELECT * FROM " + TABLE_NAME, null);

        // on below line we are creating a new array list.
        ArrayList<MessageList> messageArrayList = new ArrayList<>();
        // moving our cursor to first position.
        if (cursorMessages.moveToFirst()) {
            do {
                // on below line we are adding the data from cursor to our array list.
                messageArrayList.add(new MessageList(
                        Integer.parseInt(cursorMessages.getString(0)),
                        cursorMessages.getString(1),
                        cursorMessages.getString(2),
                        cursorMessages.getString(3),
                        cursorMessages.getString(4),
                        Boolean.parseBoolean(cursorMessages.getString(5))
                ));
            } while (cursorMessages.moveToNext());
            // moving our cursor to next.
        }
        // at last closing our cursor
        // and returning our array list.
        cursorMessages.close();
        return messageArrayList;
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // this method is called to check if the table exists already.
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
        onCreate(db);
    }
}


