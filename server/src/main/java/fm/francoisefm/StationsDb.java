package fm.francoisefm;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

public class StationsDb {

    // Our frequency range is 87.0 to 107.0 which is equal to 200 different frequencies
    private static final int MAX_FREQUENCIES = 200;

    public static final String JDBC_URL = "jdbc:sqlite:francoisefm.db";

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(JDBC_URL);
    }

    public static void initDb() {

        // SQL statement for creating a new table
        String sql = """
                CREATE TABLE IF NOT EXISTS stations (
                	token text NOT NULL,
                	name text NOT NULL,
                	frequency INTEGER,
                	UNIQUE(token, name) ON CONFLICT IGNORE
                );""";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement()) {
            // create a new table
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void createStation(UserId userId, int frequency) {

        // SQL statement for creating a new table
        String sql = "INSERT INTO stations (token, name, frequency) VALUES (?, ?, ?)";

        try (Connection conn = connect();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId.token);
            pstmt.setString(2, userId.name);
            pstmt.setInt(3, frequency);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            throw new AudioServerException("Error inserting into db: ", e);
        }
    }

    public static List<Station> getStations() {

        // SQL statement for creating a new table
        String sql = "SELECT token, name, frequency FROM stations";

        try (Connection conn = connect();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            final List<Station> recordings = new ArrayList<>();
            while (rs.next()) {
                recordings.add(toStation(rs));
            }

            return recordings;
        } catch (SQLException e) {
            throw new AudioServerException("Error querying db: ", e);
        }
    }

    public static Optional<Station> getStation(UserId userId) {

        // SQL statement for creating a new table
        String sql = "SELECT token, name, frequency FROM stations WHERE token = ? AND name = ?";

        try (Connection conn = connect();
             PreparedStatement pstmt  = conn.prepareStatement(sql)) {

            pstmt.setString(1, userId.token);
            pstmt.setString(2, userId.name);

            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                return Optional.of(toStation(rs));
            }

            return Optional.empty();
        } catch (SQLException e) {
            throw new AudioServerException("Error querying db: ", e);
        }
    }

    public static int calculateFrequency(UserId userId) {
        Optional<Station> existingStation = getStation(userId);
        if (existingStation.isPresent()) {
            return existingStation.get().frequency();
        }

        List<Station> stations = getStations();
        Set<Integer> allFrequencies = stations.stream().map(Station::frequency).collect(Collectors.toSet());

        Random random = new Random();
        int randomFrequency = 990;
        for (int i = 0; i < MAX_FREQUENCIES; i++) {
            randomFrequency = random.nextInt(870, 1070);
            if (!allFrequencies.contains(randomFrequency)) {
                break;
            }
        }
        createStation(userId, randomFrequency);
        return randomFrequency;
    }

    private static Station toStation(ResultSet rs) throws SQLException {
        return new Station(rs.getString("token"), rs.getString("name"), rs.getInt("frequency"));
    }
}
