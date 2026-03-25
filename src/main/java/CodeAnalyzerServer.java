import com.google.gson.Gson;
import com.google.gson.JsonObject;
import spark.Request;
import spark.Response;
import java.util.List;
import static spark.Spark.*;

public class CodeAnalyzerServer {
    
    private static final Gson gson = new Gson();
    
    public static void main(String[] args) {
        // ✅ CORRECT ORDER: These 3 lines MUST come first, in this exact order
        port(4567);
        staticFiles.location("/public");
        enableCORS();
        
        // Test database connection
        System.out.println("=".repeat(70));
        System.out.println("🚀 Starting Gemini Code Analyzer Server...");
        System.out.println("=".repeat(70));
        System.out.println("\nTesting database connection...");
        
        if (DatabaseManager.testConnection()) {
            System.out.println("✓ Database connected successfully!\n");
        } else {
            System.out.println("⚠ Warning: Database connection failed\n");
        }
        
        System.out.println("Server running at: http://localhost:4567");
        System.out.println("=".repeat(70) + "\n");
        
        // ============================================================
        // API ENDPOINTS (Define routes AFTER static files setup)
        // ============================================================
        
        // Health Check Endpoint
        get("/api/health", (req, res) -> {
            res.type("application/json");
            
            JsonObject response = new JsonObject();
            response.addProperty("status", "OK");
            response.addProperty("message", "Server is running");
            
            // Check database status
            boolean dbConnected = DatabaseManager.testConnection();
            response.addProperty("database", dbConnected ? "Connected" : "Disconnected");
            
            return response.toString();
        });
        
        // ============================================================
        // AUTHENTICATION ENDPOINTS
        // ============================================================
        
        // Register new user
        post("/api/auth/register", (req, res) -> {
            res.type("application/json");
            
            try {
                RegisterRequest registerData = gson.fromJson(req.body(), RegisterRequest.class);
                
                // Validate input
                if (registerData.username == null || registerData.username.trim().isEmpty()) {
                    res.status(400);
                    return gson.toJson(new ErrorResponse("Username is required"));
                }
                
                if (registerData.email == null || registerData.email.trim().isEmpty()) {
                    res.status(400);
                    return gson.toJson(new ErrorResponse("Email is required"));
                }
                
                if (registerData.password == null || registerData.password.length() < 6) {
                    res.status(400);
                    return gson.toJson(new ErrorResponse("Password must be at least 6 characters"));
                }
                
                // Check if username already exists
                if (DatabaseManager.userExists(registerData.username)) {
                    res.status(409);
                    return gson.toJson(new ErrorResponse("Username already exists"));
                }
                
                // Check if email already exists
                if (DatabaseManager.emailExists(registerData.email)) {
                    res.status(409);
                    return gson.toJson(new ErrorResponse("Email already exists"));
                }
                
                // Create user
                User newUser = DatabaseManager.createUser(
                    registerData.username,
                    registerData.email,
                    registerData.password,
                    registerData.fullName
                );
                
                if (newUser != null) {
                    // Generate JWT token
                    String token = JWTUtil.generateToken(newUser.getId(), newUser.getUsername());
                    
                    AuthResponse authResponse = new AuthResponse(
                        token,
                        newUser.toResponse(),
                        "Registration successful"
                    );
                    
                    res.status(201);
                    return gson.toJson(authResponse);
                } else {
                    res.status(500);
                    return gson.toJson(new ErrorResponse("Failed to create user"));
                }
                
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Registration failed: " + e.getMessage()));
            }
        });
        
        // Login user
        post("/api/auth/login", (req, res) -> {
            res.type("application/json");
            
            try {
                LoginRequest loginData = gson.fromJson(req.body(), LoginRequest.class);
                
                // Validate input
                if (loginData.username == null || loginData.username.trim().isEmpty()) {
                    res.status(400);
                    return gson.toJson(new ErrorResponse("Username is required"));
                }
                
                if (loginData.password == null || loginData.password.isEmpty()) {
                    res.status(400);
                    return gson.toJson(new ErrorResponse("Password is required"));
                }
                
                // Authenticate user
                User user = DatabaseManager.authenticateUser(loginData.username, loginData.password);
                
                if (user != null) {
                    // Update last login
                    DatabaseManager.updateLastLogin(user.getId());
                    
                    // Generate JWT token
                    String token = JWTUtil.generateToken(user.getId(), user.getUsername());
                    
                    AuthResponse authResponse = new AuthResponse(
                        token,
                        user.toResponse(),
                        "Login successful"
                    );
                    
                    return gson.toJson(authResponse);
                } else {
                    res.status(401);
                    return gson.toJson(new ErrorResponse("Invalid username or password"));
                }
                
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Login failed: " + e.getMessage()));
            }
        });
        
        // Verify token endpoint
        get("/api/auth/verify", (req, res) -> {
            res.type("application/json");
            
            String token = req.headers("Authorization");
            
            if (token == null || !token.startsWith("Bearer ")) {
                res.status(401);
                return gson.toJson(new ErrorResponse("No token provided"));
            }
            
            token = token.substring(7); // Remove "Bearer " prefix
            
            try {
                int userId = JWTUtil.verifyToken(token);
                User user = DatabaseManager.getUserById(userId);
                
                if (user != null) {
                    return gson.toJson(new TokenVerifyResponse(true, user.toResponse()));
                } else {
                    res.status(404);
                    return gson.toJson(new ErrorResponse("User not found"));
                }
            } catch (Exception e) {
                res.status(401);
                return gson.toJson(new ErrorResponse("Invalid token"));
            }
        });
        
        // ============================================================
        // CODE ANALYSIS ENDPOINTS (Protected - Require Authentication)
        // ============================================================
        
        // Analyze code endpoint
        post("/api/analyze", (req, res) -> {
            res.type("application/json");
            
            // Check authentication
            String token = req.headers("Authorization");
            int userId = -1;
            
            if (token != null && token.startsWith("Bearer ")) {
                try {
                    userId = JWTUtil.verifyToken(token.substring(7));
                } catch (Exception e) {
                    res.status(401);
                    return gson.toJson(new ErrorResponse("Invalid or expired token"));
                }
            } else {
                res.status(401);
                return gson.toJson(new ErrorResponse("Authentication required"));
            }
            
            try {
                AnalyzeRequest analyzeData = gson.fromJson(req.body(), AnalyzeRequest.class);
                
                if (analyzeData.code == null || analyzeData.code.trim().isEmpty()) {
                    res.status(400);
                    return gson.toJson(new ErrorResponse("Code cannot be empty"));
                }
                
                // Analyze code using Gemini AI with optional context
                GeminiCodeAnalyzer.CodeAnalysisResult result = 
                    (analyzeData.context != null && !analyzeData.context.trim().isEmpty())
                        ? GeminiCodeAnalyzer.analyzeCodeWithContext(analyzeData.code, analyzeData.context)
                        : GeminiCodeAnalyzer.analyzeCodeStatic(analyzeData.code);
                
                // Save to database with user ID
                boolean saved = DatabaseManager.saveToDatabase(result, userId);
                
                AnalyzeResponse response = new AnalyzeResponse(result, saved);
                return gson.toJson(response);
                
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Analysis failed: " + e.getMessage()));
            }
        });
        
        // Get analysis history for authenticated user
        get("/api/history", (req, res) -> {
            res.type("application/json");
            
            // Check authentication
            String token = req.headers("Authorization");
            int userId = -1;
            
            if (token != null && token.startsWith("Bearer ")) {
                try {
                    userId = JWTUtil.verifyToken(token.substring(7));
                } catch (Exception e) {
                    res.status(401);
                    return gson.toJson(new ErrorResponse("Invalid or expired token"));
                }
            } else {
                res.status(401);
                return gson.toJson(new ErrorResponse("Authentication required"));
            }
            
            try {
                String limitParam = req.queryParams("limit");
                int limit = (limitParam != null) ? Integer.parseInt(limitParam) : 10;
                
                List<DatabaseManager.HistoryItem> history = 
                    DatabaseManager.getRecentHistory(userId, limit);
                
                return gson.toJson(history);
                
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Failed to fetch history: " + e.getMessage()));
            }
        });
        
        // Get specific analysis by ID
        get("/api/analysis/:id", (req, res) -> {
            res.type("application/json");
            
            // Check authentication
            String token = req.headers("Authorization");
            int userId = -1;
            
            if (token != null && token.startsWith("Bearer ")) {
                try {
                    userId = JWTUtil.verifyToken(token.substring(7));
                } catch (Exception e) {
                    res.status(401);
                    return gson.toJson(new ErrorResponse("Invalid or expired token"));
                }
            } else {
                res.status(401);
                return gson.toJson(new ErrorResponse("Authentication required"));
            }
            
            try {
                int analysisId = Integer.parseInt(req.params(":id"));
                
                GeminiCodeAnalyzer.CodeAnalysisResult result = 
                    DatabaseManager.getAnalysisById(analysisId, userId);
                
                if (result != null) {
                    return gson.toJson(result);
                } else {
                    res.status(404);
                    return gson.toJson(new ErrorResponse("Analysis not found"));
                }
                
            } catch (NumberFormatException e) {
                res.status(400);
                return gson.toJson(new ErrorResponse("Invalid analysis ID"));
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Failed to fetch analysis: " + e.getMessage()));
            }
        });
        
        // Get statistics for authenticated user
        get("/api/stats", (req, res) -> {
            res.type("application/json");
            
            // Check authentication
            String token = req.headers("Authorization");
            int userId = -1;
            
            if (token != null && token.startsWith("Bearer ")) {
                try {
                    userId = JWTUtil.verifyToken(token.substring(7));
                } catch (Exception e) {
                    res.status(401);
                    return gson.toJson(new ErrorResponse("Invalid or expired token"));
                }
            } else {
                res.status(401);
                return gson.toJson(new ErrorResponse("Authentication required"));
            }
            
            try {
                DatabaseManager.Stats stats = DatabaseManager.getStats(userId);
                return gson.toJson(stats);
                
            } catch (Exception e) {
                res.status(500);
                return gson.toJson(new ErrorResponse("Failed to fetch statistics: " + e.getMessage()));
            }
        });
        
        // ============================================================
        // DATABASE VIEW ENDPOINTS (Protected)
        // ============================================================
        
        // Get all users (admin feature)
        get("/api/users", (req, res) -> {
            res.type("application/json");
            
            // Check authentication
            String token = req.headers("Authorization");
            
            if (token == null || !token.startsWith("Bearer ")) {
                res.status(401);
                return gson.toJson(new ErrorResponse("Authentication required"));
            }
            
            try {
                int userId = JWTUtil.verifyToken(token.substring(7));
                List<User> users = DatabaseManager.getAllUsers();
                return gson.toJson(users);
            } catch (Exception e) {
                res.status(401);
                return gson.toJson(new ErrorResponse("Invalid token"));
            }
        });
        
        // Get all analyses (admin feature)
        get("/api/analyses", (req, res) -> {
            res.type("application/json");
            
            // Check authentication
            String token = req.headers("Authorization");
            
            if (token == null || !token.startsWith("Bearer ")) {
                res.status(401);
                return gson.toJson(new ErrorResponse("Authentication required"));
            }
            
            try {
                int userId = JWTUtil.verifyToken(token.substring(7));
                List<DatabaseManager.AnalysisRecord> analyses = DatabaseManager.getAllAnalyses();
                return gson.toJson(analyses);
            } catch (Exception e) {
                res.status(401);
                return gson.toJson(new ErrorResponse("Invalid token"));
            }
        });
        
        // ============================================================
        // PUBLIC ENDPOINTS (No authentication required)
        // ============================================================
        
        // Get supported languages
        get("/api/languages", (req, res) -> {
            res.type("application/json");
            
            String[] languages = {
                "Auto Detect",
                "Java",
                "Python",
                "JavaScript",
                "C++",
                "C#",
                "Go",
                "Rust",
                "PHP",
                "TypeScript",
                "Kotlin",
                "Swift",
                "Ruby"
            };
            
            return gson.toJson(languages);
        });
        
        // 404 handler
        notFound((req, res) -> {
            res.type("application/json");
            return gson.toJson(new ErrorResponse("Endpoint not found"));
        });
        
        // Exception handler
        exception(Exception.class, (exception, req, res) -> {
            res.type("application/json");
            res.status(500);
            res.body(gson.toJson(new ErrorResponse("Internal server error: " + exception.getMessage())));
        });
    }
    
    // ============================================================
    // CORS CONFIGURATION
    // ============================================================
    
    private static void enableCORS() {
        options("/*", (request, response) -> {
            String accessControlRequestHeaders = request.headers("Access-Control-Request-Headers");
            if (accessControlRequestHeaders != null) {
                response.header("Access-Control-Allow-Headers", accessControlRequestHeaders);
            }

            String accessControlRequestMethod = request.headers("Access-Control-Request-Method");
            if (accessControlRequestMethod != null) {
                response.header("Access-Control-Allow-Methods", accessControlRequestMethod);
            }

            return "OK";
        });

        before((request, response) -> {
            response.header("Access-Control-Allow-Origin", "*");
            response.header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With");
            response.header("Access-Control-Allow-Credentials", "true");
        });
    }
    
    // ============================================================
    // REQUEST/RESPONSE DATA CLASSES
    // ============================================================
    
    // Register request
    static class RegisterRequest {
        String username;
        String email;
        String password;
        String fullName;
    }
    
    // Login request
    static class LoginRequest {
        String username;
        String password;
    }
    
    // Analyze request
    static class AnalyzeRequest {
        String code;
        String language;
        String context;  // NEW: Optional context
    }
    
    // Authentication response
    static class AuthResponse {
        String token;
        User.UserResponse user;
        String message;
        
        AuthResponse(String token, User.UserResponse user, String message) {
            this.token = token;
            this.user = user;
            this.message = message;
        }
    }
    
    // Token verification response
    static class TokenVerifyResponse {
        boolean valid;
        User.UserResponse user;
        
        TokenVerifyResponse(boolean valid, User.UserResponse user) {
            this.valid = valid;
            this.user = user;
        }
    }
    
    // Analyze response
    static class AnalyzeResponse {
        GeminiCodeAnalyzer.CodeAnalysisResult result;
        boolean savedToDatabase;
        
        AnalyzeResponse(GeminiCodeAnalyzer.CodeAnalysisResult result, boolean saved) {
            this.result = result;
            this.savedToDatabase = saved;
        }
    }
    
    // Error response
    static class ErrorResponse {
        String error;
        
        ErrorResponse(String error) {
            this.error = error;
        }    }
}