package com.shreyanshi.scamshield.ui.history;

public class CallLogModel {

    private String phoneNumber;
    private String dateTime;
    private boolean isScam;
    private boolean isBlocked;

    public CallLogModel(String phoneNumber, String dateTime, boolean isScam) {
        this.phoneNumber = phoneNumber;
        this.dateTime = dateTime;
        this.isScam = isScam;
        this.isBlocked = false;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getDateTime() {
        return dateTime;
    }

    public boolean isScam() {
        return isScam;
    }

    public boolean isBlocked() {
        return isBlocked;
    }

    public void toggleBlocked() {
        isBlocked = !isBlocked;
    }
}
