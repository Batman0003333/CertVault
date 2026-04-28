# Certificate Uploader      https://certvault-1.onrender.com---------------------------------------

A simple Spring Boot application for user registration, login, course listing, and certificate uploads.

## Features

- User registration and login
- H2 database storage for users, courses, categories, and certificate files
- Dashboard showing user progress and uploaded certificates
- Course list with upload form for certificate files (PNG, JPG, PDF, DOC)
- Basic dynamic UI using Thymeleaf

## Run

1. Make sure you have Java 17 installed.
2. Build and run with Maven:

```bash
mvn spring-boot:run
```

3. Open `http://localhost:8080` in your browser.
4. Use the registration page to create a new user, then login.

## Database Console

H2 console is available at `http://localhost:8080/h2-console`.

- JDBC URL: `jdbc:h2:file:./data/certdb`
- User: `sa`
- Password: (empty)
