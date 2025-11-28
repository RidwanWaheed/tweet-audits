# tweet-audit

Analyze your X (Twitter) archive using Gemini AI and flag tweets for deletion based on custom criteria.

## Overview

Request an archive of your posts on X, analyse them using Google's Gemini AI, and flag tweets for deletion based on any criteria. For instance:
* Posts containing certain words you no longer want associated with you
* Phrases that aren't professional
* Old opinions you've moved on from
* Any custom alignment rules you define

### Learning Focus

This is a learning project focusing on modern Java backend practices and production-ready patterns:

- **File I/O operations** for parsing large datasets (X archive format)
- **HTTP client patterns** and third-party API integration (Google Gemini)
- **Rate limiting and backpressure handling** (adaptive rate limiting, daily quota tracking)
- **Concurrent processing patterns** (batching vs sequential vs full async)
- **Error recovery strategies** (retry logic, checkpointing, partial failures)
- **State management** (tracking processed tweets, resumable workflows)
- **Configuration management** and secrets handling
- **Writing testable code** with external dependencies

For detailed architectural decisions and tradeoffs, see [TRADEOFFS.md](TRADEOFFS.md)

## Tech Stack

- **Java 21** (Latest LTS)
- **Spring Boot 3.4.0** (Modern framework)
- **Spring WebFlux** (Reactive web client - WebClient)
- **Spring Retry** (Exponential backoff retry logic)
- **Maven** (Build tool)
- **Lombok** (Reduce boilerplate)

## Output Format

The application generates a CSV file at `results/flagged_tweets.csv` with the following columns:

```csv
tweetUrl,tweetId,status,matchedCriteria,reason
https://x.com/i/status/12345,12345,FLAGGED,forbidden_words|unprofessional,Contains forbidden content
https://x.com/i/status/67890,67890,ERROR,,API timeout after 3 retries
```

**Status Types:**
- `FLAGGED` - Tweet flagged for deletion based on alignment criteria
- `ERROR` - Evaluation failed (network issues, API errors, etc.)

**Note:** Clean tweets (not flagged and no errors) are NOT included in the output CSV.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     TweetAuditService                           │
│                   (Main Orchestrator)                           │
│                                                                 │
│  1. Loads tweets                                                │
│  2. Processes in batches                                        │
│  3. Collects results                                            │
│  4. Writes output                                               │
└────┬────────────────┬────────────────┬────────────────┬─────────┘
     │                │                │                │
     │ parseTweets()  │ evaluate()     │ evaluate()     │ writeResults()
     ▼                ▼                ▼                ▼
┌─────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Archive     │  │ Gemini       │  │ Alignment    │  │ CSV          │
│ Parser      │  │ Client       │  │ Criteria     │  │ Writer       │
│             │  │              │  │              │  │              │
│ - Parse JS  │  │ - Build req  │  │ - Config     │  │ - Filter     │
│ - Filter RT │  │ - Call API   │  │ - Criteria   │  │ - Escape     │
│ - Return    │  │ - Parse resp │  │ - Context    │  │ - Format     │
│   List<T>   │  │ - Retry      │  │              │  │ - Write CSV  │
└─────────────┘  └──────┬───────┘  └──────────────┘  └──────────────┘
                        │
                        │ HTTP POST
                        ▼
                 ┌──────────────┐
                 │ Gemini API   │
                 │ (External)   │
                 │              │
                 │ - Evaluate   │
                 │ - Return     │
                 │   JSON       │
                 └──────────────┘
```

## Quick Start

### Prerequisites

- Java 21 or higher
- Maven 3.6+

### Setup

1. **Request Your X Archive**

   1. Go to [x.com](https://x.com) and log in
   2. Navigate to: More → Settings and privacy → Your account → Download an archive of your data
   3. Verify your identity
   4. Wait 24-48 hours for the email with download link
   5. Extract the ZIP file and locate the `tweets.js` file
   6. Place it in the `my_data/` folder in this project (gitignored for privacy)

   **Note:** The project comes with sample data in `src/main/resources/sample-data/tweets-sample.js` for testing.

2. **Get Gemini API Key**

   1. Visit [Google AI Studio](https://aistudio.google.com/app/apikey)
   2. Create an API key
   3. Set it as an environment variable:
      ```bash
      export GEMINI_API_KEY=your_gemini_api_key_here
      ```

3. **Clone and build:**
   ```bash
   mvn clean install
   ```

4. **Run the application:**
   ```bash
   mvn spring-boot:run
   ```

5. **Check the results:**

   Flagged tweets and errors will be written to `results/flagged_tweets.csv`

### Running Tests

Run all 20 unit and integration tests:

```bash
mvn test
```

Test coverage includes:
- Domain model builders and validation
- Archive parsing and tweet filtering
- Gemini API client with retry logic
- CSV writer with proper escaping
- End-to-end service orchestration

## Configuration

### Environment Variables

Set your Gemini API key:
```bash
export GEMINI_API_KEY=your_gemini_api_key_here
```

### Alignment Criteria

Customize what gets flagged by editing these settings in [application.properties](src/main/resources/application.properties):

```properties
# What content to flag
alignment.forbidden-words=hate speech, racial slurs, doxxing, threats of violence, sexual harassment, extremist rhetoric, personally identifiable information (PII)

# Your context (who you are, what matters to you)
alignment.context=Just a normal internet user but also mindful of his digital footprint. The goal is to be authentic and relatable without posting content that would be considered toxic or cancellable by a future employer.

# Your desired tone
alignment.desired-tone=Casual, witty, authentic, relatable, clear. Avoids being overly reactive, aggressive, or strictly corporate.

# Optional: Check for unprofessional language
alignment.check-professionalism=false
```

For advanced configuration options (API settings, batch size, paths), see [application.properties](src/main/resources/application.properties).

## License

Personal learning project - feel free to learn from it!
