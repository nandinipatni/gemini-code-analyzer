import org.mindrot.jbcrypt.BCrypt;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;

import java.sql.*;
import java.util.Date;
import java.util.UUID;

public class AuthManager {
    
    private static final String JWT_SECRET = "your-secret-key-change-in-production"; // CHANGE THIS!
    private static final Algorithm algorithm = Algorithm.HMAC256(JWT_SECRET);
    private static final long TOKEN_EXPIRY = 24 * 60 * 60 * 1000; // 24 hours
    
    // Register new user
    public static RegisterResult register(String username, String email, String password, String fullName) {
        // Validate input
        if (username == null || username.trim().length() < 3) {
            return new RegisterResult(false, "Username must be at least 3 characters", null);
        }
        
        if (email == null || !email.contains("@")) {
            return new RegisterResult(false, "Invalid email address", null);
        }
        
        if (password == null || password.length() < 6) {
            return new RegisterResult(false, "Password must be at least 6 characters", null);
        }
        
        // Check if username or email already exists
        String checkSql = "SELECT id FROM users WHERE username = ? OR email = ?";
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(checkSql)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new RegisterResult(false, "Username or email already exists", null);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            return new RegisterResult(false, "Database error occurred", null);
        }
        
        // Hash password
        String passwordHash = BCrypt.hashpw(password, BCrypt.gensalt(12));
        
        // Insert user
        String insertSql = "INSERT INTO users (username, email, password_hash, full_name) VALUES (?, ?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(insertSql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, passwordHash);
            pstmt.setString(4, fullName);
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet generatedKeys = pstmt.getGeneratedKeys();
                if (generatedKeys.next()) {
                    int userId = generatedKeys.getInt(1);
                    User user = new User();
                    user.setId(userId);
                    user.setUsername(username);
                    user.setEmail(email);
                    user.setFullName(fullName);
                    
                    return new RegisterResult(true, "Registration successful", user);
                }
            }
            
            return new RegisterResult(false, "Failed to create user", null);
            
        } catch (SQLException e) {
            e.printStackTrace();
            return new RegisterResult(false, "Database error occurred", null);
        }
    }
    
    // Login user
    public static LoginResult login(String usernameOrEmail, String password) {
        String sql = "SELECT * FROM users WHERE (username = ? OR email = ?) AND is_active = TRUE";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, usernameOrEmail);
            pstmt.setString(2, usernameOrEmail);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                
                // Verify password
                if (BCrypt.checkpw(password, storedHash)) {
                    User user = new User();
                    user.setId(rs.getInt("id"));
                    user.setUsername(rs.getString("username"));
                    user.setEmail(rs.getString("email"));
                    user.setFullName(rs.getString("full_name"));
                    user.setCreatedAt(rs.getTimestamp("created_at"));
                    
                    // Generate token
                    String token = generateToken(user.getId(), user.getUsername());
                    
                    // Update last login
                    updateLastLogin(user.getId());
                    
                    // Save session
                    saveSession(user.getId(), token);
                    
                    return new LoginResult(true, "Login successful", user, token);
                } else {
                    return new LoginResult(false, "Invalid password", null, null);
                }
            } else {
                return new LoginResult(false, "User not found", null, null);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            return new LoginResult(false, "Database error occurred", null, null);
        }
    }
    
    // Generate JWT token
    private static String generateToken(int userId, String username) {
        return JWT.create()
                .withIssuer("code-analyzer")
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + TOKEN_EXPIRY))
                .sign(algorithm);
    }
    
    // Verify token
    public static VerifyResult verifyToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer("code-analyzer")
                    .build()
                    .verify(token);
            
            int userId = Integer.parseInt(jwt.getSubject());
            String username = jwt.getClaim("username").asString();
            
            // Check if session exists in database
            if (isSessionValid(token)) {
                User user = new User();
                user.setId(userId);
                user.setUsername(username);
                return new VerifyResult(true, user);
            } else {
                return new VerifyResult(false, null);
            }
            
        } catch (JWTVerificationException e) {
            return new VerifyResult(false, null);
        }
    }
    
    // Save session to database
    private static void saveSession(int userId, String token) {
        String sql = "INSERT INTO user_sessions (user_id, session_token, expires_at) VALUES (?, ?, ?)";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.setString(2, token);
            pstmt.setTimestamp(3, new Timestamp(System.currentTimeMillis() + TOKEN_EXPIRY));
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Check if session is valid
    private static boolean isSessionValid(String token) {
        String sql = "SELECT id FROM user_sessions WHERE session_token = ? AND expires_at > NOW()";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, token);
            ResultSet rs = pstmt.executeQuery();
            
            return rs.next();
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Update last login time
    private static void updateLastLogin(int userId) {
        String sql = "UPDATE users SET last_login = NOW() WHERE id = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            pstmt.executeUpdate();
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    // Logout (invalidate session)
    public static boolean logout(String token) {
        String sql = "DELETE FROM user_sessions WHERE session_token = ?";
        
        try (Connection conn = DriverManager.getConnection(
                DatabaseConfig.DB_URL, DatabaseConfig.DB_USER, DatabaseConfig.DB_PASSWORD);
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, token);
            int rowsAffected = pstmt.executeUpdate();
            
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }
    
    // Result classes
    public static class RegisterResult {
        public boolean success;
        public String message;
        public User user;
        
        public RegisterResult(boolean success, String message, User user) {
            this.success = success;
            this.message = message;
            this.user = user;
        }
    }
    
    public static class LoginResult {
        public boolean success;
        public String message;
        public User user;
        public String token;
        
        public LoginResult(boolean success, String message, User user, String token) {
            this.success = success;
            this.message = message;
            this.user = user;
            this.token = token;
        }
    }
    
    public static class VerifyResult {
        public boolean valid;
        public User user;
        
        public VerifyResult(boolean valid, User user) {
            this.valid = valid;
            this.user = user;
        }
    }
}