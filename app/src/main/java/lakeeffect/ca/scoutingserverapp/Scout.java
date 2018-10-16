package lakeeffect.ca.scoutingserverapp;

import java.util.ArrayList;

/**
 * Used when the scouting schedule is calculated
 */
public class Scout {
    int id;
    String name;

    //the match this scout started being off
    int timeOff = 0;

    //the match this scout started being on
    int timeOn = 0;

    //matches started on (multiple if they join, then leave, then join)
    ArrayList<Integer> startMatches = new ArrayList<>();

    //matches this scout left (multiple if they join, then leave, then join)
    //inclusive
    ArrayList<Integer> lastMatches = new ArrayList<>();

    //if this just had an undo applied, used to ignore onCheckChange listeners
    boolean undo;

    public Scout(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Scout(String name, int startMatch) {
        this.name = name;

        //make sure startMatch is not negative
        if (startMatch < 0) {
            startMatch = 0;
        }
        startMatches.add(startMatch - 1);
    }

    public Scout(String name) {
        this.name = name;
    }

    //gets lowest start match
    public int getLowestStartMatch() {
        int lowestStartMatch = -1;
        for (int startMatch : startMatches) {
            if (startMatch < lowestStartMatch || lowestStartMatch == -1) {
                lowestStartMatch = startMatch;
            }
        }

        return lowestStartMatch;
    }

    //does this scout exist at this match
    public boolean existsAtMatch(int matchNum) {
        //they were never selected if this is true
        if (startMatches.size() == 0) {
            return false;
        }

        for (int i = 0; i < startMatches.size(); i++) {
            if (startMatches.get(i) <= matchNum) {
                //has it been closed since then
                if (lastMatches.size() <= i || lastMatches.get(i) > matchNum) {
                    return true;
                }
            }
        }

        return false;
    }
}
