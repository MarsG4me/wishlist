package de.marsg.wishlist.wishlist.request;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.marsg.wishlist.wishlist.request.handler.JwtToUserDto;
import de.marsg.wishlist.wishlist.services.WishService;

@RestController
@RequestMapping("/api/wishes")
public class WishController {

    private final JwtToUserDto jwtToDto;
    private final WishService wishService;

    public WishController(JwtToUserDto jwtToDto, WishService wishService) {
        this.jwtToDto = jwtToDto;
        this.wishService = wishService;
    }

    
}
