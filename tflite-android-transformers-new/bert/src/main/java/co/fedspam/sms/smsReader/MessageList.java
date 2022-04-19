package co.fedspam.sms.smsReader;

public class MessageList {
    private int id;
    private String sender;
    private String message;
    private String label;
    private String timestamp;
    private boolean flag;

    public MessageList(int id, String sender, String message, String label, String timestamp, boolean flag) {
        this.id = id;
        this.sender = sender;
        this.message = message;
        this.label = label;
        this.timestamp = timestamp;
        this.flag = flag;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }
}
