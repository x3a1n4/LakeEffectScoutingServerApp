package lakeeffect.ca.scoutinginputapp;

import android.bluetooth.BluetoothSocket;
import android.util.Log;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.Arrays;

public class BluetoothConnection extends Thread {
    BluetoothSocket bluetoothsocket = null;
    OutputStream out = null;
    InputStream in = null;

    MainActivity activity;

    public BluetoothConnection(BluetoothSocket bluetoothsocket, OutputStream out, InputStream in, MainActivity activity){
        this.bluetoothsocket = bluetoothsocket;
        this.out = out;
        this.in = in;
        this.activity = activity;
    }

    public void run(){
        String data = "";
        while(out != null && in != null && bluetoothsocket.isConnected()){
            try {
                byte[] bytes = new byte[100000];
                Log.d("HELLO", "Reading" + bytes);
                int amount = in.read(bytes);
                Log.d("HELLO", "Read" + amount);
                if(amount>0)  bytes = Arrays.copyOfRange(bytes, 0, amount);//puts data into bytes and cuts bytes
                else continue;
                String message = new String(bytes, Charset.forName("UTF-8"));
                if (bytes.length > 0){
                    activity.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(activity, "Starting To Save......",
                                    Toast.LENGTH_LONG).show();
                        }
                    });
                    MainActivity.save(data + message);
                    out.write("done".getBytes(Charset.forName("UTF-8")));
                    Log.d("HELLO", "DONE SENT" + bytes);
                    data = "";
                    Log.d("HELLO", "Useless" + amount);
                }else {
                    data += message;
                }
                final byte[] bytes2 = bytes;
                Log.d("HELLO", "KJLDJJLADS" + bytes);

            } catch (IOException e) {
                e.printStackTrace();
            }


        }
    }
}
