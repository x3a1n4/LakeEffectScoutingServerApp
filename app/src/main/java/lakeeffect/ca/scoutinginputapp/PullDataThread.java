package lakeeffect.ca.scoutinginputapp;

import android.bluetooth.BluetoothSocket;

import java.io.IOException;

/**
 * Created by Ajay on 10/15/2017.
 *
 * Class that deals with pulling the data, this class should not be able to be called multiple times
 */
public class PullDataThread extends Thread{

    boolean running = false;

    BluetoothSocket bluetoothSocket;

    public PullDataThread(BluetoothSocket bluetoothSocket){
        this.bluetoothSocket = bluetoothSocket;
    }

    @Override
    public void run() {
        //send pull request and wait for a response
        try {
            bluetoothSocket.connect();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
