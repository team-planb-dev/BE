
🚀 Project Name : TravelJang(가제)
---

# 📌 About

* *Why was this project created?*
    * Travelers with chronic conditions often need to consider medication schedules, meal timing, dietary restrictions, and physical activity in addition to sightseeing. Existing travel services primarily focus on recommending destinations, without taking individual health conditions into account.<br><br>
* *What problem does it solve?*
    * The project generates personalized travel itineraries by combining public tourism data with users’ health conditions and travel preferences. It helps users enjoy their trips more safely by considering medication schedules, meal timing, dietary preferences, walking distance, and other health-related constraints throughout the itinerary.<br><br>
* *Who is it for?*
    * This service is designed for travelers managing chronic conditions, such as diabetes, hypertension, or dyslipidemia, as well as anyone seeking a safer and more personalized travel planning experience.<br><br>
* *What are the main goals?*
    * To provide reliable AI-generated travel itineraries that balance tourism, dining, and health management by leveraging public tourism datasets, nutritional information, and AI-based itinerary planning with rule-based validation.
 


---

# 🏗️ System Architecture

Describe the overall system architecture. ( 사진첨부 )

### Components

- Frontend
- Backend
- AI Server
- Database
- External APIs

> 🗂️ Database Schema: **[ERD](docs/erd/erd.png)** ( ERD 사진 첨부 )

---

# ✨ Key Features

## 🔐 Authentication

- JWT-based authentication and authorization
- Access Token & Refresh Token management
- Redis-based Refresh Token storage
- Role-based access control (RBAC)
- Spring Security integration

---

## 🧳 Core Features (수정중)

> 🚧 Under Development

- AI-powered travel itinerary generation
- Health-aware travel scheduling
- Personalized tourism recommendations

---

## 🤖 AI Features

> 🚧 Under Development

- Natural language itinerary modification
- AI itinerary validation
- Health condition & nutrition analysis

---





# 🛠️ Tech Stack

<div align="center">

##### Language
![Java](https://img.shields.io/badge/Java-21-007396?style=flat&logo=openjdk&logoColor=white)

##### Framework
![Spring](https://img.shields.io/badge/Spring-6-6DB33F?style=flat&logo=spring&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5.x-6DB33F?style=flat&logo=springboot&logoColor=white)

##### Testing
![JUnit5](https://img.shields.io/badge/JUnit5-5.13.4-25A162?style=flat&logo=junit5&logoColor=white)
![Mockito](https://img.shields.io/badge/Mockito-5.x-78A641?style=flat)

##### Database
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?style=flat&logo=mysql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-8-DC382D?style=flat&logo=redis&logoColor=white)

##### Frontend
![React](https://img.shields.io/badge/React-19-61DAFB?style=flat&logo=react&logoColor=black)
![Tailwind CSS](https://img.shields.io/badge/Tailwind_CSS-4-06B6D4?style=flat&logo=tailwindcss&logoColor=white)

##### Monitoring
![Prometheus](https://img.shields.io/badge/Prometheus-E6522C?style=flat&logo=prometheus&logoColor=white)
![Grafana](https://img.shields.io/badge/Grafana-F46800?style=flat&logo=grafana&logoColor=white)

</div>

---

# 📁 Project Structure

```text
planB/
├── .github/
│   ├── ISSUE_TEMPLATE/
│   ├── workflows/
│   └── PULL_REQUEST_TEMPLATE.md
│
├── docs/
│
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   └── com.planb/
│   │   │       ├── domain/          # Business domains
│   │   │       ├── query/           # QueryDSL read layer
│   │   │       ├── global/          # Shared components
│   │   │       └── PlanBApplication.java
│   │   │
│   │   ├── generated/              # QueryDSL Q classes
│   │   └── resources/
│   │       ├── db/migration/        # Flyway
│   │       └── application*.yml
│   │
│   └── test/
│       ├── unit/
│       ├── slice/
│       ├── integration/
│       └── controller/
│
├── gradle/
├── build.gradle
├── settings.gradle
└── README.md
```


---

# 🚀 Getting Started

## Prerequisites

Before running the project, ensure the following software is installed:

- Java 21
- Docker & Docker Compose
- Git

---

## 1. Clone Repository

```bash
git clone https://github.com/team-planb-dev/BE.git
cd BE
```

---

## 2. Configure Environment

Configure your Spring profile and update the required properties in the corresponding `application-*.yml` file.

Typical configuration includes:

- MySQL
- Redis
- JWT Secret Key
- External API Keys

---

## 3. Start Infrastructure

Start the required infrastructure services.

```bash
docker compose up -d
```

This will start:

- MySQL
- Redis

---

## 4. Build the Project

```bash
./gradlew clean build
```

---

## 5. Run the Application

```bash
./gradlew bootRun
```

Or run the generated JAR file:

```bash
java -jar build/libs/<project-name>.jar
```

---

## 6. Verify the Application

After the application starts successfully, you can access:

- Swagger UI  
  `http://localhost:8080/swagger-ui/index.html`




---


# 📖 API Documentation

The API documentation is automatically generated using **SpringDoc OpenAPI**.

- Swagger UI  
  `http://localhost:8080/swagger-ui/index.html`

- OpenAPI Specification  
  `http://localhost:8080/v3/api-docs`

---

# 🧪 Testing

The project adopts a multi-layered testing strategy to validate each layer independently while ensuring overall system reliability.

| Test Type | Description | Framework |
|-----------|-------------|-----------|
| Unit Test | Tests individual business logic in isolation using mocked dependencies. | JUnit 5, Mockito |
| Slice Test | Verifies repository and persistence layers in an isolated Spring context. | JUnit 5, Testcontainers |
| Controller Test | Validates REST API endpoints and request/response behavior. | JUnit 5, Mockito |
| Integration Test | Tests end-to-end application flow with real infrastructure. | JUnit 5, Testcontainers |

### Test Environment

- **Framework:** JUnit 5, Mockito
- **Containerized Testing:** Testcontainers (Docker)
- **Database:** MySQL Container
- **Infrastructure:** Docker-based isolated test environment

### Run All Tests

```bash
./gradlew test
```

### Run Integration Tests

```bash
./gradlew integrationTest
```

---

# 📊 Monitoring

The project plans to monitor application performance and system health using **Prometheus** and **Grafana**. Detailed metrics and dashboards will be added as the monitoring infrastructure is finalized.

### Metrics *(Planned)*

The following metrics are planned to be monitored:

- CPU Usage
- Memory Usage
- JVM Metrics
- HTTP Request Metrics
- Database Connection Pool
- AI API Response Time

### Dashboard *(Planned)*

- Prometheus
- Grafana

> Monitoring dashboards and performance reports will be added after the monitoring environment is configured.

### Logging *(Planned)*

Application logs will be collected and analyzed to support debugging and performance monitoring.

---

# 🚢 Deployment

The deployment environment is currently under development. The following technologies are planned for automated deployment and infrastructure management.

## Infrastructure *(Planned)*

- Google Cloud Platform (GCP)
- Cloud architecture to be finalized

---

## CI/CD *(Planned)*

- GitHub Actions
- Automated build, test, and deployment pipeline

---

## Reverse Proxy *(Planned)*

- Nginx

---

## Container

- Docker
- Docker Compose (Local Development)

---


# 👥 Team

| Role | Name | GitHub |
|------|------|--------|
|PM|이승협|.|
|Designer|조예원|.|
| Backend | 강우주 | <a href="https://github.com/wooju-kang"><img src="https://github.com/wooju-kang.png?size=80" width="70"/></a> |
| Backend | 이윤서 | <a href="https://github.com/xYunaL"><img src="https://github.com/xYunaL.png?size=80" width="70"/></a> |
| Frontend | 임성은 | <a href="https://github.com/sungeunlim03"><img src="https://github.com/sungeunlim03.png?size=80" width="70"/></a> |


---

