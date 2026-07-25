# Noyyal Store — Full-Stack E-Commerce Platform

A production-ready e-commerce platform built with React 18 and Spring Boot 3.2.5.

## Tech Stack
- Frontend: React 18 (Create React App), CSS Design System
- Backend: Java 17, Spring Boot 3.2.5, Spring Security, JWT Auth
- Database: PostgreSQL 15
- Cache: Redis 7
- Payments: Razorpay
- Email: Resend
- Build: Maven, npm

## Architecture
Four role types: CUSTOMER, SELLER, ADMIN, SUPER_ADMIN
- JWT access tokens (24h) + refresh tokens (7d) with rotation
- Role-based route guards (frontend + backend enforcement)
- Seller product ownership enforcement
- Audit logging for auth events, role changes, and privileged actions
- Super Admin impersonation with time-limited sessions
- Redis caching for read-heavy public endpoints

## Prerequisites
- Java 17+
- Node.js 20+
- PostgreSQL 15+
- Redis 7+ (optional, app works without it)
- Maven 3.9+

## Quick Start (Local)

### 1. Database
Create a PostgreSQL database:
```sql
CREATE DATABASE noyyalstore;
```

### 2. Environment
Copy `.env.example` to `.env` and adjust values:
```bash
cp .env.example .env
```

### 3. Backend
```bash
cd backend-java
mvn spring-boot:run
# Starts on http://localhost:9091
```

### 4. Frontend
```bash
cd frontend
npm install
npm start
# Starts on http://localhost:3000
```

### 5. Build & Serve (Production)
```bash
cd frontend
npm run build
npx serve -s build -l 3000
```

## Docker
```bash
docker-compose up --build
# Frontend: http://localhost:3000
# Backend:  http://localhost:9091
```

## Seed Accounts

| Role | Email | Password |
|------|-------|----------|
| Customer | customer@noyyalstore.com | admin123 |
| Seller | seller@noyyalstore.com | seller123 |
| Admin | admin@noyyalstore.com | admin123 |
| Super Admin | super@noyyalstore.com | super123 |

## API Endpoints

### Authentication
| Method | Path | Access |
|--------|------|--------|
| POST | /api/auth/register | Public |
| POST | /api/auth/login | Public |
| POST | /api/auth/refresh | Public (refresh token) |
| POST | /api/auth/logout | Public |
| POST | /api/auth/change-password | Authenticated |
| POST | /api/auth/request-password-reset | Public |
| POST | /api/auth/reset-password | Public |
| GET | /api/auth/me | Authenticated |

### Role-Specific Login
| Method | Path | Role |
|--------|------|------|
| POST | /api/user/login | Any |
| POST | /api/seller/login | SELLER |
| POST | /api/admin/login | ADMIN, SUPER_ADMIN |

### Super Admin
| Method | Path | Role |
|--------|------|------|
| GET | /api/super-admin/dashboard | SUPER_ADMIN |
| GET | /api/super-admin/users | SUPER_ADMIN |
| POST | /api/super-admin/users | SUPER_ADMIN |
| PUT | /api/super-admin/users/{id} | SUPER_ADMIN |
| PUT | /api/super-admin/users/{id}/role | SUPER_ADMIN |
| PUT | /api/super-admin/users/{id}/status | SUPER_ADMIN |
| POST | /api/super-admin/users/{id}/reset-password | SUPER_ADMIN |
| DELETE | /api/super-admin/users/{id} | SUPER_ADMIN |
| GET | /api/super-admin/settings | SUPER_ADMIN |
| PUT | /api/super-admin/settings | SUPER_ADMIN |
| POST | /api/super-admin/impersonate/start | SUPER_ADMIN |
| POST | /api/super-admin/impersonate/stop | SUPER_ADMIN |
| GET | /api/super-admin/impersonate/sessions | SUPER_ADMIN |
| GET | /api/super-admin/impersonate/log | SUPER_ADMIN |
| GET | /api/super-admin/activity-log | SUPER_ADMIN |

## Features
- [x] JWT Auth with refresh token rotation
- [x] 4 roles: Customer, Seller, Admin, Super Admin
- [x] Seller product ownership enforcement
- [x] Public product browsing (no login required)
- [x] Customer account: profile, orders, wishlist, cart, addresses
- [x] Seller dashboard & product management
- [x] Admin dashboard & store management
- [x] Super Admin dashboard, user management, impersonation
- [x] Platform settings (store, security, payment, email, tax)
- [x] Audit logging for security events
- [x] Redis caching for public data
- [x] Password reset with OTP
- [x] Responsive CSS design system
- [x] Docker support
- [x] CI/CD (GitHub Actions)

## Environment Variables

See `.env.example` for all required variables.

## Testing
```bash
cd backend-java
mvn test

cd frontend
npm test
```

## Project Structure
```
noyyal-store/
├── backend-java/
│   ├── src/main/java/com/noyyalstore/
│   │   ├── config/        # JWT, Security, CORS, Cache
│   │   ├── controller/    # REST controllers
│   │   ├── dto/          # Request/Response DTOs
│   │   ├── entities/     # JPA entities
│   │   ├── exception/    # Error handling
│   │   ├── repository/   # JPA repositories
│   │   └── service/      # Business logic
│   └── src/test/         # Integration tests
├── frontend/
│   ├── src/
│   │   ├── admin/        # Admin pages
│   │   ├── components/   # Shared components
│   │   ├── context/      # Auth context
│   │   ├── layouts/      # Layouts
│   │   ├── pages/        # Customer pages
│   │   ├── styles/       # CSS design system
│   │   └── utils/        # Axios instance
│   └── public/
├── docker-compose.yml
├── .env.example
└── README.md
```

## License
MIT
