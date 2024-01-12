package client;

import shared.*;

import java.net.*;
import java.io.*;
import java.util.*;

class Client extends AbstractClass implements ValidityChecker {
    private Set<String> userList = new TreeSet<>();
    private String angemeldetesPasswort;
    private final ClientGUI clientGUI;
    private final static Map<String, ArrayList<Message>> messages = new HashMap<String, ArrayList<Message>>();

    Client(Socket socket) {
        super(socket);
        // GUI starten und anzeigen
        clientGUI = new ClientGUI(this);
    }


    public static void main(String[] args) {

        final int port = 3141;
        final String host = "localhost";

        try (Socket socket = new Socket(host, port)) {
            Client client = new Client(socket);
            client.start();
            client.join();

        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {

        } finally {
            System.out.println("Client main beendet");
        }
    }


    @Override
    public void dingeTun() {

        // Test Block
        try {
            out.writeObject(new Message(ServerBefehl.REGISTRIEREN, new String[]{"ask", "asdfa"}));
            out.writeObject(new Message(ServerBefehl.REGISTRIEREN, new String[]{"ask", "asd23424fa"}));
            out.writeObject(new Message(ServerBefehl.ANMELDEN, new String[]{"ask234", "asd23424a"}));
            out.writeObject(new Message(ServerBefehl.ANMELDEN, new String[]{"ask", "asdf2342423423523456a"}));
            out.writeObject(new Message(ServerBefehl.ANMELDEN, new String[]{"ask", "asdfa"}));

        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        listenAndExecute();

        System.out.println("Client beendet");
    }


    protected void ausführenVon(Message message) {
        if (message == null) {return;}

        switch (message.getAktion()) {
            case ANMELDEN_ERFOLGREICH -> nutzerAnmelden(message);
            case FEEDBACK -> feedback(message);
            case TEXT_MESSAGE -> receiveTextMessage(message);
            case RECEIVE_GROUPS -> receiveGroups(message);
            case RECEIVE_USER_LIST -> receiveUserList(message);
            case SET_NICKNAME -> updateNicknames(message);
            case RECEIVE_NICKNAME_LIST -> receiveNicknameList(message);
            default -> throw new IllegalStateException("Wrong enum: " + message.getAktion());
        };
    }



    //getNewMessagesFromGroupSinceTime(group, time)

    void registrieren(String user, String password) {
        sendMessage(ServerBefehl.REGISTRIEREN, user, password);
    }

    void anmelden(String user, String password) {
        sendMessage(ServerBefehl.ANMELDEN, user, password);
    }
    void abmelden() {
        sendMessage(ServerBefehl.ABMELDEN);
    }

    void setNicknameTo(String nickname) {
        sendMessage(ServerBefehl.SET_NICKNAME, angemeldeterNutzer, nickname);
    }



    void sendTextMessage(String group, String text) {
        sendMessage(ServerBefehl.TEXT_MESSAGE, group, angemeldeterNutzer, text);
    }

    void getMessagesFrom(String group) {
        sendMessage(ServerBefehl.GET_MESSAGES_FROM, group);
    }

    void getUserList(String group) {
        sendMessage(ServerBefehl.GET_USER_LIST, group, angemeldeterNutzer);
    }

    protected TextMessage readTextMessage()  {
        try {
            return (TextMessage) in.readObject();

        } catch (IOException e) {
            System.out.println("Client Message readObject() IOException");
            e.printStackTrace();
            throw new RuntimeException(e);
        } catch (ClassNotFoundException e) {
            System.out.println("Client Message readObject() ClassNotFoundException");
            throw new RuntimeException(e);
        }
    }

    void changePassword(String neuesPasswort) {
        sendMessage(ServerBefehl.SET_PASSWORD, angemeldeterNutzer, angemeldetesPasswort, neuesPasswort);
    }

    private void nutzerAnmelden(Message message) {
        String user = message.getStringAtIndex(0);
        angemeldet = true;
        angemeldeterNutzer = user;
        clientGUI.setNickname(user);
        sendRequestForGroupList(user);
    }

    private void feedback(Message message) {
        String feedback = message.getStringAtIndex(0);
        clientGUI.addFeedback(feedback);
    }

    private void receiveTextMessage(Message message) {
        showMessageInGUI(message);
        //saveMessageToCache(message);
    }

    private void receiveGroups(Message message) {
        var groups = new ArrayList<String>(Arrays.asList(message.getStringArray()));
        clientGUI.updateRooms(groups);
    }

    private void receiveUserList(Message message) {
        var groupAndUsers = new ArrayList<String>(Arrays.asList(message.getStringArray()));
        String group = groupAndUsers.remove(0);
        clientGUI.updateUsers(group, groupAndUsers);
    }

    private void updateNicknames(Message message) {
        String user = message.getStringAtIndex(0);
        String nickname = message.getStringAtIndex(1);
        // here should be a code that doesn't require requesting all nicknames again instead
        sendMessage(ServerBefehl.GET_ALL_NICKNAMES);
    }


    private void receiveNicknameList(Message message) {
        HashMap<String, String> map = new HashMap<>();

        for (int i = 0; i < message.getStringArray().length; i += 2) {
            String username = message.getStringArray()[i];
            String nickname = message.getStringArray()[i+1];

            map.put(username, nickname);
        }
        clientGUI.updateNicknameList(map);
    }


    private void saveMessageToCache(Message message) {
        String group = message.getStringAtIndex(0);
        List<Message> groupMessages = messages.computeIfAbsent(group, k -> new ArrayList<>());
        groupMessages.add(message);
    }

    private void showMessageInGUI(Message message) {
        clientGUI.showMessageInGUIIfCurrentGroup(message);
    }

    private void getAllMessagesFromRoom () {

    }


    private void sendRequestForGroupList(String user) {
        sendMessage(ServerBefehl.GET_GROUPS, user);
    }
}
