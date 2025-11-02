import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import spark.Request;
import spark.Response;
import static spark.Spark.*;

public class CodeAnalyzerServer {
    
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    public static void main(String[] args) {
        // IMPORTANT: Serve static files FIRST, before any other configuration
        staticFiles.location("/public");
        
        // Set port
        port(4567);
        
        // Enable CORS for frontend access
        enableCORS();
        
        System.out.println("=".repeat(70));
        System.out.println("🚀 Code Analyzer Server Started!");
        System.out.println("=".repeat(70));
        System.out.println("📊 Frontend: http://localhost:4567");
        System.out.println("🔌 API Base: http://localhost:4567/api");
        System.out.println("💚 Health Check: http://localhost:4567/api/health");
        System.out.println("=".repeat(70));
        System.out.println("\nPress Ctrl+C to stop the server\n");
        
        // Health check endpoint
        get("/api/health", (req, res) -> {
            res.type("application/json");
            return "{\"status\":\"OK\",\"message\":\"Server is running\",\"database\":\"" + 
                   (DatabaseManager.testConnection() ? "Connected" : "Disconnected") + "\"}";
        });
        
        // Main analysis endpoint
        post("/api/analyze", CodeAnalyzerServer::analyzeCode);
        
        // Get analysis history
        get("/api/history", CodeAnalyzerServer::getHistory);
        
        // Get specific analysis by ID
        get("/api/analysis/:id", CodeAnalyzerServer::getAnalysisById);
        
        // Stats endpoint
        get("/api/stats", CodeAnalyzerServer::getStats);
    }
    
    // Enable CORS for all routes
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
            response.header("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
            response.header("Access-Control-Allow-Headers", "Content-Type,Authorization,X-Requested-With");
        });
    }
    
    // Analyze code endpoint
    private static String analyzeCode(Request req, Response res) {
        res.type("application/json");
        
        try {
            System.out.println("📥 Received analysis request...");
            
            // Parse request body
            AnalyzeRequest request = gson.fromJson(req.body(), AnalyzeRequest.class);
            
            if (request.code == null || request.code.trim().isEmpty()) {
                res.status(400);
                return gson.toJson(new ErrorResponse("Code cannot be empty"));
            }
            
            System.out.println("🔍 Analyzing code (" + request.code.length() + " characters)...");
            
            // Use your existing GeminiCodeAnalyzer
            String code = request.code;
            GeminiCodeAnalyzer.CodeAnalysisResult result = GeminiCodeAnalyzer.analyzeCodeStatic(code);
            
            System.out.println("✓ Analysis complete: " + result.language + " | Errors: " + result.hasErrors);
            
            // Save to database
            boolean saved = DatabaseManager.saveToDatabase(result);
            
            if (saved) {
                System.out.println("✓ Saved to database");
            } else {
                System.out.println("✗ Failed to save to database");
            }
            
            // Return result
            res.status(200);
            return gson.toJson(new AnalyzeResponse(result, saved));
            
        } catch (Exception e) {
            res.status(500);
            System.err.println("❌ Analysis error: " + e.getMessage());
            e.printStackTrace();
            return gson.toJson(new ErrorResponse("Analysis failed: " + e.getMessage()));
        }
    }
    
    // Get history endpoint
    private static String getHistory(Request req, Response res) {
        res.type("application/json");
        
        try {
            int limit = req.queryParams("limit") != null ? Integer.parseInt(req.queryParams("limit")) : 10;
            var history = DatabaseManager.getRecentHistory(limit);
            
            System.out.println("📚 Retrieved " + history.size() + " history items");
            
            res.status(200);
            return gson.toJson(history);
            
        } catch (Exception e) {
            res.status(500);
            System.err.println("❌ History fetch error: " + e.getMessage());
            return gson.toJson(new ErrorResponse("Failed to fetch history: " + e.getMessage()));
        }
    }
    
    // Get analysis by ID
    private static String getAnalysisById(Request req, Response res) {
        res.type("application/json");
        
        try {
            int id = Integer.parseInt(req.params(":id"));
            var analysis = DatabaseManager.getAnalysisById(id);
            
            if (analysis != null) {
                System.out.println("📄 Retrieved analysis #" + id);
                res.status(200);
                return gson.toJson(analysis);
            } else {
                res.status(404);
                return gson.toJson(new ErrorResponse("Analysis not found"));
            }
            
        } catch (Exception e) {
            res.status(500);
            System.err.println("❌ Analysis fetch error: " + e.getMessage());
            return gson.toJson(new ErrorResponse("Failed to fetch analysis: " + e.getMessage()));
        }
    }
    
    // Get stats
    private static String getStats(Request req, Response res) {
        res.type("application/json");
        
        try {
            var history = DatabaseManager.getRecentHistory(1000);
            int total = history.size();
            int withErrors = 0;
            for (var item : history) {
                if (item.hasErrors) withErrors++;
            }
            
            Stats stats = new Stats();
            stats.totalAnalyses = total;
            stats.withErrors = withErrors;
            stats.errorRate = total > 0 ? (withErrors * 100.0 / total) : 0;
            
            res.status(200);
            return gson.toJson(stats);
            
        } catch (Exception e) {
            res.status(500);
            return gson.toJson(new ErrorResponse("Failed to fetch stats: " + e.getMessage()));
        }
    }
    
    // Request/Response classes
    static class AnalyzeRequest {
        String code;
        String language;
    }
    
    static class AnalyzeResponse {
        GeminiCodeAnalyzer.CodeAnalysisResult result;
        boolean savedToDatabase;
        
        AnalyzeResponse(GeminiCodeAnalyzer.CodeAnalysisResult result, boolean saved) {
            this.result = result;
            this.savedToDatabase = saved;
        }
    }
    
    static class ErrorResponse {
        String error;
        
        ErrorResponse(String error) {
            this.error = error;
        }
    }
    
    static class Stats {
        int totalAnalyses;
        int withErrors;
        double errorRate;
    }
}