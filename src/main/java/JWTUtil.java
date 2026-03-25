import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.JWTVerifier;
import java.util.Date;

public class JWTUtil {
    
    // Secret key for JWT signing (In production, use environment variable!)
    private static final String SECRET_KEY = System.getenv("JWT_SECRET") != null 
        ? System.getenv("JWT_SECRET") 
        : "your-secret-key-change-in-production-2024";
    
    // Token expiration time (24 hours in milliseconds)
    private static final long EXPIRATION_TIME = 24 * 60 * 60 * 1000; // 24 hours
    
    /**
     * Generate JWT token for a user
     * @param userId User ID
     * @param username Username
     * @return JWT token string
     */
    public static String generateToken(int userId, String username) {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        
        return JWT.create()
                .withIssuer("gemini-code-analyzer")
                .withSubject(String.valueOf(userId))
                .withClaim("username", username)
                .withIssuedAt(new Date())
                .withExpiresAt(new Date(System.currentTimeMillis() + EXPIRATION_TIME))
                .sign(algorithm);
    }
    
    /**
     * Verify JWT token and extract user ID
     * @param token JWT token string
     * @return User ID if valid
     * @throws JWTVerificationException if token is invalid or expired
     */
    public static int verifyToken(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer("gemini-code-analyzer")
                .build();
        
        DecodedJWT jwt = verifier.verify(token);
        
        // Extract user ID from subject
        return Integer.parseInt(jwt.getSubject());
    }
    
    /**
     * Verify token and return decoded JWT
     * @param token JWT token string
     * @return DecodedJWT object
     * @throws JWTVerificationException if token is invalid
     */
    public static DecodedJWT verifyAndDecode(String token) throws JWTVerificationException {
        Algorithm algorithm = Algorithm.HMAC256(SECRET_KEY);
        
        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer("gemini-code-analyzer")
                .build();
        
        return verifier.verify(token);
    }
    
    /**
     * Extract username from token without verification
     * @param token JWT token string
     * @return Username
     */
    public static String extractUsername(String token) {
        DecodedJWT jwt = JWT.decode(token);
        return jwt.getClaim("username").asString();
    }
}