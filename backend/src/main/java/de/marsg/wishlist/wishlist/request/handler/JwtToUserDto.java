package de.marsg.wishlist.wishlist.request.handler;

import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

import de.marsg.wishlist.wishlist.request.dto.user.UserDTO;

@Component
public class JwtToUserDto {
    
    private static final String JWT_KEYCLOAK_ID = "sub";
    private static final String JWT_EMAIL = "email";
    private static final String JWT_FIRST_NAME = "given_name";
    private static final String JWT_LAST_NAME = "family_name";
    private static final String JWT_SESSION_ID = "sid";

    public UserDTO convert(Jwt jwt){
        return new UserDTO(
                jwt.getClaimAsString(JWT_KEYCLOAK_ID),
                jwt.getClaimAsString(JWT_FIRST_NAME),
                jwt.getClaimAsString(JWT_LAST_NAME),
                jwt.getClaimAsString(JWT_EMAIL),
                jwt.getClaimAsString(JWT_SESSION_ID));
    }
}
