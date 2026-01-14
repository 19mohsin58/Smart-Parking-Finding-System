package unipi.lsmdb.SPFS.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import java.security.Key;
import java.util.Date;

@Component
public class JwtUtils {

    // IMPORTANT: Change this secure key in application.properties!
    @Value("${jwt.secret:QzRmBGJFviiO62cgM0YY5WypcyvtUUjfkI5aDJgwt4dLz6BQKuaKChKynUlhzQzRmBGJFviiO62cgM0YY5WypcyvtUUjfkI5aDJgwt4dLz6BQKuaKChKynUlhz}")
    private String jwtSecret;

    // Token valid for 24 hours
    @Value("${jwt.expiration.ms:86400000}")
    private int jwtExpirationMs;

    // Generates the token upon successful login
    public String generateJwtToken(Authentication authentication) {
        String userEmail = authentication.getName();

        return Jwts.builder()
                .setSubject(userEmail)
                .setIssuedAt(new Date())
                .setExpiration(new Date((new Date()).getTime() + jwtExpirationMs))
                .signWith(key(), SignatureAlgorithm.HS512)
                .compact();
    }

    private Key key() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtSecret));
    }

    public String getUserNameFromJwtToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key()).build()
                .parseClaimsJws(token).getBody().getSubject();
    }

    public boolean validateJwtToken(String authToken) {
        try {
            Jwts.parserBuilder().setSigningKey(key()).build().parse(authToken);
            return true;
        } catch (MalformedJwtException | ExpiredJwtException | UnsupportedJwtException | IllegalArgumentException e) {
            // Log the error
        }
        return false;
    }
}
