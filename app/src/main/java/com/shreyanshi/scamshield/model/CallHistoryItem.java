package com.shreyanshi.scamshield.model;

public class CallHistoryItem {

    public String number;
    public String dateTime;
    public String status;   // Scam / Safe
    public String reason;

    public CallHistoryItem(String number, String dateTime,
                           String status, String reason) {
        this.number = number;
        this.dateTime = dateTime;
        this.status = status;
        this.reason = reason;
    }
}
