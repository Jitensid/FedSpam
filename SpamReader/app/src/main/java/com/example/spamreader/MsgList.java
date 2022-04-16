package com.example.spamreader;

public class MsgList {
    private String message;
    private String label;

    MsgList(String message, String label) {
        this.message = message;
        this.label = label;
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
