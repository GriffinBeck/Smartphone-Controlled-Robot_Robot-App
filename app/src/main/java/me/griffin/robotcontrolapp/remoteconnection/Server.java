package me.griffin.robotcontrolapp.remoteconnection;

/**
 * Created by griffin on 2/20/2018.
 */

public class Server {
    /*private ClientThread client;
    private Socket socket;
    private ServerSocket serverSocket;
    private int port = 2048;
    private String ip;
    private Thread serverThread;

     public Server(String ip) {
         this.ip = ip;
         serverThread = new Thread(new Runnable() {
             @Override
             public void run() {
                 runServer();
             }
         });
     }

     public void startServer() {
         serverThread.start();
     }

    public void runServer() {
        socket = null;
        serverSocket = null;
        try {
            //serverSocket = new ServerSocket(port,1000, InetAddress.getLocalHost());
            serverSocket = new ServerSocket();
            serverSocket.bind(new InetSocketAddress(ip, port));
            Log.d("Server Thread", "Running on port " + port);
            Log.d("Server Thread", "Remote Connection Initiated");
            //mainActivity.setIpString(serverSocket.getInetAddress().toString());
            //mainActivity.setPortString(serverSocket.getLocalPort() +"");
            CurrentCommandHolder.serverSatus = true;
            CurrentCommandHolder.setIp(serverSocket.getInetAddress().toString());
            CurrentCommandHolder.setPort(serverSocket.getLocalPort() + "");
            ClientThread clientThread = new ClientThread(serverSocket.accept());
            clientThread.run();
            //implement checking if this should occur
            runServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if(!this.serverOpen()) {
            CurrentCommandHolder.serverSatus = false;
        }
    }

    public ClientThread getClient() {
        return client;
    }

    public Integer getPort() {
        if(serverSocket != null) {
            return  serverSocket.getLocalPort();
        }
        return null;
    }

    public String getAddress() {
        if(serverSocket != null) {
            return  serverSocket.getInetAddress().toString();
        }
        return  null;
    }

    public void setClient(ClientThread client) {
        this.client = client;
    }

    public boolean serverOpen() {
        return serverSocket != null;
    }*/


}
