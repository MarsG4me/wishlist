package de.marsg.wishlist.wishlist.services;

import java.util.Optional;
import java.util.UUID;

import org.springframework.cache.Cache;
import org.springframework.stereotype.Service;

import de.marsg.wishlist.wishlist.configs.CacheConfig;
import de.marsg.wishlist.wishlist.data.jpa.entity.User;
import de.marsg.wishlist.wishlist.data.jpa.repository.UserRepository;
import de.marsg.wishlist.wishlist.logging.LogMgr;
import de.marsg.wishlist.wishlist.request.dto.users.UserDTO;

@Service
public class UserService {

    private UserRepository userRepository;
    private final Cache sessionCache;
    private final LogMgr log;

    public UserService(UserRepository userRepository, CacheConfig cacheConfig, LogMgr log){
        this.userRepository = userRepository;
        this.sessionCache = cacheConfig.cacheManager().getCache("sessionCache");
        this.log = log;
    }

    public boolean ensuredUserExistance(UserDTO user){
        //Check cache first
        if (isCached(user)) {
            log.logInfo("User %s was cached.", user.getFirstName());
            return true;
        }

        //Check database
        else if(userRepository.findById(user.getId()).isPresent()){
            //Update cache
            sessionCache.put(user.getId(), user.getSessionID());
            log.logInfo("User %s was put into cached.", user.getFirstName());
            return true;
        }

        //Add new user
        else{
            User newUser = new User(user.getId());
            newUser.setFirstName(user.getFirstName());
            newUser.setLastName(user.getLastName());
            newUser.setEmail(user.getEmail());

            log.logInfo("User %s was created in system.", user.getFirstName());

            return userRepository.save(newUser).getKeycloakId() == user.getId();
        }
    }

    public User getUser(UserDTO dto){
        Optional<User> user = userRepository.findById(dto.getId());

        if (user.isEmpty()) {
            return null;
        }else{
            return user.get();
        }
        
    }

    private boolean isCached(UserDTO user){
        UUID knownSessionId =  sessionCache.get(user.getId(), UUID.class);

        return knownSessionId != null && knownSessionId.equals(user.getSessionID());
    }


}
