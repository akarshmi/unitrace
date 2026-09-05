# UniTrace

**University Lost & Found Platform**

UniTrace is an enterprise‑scale Lost & Found management system designed for universities with 50,000+ active users. It provides a secure, mobile‑first way for students to report lost or found items, upload images, and leverage AI‑powered matching to reunite owners with their belongings.

Built with a modern, decoupled architecture, UniTrace is free to develop locally and scales seamlessly to production using cloud configuration changes.

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
    - [Implemented](#implemented)
    - [Planned](#planned)
- [Tech Stack](#tech-stack)
- [Architecture](#architecture)
    - [High‑Level Design](#highlevel-design)
    - [Authentication Flow](#authentication-flow)
    - [Item Management](#item-management)
    - [Image Upload](#image-upload)
    - [AI Matching (Roadmap)](#ai-matching-roadmap)
- [Getting Started](#getting-started)
    - [Prerequisites](#prerequisites)
    - [Configuration](#configuration)
    - [Database Setup](#database-setup)
    - [Running the Application](#running-the-application)
- [API Endpoints](#api-endpoints)
- [Project Structure](#project-structure)
- [Roadmap](#roadmap)
- [Contributing](#contributing)
- [License](#license)

---

## Overview

UniTrace addresses a common problem on large campuses: the inefficiency of lost‑and‑found offices. Students often rely on physical bulletin boards or scattered social media posts. UniTrace centralises reporting, provides image‑based search, and will soon offer AI‑driven matching to automatically suggest potential matches between lost and found items.

The platform is designed with security and scalability in mind:
- **Stateless backend** – easy to horizontally scale.
- **Decoupled components** – independent services for storage, AI, and database.
- **Cost‑effective local development** – free tools (Ollama, local PostgreSQL, Redis) that can be swapped for cloud equivalents.

---

## Features

### Implemented

- **User Registration & Verification**
    - Signup with university email (e.g., `@lpu.in`) and self‑entered Registration Number.
    - OTP‑based email verification (6‑digit, HMAC‑hashed in Redis, 5‑min TTL).
    - Password hashing using Argon2 (Spring Security `PasswordEncoder`).
    - Pending registration data stored in Redis until OTP verification completes; user account only created after successful verification.
- **Authentication & Session Management**
    - JWT access tokens (short‑lived) + refresh tokens (long‑lived) using the JTI model.
    - Refresh tokens sent via **HttpOnly, Secure, SameSite cookies** – never exposed in JSON responses.
    - Refresh token revocation tracked in PostgreSQL (single active refresh token per user).
    - Rate limiting and lockout mechanisms for OTP attempts.
- **Item Reporting (Lost & Found)**
    - Create, retrieve, and list items via REST API.
    - Single endpoint for both lost and found items using an `ItemType` enum (`LOST` / `FOUND`).
    - Item details include title, description, category, location, date, and status.
- **Image Upload & Storage**
    - Multipart file upload integrated with **Cloudinary** (S3‑compatible).
    - Magic‑byte validation to ensure uploaded files are legitimate images (not just based on content‑type header).
    - Images stored under a dedicated folder (`unitrace/items`) and served via CDN.
- **Global Exception Handling**
    - Typed exceptions for authentication, OTP, and resource errors.
    - Consistent JSON error responses.

### Planned

- **AI‑Based Item Matching**
    - Generate embeddings from item text and images using Spring AI.
    - Vector similarity search in PostgreSQL with `pgvector`.
    - Automatic suggestion of potential matches to users.
- **User Notifications**
    - Email/in‑app notifications when a potential match is found.
- **Contact / Submit‑at‑Location Flow**
    - Streamlined process for returning found items through designated campus locations.
- **Flutter Mobile App**
    - Cross‑platform frontend with image capture, caching, and seamless API integration.
- **Multi‑device Refresh Token Support**
    - Allow multiple active refresh tokens per user for better UX.

---

## Tech Stack

| Component          | Technology                                                                 |
|--------------------|-----------------------------------------------------------------------------|
| **Backend**        | Spring Boot (Java 17+), Spring Security, Spring AI                         |
| **Frontend**       | Flutter (planned)                                                          |
| **Database**       | PostgreSQL 16+ with `pgvector` extension                                   |
| **Cache**          | Redis (for OTP, pending registration, rate limiting)                       |
| **Object Storage** | Cloudinary (or AWS S3)                                                     |
| **AI**             | Spring AI + Ollama (local dev) → OpenAI / Azure AI (production)            |
| **Email**          | JavaMailSender (SMTP) – async with dedicated thread pool                    |
| **Build Tool**     | Maven                                                                      |

---

## Architecture

### High‑Level Design

```
[Flutter App] 
    |
    | HTTPS (JSON + Multipart)
    v
[Spring Boot REST API] ---- [PostgreSQL + pgvector]
    |            | 
    |            +---- [Redis]
    |            |
    |            +---- [Cloudinary/S3]
    |            |
    |            +---- [AI Service (Ollama / OpenAI)]
    |
    +---- [SMTP Server]
```

- **Stateless API** – no server‑side sessions; all state maintained in tokens and database.
- **Decoupled storage** – images are uploaded directly to Cloudinary/S3 from the backend; the backend only stores URLs.
- **AI service** – independent, can be swapped by changing configuration.

### Authentication Flow

1. **Registration**
    - User submits name, email, registration number, password.
    - Password is hashed with Argon2; pending data stored in Redis with a short TTL.
    - A 6‑digit OTP is generated, HMAC‑hashed, and stored in Redis (5‑min TTL). An email with the OTP is sent.
2. **OTP Verification**
    - User submits OTP along with their email.
    - OTP hash is verified atomically (Lua script) – if correct, pending data is retrieved, user account is created in PostgreSQL, and pending data is removed.
3. **Login**
    - User provides email + password. On success:
        - Access token (JWT, 15 min) returned in response body.
        - Refresh token (JWT with `jti`) set as an **HttpOnly cookie**.
        - A `RefreshToken` row is created in the database with the `jti` and revocation status.
4. **Token Refresh**
    - Client sends request to `/auth/refresh`; the refresh token is read from the cookie.
    - Server validates the JWT, checks the `jti` in the database for revocation, and issues a new access token (and optionally rotates the refresh token).
5. **Logout**
    - Client calls `/auth/logout`; server revokes the refresh token (marks it as revoked in DB) and clears the cookie.

### Item Management

- **Create Item**: `POST /items` with JSON body (title, description, type, etc.) and optional image upload (multipart). Image is uploaded to Cloudinary first, then the item is persisted with the image URL.
- **Retrieve Item**: `GET /items/{id}` returns item details including image URL.
- **List Items**: `GET /items` with optional filters (type, category, status, pagination).

### Image Upload

- Endpoint accepts `multipart/form-data` with item data and an image file.
- File bytes are validated using magic numbers to prevent malicious uploads.
- Validated image is streamed to Cloudinary (or S3) with a unique identifier.
- The returned CDN URL is stored with the item entity.

### AI Matching (Roadmap)

- **Embedding Generation**: Using Spring AI, item titles/descriptions are converted to vector embeddings (e.g., `text-embedding-3-small` or a local model via Ollama).
- **Storage**: Embeddings are stored in PostgreSQL using the `pgvector` column type.
- **Similarity Search**: When a new item is reported (lost or found), the system performs a vector similarity query to find potential matches of the opposite type.
- **Notification**: Users are notified about candidate matches; they can then contact each other or follow the return flow.

---

## Getting Started

### Prerequisites

- **Java 17** or higher
- **Maven 3.9+**
- **PostgreSQL 16** with `pgvector` extension enabled
- **Redis 7** (local or Docker)
- **Cloudinary account** (or AWS S3 bucket)
- **SMTP server** (e.g., MailHog for local testing, Gmail SMTP for dev)
- (Optional) **Ollama** running locally for AI features

### Configuration

Copy `.env.example` to `.env` (or set environment variables) and adjust the values:

```properties
# Database
DB_URL=jdbc:postgresql://localhost:5432/unitrace
DB_USERNAME=postgres
DB_PASSWORD=postgres

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Cloudinary
CLOUDINARY_CLOUD_NAME=your_cloud_name
CLOUDINARY_API_KEY=your_api_key
CLOUDINARY_API_SECRET=your_api_secret

# JWT
JWT_SECRET=your-256-bit-secret-key
JWT_ACCESS_TOKEN_EXPIRY=900000   # 15 minutes in ms
JWT_REFRESH_TOKEN_EXPIRY=2592000000 # 30 days in ms

# Email (SMTP)
SMTP_HOST=smtp.gmail.com
SMTP_PORT=587
SMTP_USERNAME=your_email@gmail.com
SMTP_PASSWORD=your_app_password
SMTP_AUTH=true
SMTP_STARTTLS=true

# AI (optional for Phase 4)
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3
```

### Database Setup

1. Create a PostgreSQL database:
   ```sql
   CREATE DATABASE unitrace;
   ```
2. Enable `pgvector`:
   ```sql
   CREATE EXTENSION IF NOT EXISTS vector;
   ```
3. The application will automatically create tables using JPA/Hibernate on first run (ensure `spring.jpa.hibernate.ddl-auto=update` is set in `application.yml`).

### Running the Application

1. Clone the repository:
   ```bash
   git clone https://github.com/akarshmi/unitrace.git
   cd unitrace
   ```
2. Build the project:
   ```bash
   mvn clean install
   ```
3. Run the Spring Boot application:
   ```bash
   mvn spring-boot:run
   ```
4. The API will be available at `http://localhost:8080`.

---

## API Endpoints

| Method | Endpoint               | Description                                   | Auth Required |
|--------|------------------------|-----------------------------------------------|---------------|
| POST   | `/api/auth/register`   | Register a new user (sends OTP)               | No            |
| POST   | `/api/auth/verify-otp` | Verify OTP and create account                 | No            |
| POST   | `/api/auth/login`      | Login, returns access token + refresh cookie  | No            |
| POST   | `/api/auth/refresh`    | Refresh access token using refresh cookie     | No (cookie)   |
| POST   | `/api/auth/logout`     | Revoke refresh token and clear cookie         | Yes           |
| POST   | `/api/items`           | Create a lost/found item (with optional image)| Yes           |
| GET    | `/api/items/{id}`      | Get item details                              | Yes           |
| GET    | `/api/items`           | List items (filters, pagination)              | Yes           |
| PATCH  | `/api/items/{id}/status`| Update item status                            | Yes           |

*Detailed request/response schemas can be found in the API documentation (Swagger UI will be added soon).*

---

## Project Structure

```
src/main/java/dev/akarshmi/unitrace/
├── auth/                 # Authentication & registration
│   ├── controller/       # AuthController
│   ├── dto/              # Request/response DTOs
│   ├── service/          # AuthService, OtpService, PendingRegistrationService
│   ├── entity/           # User, RefreshToken
│   └── config/           # Security config, JWT filter
├── item/                 # Item management
│   ├── controller/       # ItemController
│   ├── dto/              # ItemRequest, ItemResponse
│   ├── service/          # ItemService
│   ├── entity/           # Item (with ItemType enum)
│   └── repository/       # ItemRepository
├── image/                # Image upload/storage
│   ├── service/          # CloudinaryService, ImageValidationService
│   └── config/           # Cloudinary configuration
├── common/               # Shared utilities
│   ├── exception/        # GlobalExceptionHandler, custom exceptions
│   ├── config/           # Redis, Mail, etc.
│   └── util/             # EmailHashUtil, CookieService
└── UnitraceApplication.java
```

---

## Roadmap

- **Phase 1: Authentication** ✅ *Completed* – Registration, OTP, login, refresh, logout.
- **Phase 2: Item CRUD** ✅ *Completed* – Create, read, list, update status.
- **Phase 3: Image Upload** ✅ *Completed* – Multipart upload to Cloudinary with validation.
- **Phase 4: AI Embedding Generation** 🔄 *In Progress* – Integrate Spring AI + Ollama, generate embeddings from item text (and later images).
- **Phase 5: Vector Similarity Search** ⏳ – Store embeddings in `pgvector`, implement matching queries.
- **Phase 6: Notifications** ⏳ – Email/in‑app alerts for potential matches.
- **Phase 7: Return Flow** ⏳ – Submit‑at‑location workflow.
- **Phase 8: Flutter Mobile App** ⏳ – Cross‑platform frontend.

---

## Contributing

Contributions are welcome! Please fork the repository and submit a pull request. For major changes, open an issue first to discuss what you would like to change.

---

## License

This project is licensed under the MIT License – see the [LICENSE](LICENSE) file for details.

---

*Built with ❤️ for universities to help students find what they’ve lost.*