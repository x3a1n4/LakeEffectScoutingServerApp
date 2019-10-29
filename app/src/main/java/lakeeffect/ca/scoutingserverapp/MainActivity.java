package lakeeffect.ca.scoutingserverapp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;

    ArrayList<String> deviceNames = new ArrayList<>();

    Button connect;
    Button visibility;
    TextView status;
    EditText versionNumEditText;
    EditText timeOffEditText;
    Button timeOffSet;
    EditText timeOffMatchNumEditText;
    Button viewSchedule;

    Button scheduleUrl;
    int minVersionNum;
    TimeOff targetTimeOff;

    String labels = null; //Retrieved one time per session during the first pull

    ArrayList<String> uuids = new ArrayList<>();

    ArrayList<PullDataThread> pullDataThreads = new ArrayList<>();

    //data for the devices added to the list
    ArrayList<BluetoothDevice> devicesSelected = new ArrayList<>();
    ArrayList<View> devicesSelectedView = new ArrayList<>();

    //The format for time when displayed
    SimpleDateFormat timeFormat = new SimpleDateFormat("h:mm");
    Calendar cal = Calendar.getInstance();

    //the robots schedules
    ArrayList<ArrayList<Integer>> robotSchedule = new ArrayList<>();

    //the list of all scouts
    ArrayList<Scout> allScouts = new ArrayList<>();
    //for each selected name, there is an array of assigned robots (0 - 5 per match, -1 being a break)
    ArrayList<int[]> assignedRobots = new ArrayList<>();

    //the last actions that have happened, used to undo actions is necessary
    ArrayList<Action> pastActions = new ArrayList<>();
    final int PAST_ACTIONS_MAX = 25;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Intent turnOn = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(turnOn, 0);

        connect = ((Button) findViewById(R.id.connect));
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openPullDataDialog();
            }
        });

        visibility = ((Button) findViewById(R.id.visibility));
        visibility.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
                intent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 0);
                startActivityForResult(intent, 3);
            }
        });

        status = ((TextView) findViewById(R.id.status));
        versionNumEditText = ((EditText) findViewById(R.id.versionNum));
        versionNumEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                if (versionNumEditText.getText().toString().equals("")) return;

                minVersionNum = Integer.parseInt(versionNumEditText.getText().toString());

                SharedPreferences sharedPreferences = getSharedPreferences("minVersionNum", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("minVersionNum", minVersionNum);
                editor.apply();
            }
        });
        SharedPreferences sharedPreferences = getSharedPreferences("minVersionNum", MODE_PRIVATE);
        minVersionNum = sharedPreferences.getInt("minVersionNum", 0);
        versionNumEditText.setText(minVersionNum + "");

        //load schedule into memory
        try {
            readSchedule();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //load schedule when button is pressed.
        findViewById(R.id.reloadSchedule).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {
                    readSchedule();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        //setup time off text view
        timeOffEditText = ((EditText) findViewById(R.id.timeOff));
        timeOffMatchNumEditText = ((EditText) findViewById(R.id.timeOffMatchNum));
        timeOffSet = ((Button) findViewById(R.id.timeOffSetButton));
        timeOffSet.setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                if (timeOffEditText.getText().toString().equals("")) return;
                if (timeOffMatchNumEditText.getText().toString().equals("")){
                    runOnUiThread(new Thread(){
                        public void run() {
                            Toast.makeText(MainActivity.this, "You need to specify a match number for this to happen at (can be 1)", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                int newTimeOff = Integer.parseInt(timeOffEditText.getText().toString());
                int switchMatch = Integer.parseInt(timeOffMatchNumEditText.getText().toString()) - 1;

                targetTimeOff.targetTimeOff.add(newTimeOff);
                targetTimeOff.switchMatches.add(switchMatch);

                SharedPreferences sharedPreferences = getSharedPreferences("targetTimeOff", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("targetTimeOff" + (targetTimeOff.targetTimeOff.size() - 1), newTimeOff);
                editor.putInt("switchMatches" + (targetTimeOff.switchMatches.size() - 1), switchMatch);
                editor.putInt("targetTimeOffSize", targetTimeOff.targetTimeOff.size());
                editor.putInt("switchMatchesSize", targetTimeOff.switchMatches.size());
                editor.apply();

                //update schedule
                final String success = createSchedule();
                if (success != null) {
                    runOnUiThread(new Thread(){
                        public void run() {
                            Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                        }
                    });
                }

                runOnUiThread(new Thread(){
                    public void run() {
                        Toast.makeText(MainActivity.this, "Added new time off", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });
        sharedPreferences = getSharedPreferences("targetTimeOff", MODE_PRIVATE);
        targetTimeOff = new TimeOff();
        int targetTimeOffSize = sharedPreferences.getInt("targetTimeOffSize", 1);
        for (int i = 0; i < targetTimeOffSize; i++) {
            targetTimeOff.targetTimeOff.add(sharedPreferences.getInt("targetTimeOff" + i, 2));
        }
        int switchMatchesSize = sharedPreferences.getInt("switchMatchesSize", 1);
        for (int i = 0; i < switchMatchesSize; i++) {
            targetTimeOff.switchMatches.add(sharedPreferences.getInt("switchMatches" + i, 0));
        }

        //set text view to current target time off
        timeOffEditText.setText(targetTimeOff.getTimeOff(robotSchedule.size() - 1) + "");

        //add click listener for pull all
        findViewById(R.id.pullAll).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < devicesSelected.size(); i++){
                    pullFromDevice(devicesSelected.get(i), devicesSelectedView.get(i));
                }
            }
        });

        //add click listener for add user
        findViewById(R.id.addUser).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openNameEditor();
            }
        });

        viewSchedule = (Button) findViewById(R.id.viewSchedule);
        //add click listener for view schedule
        viewSchedule.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openScheduleViewer();
            }
        });

        scheduleUrl = (Button) findViewById(R.id.scheduleUrl);
        scheduleUrl.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openScheduleWindow();
            }
        });

        //load UUIDs of all data collected to make sure there are no duplicates
        ArrayList<String> data = new ArrayList<>();
        try {
            data = readData();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(String line: data){
            uuids.add(getUUIDFromData(line, false));
        }

        //load names
        SharedPreferences namesPreferences = getSharedPreferences("names", MODE_PRIVATE);
        String selectedNamesText = namesPreferences.getString("allScouts", "");
        if (!selectedNamesText.equals("")) {
            String[] selectedNamesArray = selectedNamesText.split(",");

            for (int i = 0; i < selectedNamesArray.length; i++) {
                Scout scout = new Scout(selectedNamesArray[i]);
                allScouts.add(scout);
            }
        }

        String selectedNameStartMatchesText = namesPreferences.getString("selectedNameStartMatches", "");
        if (!selectedNameStartMatchesText.equals("")) {
            String[] selectedNameStartMatchesArray = selectedNameStartMatchesText.split(",");

            for (int i = 0; i < selectedNameStartMatchesArray.length; i++) {
                //add all start matches
                String[] scoutStartMatchesArray = selectedNameStartMatchesArray[i].split(";");
                for (int s = 0; s < scoutStartMatchesArray.length; s++) {
                    allScouts.get(i).startMatches.add(Integer.parseInt(scoutStartMatchesArray[s]));
                }
            }
        }

        String selectedNameLastMatchesText = namesPreferences.getString("selectedNameLastMatches", "");
        if (!selectedNameLastMatchesText.equals("")) {
            String[] selectedNameLastMatchesArray = selectedNameLastMatchesText.split(",");

            for (int i = 0; i < selectedNameLastMatchesArray.length; i++) {
                if (!selectedNameLastMatchesArray[i].equals("")) {
                    //add all last matches
                    String[] scoutLastMatchesArray = selectedNameLastMatchesArray[i].split(";");
                    for (int s = 0; s < scoutLastMatchesArray.length; s++) {
                        allScouts.get(i).lastMatches.add(Integer.parseInt(scoutLastMatchesArray[s]));
                    }
                }
            }
        }

        //load devices
        SharedPreferences devicesPreferences = getSharedPreferences("devices", MODE_PRIVATE);
        String deviceText = devicesPreferences.getString("selectedDevices", "");
        if (!deviceText.equals("")) {
            String[] deviceArray = deviceText.split(",");

            for (int i = 0; i < deviceArray.length; i++) {
                BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(deviceArray[i]);

                addSelectedDevice(bluetoothDevice);
            }
        }

        //create the schedule out of the currently selected names
        final String success = createSchedule();
        if (success != null) {
            runOnUiThread(new Thread(){
                public void run() {
                    Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    /**
     * Opens the dialog that chooses which device to pull data from
     */
    public void openPullDataDialog(){
        Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();

        final BluetoothDevice[] devices = pairedDevices.toArray(new BluetoothDevice[0]);

        String[] names = new String[devices.length];
        if (pairedDevices.size() > 0) {
            for (int i = 0; i < devices.length; i++) {
                names[i] = devices[i].getName();
            }
        }

        new AlertDialog.Builder(MainActivity.this)
        .setTitle("Which device would you like to add?")
        .setMultiChoiceItems(names, null, null)
        .setPositiveButton("Add Devices", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SparseBooleanArray checked = ((AlertDialog) dialog).getListView().getCheckedItemPositions();

                for (int i = 0; i < ((AlertDialog) dialog).getListView().getCount(); i++) {
                    if(checked.get(i)) {
                        addSelectedDevice(devices[i]);
                    }
                }

                //convert all devices into a csv of addresses
                String devicesString = "";

                for (int i = 0; i < devicesSelected.size(); i++){
                    //add it to the list
                    devicesString += devicesSelected.get(i).getAddress();
                    if (i != devicesSelected.size() - 1){
                        devicesString += ",";
                    }
                }

                //save this data in shared preferences
                SharedPreferences sharedPreferences = getSharedPreferences("devices", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("selectedDevices", devicesString);
                editor.apply();

                runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(MainActivity.this, "Updated list", Toast.LENGTH_LONG).show();
                    }
                });
            }


        })
        .create()
        .show();
    }

    //Add one device to the selected device list
    public void addSelectedDevice(final BluetoothDevice device) {
        devicesSelected.add(device);

        final LinearLayout linearLayout = (LinearLayout) findViewById(R.id.devicesMenuLinearLayout);

        final View newDeviceMenu = LayoutInflater.from(this).inflate(R.layout.device_name, null);

        devicesSelectedView.add(newDeviceMenu);

        //set onClick listeners
        newDeviceMenu.findViewById(R.id.pull).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                pullFromDevice(device, newDeviceMenu);
            }
        });
        newDeviceMenu.findViewById(R.id.remove).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                linearLayout.removeView(newDeviceMenu);

                devicesSelected.remove(device);

                //update the shared preferences with the new list of devices
                //convert all devices into a csv of addresses
                String devicesString = "";

                for (int i = 0; i < devicesSelected.size(); i++){
                    //add it to the list
                    devicesString += devicesSelected.get(i).getAddress();
                    if (i != devicesSelected.size() - 1){
                        devicesString += ",";
                    }
                }

                //save this data in shared preferences
                SharedPreferences sharedPreferences = getSharedPreferences("devices", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putString("selectedDevices", devicesString);
                editor.apply();

                runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(MainActivity.this, "Removed " + device.getName(), Toast.LENGTH_SHORT).show();
                    }
                });
            }
        });

        //set text
        ((TextView) newDeviceMenu.findViewById(R.id.deviceName)).setText(device.getName() + " (Never pulled)");

        linearLayout.addView(newDeviceMenu);
    }

    //Pull data from the specified device and send data about schedule
    public void pullFromDevice(BluetoothDevice device, View deviceMenu) {
        pullDataThreads.add(new PullDataThread(MainActivity.this, device, deviceMenu));

        if(pullDataThreads.size() <= 1) {
            pullDataThreads.get(0).start();
        }
    }

    //reads the schedule code and will save the robot schedule for you
    //store schedule in /#ScoutingSchedule/
    //schedule is a csv with 6 robots on each line
    public void readSchedule() throws IOException {
        File sdCard = Environment.getExternalStorageDirectory();

        File[] files = new File(sdCard.getPath() + "/#ScoutingSchedule/").listFiles();

        //there is no schedule
        if(files == null) {
            Toast.makeText(this, "There is no schedule", Toast.LENGTH_LONG).show();
            return;
        }

        for(int i = 0; i < files.length; i++) {
            if(files[i].isDirectory()) continue;

            BufferedReader br = new BufferedReader(new FileReader(files[i]));
            String line;

            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                String[] robots = line.split(",");

                ArrayList<Integer> robotNumbers = new ArrayList<>();

                for (int s = 0; s < robots.length; s++) {
                    robotNumbers.add(Integer.parseInt(robots[s]));
                }

                robotSchedule.add(robotNumbers);
                lineNum ++;
            }
            br.close();
        }
    }

    //creates the schedule based on the selected usernames
    //returns null if successful, and error message if not
    public String createSchedule() {
        if (getSelectedAmount() < 6) {
            return "You must select at least 6 scouts";
        }

        //scouts currently scouting or not
        Scout[] scoutsOn = new Scout[6];
        ArrayList<Scout> scoutsOff = new ArrayList<>();

        //set assigned robots to correct size
        assignedRobots = new ArrayList<>();
        for (int i = 0; i < allScouts.size(); i++) {
            assignedRobots.add(new int[robotSchedule.size()]);
        }

        //reset assigned robots
        for (int i = 0; i < assignedRobots.size(); i++) {
            for (int s = 0; s < assignedRobots.get(i).length; s++) {
                assignedRobots.get(i)[s] = -1;
            }
        }

        //used to determine if there are not enough ready scouts
        int nonReadyScouts = 0;
        for (int i = 0; i < scoutsOn.length; i++) {
            if (scoutsOn.length - nonReadyScouts < 6) {
                //not enough scouts
                return "You must select at least 6 ready scouts";
            }

            //if they are not starting on the first match
            if (allScouts.get(i).getLowestStartMatch() > 0) {
                Scout scout = allScouts.get(i);
                allScouts.remove(scout);
                allScouts.add(scout);
                //try again
                i--;
                //add to the non ready scouts to make sure this is not an infinite loop
                nonReadyScouts++;
                continue;
            }

            scoutsOn[i] = new Scout(i, allScouts.get(i).name);
        }

        for (int i = 6; i < allScouts.size(); i++) {
            //if they are not starting on the first match
            if (allScouts.get(i).getLowestStartMatch() > 0) {
                continue;
            }

            Scout scout = new Scout(i, allScouts.get(i).name);
            scoutsOff.add(scout);
            scout.timeOff = 0;
        }

        for (int matchNum = 0; matchNum < robotSchedule.size(); matchNum++) {
            //figure out if some selected names should be added or removed because it is now their start match or last match
            for (int i = 0; i < allScouts.size(); i++) {
                //if it is time to add this scout to the roster and they are not already added
                Scout scout = allScouts.get(i);
                boolean existsAtMatch = scout.existsAtMatch(matchNum);
                if (existsAtMatch && getScout(i, scoutsOff) == -1 && getScout(i, scoutsOn) == -1) {
                    Scout newScout = new Scout(i, scout.name);
                    scoutsOff.add(newScout);
                    newScout.timeOff = 0;
                } else if (!existsAtMatch && (getScout(i, scoutsOff) != -1 || getScout(i, scoutsOn) != -1)) {
                    int index = getScout(i, scoutsOff);
                    if (index == -1){
                        index = getScout(i, scoutsOn);

                        //check if there this scout can switch off
                        boolean canSwitchOff = false;
                        for (int s = 0; s < scoutsOff.size(); s++) {
                            if (matchNum - scoutsOff.get(s).timeOff >= targetTimeOff.getTimeOff(matchNum)) {
                                canSwitchOff = true;
                                break;
                            }
                        }

                        if (!canSwitchOff) {
                            return "Nobody is ready to switch off this match, try next match.";
                        }

                        //make sure this scout goes off next
                        scoutsOn[index].timeOn = -targetTimeOff.getTimeOff(matchNum);
                    } else {
                        scoutsOff.remove(index);
                    }
                }
            }

            //calculate the schedule for this match
            //find scouts to switch on (the scouts that have been off >= targetTimeOff)
            ArrayList<Scout> scoutsToSwitchOn = new ArrayList<>();
            for (int i = 0; i < scoutsOff.size(); i++) {
                if (matchNum - scoutsOff.get(i).timeOff >= targetTimeOff.getTimeOff(matchNum) && scoutsToSwitchOn.size() < 6) {
                    scoutsToSwitchOn.add(scoutsOff.get(i));
                } else if (matchNum - scoutsOff.get(i).timeOff >= targetTimeOff.getTimeOff(matchNum) && scoutsToSwitchOn.size() >= 6) {
                    //too many people have already been switched on, reset these scout's timeOff
                    scoutsOff.get(i).timeOff = matchNum;
                }
            }

            //find scouts to switch off (scouts with the highest time)
            ArrayList<Scout> scoutsToSwitchOff = new ArrayList<>();
            //sort by time on
            for (int i = 0; i < scoutsOn.length; i++) {
                //the index to add this scout when sorted
                int indexToAdd = scoutsToSwitchOff.size();
                for (int s = 0; s < scoutsToSwitchOff.size(); s++) {
                    if (matchNum - scoutsOn[i].timeOn > matchNum - scoutsToSwitchOff.get(s).timeOn) {
                        indexToAdd = s;
                        break;
                    }
                }

                //add at index
                scoutsToSwitchOff.add(indexToAdd, scoutsOn[i]);
            }

            //swap scouts on with scouts off
            for (int i = 0; i < scoutsToSwitchOn.size(); i++) {
                //scout switching on and off
                Scout switchingOn = scoutsToSwitchOn.get(i);
                Scout switchingOff = scoutsToSwitchOff.get(i);

                scoutsOn[getScout(switchingOff.id, scoutsOn)] = switchingOn;

                scoutsOff.remove(switchingOn);
                scoutsOff.add(switchingOff);

                //update targetTimeOff and timeOn
                switchingOn.timeOn = matchNum;
                switchingOff.timeOff = matchNum;
            }

            //set the schedule for this match
            for (int i = 0; i < scoutsOn.length; i++) {
                assignedRobots.get(scoutsOn[i].id)[matchNum] = i;
            }
            for (int i = 0; i < scoutsOff.size(); i++) {
                assignedRobots.get(scoutsOff.get(i).id)[matchNum] = -1;
            }
        }

        return null;
    }

    public int getScout(int id, ArrayList<Scout> scouts) {
        for (int i = 0; i < scouts.size(); i++) {
            if (scouts.get(i).id == id) {
                return i;
            }
        }
        return -1;
    }

    public int getScout(int id, Scout[] scouts) {
        for (int i = 0; i < scouts.length; i++) {
            if (scouts[i].id == id) {
                return i;
            }
        }
        return -1;
    }

    public int getScout(String name, ArrayList<Scout> scouts) {
        for (int i = 0; i < scouts.size(); i++) {
            if (scouts.get(i).name.equals(name)) {
                return i;
            }
        }
        return -1;
    }

    public int getSelectedAmount() {
        int selectedAmount = 0;
        for (Scout scout : allScouts) {
            if (scout.startMatches.size() > 0) {
                selectedAmount++;
            }
        }

        return selectedAmount;
    }

    //dialog box that lets you edit and deselect names
    public void openNameEditor() {

        final ScrollView fullScrollView = new ScrollView(this);

        //contains all the names plus the view to add more
        final LinearLayout fullView = new LinearLayout(this);
        fullView.setOrientation(LinearLayout.VERTICAL);

        //all the names already in the list
        final LinearLayout createdNames = new LinearLayout(this);
        createdNames.setOrientation(LinearLayout.VERTICAL);

        //create view to add a new name
        final View addName = View.inflate(this, R.layout.name_submitter, null);
        //get match number view
        final TextView currentMatchNumber = ((TextView) addName.findViewById(R.id.currentMatchNumber));

        //create the checkboxes and close buttons
        createNameSelections(createdNames, currentMatchNumber, fullScrollView);

        fullView.addView(createdNames);

        //set add name action
        addName.findViewById(R.id.nameAddButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EditText nameEditText = ((EditText) addName.findViewById(R.id.nameEditText));

                final String name = nameEditText.getText().toString();

                //reset text
                nameEditText.setText("");

                //add it to the UI
                final View view = View.inflate(MainActivity.this, R.layout.closable_checkbox, null);

                //add it to the list
                final Scout scout = new Scout(-1, name, view);
                allScouts.add(scout);

                updateNames();

                //add the action to the past actions list
                pastActions.add(new Action(2, scout, view));
                if (pastActions.size() > PAST_ACTIONS_MAX) {
                    pastActions.remove(0);
                }

                CheckBox checkBox = ((CheckBox) view.findViewById(R.id.nameCheckBox));

                checkBox.setText(name);

                //set checkbox onChange listener
                checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                    @Override
                    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                        nameClicked(buttonView, isChecked, name, currentMatchNumber);
                    }
                });

                //set close action
                view.findViewById(R.id.nameClose).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        createdNames.removeView(scout.view);
                        allScouts.remove(scout);

                        //a scout was removed
                        //add the action to the past actions list
                        pastActions.add(new Action(3, scout, view));
                        if (pastActions.size() > PAST_ACTIONS_MAX) {
                            pastActions.remove(0);
                        }

                        updateNames();
                    }
                });

                createdNames.addView(view);
            }
        });

        //setup undo button
        addName.findViewById(R.id.undoPastAction).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //create confirmation dialog
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Are you sure you would like to undo?")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            //get last action
                            Action undoAction = pastActions.get(pastActions.size() - 1);

                            switch (undoAction.type) {
                                case 0:
                                    undoAction.scout.startMatches.remove(undoAction.scout.startMatches.size() - 1);
                                    undoAction.scout.undo = true;
                                    ((CheckBox) undoAction.view).setChecked(false);
                                    break;
                                case 1:
                                    undoAction.scout.lastMatches.remove(undoAction.scout.lastMatches.size() - 1);
                                    undoAction.scout.undo = true;
                                    ((CheckBox) undoAction.view).setChecked(true);
                                    break;
                                case 2:
                                    allScouts.remove(undoAction.scout);
                                    createdNames.removeView(undoAction.view);
                                    break;
                                case 3:
                                    allScouts.add(undoAction.scout);
                                    createdNames.addView(undoAction.view);
                            }

                            //remove past action from list now
                            pastActions.remove(undoAction);
                        }
                    })
                    .show();
            }
        });

        //setup reset names button
        addName.findViewById(R.id.resetNames).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //create confirmation dialog
                new AlertDialog.Builder(MainActivity.this)
                    .setTitle("Are you sure you would like to reset the names?")
                    .setMessage("This will reset all the on and off schedule assosiated with them, you cannot undo this change.")
                    .setNegativeButton("No", null)
                    .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            for (Scout scout : allScouts) {
                                createdNames.removeView(scout.view);
                            }

                            allScouts.removeAll(allScouts);

                            updateNames();
                        }
                    })
                    .show();
            }
        });

        fullView.addView(addName);

        fullScrollView.addView(fullView);

        //create the dialog box
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Select Names")
                .setView(fullScrollView)
                .setPositiveButton("Ok", null)
                .show();
    }

    public void createNameSelections(final LinearLayout createdNames,
                                     final TextView currentMatchNumber, final ScrollView fullScrollView) {
        //add checkbox and close button for each username
        for (int i = 0; i < allScouts.size(); i++) {
            final View view = View.inflate(this, R.layout.closable_checkbox, null);

            CheckBox checkBox = ((CheckBox) view.findViewById(R.id.nameCheckBox));

            final Scout scout = allScouts.get(i);
            scout.view = view;

            checkBox.setText(scout.name);

            //if it is selected, check the box
            if (scout.existsAtMatch(robotSchedule.size() - 1)) {
                checkBox.setChecked(true);
            }

            //set checkbox onChange listener
            checkBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                    nameClicked(buttonView, isChecked, scout.name, currentMatchNumber);
                }
            });

            checkBox.setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(final View v) {
                    final EditText newName = new EditText(MainActivity.this);
                    newName.setText(scout.name);

                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle("Please enter the new name")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Set", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    scout.name = newName.getText().toString();
                                    ((CheckBox) v).setText(scout.name);

                                    updateNames();
                                }
                            })
                            .setView(newName)
                            .show();
                    return false;
                }
            });

            //set close action
            view.findViewById(R.id.nameClose).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createdNames.removeView(view);
                    allScouts.remove(scout);

                    //a scout was removed
                    //add the action to the past actions list
                    pastActions.add(new Action(3, scout, view));

                    if (pastActions.size() > PAST_ACTIONS_MAX) {
                        pastActions.remove(0);
                    }

                    updateNames();
                }
            });

            //set move up action
            view.findViewById(R.id.moveUp).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Scout currentScout = scout;
                    View currentView = view;

                    int currentScoutIndex = allScouts.indexOf(scout);
                    if (currentScoutIndex <= 0) {
                        return;
                    }

                    Scout scoutAbove = allScouts.get(currentScoutIndex - 1);

                    //swap them, making the scout selected move up
                    allScouts.set(currentScoutIndex - 1, scout);
                    allScouts.set(currentScoutIndex, scoutAbove);

                    updateNames();

                    //reload the UI
                    createdNames.removeAllViews();
                    createNameSelections(createdNames, currentMatchNumber, fullScrollView);
                }
            });

            //set move down action
            view.findViewById(R.id.moveDown).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Scout currentScout = scout;
                    View currentView = view;

                    int currentScoutIndex = allScouts.indexOf(scout);
                    if (currentScoutIndex >= allScouts.size() - 1) {
                        return;
                    }

                    Scout scoutBelow = allScouts.get(currentScoutIndex + 1);

                    //swap them, making the scout selected move up
                    allScouts.set(currentScoutIndex + 1, scout);
                    allScouts.set(currentScoutIndex, scoutBelow);

                    updateNames();

                    //reload the UI
                    createdNames.removeAllViews();
                    createNameSelections(createdNames, currentMatchNumber, fullScrollView);
                }
            });

            createdNames.addView(view);
        }
    }

    public void openScheduleViewer() {
        final ScrollView fullScrollView = new ScrollView(this);

        final LinearLayout scheduleViewer = (LinearLayout) getLayoutInflater().inflate(R.layout.schedule_viewer, null);

        //get the match number being used
        EditText matchNumText = (EditText) scheduleViewer.findViewById(R.id.scheduleMatchNum);
        matchNumText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {

            }

            @Override
            public void afterTextChanged(Editable s) {
                //display new schedule
                int matchNum = 0;
                try {
                    matchNum = Integer.parseInt(s.toString());
                } catch (NumberFormatException e) {
                    runOnUiThread(new Thread(){
                        public void run() {
                            Toast.makeText(MainActivity.this, "This match number is invalid", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }
                //because match number starts at 1 while inputting, not 0
                matchNum --;

                //setup schedule viewer
                //add all the info about the current schedule
                TextView schedules = (TextView) scheduleViewer.findViewById(R.id.scheduleViewerStatus);
                StringBuilder schedulesText = new StringBuilder();

                for (int i = 0; i < allScouts.size(); i++) {
                    if (assignedRobots.get(i)[matchNum] != -1) {
                        schedulesText.append(allScouts.get(i).name + " is scouting robot " + robotSchedule.get(matchNum).get(assignedRobots.get(i)[matchNum]) + " and is off at match " + getNextMatchOff(i, matchNum));
                    } else {
                        schedulesText.append(allScouts.get(i).name + " is off and will be back on at match " + getNextMatchOn(i, matchNum));
                    }

                    schedulesText.append("\n\n");
                }

                schedules.setText(schedulesText.toString());
            }
        });

        fullScrollView.addView(scheduleViewer);

        //create the dialog box
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("View Schedule")
                .setView(fullScrollView)
                .setPositiveButton("Dismiss", null)
                .show();
    }

    public void openScheduleWindow(){
        final LinearLayout scheduleWindow = (LinearLayout) getLayoutInflater().inflate(R.layout.get_schedule, null);
        final EditText url = (EditText) scheduleWindow.findViewById(R.id.scheduleUrl);

        final DialogInterface.OnClickListener onClickListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialogInterface, int i) {
                try{
                    String website = new ScheduleWindow().execute(url.getText().toString()).get();
                    System.out.println(website);
                }catch(Exception e){
                    System.out.println(e);
                }

            }
        };

        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Set url")
                .setView(scheduleWindow)
                .setPositiveButton("Ok", onClickListener)
                .setNegativeButton("Cancel", null)
                .show();
    }

    //this will return the match number when they have can stop scouting
    public int getNextMatchOff(int scoutIndex, int matchNumber) {
        int matchBack = -1;

        //there is no schedule
        if (scoutIndex == -1) return -1;

        //find next match number
        if (matchNumber <= 1) matchNumber = 1;
        for (int i = matchNumber; i < assignedRobots.get(scoutIndex).length; i++) {
            if (assignedRobots.get(scoutIndex)[i] == -1) {
                matchBack = i + 1;
                break;
            }
        }

        return matchBack;
    }

    //this will return the match number when they have have to start scouting again
    public int getNextMatchOn(int scoutIndex, int matchNumber) {
        int matchBack = -1;

        //there is no schedule
        if (scoutIndex == -1) return -1;

        //find next match number
        if (matchNumber <= 0) matchNumber = 1;
        for (int i = matchNumber; i < assignedRobots.get(scoutIndex).length; i++) {
            if (assignedRobots.get(scoutIndex)[i] != -1) {
                matchBack = i + 1;
                break;
            }
        }

        return matchBack;
    }

    //called when a name is checked or unchecked
    public void nameClicked(CompoundButton checkbox, boolean isChecked, String name, TextView currentMatchNumber) {
        int scoutIndex = getScout(name, allScouts);
        int matchNum = Integer.parseInt(currentMatchNumber.getText().toString()) - 1;

        //the checkbox was programmatically checked, no need to check anything
        if (scoutIndex != -1 && allScouts.get(scoutIndex).undo) {
            allScouts.get(scoutIndex).undo = false;
            return;
        }

        if (isChecked) {
            Scout scout = allScouts.get(scoutIndex);

            if (scout.existsAtMatch(matchNum)) {
                //this action should not happen, this is an invalid time
                runOnUiThread(new Thread() {
                    public void run() {
                        Toast.makeText(MainActivity.this, "This scout is already enabled at that match", Toast.LENGTH_SHORT).show();
                    }
                });
                checkbox.setChecked(false);
                return;
            }

            //enable them at that match number
            scout.startMatches.add(matchNum);

            //add the action to the past actions list
            pastActions.add(new Action(0, scout, checkbox));
            if (pastActions.size() > PAST_ACTIONS_MAX) {
                pastActions.remove(0);
            }
        } else {
            if (scoutIndex != -1) {
                Scout scout = allScouts.get(scoutIndex);

                if (!scout.existsAtMatch(matchNum)) {
                    //this action should not happen, this is an invalid time
                    runOnUiThread(new Thread() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "The scout is not enabled at that match", Toast.LENGTH_SHORT).show();
                        }
                    });
                    checkbox.setChecked(true);
                    return;
                }

                scout.lastMatches.add(matchNum);

                //check if there were errors from this change
                final String success = createSchedule();
                if (success != null) {
                    runOnUiThread(new Thread(){
                        public void run() {
                            Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                        }
                    });
                    //undo as an error has been caused
                    scout.lastMatches.remove(scout.lastMatches.size() - 1);
                    checkbox.setChecked(true);
                } else {
                    //it was successful

                    //add the action to the past actions list
                    pastActions.add(new Action(1, scout, checkbox));

                    if (pastActions.size() > PAST_ACTIONS_MAX) {
                        pastActions.remove(0);
                    }
                }
            }
        }

        updateNames();
    }

    //updates the shared preferences with the all names and selected names list
    public void updateNames() {
        SharedPreferences sharedPreferences = getSharedPreferences("names", MODE_PRIVATE);

        //get all the names in csv
        String names = "";
        for (Scout scout : allScouts) {
            names += scout.name;
            if (allScouts.indexOf(scout) < allScouts.size() - 1) {
                names += ",";
            }
        }

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("allNames", names);

        //get all the selected names in csv
        String allSelectedNames = "";
        String allSelectedNameStartMatches = "";
        String allSelectedNameLastMatches = "";
        for (Scout scout : allScouts) {
            allSelectedNames += scout.name;
            for (int i = 0; i < scout.startMatches.size(); i++) {
                allSelectedNameStartMatches += scout.startMatches.get(i);
                if (i < scout.startMatches.size() - 1) {
                    allSelectedNameStartMatches += ";";
                }
            }
            for (int i = 0; i < scout.lastMatches.size(); i++) {
                allSelectedNameLastMatches += scout.lastMatches.get(i);
                if (i < scout.lastMatches.size() - 1) {
                    allSelectedNameLastMatches += ";";
                }
            }

            if (allScouts.indexOf(scout) < allScouts.size() - 1) {
                allSelectedNames += ",";
                allSelectedNameStartMatches += ",";
                allSelectedNameLastMatches += ",";
            }
        }
        editor.putString("allScouts", allSelectedNames);
        editor.putString("selectedNameStartMatches", allSelectedNameStartMatches);
        editor.putString("selectedNameLastMatches", allSelectedNameLastMatches);


        editor.apply();


        //update schedule
        final String success = createSchedule();
        if (success != null) {
            runOnUiThread(new Thread(){
                public void run() {
                    Toast.makeText(MainActivity.this, success, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    @Override
    public void onBackPressed() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            for(PullDataThread pullDataThread: pullDataThreads){
                if(pullDataThread != null) pullDataThread.onDestroy();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String parse(String string){
        String[] data = string.split("\n")[1].split(","); //auto
        String message = "Available: ";
        ArrayList<Integer> available = new ArrayList<>();

        for(int i=0;i<data.length;i+=3){
            if(Boolean.parseBoolean(data[i])){
                message += getDefense(i/3) + ", ";
                available.add(new Integer(i/3));
            }
        }

        message+= "\nReached: ";
        for(int i=1;i<data.length;i+=3){
            if(Boolean.parseBoolean(data[i])){
                message += getDefense((i-1)/3) + ", ";
            }
        }

        message+= "\nCrossed: ";
        for(int i=2;i<data.length;i+=3){
            if(Boolean.parseBoolean(data[i])){
                message += getDefense((i-2)/3) + ", ";
            }
        }

        data = string.split("\n")[2].split(","); //teleop

        for(Integer i: available){
            message += "\n" + getDefense(i) + ": " + data[i*2] + " attemps and " + data[i*2+1] + " crossed";
        }

        message += "Low Goals: " + data[data.length-4] + " attemps and " + data[data.length-3] + " goals scored";
        message += "High Goals: " + data[data.length-2] + " attemps and " + data[data.length-1] + " goals scored";

            return message;
    }

    public String getDefense(int i){
        switch(i){
            case 0:
                return "Low Bar";
            case 1:
                return "Portcullis";
            case 2:
                return "Cheval de Frise";
            case 3:
                return "Moat";
            case 4:
                return "Rampart";
            case 5:
                return "Drawbridge";
            case 6:
                return "Sally Port";
            case 7:
                return "Rock Wall";
            case 8:
                return "Rough Terrain";
        }
        return "";
    }

    public String getUUIDFromData(String data, boolean encodedInBase64){

        final String decodedMatchData;
        if (encodedInBase64) {
            data = data.replace(data.split(":")[0] + ":", "");

            if(data.split(":").length >= 2) {
                //there are events
                if (data.split(":").length >= 3) {
                    //there are tele and auto events
                    data = data.replace(":" + data.split(":")[1] + ":" + data.split(":")[2], "");
                    System.out.println(data);
                } else {
                    data = data.replace(":" + data.split(":")[1], "");
                }
            }

            final String encodedData = data;
            decodedMatchData = Base64Encoder.decode(data);

            if (decodedMatchData == null) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(getApplicationContext(), "Your base 64 is broken when getting the UUID. Base 64: " + encodedData, Toast.LENGTH_LONG).show();
                    }
                });
            }

        } else {
            decodedMatchData = data;
        }

        String[] dataArray = decodedMatchData.split(",");
        if(dataArray.length <= 2){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Your data is broken, the amount of commas is too small in " + decodedMatchData, Toast.LENGTH_LONG).show();
                }
            });
            return "null";
        }

        return dataArray[dataArray.length - 1];

    }

    public boolean stringListContains(ArrayList<String> list, String string) {
        for (String listString : list) {
            if (listString.equals(string)) {
                return true;
            }
        }

        return false;
    }

    public ArrayList<String> readData() throws IOException {

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/");
        file.mkdirs();

        File[] files = file.listFiles();
        if(files == null) return new ArrayList<>(); //no files in the directory

        ArrayList<String> data = new ArrayList<>();

        for(int i = 0; i < files.length; i++) {
            if(files[i].isDirectory()) continue;

            BufferedReader br = new BufferedReader(new FileReader(files[i]));
            String line;

            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                if(lineNum > 0) {
                    data.add(line);
                }
                lineNum ++;
            }
            br.close();
        }

        return data;
    }

    public void saveEvents(String data) {

        if(data.split(":").length <= 2){
            //there are no events
            return;
        }

        File sdCard = Environment.getExternalStorageDirectory();

        File autoFile = new File(sdCard.getPath() + "/#ScoutingData/AutoEventData/" + data.split(":")[0] + ".csv");
        File teleOpFile = new File(sdCard.getPath() + "/#ScoutingData/EventData/" + data.split(":")[0] + ".csv");

        data = data.replace(data.split(":")[0] + ":" + data.split(":")[1] + ":", "");
        String autoData = Base64Encoder.decode(data.split(":")[0]);
        String teleOpData = "nodata";
        if(data.split(":").length >= 2){
            //there is teleOpData
            teleOpData = Base64Encoder.decode(data.split(":")[1]);
        }

        //check if base 64 decode failed
        if (autoData == null || teleOpData == null) {
            if (autoData == null) {
                autoData = "Base 64 failed to decode. Base 64: " + data.split(":")[0];
            } else {
                teleOpData = "Base 64 failed to decode. Base 64: " + data.split(":")[1];
            }
            runOnUiThread(new Thread() {
                public void run() {
                    Toast.makeText(MainActivity.this, "Base 64 failed to decode in saveEvents()", Toast.LENGTH_LONG).show();
                }
            });
        }

        if (autoData.endsWith("\n")) {
            autoData = autoData.substring(0, autoData.length() - 1);
        }
        if (teleOpData.endsWith("\n")) {
            teleOpData = teleOpData.substring(0, teleOpData.length() - 1);
        }

        try {
            //save auto data
            if (!autoData.equals("nodata")) {
                boolean newfile = false;
                autoFile.getParentFile().mkdirs();
                if (!autoFile.exists()) {
                    autoFile.createNewFile();
                    newfile = true;
                }

                FileOutputStream f = new FileOutputStream(autoFile, true);

                OutputStreamWriter out = new OutputStreamWriter(f);

                out.write(autoData + "\n");

                out.close();
                f.close();
            }

            //save teleop data
            if (!teleOpData.equals("nodata")) {
                boolean newfile = false;
                teleOpFile.getParentFile().mkdirs();
                if (!teleOpFile.exists()) {
                    teleOpFile.createNewFile();
                    newfile = true;
                }

                FileOutputStream f = new FileOutputStream(teleOpFile, true);

                OutputStreamWriter out = new OutputStreamWriter(f);

                out.write(teleOpData + "\n");

                out.close();
                f.close();
            }
        }catch(IOException e){
            e.printStackTrace();
        }

    }

    public void save(String data, String labels){

        saveEvents(data);

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/" + data.split(":")[0] + ".csv");

        data = data.replace(data.split(":")[0] + ":", "");

        if(data.split(":").length >= 2){
            //there are events
            if (data.split(":").length >= 3) {
                //there are tele and auto events
                data = data.replace(":" + data.split(":")[1] + ":" + data.split(":")[2], "");
                System.out.println(data);
            } else {
                data = data.replace(":" + data.split(":")[1], "");
            }
        }

        //decode data from base 64
        String originalData = data;
        data = Base64Encoder.decode(data);

        if (data == null) {
            data = "Base 64 failed to decode. Base 64: " + originalData;
            runOnUiThread(new Thread() {
                public void run() {
                    Toast.makeText(MainActivity.this, "Base 64 failed to decode in save()", Toast.LENGTH_LONG).show();
                }
            });
        }

        System.out.println(data);

        try {
            boolean newfile = false;
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
                newfile = true;
            }

            FileOutputStream f = new FileOutputStream(file, true);

            OutputStreamWriter out = new OutputStreamWriter(f);

            if(newfile) out.write(labels);
            out.write(data);

            out.close();
            f.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
