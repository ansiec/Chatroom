package server;

import org.sqlite.SQLiteDataSource;
import shared.*;

import java.sql.*;
import java.util.*;
import java.util.concurrent.Semaphore;

class Database {
    private SQLiteDataSource dataSource;
    private final Semaphore semaphoreSQLite;
    // speichert Benutzername und Passwort

    final Map<String, String> userAndPassword = Collections.synchronizedMap(new HashMap<String, String>());

    // groupAndThread: Speichert ClientHandler nach den Gruppen ab, in denen der dazugehörige User ist
    final Map<String, HashSet<ClientHandler>> groupAndThread = Collections.synchronizedMap(new HashMap<String, HashSet<ClientHandler>>());

    final Map<String, ArrayList<Message>> messages = Collections.synchronizedMap(new HashMap<String, ArrayList<Message>>());

    private Database() {
        this.semaphoreSQLite = new Semaphore(1);
        this.dataSource = new SQLiteDataSource();
        dataSource.setUrl("jdbc:sqlite:userAndGroup.db");
        createDatabaseIfNotExists();
    }

    public static Database getInstance() {
        return NestedSingletonHelper.databaseSingleton;
    }

    private static class NestedSingletonHelper {
        public static Database databaseSingleton = new Database();
    }


    // alle SQL Methoden sind von ChatGPT
    private void createDatabaseIfNotExists() {
        try (Connection connection = DriverManager.getConnection(dataSource.getUrl());
             Statement statement = connection.createStatement()) {
            // Create users table
            String createUserTableQuery = "CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "username TEXT UNIQUE," +
                    "nickname TEXT);";
            statement.executeUpdate(createUserTableQuery);

            // Create groups table
            String createGroupTableQuery = "CREATE TABLE IF NOT EXISTS groups (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "group_name TEXT UNIQUE);";
            statement.executeUpdate(createGroupTableQuery);

            // Create user_groups table for the relationship
            String createUserGroupsTableQuery = "CREATE TABLE IF NOT EXISTS user_groups (" +
                    "user_id INTEGER," +
                    "group_id INTEGER," +
                    "PRIMARY KEY (user_id, group_id)," +
                    "FOREIGN KEY (user_id) REFERENCES users(id)," +
                    "FOREIGN KEY (group_id) REFERENCES groups(id));";
            statement.executeUpdate(createUserGroupsTableQuery);
        } catch (SQLException e) {
            e.printStackTrace(); // Handle the exception according to your needs
        }
    }

    public List<String> getUsersInGroup(String group) {
        List<String> usersInGroup = new ArrayList<>();

        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                String query = "SELECT users.username FROM users " +
                        "JOIN user_groups ON users.id = user_groups.user_id " +
                        "JOIN groups ON user_groups.group_id = groups.id " +
                        "WHERE groups.group_name = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, group);

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            String username = resultSet.getString("username");
                            usersInGroup.add(username);
                        }
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace(); // Handle exceptions according to your needs
        } finally {
            semaphoreSQLite.release(); // Release the permit in a finally block to ensure it's released even if an exception occurs
        }
        return usersInGroup;
    }

    public List<String> getGroupsForUser(String username) {
        List<String> userGroups = new ArrayList<>();
        try {
            semaphoreSQLite.acquire();
        } catch (InterruptedException e) {
            return null;
        }
        try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
            String query = "SELECT groups.group_name FROM users " +
                    "JOIN user_groups ON users.id = user_groups.user_id " +
                    "JOIN groups ON user_groups.group_id = groups.id " +
                    "WHERE users.username = ?";

            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setString(1, username);

                try (ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        String groupName = resultSet.getString("group_name");
                        userGroups.add(groupName);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace(); // Handle the exception according to your needs
        } finally {
            semaphoreSQLite.release();
        }

        return userGroups;
    }

    public List<String> getAllUsers() {
        List<String> allUsers = new ArrayList<>();

        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                String query = "SELECT username FROM users";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query);
                     ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        String username = resultSet.getString("username");
                        allUsers.add(username);
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace(); // Handle exceptions according to your needs
        } finally {
            semaphoreSQLite.release(); // Release the permit in a finally block to ensure it's released even if an exception occurs
        }

        return allUsers;
    }


    void removeThreadFromAllGroups(ClientHandler thread) {
        synchronized (groupAndThread) {
            for (Map.Entry<String, HashSet<ClientHandler>> entry : groupAndThread.entrySet()) {

                HashSet<ClientHandler> listOfThreads = entry.getValue();

                listOfThreads.removeIf(handler -> handler.equals(thread));
            }
        }
    }

    public HashSet<ClientHandler> getAllThreads() {
        HashSet<ClientHandler> allClientHandlers = new HashSet<>();
        synchronized (groupAndThread) {
            for (HashSet<ClientHandler> clientHandlers : groupAndThread.values()) {
                allClientHandlers.addAll(clientHandlers);
            }
        }
        return allClientHandlers;
    }

    @Deprecated
    // Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key.
    String getPasswordForUser(String username) {
        return userAndPassword.get(username);
    }

    @Deprecated
    void setPasswordForUser(String password, String username) {
        userAndPassword.computeIfPresent(username, (k, v) -> password);
    }

    @Deprecated
    void setUserAndPassword(String username, String password) {
        userAndPassword.put(username, password);
    }

    @Deprecated
    boolean isRegistriert(String username) {
        return userAndPassword.containsKey(username);
    }

    @Deprecated
    Set<String> getUserSet() {
        return userAndPassword.keySet();
    }

    void addMessage(Message message) {
        String group = message.getStringAtIndex(0);
        messages.computeIfAbsent(group, k -> new ArrayList<>()).add(message);
    }

    private void addThreadToGroup(ClientHandler clientHandler, String group) {
        groupAndThread.computeIfAbsent(group, k -> new HashSet<>()).add(clientHandler);
    }

    // ChatGPT
    private void addUserToGroup(String user, String group) {
        try {
            semaphoreSQLite.acquire();
        } catch (InterruptedException e) {
            return;
        }
        try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
            // Get user and group IDs
            int userId = getUserIdByUsername(connection, user);
            int groupId = getGroupIdByName(connection, group);

            // Insert into user_groups table
            String query = "INSERT INTO user_groups (user_id, group_id) VALUES (?, ?)";
            try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                preparedStatement.setInt(1, userId);
                preparedStatement.setInt(2, groupId);
                preparedStatement.executeUpdate();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        } finally {
            semaphoreSQLite.release();
        }
    }

    private int getUserIdByUsername(Connection connection, String username) throws SQLException {
        String query = "SELECT id FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);

            try (var resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }
        throw new SQLException("User not found: " + username);
    }

    private int getGroupIdByName(Connection connection, String groupName) throws SQLException {
        String query = "SELECT id FROM groups WHERE group_name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, groupName);

            try (var resultSet = preparedStatement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getInt("id");
                }
            }
        }
        throw new SQLException("Group not found: " + groupName);
    }


    public void addGroup(String groupName) {
        try {
            semaphoreSQLite.acquire();
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                // Check if the group already exists
                if (groupExists(connection, groupName)) {
                    System.out.println("Group already exists: " + groupName);
                    return;
                }

                // Insert into groups table
                String query = "INSERT INTO groups (group_name) VALUES (?)";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, groupName);
                    preparedStatement.executeUpdate();
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        } finally {
            semaphoreSQLite.release();
        }
    }

    private boolean groupExists(Connection connection, String groupName) throws SQLException {
        String query = "SELECT 1 FROM groups WHERE group_name = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, groupName);

            try (var resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        }
    }


    public void addUser(String username) {
        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                // Check if the user already exists
                if (userExists(connection, username)) {
                    System.out.println("User already exists: " + username);
                    return;
                }

                // Insert into users table
                String query = "INSERT INTO users (username) VALUES (?)";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.executeUpdate();
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
        } finally {
            semaphoreSQLite.release();
        }
    }

    private boolean userExists(Connection connection, String username) throws SQLException {
        String query = "SELECT 1 FROM users WHERE username = ?";
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setString(1, username);

            try (var resultSet = preparedStatement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public List<String> getUsernamesInGroup(String group) {
        List<String> usernamesInGroup = new ArrayList<>();

        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                // Retrieve usernames in the specified group
                String query = "SELECT users.username FROM users " +
                        "JOIN user_groups ON users.id = user_groups.user_id " +
                        "JOIN groups ON user_groups.group_id = groups.id " +
                        "WHERE groups.group_name = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, group);

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        while (resultSet.next()) {
                            String username = resultSet.getString("username");
                            usernamesInGroup.add(username);
                        }
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace(); // Handle exceptions according to your needs
        } finally {
            semaphoreSQLite.release(); // Release the permit in a finally block to ensure it's released even if an exception occurs
        }

        return usernamesInGroup;
    }

    public ArrayList<String> getNicknamesAsList() {
        ArrayList<String> nicknamesAsList = new ArrayList<>();
        HashMap<String, String> nicknamesAsMap = getNicknamesAsMap();

        if (nicknamesAsMap != null) {
            nicknamesAsMap.forEach((username, nickname) -> {
                nicknamesAsList.add(username);
                nicknamesAsList.add(nickname);
            });
        }
        return nicknamesAsList;
    }


    public HashMap<String, String> getNicknamesAsMap() {
        HashMap<String, String> usersWithNicknames = new HashMap<>();

        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                String query = "SELECT username, nickname FROM users";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query);
                     ResultSet resultSet = preparedStatement.executeQuery()) {
                    if (resultSet != null) {
                        while (resultSet.next()) {
                            String username = resultSet.getString("username");
                            String nickname = resultSet.getString("nickname");
                            if (nickname!=null){
                                usersWithNicknames.put(username, nickname);
                            }
                        }
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace(); // Handle exceptions according to your needs
        } finally {
            semaphoreSQLite.release(); // Release the permit in a finally block to ensure it's released even if an exception occurs
        }
        return usersWithNicknames;
    }


    void addUserAndThreadToGroup(ClientHandler thread, String user, String group) {
        addThreadToGroup(thread, group);
        addUserToGroup(user, group);
    }

    HashSet<ClientHandler> getThreadsOfGroup(String group) {
        return groupAndThread.get(group);
    }


    boolean currentThreadIsInGroup(ClientHandler clientHandler, String group) {
        if (groupAndThread.containsKey(group)) {
            return groupAndThread.get(group).contains(clientHandler);
        }
        else {
            return false;
        }
    }

    public boolean currentUserIsInGroup(String username, String group) {
        try {
            semaphoreSQLite.acquire();
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                String query = "SELECT 1 FROM users " +
                        "JOIN user_groups ON users.id = user_groups.user_id " +
                        "JOIN groups ON user_groups.group_id = groups.id " +
                        "WHERE users.username = ? AND groups.group_name = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
                    preparedStatement.setString(1, username);
                    preparedStatement.setString(2, group);

                    try (ResultSet resultSet = preparedStatement.executeQuery()) {
                        return resultSet.next();
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            semaphoreSQLite.release();
        }
    }

    public boolean setNicknameForUser(String nickname, String username) {
        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                // Check if the user exists
                if (!userExists(connection, username)) {
                    System.out.println("User not found: " + username);
                    return false;
                }

                // Update the nickname
                String updateNicknameQuery = "UPDATE users SET nickname = ? WHERE username = ?";
                try (PreparedStatement preparedStatement = connection.prepareStatement(updateNicknameQuery)) {
                    preparedStatement.setString(1, nickname);
                    preparedStatement.setString(2, username);

                    preparedStatement.executeUpdate();
                    return true;
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace();
            return false;
        } finally {
            semaphoreSQLite.release(); // Release the permit in a finally block to ensure it's released even if an exception occurs
        }
    }

    List<String> getAllGroups() {
        List<String> allGroups = new ArrayList<>();

        try {
            semaphoreSQLite.acquire(); // Acquire the permit before accessing the shared resource
            try (Connection connection = DriverManager.getConnection(dataSource.getUrl())) {
                String query = "SELECT group_name FROM groups";
                try (PreparedStatement preparedStatement = connection.prepareStatement(query);
                     ResultSet resultSet = preparedStatement.executeQuery()) {
                    while (resultSet.next()) {
                        String groupName = resultSet.getString("group_name");
                        allGroups.add(groupName);
                    }
                }
            }
        } catch (InterruptedException | SQLException e) {
            e.printStackTrace(); // Handle exceptions according to your needs
        } finally {
            semaphoreSQLite.release(); // Release the permit in a finally block to ensure it's released even if an exception occurs
        }

        return allGroups;
    }
}


