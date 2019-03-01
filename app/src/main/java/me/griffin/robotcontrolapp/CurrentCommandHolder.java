package me.griffin.robotcontrolapp;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import me.griffinbeck.server.CommandPacket;

/**
 * Created by griffin on 2/20/2018.
 */

public class CurrentCommandHolder {
    public static boolean serverSatus;
    public static boolean serverConnectionOpen;
    public static UsbService usbService;
    public static SerialThread serialThread;
    public static boolean autonomousEnabled = false;
    /**
     * TODO: Make getting of data thread safe
     */
    private static double speed;
    private static double turn;
    private static String ip;
    private static String port;
    private static List<String> cmds;
    private static boolean serialThreadStatus = false;
    private static boolean isCameraOpen = false;
    private static Handler serialHandler;
    private static List<String> commandResponse;
    private static List<CommandPacket> networkPacketsToSend;


    static {
        cmds = new ArrayList<>();
        commandResponse = new ArrayList<>();
        networkPacketsToSend = new ArrayList<>();
        serialThreadStatus = false;
        serverConnectionOpen = false;
    }

    public static void addNetworkPacket(CommandPacket packet) {
        if (networkPacketsToSend.size() > 10) {
            networkPacketsToSend.clear();
        }
        networkPacketsToSend.add(packet);
    }

    public static CommandPacket takeNetworkPacket() {
        if (!networkPacketsToSend.isEmpty())
            return networkPacketsToSend.remove(0);
        return null;
    }

    public static void addCommand(String cmd, double... args) {
        if (Objects.equals(cmd, "d")) {
            if (args.length > 1) {
                speed = args[0];
                turn = args[1];
                if (speed > 1)
                    speed = 1;
                if (speed < -1)
                    speed = -1;
                if (turn > 1)
                    turn = 1;
                if (turn < -1)
                    turn = -1;
                sendToSerial(speed, turn);
            } else {
                Log.e("Command Data Holder", "Invalid arguments for drive command");
            }
        } else {
            if (Objects.equals(cmd, "encoderAll")) {
                cmds.add("enA");
            } else {
                cmds.add(cmd);
            }
        }
    }

    public static void addCommand(String cmd, int... args) {
        if (Objects.equals(cmd, "d")) {
            if (args.length > 1) {
                speed = args[0];
                turn = args[1];
                if (speed > 127)
                    speed = 127;
                if (speed < -127)
                    speed = -127;
                if (turn > 127)
                    turn = 127;
                if (turn < -127)
                    turn = -127;
                sendToSerial((int) speed, (int) turn);
            } else {
                Log.e("Command Data Holder", "Invalid arguments for drive command");
            }
        } else {
            if (Objects.equals(cmd, "encoderAll")) {
                cmds.add("enA");
            } else {
                cmds.add(cmd);
            }
        }
    }

    public static void rushCommand(String cmd, int... args) {
        if (Objects.equals(cmd, "d")) {
            if (args.length > 1) {
                speed = args[0];
                turn = args[1];
                if (speed > 127)
                    speed = 127;
                if (speed < -127)
                    speed = -127;
                if (turn > 127)
                    turn = 127;
                if (turn < -127)
                    turn = -127;
                rushToSerial((int) speed, (int) turn);
            } else {
                Log.e("Command Data Holder", "Invalid arguments for drive command");
            }
        }
    }

    public static void addResponse(String response) {
        commandResponse.add(response);
    }

    public static boolean hasCmd() {
        return !cmds.isEmpty();
    }

    public static boolean hasResponse() {
        return !commandResponse.isEmpty();
    }

    public static String takeCommand() {
        if (!cmds.isEmpty())
            return cmds.remove(0);
        return null;
    }

    public static String getResponse() {
        if (!commandResponse.isEmpty())
            return commandResponse.remove(0);
        return null;
    }

    public static String getIp() {
        return ip;
    }

    public static void setIp(String ip) {
        CurrentCommandHolder.ip = ip;
    }

    public static String getPort() {
        return port;
    }

    public static void setPort(String port) {
        CurrentCommandHolder.port = port;
    }

    public static double getSpeed() {
        return speed;
    }

    public static double getTurn() {
        return turn;
    }

    public static boolean getSerialStatus() {
        return serialThreadStatus;
    }

    private static void sendToSerial(double... arr) {
        if (serialThreadStatus) {
            if (serialHandler != null) {
                Message msg = new Message();
                msg.obj = arr;
                serialHandler.sendMessage(msg);
            } else {
                serialHandler = serialThread.getHandler();
            }
        }
    }

    private static void sendToSerial(int... arr) {
        if (serialThreadStatus) {
            if (serialHandler != null) {
                Message msg = new Message();
                msg.obj = arr;
                serialHandler.sendMessage(msg);
            } else {
                serialHandler = serialThread.getHandler();
            }
        }
    }

    private static void rushToSerial(int... arr) {
        if (serialThreadStatus) {
            if (serialHandler != null) {
                Message msg = new Message();
                msg.obj = arr;
                serialHandler.removeCallbacksAndMessages(null);//clears all callbacks and messages since token is null
                serialHandler.sendMessage(msg);
            } else {
                serialHandler = serialThread.getHandler();
            }
        }
    }

    public static void toggleThread(final MainActivity mainActivity) {
        if (serialThreadStatus) {
            stopThread(mainActivity);
        } else {
            startThread(mainActivity);
        }
    }

    public static void startThread(final MainActivity mainActivity) {
        if (serialThread == null) {
            serialThread = new SerialThread("SerialThread");
            serialThread.start();
            serialHandler = serialThread.getHandler();
        }
        serialThread.turnOn();
        serialThreadStatus = true;
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                mainActivity.showUSBConnectedIcon();
            }
        });
    }

    public static void stopThread(final MainActivity mainActivity) {
        if (serialThread != null) {
            serialThreadStatus = false;
            serialThread.turnOff();
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mainActivity.hideUSBConnectedIcon();
                }
            });
        }
    }

    public static boolean isIsCameraOpen() {
        return isCameraOpen;
    }

    public static void setIsCameraOpen(boolean isCameraOpen) {
        CurrentCommandHolder.isCameraOpen = isCameraOpen;
    }
}
