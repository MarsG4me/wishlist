package de.marsg.wishlist.wishlist.services;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import de.marsg.wishlist.wishlist.configs.CacheConfig;
import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.repository.GroupMembershipRepository;
import de.marsg.wishlist.wishlist.data.jpa.repository.UserRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.users.ReturnOwnUserDTO;
import de.marsg.wishlist.wishlist.request.dto.users.ReturnSimpleUserDTO;
import de.marsg.wishlist.wishlist.request.dto.users.UserDTO;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final GroupMembershipRepository membershipRepository;

    private final Cache sessionCache;
    private final LogMgr log;

    public UserService(UserRepository userRepository, CacheConfig cacheConfig, LogMgr log,
            GroupMembershipRepository membershipRepository) {
        this.userRepository = userRepository;
        this.sessionCache = cacheConfig.cacheManager().getCache("sessionCache");
        this.log = log;
        this.membershipRepository = membershipRepository;
    }

    public ResponseEntity<Object> fetchOwnUserInfo(UserDTO userDto) {
        Optional<User> user = userRepository.findById(userDto.getId());

        if (user.isEmpty()) {
            log.logWarning("User %s tried to fetch a user that doesn't exist. (SELF!!)", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "User not found.",
                            "errId", "USER_NOT_FOUND"));
        }

        return ResponseEntity.ok().body(
                new ReturnOwnUserDTO(
                        user.get().getKeycloakId(),
                        user.get().getFirstName(),
                        user.get().getLastName(),
                        user.get().getEmail()));

    }

    public ResponseEntity<Object> fetchOtherUserInfo(UUID userId, UserDTO userDto) {
        Optional<User> user = userRepository.findById(userId);

        if (user.isEmpty()) {
            log.logInfo("User %s tried to fetch a user that doesn't exist.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                    Map.of("error", "User not found.",
                            "errId", "USER_NOT_FOUND"));
        }

        if (membershipRepository.existsGroupWithBothUsers(userId, userDto.getId())) {
            log.logInfo("User %s tried to fetch a user that shares no common group.", userDto.getFirstName());
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                    Map.of("error", "User shares no common group.",
                            "errId", "USER_HAS_NO_COMMON_GROUP"));
        }

        return ResponseEntity.ok()
                .body(new ReturnSimpleUserDTO(userId, user.get().getFirstName(), user.get().getLastName()));

    }

    /*
     * Helper functions
     */

    public boolean ensuredUserExistance(UserDTO user) {
        // Check cache first
        if (isCached(user)) {
            log.logInfo("User %s was cached.", user.getFirstName());
            return true;
        }

        // Check database
        else if (userRepository.findById(user.getId()).isPresent()) {
            // Update cache
            sessionCache.put(user.getId(), user.getSessionID());
            log.logInfo("User %s was put into cached.", user.getFirstName());
            return true;
        }

        // Add new user
        else {
            User newUser = new User(user.getId());
            newUser.setFirstName(user.getFirstName());
            newUser.setLastName(user.getLastName());
            newUser.setEmail(user.getEmail());

            log.logInfo("User %s was created in system.", user.getFirstName());

            return userRepository.save(newUser).getKeycloakId() == user.getId();
        }
    }

    public User getUser(UserDTO dto) {
        Optional<User> user = userRepository.findById(dto.getId());

        if (user.isEmpty()) {
            return null;
        } else {
            return user.get();
        }

    }

    private boolean isCached(UserDTO user) {
        UUID knownSessionId = sessionCache.get(user.getId(), UUID.class);

        return knownSessionId != null && knownSessionId.equals(user.getSessionID());
    }

}
