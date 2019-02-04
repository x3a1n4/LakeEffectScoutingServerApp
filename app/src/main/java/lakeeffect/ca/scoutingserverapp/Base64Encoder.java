package lakeeffect.ca.scoutingserverapp;

import android.util.Base64;

import java.nio.charset.Charset;

public class Base64Encoder {
    public String decode(String encodedString){
        try{
            String returnString = new String(Base64.decode(encodedString, Base64.DEFAULT), Charset.forName("UTF-8"));
            return returnString;
        } catch (IllegalArgumentException e){
            return "";
        }
    }
}
