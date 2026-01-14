// package unipi.lsmdb.SPFS.util;

// import org.springframework.boot.CommandLineRunner;
// import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
// import org.springframework.stereotype.Component;

// import java.util.Scanner;

// @Component
// public class HashGeneratorRunner implements CommandLineRunner {

// // IMPORTANT: This class will run automatically when you start the Spring
// Boot
// // app.
// @Override
// public void run(String... args) throws Exception {
// System.out.println("--- BCRYPT PASSWORD HASH GENERATOR ---");
// System.out.println("Enter the password (e.g., adminpass123) you want to
// hash:");

// try (Scanner scanner = new Scanner(System.in)) {
// String password = scanner.nextLine();

// // We use the same encoder as defined in SecurityConfig
// BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
// String hashedPassword = encoder.encode(password);

// System.out.println("\n------------------------------------------------");
// System.out.println("COPY THIS HASH for MongoDB Compass 'password' field:");
// System.out.println(hashedPassword);
// System.out.println("------------------------------------------------\n");

// } catch (Exception e) {
// System.err.println("Error reading input.");
// }

// // Ensure you stop the application after getting the hash if running
// separately.
// // If you are using your IDE's run button, you can stop it manually.
// }
// }
