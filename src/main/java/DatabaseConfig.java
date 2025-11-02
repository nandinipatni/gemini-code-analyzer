public class DatabaseConfig {
    // Database connection settings
    public static final String DB_URL = "jdbc:mysql://localhost:3306/code_analyzer";
    public static final String DB_USER = "root";
    public static final String DB_PASSWORD = "nandini"; // CHANGE THIS!
    
    // Table name
    public static final String TABLE_NAME = "code_analysis";
    
    // Gemini API Key (use environment variable or hardcode for testing)
    public static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY") != null 
        ? System.getenv("GEMINI_API_KEY") 
        : "AIzaSyDC-2sOsofh8Z3oxFfdVJBUZxlXqi5MNDg"; // CHANGE THIS!
}