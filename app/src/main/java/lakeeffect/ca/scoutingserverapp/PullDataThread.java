package lakeeffect.ca.scoutingserverapp;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.util.Base64;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;

/**
 * Created by Ajay on 10/15/2017.
 *
 * Class that deals with pulling the data, this class should not be able to be called multiple times
 */
public class PullDataThread extends Thread{

    boolean running = false;

    BluetoothSocket bluetoothSocket;
    OutputStream out;
    InputStream in;

    MainActivity mainActivity;

    BluetoothDevice device;
    View deviceMenu;

    //if the pull fails, it will try until the max tries is reached
    int tries = 0;
    int maxTries = 5;

    final String endSplitter = "{e}";

    public PullDataThread(MainActivity mainActivity, BluetoothDevice bluetoothDevice, View deviceMenu){
        this.mainActivity = mainActivity;
        this.device = bluetoothDevice;
        this.deviceMenu = deviceMenu;
    }

    @Override
    public void run() {
        running = true;

        //create device
        try {
            bluetoothSocket = device.createRfcommSocketToServiceRecord(UUID.fromString("6ba6afdc-6a0a-4b1d-a2bf-f71ac108b636"));
        } catch (IOException e) {
            e.printStackTrace();
        }

        //set status
        mainActivity.runOnUiThread(new Thread() {
            public void run() {
                mainActivity.status.setText("Connecting to " + device.getName() + "...");
            }
        });

        //send pull request and wait for a response

        //true if the try catch breaks
        boolean error = false;

        try {
            bluetoothSocket.connect();
            in = bluetoothSocket.getInputStream();
            out = bluetoothSocket.getOutputStream();

            if(mainActivity.labels == null){
                mainActivity.runOnUiThread(new Thread() {
                   public void run() {
                       mainActivity.status.setText("Connected! Requesting Labels from " + device.getName() + "...");
                   }
               });

                out.write((toBase64("REQUEST LABELS") + endSplitter).getBytes(Charset.forName("UTF-8")));
                String fullLabelsMessage = waitForMessage();

                int version = Integer.parseInt(fullLabelsMessage.split(":::")[0]);
                if(version >= mainActivity.minVersionNum){
                    String labels = fullLabelsMessage.split(":::")[1];
                    String decodedLabels = Base64Encoder.decode(labels);
                    mainActivity.labels = decodedLabels;
                }else{
                    //send toast saying that the client has a version too old
                    mainActivity.runOnUiThread(new Thread(){
                        public void run(){
                            Toast.makeText(mainActivity, "The Scouting App on the device you connected too is too old, either tell them to update or change the minimum version number", Toast.LENGTH_LONG).show();
                        }
                    });
                    running = false;
                    return;
                }
            }

            //send schedule
            {
                mainActivity.runOnUiThread(new Thread() {
                    public void run() {
                        mainActivity.status.setText("Connected! Sending schedule to " + device.getName() + "...");
                    }
                });

                //the string that will contain the scout schedule data to send to the client
                StringBuilder scheduleMessage = new StringBuilder("SEND SCHEDULE:::");

                StringBuilder userSchedule = new StringBuilder();
                for (int scoutIndex = 0; scoutIndex < mainActivity.assignedRobots.size(); scoutIndex++) {
                    userSchedule.append(mainActivity.allScouts.get(scoutIndex).name);

                    //add separator
                    userSchedule.append(":");

                    for (int i = 0 ; i < mainActivity.assignedRobots.get(scoutIndex).length; i++) {
                        userSchedule.append(mainActivity.assignedRobots.get(scoutIndex)[i]);

                        if (i < mainActivity.assignedRobots.get(scoutIndex).length - 1) {
                            //add a comma, it's not the last item
                            userSchedule.append(",");
                        }
                    }

                    if (scoutIndex < mainActivity.assignedRobots.size() - 1) {
                        //add a separator, it's not the last item
                        userSchedule.append("::");
                    }
                }

                //send the robot schedule
                StringBuilder robotSchedule = new StringBuilder();

                for (int i = 0; i < mainActivity.robotSchedule.size(); i++) {
                    for (int s = 0; s < mainActivity.robotSchedule.get(i).size(); s++) {
                        robotSchedule.append(mainActivity.robotSchedule.get(i).get(s));

                        if (s < mainActivity.robotSchedule.get(i).size() - 1) {
                            //add a comma, it's not the last item
                            robotSchedule.append(",");
                        }
                    }
                    if (i < mainActivity.robotSchedule.size() - 1) {
                        //add a separator, it's not the last item
                        robotSchedule.append("::");
                    }
                }

                //this message has finished, add everything together
                scheduleMessage.append(Base64.encodeToString(userSchedule.toString().getBytes(Charset.forName("UTF-8")), Base64.DEFAULT));
                scheduleMessage.append(":::");
                scheduleMessage.append(Base64.encodeToString(robotSchedule.toString().getBytes(Charset.forName("UTF-8")), Base64.DEFAULT));

                String finalMessage = toBase64(scheduleMessage.toString()) + endSplitter;

                out.write(finalMessage.getBytes(Charset.forName("UTF-8")));
                String receivedMessage = waitForMessage();

                if (!receivedMessage.contains("RECEIVED")) {
                    //did not succeed, try again
                    error = true;
                }
            }

            mainActivity.runOnUiThread(new Thread() {
                public void run() {
                    mainActivity.status.setText("Connected! Requesting Data from " + device.getName() + "...");
                }
            });

            out.write((toBase64("REQUEST DATA") + endSplitter).getBytes(Charset.forName("UTF-8")));
            String message = waitForMessage();

            mainActivity.runOnUiThread(new Thread() {
                public void run() {
                mainActivity.status.setText("Connected! Saving Data from " + device.getName() + "...");
                }
            });

            int version = Integer.parseInt(message.split(":::")[0]);
            if(version < mainActivity.minVersionNum){
                //send toast saying that the client has a version too old
                mainActivity.runOnUiThread(new Thread(){
                    public void run(){
                        Toast.makeText(mainActivity, "The Scouting App on the device you connected too is too old, either tell them to update or change the minimum version number", Toast.LENGTH_LONG).show();
                    }
                });
                    running = false;
                    return;
            } else {
                String[] data = message.split(":::")[1].split("::");

                if(data[0].equals("nodata")){
                    mainActivity.runOnUiThread(new Thread() {
                        public void run() {
                            mainActivity.status.setText("Connected! " + device.getName() + " has no data to send...");
                        }
                    });
                }else{
                    for(int i = 0; i < data.length; i++){
                        String matchData = data[i];
                        String decodedMatchData = Base64Encoder.decode(matchData);
                        if(mainActivity.stringListContains(mainActivity.uuids, mainActivity.getUUIDFromData(decodedMatchData))){
                            //send toast saying that the data already exists
                            mainActivity.runOnUiThread(new Thread(){
                                public void run(){
                                    Toast.makeText(mainActivity, "Duplicate data detected and removed", Toast.LENGTH_LONG).show();
                                }
                            });

                            //don't save the data
                            continue;
                        }
                        mainActivity.save(decodedMatchData, mainActivity.labels);
                        mainActivity.uuids.add(decodedMatchData);
                    }
                }

            }

            out.write((toBase64("RECEIVED") + endSplitter).getBytes(Charset.forName("UTF-8")));

        } catch (IOException e) {
            e.printStackTrace();

            error = true;
        }

        //send toast of completion
        if (error) {
            mainActivity.runOnUiThread(new Thread() {
                public void run() {
                    mainActivity.status.setText("Pull failed, trying again...");
                }
            });

            if (tries < maxTries) {
                tries++;
                //try again
                run();
                return;
            } else {
                //This device is probably offline
                mainActivity.runOnUiThread(new Thread() {
                    public void run() {
                        mainActivity.status.setText("All ready!");
                        Toast.makeText(mainActivity, "Failed to connect to " + device.getName() + " after " + tries + " tries", Toast.LENGTH_SHORT).show();
                    }
                });
            }
        } else {
            mainActivity.runOnUiThread(new Thread() {
                public void run() {
                    mainActivity.status.setText("All ready!");
                }
            });

            //update last pull time
            ((TextView) deviceMenu.findViewById(R.id.deviceName)).setText(device.getName() + " (" + mainActivity.timeFormat.format(mainActivity.cal.getTime()) + ")");
        }

        try {
            onDestroy();
        } catch (IOException e) {
            e.printStackTrace();
        }

        running = false;

        mainActivity.pullDataThreads.remove(this);
        if(mainActivity.pullDataThreads.size() > 0){
            new Thread(mainActivity.pullDataThreads.get(0)).start();
        }
    }

    public String waitForMessage(){
        String finalMessage = "";
        while(out != null && in != null && bluetoothSocket.isConnected()){
            byte[] bytes = new byte[100000];
            int amount = 0;
            try {
                amount = in.read(bytes);
            } catch (IOException e) {
                e.printStackTrace();
            }

            if(amount>0)  bytes = Arrays.copyOfRange(bytes, 0, amount);//puts data into bytes and cuts bytes
            else continue;

            String message = finalMessage + new String(bytes, Charset.forName("UTF-8"));
            if(!message.endsWith(endSplitter)){
                finalMessage = message;
                continue;
            }

            message = message.substring(0, message.length() - endSplitter.length());

            //convert message out of base 64
            String decodedMessage = Base64Encoder.decode(message);

            return decodedMessage;
        }

        return null;
    }

    public String toBase64(String string) {
        return Base64.encodeToString(string.getBytes(Charset.forName("UTF-8")), Base64.DEFAULT);
    }

    public void onDestroy() throws IOException {
        if(in!=null) in.close();
        if(out!=null) out.close();
        if(bluetoothSocket!=null) bluetoothSocket.close();
    }
}
