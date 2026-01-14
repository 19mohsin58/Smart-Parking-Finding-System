package unipi.lsmdb.SPFS.Controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import unipi.lsmdb.SPFS.DTO.LoginRequestDTO;

@RestController
@RequestMapping("/api/public")
public class AuthController {

        @Autowired
        private AuthenticationManager authenticationManager;

        @Autowired
        private unipi.lsmdb.SPFS.Repositories.UserRepository userRepository;

        @Autowired
        private unipi.lsmdb.SPFS.security.JwtUtils jwtUtils;

        @PostMapping("/login")
        public ResponseEntity<?> authenticateUser(@RequestBody LoginRequestDTO loginRequest) {

                // 1. Authenticate credentials using the AuthenticationManager
                Authentication authentication = authenticationManager.authenticate(
                                new UsernamePasswordAuthenticationToken(
                                                loginRequest.getEmail(),
                                                loginRequest.getPassword()));

                // 2. Store authentication object in the Security Context
                SecurityContextHolder.getContext().setAuthentication(authentication);

                // 3. Generate JWT Token
                String jwt = jwtUtils.generateJwtToken(authentication);

                // 4. Get User Details
                org.springframework.security.core.userdetails.User springUser = (org.springframework.security.core.userdetails.User) authentication
                                .getPrincipal();
                String role = springUser.getAuthorities().stream().findFirst().map(item -> item.getAuthority())
                                .orElse("USER");

                // 5. Fetch full user entity for additional details (cityId, fullName)
                unipi.lsmdb.SPFS.Entities.Users dbUser = userRepository.findByEmail(springUser.getUsername())
                                .orElseThrow(() -> new RuntimeException("Error: User not found."));

                // 6. Check Verification Status
                if (!dbUser.isVerified()) {
                        return ResponseEntity.status(org.springframework.http.HttpStatus.FORBIDDEN)
                                        .body(new unipi.lsmdb.SPFS.DTO.MessageResponse(
                                                        "Error: Email is not verified. Please verify your email first."));
                }

                return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.JwtResponse(
                                jwt,
                                dbUser.getId(),
                                springUser.getUsername(),
                                role,
                                dbUser.getFullName(),
                                dbUser.getCity() != null ? dbUser.getCity().getId() : null,
                                dbUser.isVerified()));
        }

        @PostMapping("/logout")
        public ResponseEntity<?> logoutUser() {
                SecurityContextHolder.clearContext();
                return ResponseEntity.ok(new unipi.lsmdb.SPFS.DTO.MessageResponse("Log out successful!"));
        }
}
