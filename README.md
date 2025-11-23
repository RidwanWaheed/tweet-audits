# tweet-audit

Analyze your X (Twitter) archive using Gemini AI and flag tweets for deletion based on custom criteria.

## Project Status

**In Development** - Learning project focusing on modern Java backend practices

## Tech Stack

- **Java 21** (Latest LTS)
- **Spring Boot 3.4.0** (Modern framework)
- **Spring WebFlux** (Reactive web client - WebClient)
- **Spring Retry** (Exponential backoff retry logic)
- **Maven** (Build tool)
- **Lombok** (Reduce boilerplate)

## What This Tool Does

1. Reads your Twitter/X archive (tweets.js)
2. Analyzes each tweet against your alignment criteria using Gemini AI
3. Flags tweets for deletion (e.g., unprofessional language, regretted keywords)
4. Outputs a CSV file with flagged tweet URLs

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6+
- Gemini API key ([Get one here](https://aistudio.google.com/app/apikey))
- Twitter archive ([Request here](https://x.com/settings/download_your_data))

### Setup

1. **Clone and build:**
   ```bash
   mvn clean install
   ```

2. **Configure API key:**
   ```bash
   cp src/main/resources/application-example.properties src/main/resources/application-local.properties
   # Edit application-local.properties and add your real API key
   ```

3. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

## Project Structure

```
tweet-audit/
├── src/
│   ├── main/
│   │   ├── java/com/ridwan/tweetaudit/
│   │   │   ├── TweetAuditApplication.java   # Main entry point
│   │   │   ├── config/                       # Configuration classes
│   │   │   ├── model/                        # Domain models
│   │   │   ├── service/                      # Business logic
│   │   │   └── client/                       # External API clients
│   │   └── resources/
│   │       ├── application.properties        # Base config
│   │       └── application-example.properties # Template
│   └── test/
│       └── java/com/ridwan/tweetaudit/       # Unit tests
├── pom.xml                                    # Maven dependencies
├── TRADEOFFS.md                               # Architecture decisions
└── LEARNING_NOTES.md                          # Learning journal
```

## Configuration

Key configuration in `application.properties`:

- `gemini.api.rate-limit.requests-per-minute=10` - Respect Gemini free tier limits
- `tweet.processing.batch-size=10` - Process tweets in batches
- `tweet.processing.max-retries=3` - Retry failed API calls
- `tweet.processing.initial-backoff-ms=1000` - Exponential backoff starting point

## Learning Goals

This project focuses on:
- Modern API integration (WebFlux, WebClient)
- Error handling & retry logic (Spring Retry, exponential backoff)
- Concurrency patterns (batched processing with rate limiting)
- Testing strategies (mocking external APIs)
- Architectural decision-making (documented in TRADEOFFS.md)

## License

Personal learning project - feel free to learn from it!
