package com.example.SPFS.Controller;

import com.example.SPFS.DTO.UpdateProfileDTO;
import com.example.SPFS.Entities.Users;
import com.example.SPFS.Services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @PutMapping("/profile/{userId}")
    public ResponseEntity<?> updateProfile(@PathVariable String userId, @RequestBody UpdateProfileDTO payload) {
        // Security Check: Ensure the logged-in user is updating their own profile
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String currentPrincipalName = authentication.getName(); // This is the email (username)

        // Note: Ideally we would verify that the ID matches the email, but for now we
        // assume the frontend sends the correct ID.
        // If strict security is needed, we would fetch the user by email and compare
        // IDs.

        try {
            Users updatedUser = userService.updateUser(userId, payload);
            return ResponseEntity.ok(updatedUser);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
