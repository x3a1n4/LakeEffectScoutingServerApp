package lakeeffect.ca.scoutingserverapp;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import java.util.ArrayList;

public class TimeOff {

    ArrayList<Integer> targetTimeOff = new ArrayList<>();

    //the matches that the new target time off starts
    ArrayList<Integer> switchMatches = new ArrayList<>();

    //returns the time off at a specified match number
    public int getTimeOff(final int matchNum) {
        for (int i = 0; i < switchMatches.size(); i++) {
            if (matchNum >= switchMatches.get(i) && (switchMatches.size() == i + 1 || matchNum < switchMatches.get(i + 1))) {
                //this is the index of the time off for this match
                return targetTimeOff.get(i);
            }
        }

        return targetTimeOff.get(0);
    }
}
