package com.devintel.smartrepo.Security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.util.Date;

@Component
public class JwtUtil {

    private static SecretKey key= Keys.hmacShaKeyFor(JwtConstant.SECRET_KEY.getBytes());

    public static String generateToken (Authentication auth){
        String jwt= Jwts.builder()
                .setIssuer("Blog Platform").setIssuedAt(new Date())
                .setExpiration(new Date(new Date().getTime()+8600000))
                .claim("email",auth.getName())
                .signWith(key)
                .compact();

        return jwt;

    }

    public static String getEmailFromToken(String jwt){
        jwt=jwt.substring(7);
        Claims claims= Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(jwt).getBody();

        String email=String.valueOf(claims.get("email"));
        return email;
    }
}
