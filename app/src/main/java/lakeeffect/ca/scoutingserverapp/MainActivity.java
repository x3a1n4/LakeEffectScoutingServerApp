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
import android.util.SparseBooleanArray;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
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
    TextView versionNumTextView;
    Button versionNumButton;

    int minVersionNum;

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

    //list of names added
    ArrayList<String> names = new ArrayList<>();

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
        versionNumTextView = ((TextView) findViewById(R.id.versionNum));
        versionNumButton = ((Button) findViewById(R.id.setVersionNumber));
        versionNumButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                minVersionNum = Integer.parseInt(versionNumTextView.getText().toString());

                SharedPreferences sharedPreferences = getSharedPreferences("minVersionNum", MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPreferences.edit();
                editor.putInt("minVersionNum", minVersionNum);
                editor.apply();
            }
        });
        SharedPreferences sharedPreferences = getSharedPreferences("minVersionNum", MODE_PRIVATE);
        minVersionNum = sharedPreferences.getInt("minVersionNum", 0);
        versionNumTextView.setText(minVersionNum + "");

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

        //load UUIDs of all data collected to make sure there are no duplicates
        ArrayList<String> data = new ArrayList<>();
        try {
            data = readData();
        } catch (IOException e) {
            e.printStackTrace();
        }

        for(String line: data){
            uuids.add(getUUIDFromData(line));
        }

        //load schedule into memory
        try {
            readSchedule();
        } catch (IOException e) {
            e.printStackTrace();
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

                runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(MainActivity.this, "Added to list", Toast.LENGTH_LONG).show();
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
                if(lineNum > 0) {
                    String[] robots = line.split(",");

                    ArrayList<Integer> robotNumbers = new ArrayList<>();

                    for (int s = 0; s < robots.length; s++) {
                        robotNumbers.add(Integer.parseInt(robots[i]));
                    }

                    robotSchedule.add(robotNumbers);
                }
                lineNum ++;
            }
            br.close();
        }
    }

    //dialog box that lets you edit and deselect names
    public void openNameEditor() {

        //contains all the names plus the view to add more
        final LinearLayout fullView = new LinearLayout(this);
        fullView.setOrientation(LinearLayout.VERTICAL);

        //all the names already in the list
        final LinearLayout createdNames = new LinearLayout(this);
        createdNames.setOrientation(LinearLayout.VERTICAL);

        //add checkbox and close button for each username
        for (int i = 0; i < names.size(); i++) {
            final View view = View.inflate(this, R.layout.closable_checkbox, null);
            ((CheckBox) view.findViewById(R.id.nameCheckBox)).setText(names.get(i));

            final String name = names.get(i);

            //set close action
            view.findViewById(R.id.nameClose).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    createdNames.removeView(view);
                    names.remove(name);
                }
            });

            createdNames.addView(view);
        }

        fullView.addView(createdNames);

        //create view to add a new name
        final View addName = View.inflate(this, R.layout.name_submitter, null);

        //set add name action
        addName.findViewById(R.id.nameAddButton).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                final String name = ((EditText) addName.findViewById(R.id.nameEditText)).getText().toString();

                //add it to the list
                names.add(name);

                //add it to the UI
                final View view = View.inflate(MainActivity.this, R.layout.closable_checkbox, null);
                ((CheckBox) view.findViewById(R.id.nameCheckBox)).setText(name);

                //set close action
                view.findViewById(R.id.nameClose).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        createdNames.removeView(view);
                        names.remove(name);
                    }
                });

                createdNames.addView(view);
            }
        });

        fullView.addView(addName);

        //create the dialog box
        new AlertDialog.Builder(MainActivity.this)
                .setTitle("Select Names")
                .setView(fullView)
                .setPositiveButton("Ok", null)
                .show();
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

    public String getUUIDFromData(final String data){
        String[] dataArray = data.split(",");
        if(dataArray.length <= 2){
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(getApplicationContext(), "Your data is broken, the amount of commas is too small in " + data, Toast.LENGTH_LONG).show();
                }
            });
            return "null";
        }
        return dataArray[dataArray.length-2];

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

        if(data.split(":").length < 2){
            //there are no events
            return;
        }

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/EventData/" + data.split(":")[0] + ".csv");

        data = data.replace(data.split(":")[0] + ":" + data.split(":")[1] + ":", "");

        if (data.equals("") || data.equals("\n")) {
            return;
        }

        if (data.endsWith("\n")) {
            data = data.substring(0, data.length() - 1);
        }

        try {
            boolean newfile = false;
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
                newfile = true;
            }

            FileOutputStream f = new FileOutputStream(file, true);

            OutputStreamWriter out = new OutputStreamWriter(f);

            out.write(data + "\n");

            out.close();
            f.close();
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
            data = data.replace(":" + data.split(":")[1], "");
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

//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    Toast.makeText(MainActivity.this, "Saved",
//                            Toast.LENGTH_LONG).show();
//                }
//            });

            out.close();
            f.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}
