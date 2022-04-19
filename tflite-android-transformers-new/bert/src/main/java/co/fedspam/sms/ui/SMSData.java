package co.fedspam.sms.ui;

public class SMSData {
    private int _id;
    private String senderName;
    private long timeSent;
    private String message;

    public SMSData(int id, String senderName, String message, long timeSent) {
        this._id = id;
        this.senderName = senderName;
        this.message = message;
        this.timeSent = timeSent;
    }

    public SMSData() {
    }

    public int get_id() {
        return _id;
    }

    public void set_id(int _id) {
        this._id = _id;
    }

    public String getSenderName() {
        return senderName;
    }

    public void setSenderName(String senderName) {
        this.senderName = senderName;
    }

    public long getTimeSent() {
        return timeSent;
    }

    public void setTimeSent(long timeSent) {
        this.timeSent = timeSent;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
