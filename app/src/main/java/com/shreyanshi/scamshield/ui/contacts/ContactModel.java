package com.shreyanshi.scamshield.ui.contacts;

public class ContactModel {

    private String name;
    private String number;
    private boolean isFavorite;

    public ContactModel(String name, String number) {
        this.name = name;
        this.number = number;
        this.isFavorite = false;
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
}
