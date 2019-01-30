package me.griffin.robotcontrolapp;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

/**
 * Created by griff on 3/16/2018.
 */

public class SerialThread extends HandlerThread {
    private byte startByte = (byte) '<';
    private byte endByte = (byte) '>';
    private int packetSize = 3;
    private CustomHandler cmdHandler;
    private boolean doRun = false;

    public SerialThread(String name) {
        super(name);
    }

    /*public void run() {
        Looper.prepare();
        this.cmdHandler = new CustomHandler(Looper.myLooper());
        //handleMessage(cmdHandler.obtainMessage());
        Log.e("Serial Thread", "Looper has ran");
        Looper.loop();
        //sendQuitMessage();
    }*/
    @Override
    protected void onLooperPrepared() {
        super.onLooperPrepared();
        cmdHandler = new CustomHandler(getLooper());
    }

    public void handleMessage(Message msg) {
        //Log.e("Serial Thread", "Message Recieved");
        if (msg == null)
            return;
        if (doRun) {
            //Log.e("Serial Thread", "Handling message");
            //msg.getData();
            if (msg.obj instanceof double[]) {
                double[] arr = (double[]) msg.obj;
                autoSerial((arr[0]), (arr[1]));
            }
            if (msg.obj instanceof int[]) {
                int[] arr = (int[]) msg.obj;
                autoSerial((arr[0]), (arr[1]));
            }
        }
    }

    public void autoSerial(double speedD, double turnD) {
        //add if statement to figure out if other commands need to be sent
        int speed = (int) (speedD * 127);
        int turn = -1 * (int) (turnD * 127);
        byte signedSpeed = (byte) speed;
        byte signedTurn = (byte) turn;
        byte[] input = {'d', signedSpeed, signedTurn};
        sendPacket(input);
    }

    public void autoSerial(int speed, int turn) {
        //add if statement to figure out if other commands need to be sent
        byte signedSpeed = (byte) speed;
        byte signedTurn = (byte) turn;
        byte[] input = {'d', signedSpeed, signedTurn};
        sendPacket(input);
    }

    public void autoSerial() {
        //add if statement to figure out if other commands need to be sent
        double speedD = CurrentCommandHolder.getSpeed();
        double turnD = CurrentCommandHolder.getTurn();
        int speed = (int) (speedD * 127);
        int turn = (int) (turnD * 127);
        byte signedSpeed = (byte) speed;
        byte signedTurn = (byte) turn;
        byte[] input = {'d', signedSpeed, signedTurn};
        sendPacket(input);
    }

    private void sendQuitMessage() {
        byte[] packets = {'e', ' ', ' '};
        sendPacket(packets);
    }

    public void sendPacket(byte[] toSend) {
        if (toSend.length > packetSize)
            return;
        byte[] packet = new byte[packetSize + 2];
        int index = 0;
        packet[index] = startByte;
        index++;
        for (int i = 0; i < toSend.length; i++, index++) {
            packet[index] = toSend[i];
        }
        packet[index] = endByte;

        if (CurrentCommandHolder.usbService != null)
            CurrentCommandHolder.usbService.write(packet);
        else
            Log.e("AutoSerial", "USB Service is NULL");
    }

    public void turnOff() {
        doRun = false;
    }

    public void turnOn() {
        doRun = true;
    }

    public Handler getHandler() {
        return cmdHandler;
    }

    private class CustomHandler extends Handler {
        public CustomHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            //Log.e("Serial Thread", "Handling message");
            if (doRun) {
                Log.e("Serial Thread", "Handling message");
                //msg.getData();
                if (msg.obj instanceof double[]) {
                    double[] arr = (double[]) msg.obj;
                    autoSerial((arr[0]), (arr[1]));
                }
                if (msg.obj instanceof int[]) {
                    int[] arr = (int[]) msg.obj;
                    Log.i("Serial Thread", "Handling int message: " + arr[0] + " " + arr[1]);
                    autoSerial((arr[0]), (arr[1]));
                }
            }
        }
    }
}
