package lakeeffect.ca.scoutingserverapp;

import android.view.View;

/**
 * An action that happens (someone adds a scout, changes properties, etc.)
 *
 * This is used to undo that action. The actions are stored in a list to be recalled upon if an undo is necessary
 */
public class Action {
    //0: start match added
    //1: last match added
    //2: added scout
    //3: removed scout
    int type;

    //the scout that this happened to
    Scout scout;

    //the view that was modified, added or removed
    View view;

    public Action(int type, Scout scout, View view) {
        this.type = type;
        this.scout = scout;
        this.view = view;
    }
}
