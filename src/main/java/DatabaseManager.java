import com.google.gson.Gson;
import org.mindrot.jbcrypt.BCrypt;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    
    private static final Gson gson = new Gson();
    
    // ============================================================
    // DATABASE CONNECTION TEST
    // ============================================================
    
    public static boolean testConnection() {
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD)) {
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            System.err.println("Database connection test failed: " + e.getMessage());
            return false;
        }
    }
    
    // ============================================================
    // CODE ANALYSIS METHODS
    // ============================================================
    
    /**
     * Save code analysis result to database with user ID
     */
    public static boolean saveToDatabase(GeminiCodeAnalyzer.CodeAnalysisResult result, int userId) {
        String sql = "INSERT INTO " + DatabaseConfig.TABLE_NAME +
                " (user_id, original_code, timestamp, language, has_errors, errors, output, suggestions, optimal_code) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Convert arrays to JSON strings for storage
            String errorsJson = gson.toJson(result.errors);
            String suggestionsJson = gson.toJson(result.suggestions);
            
            pstmt.setInt(1, userId);
            pstmt.setString(2, result.originalCode);
            pstmt.setString(3, result.timestamp);
            pstmt.setString(4, result.language);
            pstmt.setBoolean(5, result.hasErrors);
            pstmt.setString(6, errorsJson);
            pstmt.setString(7, result.output);
            pstmt.setString(8, suggestionsJson);
            pstmt.setString(9, result.optimalCode);
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Failed to save to database: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    /**
     * Get recent analysis history for a specific user
     */
    public static List<HistoryItem> getRecentHistory(int userId, int limit) {
        String sql = "SELECT id, language, has_errors, timestamp, " +
                "LEFT(original_code, 100) as code_preview " +
                "FROM " + DatabaseConfig.TABLE_NAME + " " +
                "WHERE user_id = ? " +
                "ORDER BY id DESC LIMIT ?";
        
        List<HistoryItem> history = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.setInt(2, limit);
            
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                HistoryItem item = new HistoryItem();
                item.id = rs.getInt("id");
                item.language = rs.getString("language");
                item.hasErrors = rs.getBoolean("has_errors");
                item.timestamp = rs.getString("timestamp");
                item.preview = rs.getString("code_preview");
                history.add(item);
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to retrieve history: " + e.getMessage());
        }
        
        return history;
    }
    
    /**
     * Get specific analysis by ID (with user verification)
     */
    public static GeminiCodeAnalyzer.CodeAnalysisResult getAnalysisById(int id, int userId) {
        String sql = "SELECT * FROM " + DatabaseConfig.TABLE_NAME + 
                " WHERE id = ? AND user_id = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            pstmt.setInt(2, userId);
            
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                GeminiCodeAnalyzer.CodeAnalysisResult result = 
                    new GeminiCodeAnalyzer.CodeAnalysisResult();
                
                result.originalCode = rs.getString("original_code");
                result.timestamp = rs.getString("timestamp");
                result.language = rs.getString("language");
                result.hasErrors = rs.getBoolean("has_errors");
                
                // Deserialize JSON arrays
                String errorsJson = rs.getString("errors");
                result.errors = errorsJson != null ? 
                    gson.fromJson(errorsJson, String[].class) : new String[0];
                
                result.output = rs.getString("output");
                
                String suggestionsJson = rs.getString("suggestions");
                result.suggestions = suggestionsJson != null ? 
                    gson.fromJson(suggestionsJson, String[].class) : new String[0];
                
                result.optimalCode = rs.getString("optimal_code");
                
                return result;
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to retrieve analysis: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get statistics for a specific user
     */
    public static Stats getStats(int userId) {
        String sql = "SELECT COUNT(*) as total, " +
                "SUM(CASE WHEN has_errors = 1 THEN 1 ELSE 0 END) as with_errors " +
                "FROM " + DatabaseConfig.TABLE_NAME + " " +
                "WHERE user_id = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                int total = rs.getInt("total");
                int withErrors = rs.getInt("with_errors");
                double errorRate = total > 0 ? (withErrors * 100.0 / total) : 0.0;
                
                return new Stats(total, withErrors, errorRate);
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to retrieve statistics: " + e.getMessage());
        }
        
        return new Stats(0, 0, 0.0);
    }
    
    // ============================================================
    // USER AUTHENTICATION METHODS
    // ============================================================
    
    /**
     * Check if username already exists
     */
    public static boolean userExists(String username) {
        String sql = "SELECT COUNT(*) FROM users WHERE username = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("Error checking username: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Check if email already exists
     */
    public static boolean emailExists(String email) {
        String sql = "SELECT COUNT(*) FROM users WHERE email = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            
        } catch (SQLException e) {
            System.err.println("Error checking email: " + e.getMessage());
        }
        
        return false;
    }
    
    /**
     * Create new user with hashed password
     */
    public static User createUser(String username, String email, String password, String fullName) {
        String sql = "INSERT INTO users (username, email, password_hash, full_name) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            // Hash password using BCrypt
            String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
            
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, fullName);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                // Get generated user ID
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    
                    // Retrieve and return the complete user object
                    return getUserById(userId);
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to create user: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
    
    /**
     * Authenticate user with username and password
     */
    public static User authenticateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND is_active = 1";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, username);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                
                // Verify password using BCrypt
                if (BCrypt.checkpw(password, storedHash)) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setPasswordHash(storedHash);
                    user.setFullName(rs.getString("full_name"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    user.setLastLogin(rs.getTimestamp("last_login"));
                    user.setActive(rs.getBoolean("is_active"));
                    
                    return user;
                }
            }
            
        } catch (SQLException e) {
            System.err.println("Authentication failed: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Get user by ID
     */
    public static User getUserById(int userId) {
        String sql = "SELECT * FROM users WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setPasswordHash(rs.getString("password_hash"));
                user.setFullName(rs.getString("full_name"));
                user.setCreatedAt(rs.getTimestamp("created_at"));
                user.setLastLogin(rs.getTimestamp("last_login"));
                user.setActive(rs.getBoolean("is_active"));
                
                return user;
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to get user: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Update user's last login timestamp
     */
    public static boolean updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login = CURRENT_TIMESTAMP WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            int rowsAffected = pstmt.executeUpdate();
            
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Failed to update last login: " + e.getMessage());
            return false;
        }
    }
    
    // ============================================================
    // ADMIN/DATABASE VIEW METHODS
    // ============================================================
    
    /**
     * Get all users (excluding password hashes) - Admin feature
     */
    public static List<User> getAllUsers() {
        String sql = "SELECT id, username, email, full_name, created_at, last_login, is_active FROM users ORDER BY id DESC";
        List<User> users = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                User user = new User();
                user.setId(rs.getInt("id"));
                user.setUsername(rs.getString("username"));
                user.setEmail(rs.getString("email"));
                user.setFullName(rs.getString("full_name"));
                user.setCreatedAt(rs.getTimestamp("created_at"));
                user.setLastLogin(rs.getTimestamp("last_login"));
                user.setActive(rs.getBoolean("is_active"));
                // Note: password_hash is NOT included for security
                
                users.add(user);
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to retrieve all users: " + e.getMessage());
        }
        
        return users;
    }
    
    /**
     * Get all analyses from all users - Admin feature
     */
    public static List<AnalysisRecord> getAllAnalyses() {
        String sql = "SELECT a.id, a.user_id, u.username, a.original_code, a.language, " +
                "a.has_errors, a.timestamp, a.errors, a.suggestions, a.optimal_code " +
                "FROM " + DatabaseConfig.TABLE_NAME + " a " +
                "LEFT JOIN users u ON a.user_id = u.id " +
                "ORDER BY a.id DESC";
        
        List<AnalysisRecord> analyses = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL,
                DatabaseConfig.DB_USER,
                DatabaseConfig.DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                AnalysisRecord record = new AnalysisRecord();
                record.id = rs.getInt("id");
                record.userId = rs.getInt("user_id");
                record.username = rs.getString("username");
                record.originalCode = rs.getString("original_code");
                record.language = rs.getString("language");
                record.hasErrors = rs.getBoolean("has_errors");
                record.timestamp = rs.getString("timestamp");
                
                // Deserialize JSON arrays
                String errorsJson = rs.getString("errors");
                record.errors = errorsJson != null ? 
                    gson.fromJson(errorsJson, String[].class) : new String[0];
                
                String suggestionsJson = rs.getString("suggestions");
                record.suggestions = suggestionsJson != null ? 
                    gson.fromJson(suggestionsJson, String[].class) : new String[0];
                
                record.optimalCode = rs.getString("optimal_code");
                
                analyses.add(record);
            }
            
        } catch (SQLException e) {
            System.err.println("Failed to retrieve all analyses: " + e.getMessage());
        }
        
        return analyses;
    }
    
    // ============================================================
    // DATA CLASSES
    // ============================================================
    
    public static class HistoryItem {
        public int id;
        public String language;
        public boolean hasErrors;
        public String timestamp;
        public String preview;
    }
    
    public static class Stats {
        public int totalAnalyses;
        public int withErrors;
        public double errorRate;
        
        public Stats(int totalAnalyses, int withErrors, double errorRate) {
            this.totalAnalyses = totalAnalyses;
            this.withErrors = withErrors;
            this.errorRate = Math.round(errorRate * 100.0) / 100.0; // Round to 2 decimals
        }
    }
    
    public static class AnalysisRecord {
        public int id;
        public int userId;
        public String username;
        public String originalCode;
        public String language;
        public boolean hasErrors;
        public String timestamp;
        public String[] errors;
        public String[] suggestions;
        public String optimalCode;
    }
}