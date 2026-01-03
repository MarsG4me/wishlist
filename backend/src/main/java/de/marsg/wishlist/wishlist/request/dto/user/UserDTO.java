package de.marsg.wishlist.wishlist.request.dto.user;

import java.util.UUID;

public class UserDTO {

    private final UUID id;
    private final String firstName;
    private final String lastName;
    private final String email;

    private final UUID sessionID;

    public UserDTO(String id, String firstName, String lastName, String email, String sessionID) {
        this.id = UUID.fromString(id);
        this.firstName = firstName;
        this.lastName = lastName;
        this.email = email;
        this.sessionID = UUID.fromString(sessionID);
    }

    public UUID getId() {
        return id;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public String getEmail() {
        return email;
    }

    public UUID getSessionID(){
        return sessionID;
    }

}
