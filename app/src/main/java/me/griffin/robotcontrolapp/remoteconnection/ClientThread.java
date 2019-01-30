package me.griffin.robotcontrolapp.remoteconnection;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;
import android.widget.Toast;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;

import me.griffin.robotcontrolapp.CurrentCommandHolder;
import me.griffin.robotcontrolapp.MainActivity;
import me.griffinbeck.server.ClientConnectionManager;
import me.griffinbeck.server.ClientSocketConnector;
import me.griffinbeck.server.CommandPacket;
import me.griffinbeck.server.cmdresponses.CommandArguments;
import me.griffinbeck.server.cmdresponses.Commands;

/**
 * Created by griffin on 2/20/2018.
 */

public class ClientThread extends Thread {
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;
    private ClientSocketConnector clientSocketConnector;
    private ClientConnectionManager clientConnectionManager;
    private MainActivity mainActivity;
    //private Server server;

    /*public ClientThread(Socket socket) {
        this.socket = socket;
    }

    public ClientThread(Socket socket, Server server) {
        this.socket = socket;
        this.server = server;

    }*/

    public ClientThread(String ip, int port, MainActivity mainActivity) {
        this.mainActivity = mainActivity;
        clientSocketConnector = new ClientSocketConnector(ip, port, true);
    }

    /**
     * Note this method does not allow reconnection9 after connection is dropped
     * TODO: Add functionality to restart server and allow a reconnection after a user disconects or drops
     */
    @Override
    public void run() {
        socket = clientSocketConnector.connectToServer();
        if (socket == null) {

        } else {
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mainActivity.showNetworkConnectedIcon();
                }
            });
            clientConnectionManager = new ClientConnectionManager(socket, clientSocketConnector);
            /*try {
                out = new DataOutputStream(socket.getOutputStream());
                in = new DataInputStream(socket.getInputStream());

            } catch (Exception e) {
                Log.e("Client Thread", e.getMessage());
                Log.e("Client Thread", "Unable to initialize remote client input/output. Closing client socket.");
                try {
                    socket.close();
                } catch (Exception ignored) {
                }
                return;
            }*/
            Log.i("Remote Thread", "Starting communication between client");
            //String currentLine;
            //StringBuilder hold = new StringBuilder();
            //while (socket.isConnected()) {
            try {
                handleLinkingNegotiation();
                Log.i("Connection", "Linking Complete");
                CommandPacket packetIn = null;
                CommandPacket packetOut = null;
                CurrentCommandHolder.serverConnectionOpen = true;
                while (clientConnectionManager.isConnected()) {
                    try {
                        packetIn = clientConnectionManager.getPacket(false);
                        if (packetIn != null) {
                            handlePacketIn(packetIn);
                        }
                        packetOut = CurrentCommandHolder.takeNetworkPacket();

                        if (packetOut != null) {
                            clientConnectionManager.sendPacket(packetOut);
                        }

                        /*
                         * TODO: Add handler for response messages
                         */
                    } catch (IOException e) {
                        CurrentCommandHolder.rushCommand("d", 0, 0);
                        mainActivity.runOnUiThread(new Runnable() {
                            public void run() {
                                mainActivity.hideNetworkConnectedIcon();
                            }
                        });
                        Log.i("Connection", "Attempting to Recconnect");
                        if (tryRecconnect()) {
                            handleLinkingNegotiation();
                            mainActivity.runOnUiThread(new Runnable() {
                                public void run() {
                                    mainActivity.showNetworkConnectedIcon();
                                }
                            });
                        } else {
                            Log.i("Connection", "Recconnection Failed");
                            clientConnectionManager.closeConnection();
                            mainActivity.runOnUiThread(new Runnable() {
                                public void run() {
                                    mainActivity.hideNetworkConnectedIcon();
                                }
                            });
                        }
                    }
                }
                CurrentCommandHolder.serverConnectionOpen = false;
            } catch (Exception e) {
                e.printStackTrace();
                /*for (Object i : e.getStackTrace()) {
                    Log.e("Client Thread", e.toString());
                }*/
                Log.d("Client Thread", "Unable to read client input. Assuming client has disconnected and closing socket.");
                CurrentCommandHolder.addCommand("d", 0.0, 0.0);
                try {
                    clientConnectionManager.closeConnection();
                    Log.d("Client Thread", "Closed client socket");
                    CurrentCommandHolder.addCommand("d", 0.0, 0.0);
                    return;
                } catch (Exception f) {
                    Log.d("Client Thread", "Also unable to close the client socket. Recommended to kill app");
                    CurrentCommandHolder.addCommand("d", 0.0, 0.0);
                }
            }
            clientConnectionManager.closeConnection();
            mainActivity.runOnUiThread(new Runnable() {
                public void run() {
                    mainActivity.hideNetworkConnectedIcon();
                }
            });
            CurrentCommandHolder.serverConnectionOpen = false;
        }
    }

    private boolean tryRecconnect() {
        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, "Reconnecting To Server", Toast.LENGTH_LONG).show();
            }
        });
        long time = System.currentTimeMillis();
        ConnectivityManager cm =
                (ConnectivityManager) mainActivity.getSystemService(Context.CONNECTIVITY_SERVICE);

        NetworkInfo activeNetwork;
        boolean isConnected = false;
        while ((System.currentTimeMillis() - 60000) < time) {
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            if (cm != null) {
                activeNetwork = cm.getActiveNetworkInfo();
                isConnected = activeNetwork != null && activeNetwork.isConnected();
            }
            if (isConnected) {
                if (clientConnectionManager.tryRecconnect())
                    return true;
            }
        }

        mainActivity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(mainActivity, "Reconnect Failed", Toast.LENGTH_LONG).show();
            }
        });
        return false;

    }

    private void handlePacketIn(CommandPacket packet) throws IOException {
        Log.i("Connection", "Recieved: " + packet.getCmd() + " ");
        if (Commands.EXIT.equalTo(packet.getCmd())) {
            //server.setClient(null);
            //in.close();
            //clientConnectionManager.close();
            //socket.close();
            //Log.d("Client Thread", "User is disconnecting");
            clientConnectionManager.closeConnection();
            return;
        } else if (packet.getCmd().equals("d")) {
            String[] array = packet.getArgs();
            //Log.i("Network Connection", "got packet args " + Arrays.toString(array));
            int[] drive = new int[array.length];
            for (int i = 0; i < array.length; i++) {
                drive[i] = Integer.parseInt(array[i]);
            }
            //Log.i("Network Connection", "Added cmd: " + packetIn.getCmd() + Arrays.toString(drive));
            CurrentCommandHolder.addCommand(packet.getCmd(), drive);
        } else if (Commands.PAUSE.equalTo(packet.getCmd())) {
            if (CommandArguments.PAUSE_PAUSECONNECTION.equals(packet.getArg(0))) {
                CurrentCommandHolder.serverConnectionOpen = false;
                holdPause();
                CurrentCommandHolder.serverConnectionOpen = true;
            }
        } else if (Commands.REQUEST.equalTo(packet.getCmd())) {
            Log.i("Connection", "Checking Request");
            if (CommandArguments.REQUEST_IMG.equals(packet.getArg(0))) {
                Log.i("Connectiono", "IMG request found");
                CurrentCommandHolder.setIsCameraOpen(true);
                clientConnectionManager.sendPacket(new CommandPacket(Commands.RESPONSE, CommandArguments.RESPONSE_IMG));
            }
        }
    }

    private void handleLinkingNegotiation() throws IOException {
        boolean hold = true;
        CommandPacket receiving = clientConnectionManager.getPacket(true);
        Log.i("Connection", "Linking recived: " + receiving.getPacket() + " ");
        if (Commands.PAUSE.equalTo(receiving.getCmd())) {
            if (CommandArguments.PAUSE_PAUSECONNECTION.equals(receiving.getArgs()[0])) {
                while (hold) {
                    receiving = clientConnectionManager.getPacket(true);
                    if (Commands.PAUSE.equalTo(receiving.getCmd())) {
                        if (CommandArguments.PAUSE_UNPAUSECONNECTION.equals(receiving.getArgs()[0])) {
                            hold = false;
                            clientConnectionManager.sendPacket(new CommandPacket(Commands.LINK, CommandArguments.LINK_REQUESTLINK));
                            Log.i("Connection", "Sent Link Request Packet");
                        }
                    }
                }
                hold = true;
                while (hold) {
                    receiving = clientConnectionManager.getPacket(true);
                    if (Commands.LINK.equalTo(receiving.getCmd())) {
                        if (CommandArguments.LINK_LINKOPENED.equals(receiving.getArgs()[0])) {
                            Log.i("Connection", "Received Link Open");
                            hold = false;
                        }
                    }
                }
            }
        } else if (Commands.LINK.equalTo(receiving.getCmd())) {
            if (CommandArguments.LINK_PREPARE.equals(receiving.getArgs()[0])) {
                while (hold) {
                    receiving = clientConnectionManager.getPacket(true);
                    if (Commands.LINK.equalTo(receiving.getCmd())) {
                        if (CommandArguments.LINK_REQUESTLINK.equals(receiving.getArgs()[0])) {
                            clientConnectionManager.sendPacket(new CommandPacket(Commands.LINK, CommandArguments.LINK_LINKOPENED));
                            hold = false;
                        }
                    }
                }
            }
        }
        if (hold)
            handleLinkingNegotiation();
    }

    /**
     * Will handle incoming packets until the unpause packet is recieved at which time it will end the method
     */
    public void holdPause() throws IOException {
        CommandPacket inPacket = null;
        boolean pause = true;
        Log.i("Connection", "Now Paused");
        while (clientConnectionManager.isConnected() && pause) {
            inPacket = clientConnectionManager.getPacket(true);
            if (inPacket.getCmd().equalsIgnoreCase(Commands.PAUSE.toString())) {
                if (CommandArguments.PAUSE_UNPAUSECONNECTION.equals(inPacket.getArg(0))) {
                    handleLinkingNegotiationPostPause();
                    /*if(CommandArguments.PAUSE_ESTABLISHLINK.equals(inPacket.getArg(1))) {
                        pause = false;
                    } else {
                        pause = false;
                    }*/
                    pause = false;
                }
            } else if (Commands.HEARTBEAT.equalTo(inPacket.getCmd())) {
                clientConnectionManager.sendPacket(new CommandPacket(Commands.HEARTBEAT));
            }
        }
    }

    public void handleLinkingNegotiationPostPause() throws IOException {
        boolean hold = true;
        clientConnectionManager.sendPacket(new CommandPacket(Commands.LINK, CommandArguments.LINK_REQUESTLINK));
        CommandPacket receiving;
        hold = true;
        while (hold) {
            receiving = clientConnectionManager.getPacket(true);
            Log.i("Connection", "received: " + receiving.getCmd() + " ");
            if (Commands.LINK.equalTo(receiving.getCmd())) {
                if (CommandArguments.LINK_LINKOPENED.equals(receiving.getArgs()[0])) {
                    hold = false;
                }
            } else if (Commands.HEARTBEAT.equalTo(receiving.getCmd())) {
                clientConnectionManager.sendPacket(new CommandPacket(Commands.HEARTBEAT));
            }
        }

        if (hold)
            handleLinkingNegotiation();
    }
}
