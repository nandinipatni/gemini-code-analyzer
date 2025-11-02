import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;

public class GeminiCodeAnalyzer {
    
    private static final String GEMINI_API_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";
    private static String API_KEY = "";
    
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        
        try {
            // Test database connection
            System.out.println("Testing database connection...");
            if (DatabaseManager.testConnection()) {
                System.out.println("✓ Database connected successfully!\n");
            } else {
                System.err.println("⚠ Warning: Database connection failed. Results will only be saved to JSON.\n");
            }
            
            // Display Header
            System.out.println("=".repeat(70));
            System.out.println("          GEMINI CODE ANALYZER");
            System.out.println("=".repeat(70));
            
            // Try to get API Key from environment variable first
            API_KEY = System.getenv("GEMINI_API_KEY");
            
            // If not found in environment, ask user to input
            if (API_KEY == null || API_KEY.isEmpty()) {
                System.out.print("\nEnter your Gemini API Key: ");
                API_KEY = scanner.nextLine().trim();
                
                if (API_KEY.isEmpty()) {
                    System.err.println("Error: API Key cannot be empty!");
                    return;
                }
            } else {
                System.out.println("\n✓ Using API Key from environment variable");
            }
            
            // Get Code Input
            System.out.println("\n" + "-".repeat(70));
            System.out.println("Paste your code below (Type 'END' on a new line to finish):");
            System.out.println("-".repeat(70));
            
            StringBuilder codeBuilder = new StringBuilder();
            String line;
            while (scanner.hasNextLine()) {
                line = scanner.nextLine();
                if (line.trim().equals("END")) {
                    break;
                }
                codeBuilder.append(line).append("\n");
            }
            
            String code = codeBuilder.toString().trim();
            
            if (code.isEmpty()) {
                System.err.println("Error: No code provided!");
                return;
            }
            
            System.out.println("\n" + "=".repeat(70));
            System.out.println("Analyzing code with Gemini AI...");
            System.out.println("=".repeat(70) + "\n");
            
            // Analyze code with Gemini
            CodeAnalysisResult result = analyzeCode(code, API_KEY);
            
            // Display results on console
            displayResults(result);
            
            // Save to JSON file
            String filename = saveToJson(result);
            System.out.println("\n" + "=".repeat(70));
            System.out.println("✓ Results saved to JSON: " + filename);
            
            // Save to Database
            System.out.print("Saving to database... ");
            if (DatabaseManager.saveToDatabase(result)) {
                System.out.println("✓ Saved to database successfully!");
            } else {
                System.out.println("✗ Failed to save to database.");
            }
            System.out.println("=".repeat(70));
            
        } catch (Exception e) {
            System.err.println("\n❌ Error during analysis: " + e.getMessage());
            e.printStackTrace();
        } finally {
            scanner.close();
        }
    }
    
    // Static method for API server to call
    public static CodeAnalysisResult analyzeCodeStatic(String code) throws IOException {
        String apiKey = DatabaseConfig.GEMINI_API_KEY;
        return analyzeCode(code, apiKey);
    }
    
    private static CodeAnalysisResult analyzeCode(String code, String apiKey) throws IOException {
        // Create the prompt for Gemini
        String prompt = String.format(
            "Analyze the following code and provide a detailed response in this EXACT JSON format:\n\n" +
            "{\n" +
            "  \"language\": \"detected programming language\",\n" +
            "  \"has_errors\": true/false,\n" +
            "  \"errors\": [\"list of errors if any, empty array if none\"],\n" +
            "  \"output\": \"expected output if code runs successfully, or error description\",\n" +
            "  \"suggestions\": [\"list of improvement suggestions\"],\n" +
            "  \"optimal_code\": \"optimized version of the code\"\n" +
            "}\n\n" +
            "Code to analyze:\n```\n%s\n```\n\n" +
            "Provide ONLY the JSON response, no additional text.", code
        );
        
        // Create JSON payload for Gemini API
        JsonObject parts = new JsonObject();
        parts.addProperty("text", prompt);
        
        JsonObject content = new JsonObject();
        content.add("parts", JsonParser.parseString("[" + parts + "]"));
        
        JsonObject payload = new JsonObject();
        payload.add("contents", JsonParser.parseString("[" + content + "]"));
        
        // Make API call
        URL url = new URL(GEMINI_API_ENDPOINT + "?key=" + apiKey);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        // Send request
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }
        
        // Read response
        int responseCode = conn.getResponseCode();
        InputStream is = (responseCode >= 400) ? conn.getErrorStream() : conn.getInputStream();
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
        }
        
        if (responseCode != 200) {
            throw new IOException("API Error (Code " + responseCode + "): " + response.toString());
        }
        
        // Parse Gemini response
        JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
        String aiResponse = jsonResponse
            .getAsJsonArray("candidates")
            .get(0).getAsJsonObject()
            .get("content").getAsJsonObject()
            .getAsJsonArray("parts")
            .get(0).getAsJsonObject()
            .get("text").getAsString();
        
        // Extract JSON from AI response (remove markdown code blocks if present)
        aiResponse = aiResponse.replaceAll("```json\\s*", "").replaceAll("```\\s*$", "").trim();
        
        // Parse the analysis result
        Gson gson = new Gson();
        CodeAnalysisResult result = gson.fromJson(aiResponse, CodeAnalysisResult.class);
        result.originalCode = code;
        result.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        
        return result;
    }
    
    private static void displayResults(CodeAnalysisResult result) {
        System.out.println("📋 ANALYSIS RESULTS");
        System.out.println("=".repeat(70));
        
        System.out.println("\n🔤 Detected Language: " + result.language);
        System.out.println("\n⏰ Timestamp: " + result.timestamp);
        
        System.out.println("\n" + "-".repeat(70));
        System.out.println("❌ ERRORS FOUND: " + (result.hasErrors ? "YES" : "NO"));
        System.out.println("-".repeat(70));
        
        if (result.hasErrors && result.errors != null && result.errors.length > 0) {
            for (int i = 0; i < result.errors.length; i++) {
                System.out.println("  " + (i + 1) + ". " + result.errors[i]);
            }
        } else {
            System.out.println("  ✓ No errors detected!");
        }
        
        System.out.println("\n" + "-".repeat(70));
        System.out.println("📤 OUTPUT / RESULT");
        System.out.println("-".repeat(70));
        System.out.println(result.output);
        
        System.out.println("\n" + "-".repeat(70));
        System.out.println("💡 SUGGESTIONS FOR IMPROVEMENT");
        System.out.println("-".repeat(70));
        
        if (result.suggestions != null && result.suggestions.length > 0) {
            for (int i = 0; i < result.suggestions.length; i++) {
                System.out.println("  " + (i + 1) + ". " + result.suggestions[i]);
            }
        } else {
            System.out.println("  No suggestions provided.");
        }
        
        System.out.println("\n" + "-".repeat(70));
        System.out.println("⚡ OPTIMAL CODE VERSION");
        System.out.println("-".repeat(70));
        System.out.println(result.optimalCode != null ? result.optimalCode : "No optimization provided");
    }
    
    private static String saveToJson(CodeAnalysisResult result) throws IOException {
        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(result);
        
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "code_analysis_" + timestamp + ".json";
        
        Files.write(Paths.get(filename), json.getBytes(StandardCharsets.UTF_8));
        
        return filename;
    }
    
    // Data class to hold analysis results
    public static class CodeAnalysisResult {
        public String originalCode;
        public String timestamp;
        public String language;
        public boolean hasErrors;
        public String[] errors;
        public String output;
        public String[] suggestions;
        public String optimalCode;
    }
}