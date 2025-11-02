import com.google.gson.Gson;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class DatabaseManager {
    
    // Save analysis result to database
    public static boolean saveToDatabase(GeminiCodeAnalyzer.CodeAnalysisResult result) {
        String sql = "INSERT INTO " + DatabaseConfig.TABLE_NAME + 
                     " (original_code, timestamp, language, has_errors, errors, output, suggestions, optimal_code) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, 
                DatabaseConfig.DB_USER, 
                DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            // Convert arrays to JSON strings for storage
            Gson gson = new Gson();
            String errorsJson = (result.errors != null) ? gson.toJson(result.errors) : null;
            String suggestionsJson = (result.suggestions != null) ? gson.toJson(result.suggestions) : null;
            
            // Set parameters
            pstmt.setString(1, result.originalCode);
            pstmt.setString(2, result.timestamp);
            pstmt.setString(3, result.language);
            pstmt.setBoolean(4, result.hasErrors);
            pstmt.setString(5, errorsJson);
            pstmt.setString(6, result.output);
            pstmt.setString(7, suggestionsJson);
            pstmt.setString(8, (result.optimalCode != null) ? result.optimalCode : "");
            
            // Execute insert
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Database Error: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // Test database connection
    public static boolean testConnection() {
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, 
                DatabaseConfig.DB_USER, 
                DatabaseConfig.DB_PASSWORD)) {
            return true;
        } catch (SQLException e) {
            System.err.println("Failed to connect to database: " + e.getMessage());
            return false;
        }
    }
    
    // Get recent history
    public static List<HistoryItem> getRecentHistory(int limit) {
        String sql = "SELECT id, language, has_errors, timestamp, " +
                     "LEFT(original_code, 100) as code_preview " +
                     "FROM " + DatabaseConfig.TABLE_NAME + " " +
                     "ORDER BY id DESC LIMIT ?";
        
        List<HistoryItem> history = new ArrayList<>();
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, limit);
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
            System.err.println("Error fetching history: " + e.getMessage());
        }
        
        return history;
    }
    
    // Get analysis by ID
    public static GeminiCodeAnalyzer.CodeAnalysisResult getAnalysisById(int id) {
        String sql = "SELECT * FROM " + DatabaseConfig.TABLE_NAME + " WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, id);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                Gson gson = new Gson();
                GeminiCodeAnalyzer.CodeAnalysisResult result = new GeminiCodeAnalyzer.CodeAnalysisResult();
                
                result.originalCode = rs.getString("original_code");
                result.timestamp = rs.getString("timestamp");
                result.language = rs.getString("language");
                result.hasErrors = rs.getBoolean("has_errors");
                
                String errorsJson = rs.getString("errors");
                String suggestionsJson = rs.getString("suggestions");
                
                result.errors = errorsJson != null ? gson.fromJson(errorsJson, String[].class) : new String[0];
                result.output = rs.getString("output");
                result.suggestions = suggestionsJson != null ? gson.fromJson(suggestionsJson, String[].class) : new String[0];
                result.optimalCode = rs.getString("optimal_code");
                
                return result;
            }
            
        } catch (SQLException e) {
            System.err.println("Error fetching analysis: " + e.getMessage());
        }
        
        return null;
    }
    
    // Helper class
    public static class HistoryItem {
        public int id;
        public String language;
        public boolean hasErrors;
        public String timestamp;
        public String preview;
    }
}