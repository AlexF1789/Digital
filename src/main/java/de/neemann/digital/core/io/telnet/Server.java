/*
 * Copyright (c) 2021 Helmut Neemann.
 * Use of this source code is governed by the GPL v3 license
 * that can be found in the LICENSE file.
 */
package de.neemann.digital.core.io.telnet;

import de.neemann.digital.core.Observer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The telnet server
 */
public class Server {
    private final ServerSocket serverSocket;
    private final ByteBuffer buffer;
    private boolean telnetEscape;
    private ClientThread client;
    private Observer notify;

    Server(int port) throws IOException {
        buffer = new ByteBuffer(1024);
        serverSocket = new ServerSocket(port);
        ServerThread listener = new ServerThread();
        listener.start();
    }

    void send(int value) {
        if (client != null)
            client.send(value);
    }

    int getData() {
        return buffer.peek();
    }

    void delete() {
        buffer.delete();
    }

    void setNotify(Observer notify) {
        this.notify = notify;
    }

    boolean hasData() {
        return buffer.hasData();
    }

    private void setClient(ClientThread client) {
        this.client = client;
    }

    void setTelnetEscape(boolean telnetEscape) {
        this.telnetEscape = telnetEscape;
    }

    private void dataReceived(int data) {
        buffer.put((byte) data);
        if (notify != null)
            notify.hasChanged();
    }

    private final class ServerThread extends Thread {

        private ServerThread() {
            setDaemon(true);
        }

        @Override
        public void run() {
            try {
                while (true) {
                    Socket client = serverSocket.accept();
                    ClientThread cl = new ClientThread(client, Server.this);
                    cl.start();
                    setClient(cl);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

    }

    private static final class ClientThread extends Thread {

        private static final int ECHO = 1;
        private static final int SGA = 3;
        private static final int WILL = 251;
        private static final int WONT = 252;
        private static final int DO = 253;
        private static final int DONT = 254;
        private static final int IAC = 255;

        private final InputStream in;
        private final OutputStream out;
        private final Socket client;
        private final Server server;

        private ClientThread(Socket client, Server server) throws IOException {
            setDaemon(true);
            in = client.getInputStream();
            out = client.getOutputStream();
            if (server.telnetEscape) {
                out.write(IAC);
                out.write(WILL);
                out.write(SGA);
                out.write(IAC);
                out.write(WILL);
                out.write(ECHO);
                out.flush();
            }
            this.client = client;
            this.server = server;
        }

        @Override
        public void run() {
            try {
                while (true) {
                    int data = in.read();
                    if (data == IAC && server.telnetEscape) {
                        int command = in.read();
                        int option = in.read();
                    } else
                        server.dataReceived(data);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                client.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        public void send(int value) {
            try {
                out.write(value);
                out.flush();
            } catch (IOException e) {
                e.printStackTrace();
                try {
                    client.close();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }
        }
    }

}
