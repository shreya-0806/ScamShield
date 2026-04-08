package com.shreyanshi.scamshield.ui.contacts;

public class ContactModel {

    private String name;
    private String number;
    private boolean isFavorite;
    private boolean isBlocked;

    public ContactModel(String name, String number) {
        this.name = name;
        this.number = number;
        this.isFavorite = false;
        this.isBlocked = false;
    }

    public String getName() {
        return name;
    }

    public String getNumber() {
        return number;
    }

    public boolean isFavorite() {
        return isFavorite;
    }

    public void setFavorite(boolean favorite) {
        isFavorite = favorite;
    }
    
    public boolean isBlocked() {
        return isBlocked;
    }
    
    public void setBlocked(boolean blocked) {
        isBlocked = blocked;
    }
}
