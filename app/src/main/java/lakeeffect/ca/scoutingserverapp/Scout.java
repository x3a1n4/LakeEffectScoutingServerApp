package lakeeffect.ca.scoutingserverapp;

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

    //match started on
    int startMatch = 0;

    public Scout(int id, String name) {
        this.id = id;
        this.name = name;
    }

    public Scout(String name, int startMatch) {
        this.name = name;
        this.startMatch = startMatch - 1;
        if (this.startMatch < 0) {
            this.startMatch = 0;
        }
    }
}
