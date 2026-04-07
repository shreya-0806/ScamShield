package com.shreyanshi.scamshield.ui.history;

public class CallLogModel {
    public static final int TYPE_INCOMING = 1;
    public static final int TYPE_OUTGOING = 2;
    public static final int TYPE_MISSED = 3;

    private String name;
    private String number;
    private String dateTime;
    private int callType;
    private boolean isScam;

    public CallLogModel(String name, String number, String dateTime, int callType) {
        this.name = name;
        this.number = number;
        this.dateTime = dateTime;
        this.callType = callType;
        this.isScam = false;
    }

    public CallLogModel(String number, String dateTime, boolean isScam) {
        this.name = number;
        this.number = number;
        this.dateTime = dateTime;
        this.callType = TYPE_INCOMING;
        this.isScam = isScam;
    }

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public String getDateTime() {
        return dateTime;
    }

    public int getCallType() {
        return callType;
    }

    public boolean isScam() {
        return isScam;
    }

    public void setScam(boolean scam) {
        isScam = scam;
    }
}
