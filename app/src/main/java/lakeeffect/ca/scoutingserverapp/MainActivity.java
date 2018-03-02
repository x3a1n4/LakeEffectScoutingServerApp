package lakeeffect.ca.scoutingserverapp;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    BluetoothAdapter bluetoothAdapter;

    ArrayList<String> deviceNames = new ArrayList<>();

    Button connect;
    Button visibility;
    TextView status;
    TextView versionNumTextView;
    Button versionNumButton;

    int minVersionNum;

    String labels = null; //Retreived one time per session during the first pull

    ArrayList<String> uuids = new ArrayList<>();

    ArrayList<PullDataThread> pullDataThreads = new ArrayList<>();

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
        .setTitle("Which device?")
        .setMultiChoiceItems(names, null, new DialogInterface.OnMultiChoiceClickListener(){

            @Override
            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
//                if(pullDataThread == null || !pullDataThread.running) {
//                    pullDataThread = new PullDataThread(MainActivity.this, devices[which]);
//                    pullDataThread.start();
//
//                }else{
//                    runOnUiThread(new Thread(){
//                        public void run(){
//                            Toast.makeText(MainActivity.this, "Already pulling data, wait until that pull is finished!", Toast.LENGTH_LONG).show();
//                        }
//                    });
//                }
//            dialog.dismiss();
            }
        })
        .setPositiveButton("Pull", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SparseBooleanArray checked = ((AlertDialog) dialog).getListView().getCheckedItemPositions();
                for (int i = 0; i < ((AlertDialog) dialog).getListView().getCount(); i++){
                    if(checked.get(i)) {
                        pullDataThreads.add(new PullDataThread(MainActivity.this, devices[i]));
                        if(pullDataThreads.size() <= 1){
                            pullDataThreads.get(0).start();
                        }

                    }
                }

                runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(MainActivity.this, "Added to queue", Toast.LENGTH_LONG).show();
                    }
                });
            }


        })
        .create()
        .show();
    }

    @Override
    public void onBackPressed(){

    }

    @Override
    public void onDestroy(){
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

    public String getUUIDFromData(String data){
        String[] dataArray = data.split(",");
        return dataArray[dataArray.length-2];

    }

    public ArrayList<String> readData() throws IOException {

        File sdCard = Environment.getExternalStorageDirectory();

        File file = new File(sdCard.getPath() + "/#ScoutingData/");
        file.mkdirs();

        File[] files = new File(sdCard.getPath() + "/#ScoutingData/").listFiles();
        if(files == null) return new ArrayList<>(); //no files in the directory

        ArrayList<String> data = new ArrayList<>();

        for(int i=0;i<files.length;i++){
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

        try {
            boolean newfile = false;
            file.getParentFile().mkdirs();
            if (!file.exists()) {
                file.createNewFile();
                newfile = true;
            }

            FileOutputStream f = new FileOutputStream(file, true);

            OutputStreamWriter out = new OutputStreamWriter(f);

            out.write(data);

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

        data = data.replaceFirst(data.split(":")[1] + ":", "");

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
