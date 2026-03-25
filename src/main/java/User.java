import java.sql.Timestamp;

public class User {
    private int id;
    private String username;
    private String email;
    private String passwordHash;
    private String fullName;
    private Timestamp createdAt;
    private Timestamp lastLogin;
    private boolean isActive;
    
    // Constructors
    public User() {}
    
    public User(String username, String email, String passwordHash, String fullName) {
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.fullName = fullName;
    }
    
    // Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    
    public String getFullName() { return fullName; }
    public void setFullName(String fullName) { this.fullName = fullName; }
    
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    
    public Timestamp getLastLogin() { return lastLogin; }
    public void setLastLogin(Timestamp lastLogin) { this.lastLogin = lastLogin; }
    
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }
    
    // For JSON serialization (without password hash)
    public UserResponse toResponse() {
        return new UserResponse(id, username, email, fullName, createdAt, lastLogin);
    }
    
    // Nested class for API responses (excludes sensitive data)
    public static class UserResponse {
        public int id;
        public String username;
        public String email;
        public String fullName;
        public Timestamp createdAt;
        public Timestamp lastLogin;
        
        public UserResponse(int id, String username, String email, String fullName, 
                          Timestamp createdAt, Timestamp lastLogin) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.fullName = fullName;
            this.createdAt = createdAt;
            this.lastLogin = lastLogin;
        }
    }
}