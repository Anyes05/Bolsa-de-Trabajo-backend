package uy.ccisj.api.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/auth/me/foto")
public class ProfilePhotoController {
    private final ProfilePhotoService photos;

    public ProfilePhotoController(ProfilePhotoService photos) {
        this.photos = photos;
    }

    @PostMapping
    public PhotoResponse upload(Authentication authentication, @RequestParam("file") MultipartFile file) {
        return new PhotoResponse(photos.upload(authentication.getName(), file));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication authentication) {
        photos.remove(authentication.getName());
    }

    public record PhotoResponse(String fotoUrl) {}
}
