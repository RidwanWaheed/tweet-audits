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

## Architecture

### Component Communication Flow

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

### Component Responsibilities

**TweetAuditService** (Orchestrator)
- Entry point via CommandLineRunner
- Coordinates all components
- Implements batch processing logic
- Rate limiting between batches
- Error aggregation

**ArchiveParser**
- Reads tweets.js file
- Parses JavaScript to JSON
- Deserializes to Tweet objects
- Filters out retweets
- Returns `List<Tweet>`

**GeminiClient**
- Builds Gemini API requests
- Makes HTTP calls via WebClient
- Parses API responses
- Handles retries with exponential backoff
- Returns `TweetEvaluationResult`

**AlignmentCriteria** (Configuration)
- Loaded from application.properties
- Provides forbidden words list
- Defines evaluation context
- Specifies desired tone
- Injected into GeminiClient

**CSVWriter**
- Filters flagged tweets and errors
- Escapes CSV special characters
- Formats output columns
- Writes to results/ folder

### Data Flow

```
tweets.js → ArchiveParser → List<Tweet> → TweetAuditService
                                              │
                                              ▼
                                         For each tweet:
                                              │
                    ┌─────────────────────────┴─────────────────────┐
                    │                                               │
                    ▼                                               │
    GeminiClient.evaluateTweet(tweet, criteria)                    │
                    │                                               │
                    ├─ Build request with criteria                  │
                    ├─ POST to Gemini API                          │
                    ├─ Parse response                              │
                    └─ Return TweetEvaluationResult ───────────────┘
                                              │
                                              ▼
                                    List<TweetEvaluationResult>
                                              │
                                              ▼
                               CSVWriter.writeResults(results)
                                              │
                                              ▼
                                    results/flagged_tweets.csv
```

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

2. **Set up your Twitter archive:**

   The project comes with sample data in `src/main/resources/sample-data/tweets-sample.js` for testing.

   To use your real Twitter archive:
   - Download your Twitter archive from X/Twitter
   - Extract the `tweets.js` file
   - Place it in the `data/` folder (gitignored for privacy)

3. **Configure environment variables:**
   ```bash
   export GEMINI_API_KEY=your_gemini_api_key_here
   export TWITTER_ARCHIVE_PATH=data/tweets.js  # or use default sample data
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

## Project Structure

```
tweet-audit/
├── src/
│   ├── main/
│   │   ├── java/com/ridwan/tweetaudit/
│   │   │   ├── TweetAuditApplication.java    # Main entry point
│   │   │   ├── config/                        # Configuration classes
│   │   │   ├── model/                         # Domain models (Tweet, TweetEvaluationResult)
│   │   │   ├── dto/                           # Gemini API DTOs
│   │   │   ├── parser/                        # Archive parser
│   │   │   ├── client/                        # Gemini API client
│   │   │   ├── output/                        # CSV writer
│   │   │   └── service/                       # Main orchestration service
│   │   └── resources/
│   │       ├── application.properties         # Configuration
│   │       └── sample-data/
│   │           └── tweets-sample.js           # Sample tweets for testing
│   └── test/
│       └── java/com/ridwan/tweetaudit/        # Unit & integration tests
├── data/                                       # Your real Twitter archive (gitignored)
├── results/                                    # Output CSV files (gitignored)
├── pom.xml                                     # Maven dependencies
├── TRADEOFFS.md                                # Architecture decisions
├── LEARNING_NOTES.md                           # Learning journal
└── CLAUDE.md                                   # Instructions for AI assistant
```

## Configuration

Key settings in [application.properties](src/main/resources/application.properties):

```properties
# Gemini API Configuration
gemini.api.key=${GEMINI_API_KEY}
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
gemini.api.rate-limit.requests-per-minute=10

# Tweet Processing
tweet.processing.batch-size=10

# Archive Input (defaults to sample data)
archive.input-path=${TWITTER_ARCHIVE_PATH:src/main/resources/sample-data/tweets-sample.js}

# Output Location
output.csv-path=results/flagged_tweets.csv

# Alignment Criteria
alignment.forbidden-words=kill,bum,damn,stupid
alignment.check-professionalism=true
alignment.context=Just a normal internet user but also mindful of his digital footprint
alignment.desired-tone=Respectful, thoughtful, and sometimes professional
```

**Environment Variables:**
- `GEMINI_API_KEY` (required) - Your Gemini API key
- `TWITTER_ARCHIVE_PATH` (optional) - Path to your tweets.js file (defaults to sample data)

**Retry Configuration:**

The GeminiClient uses Spring Retry with:
- Max attempts: 3
- Initial backoff: 1000ms
- Backoff multiplier: 2.0 (exponential)
- Retry on: Rate limit (429) and Service Unavailable (503) errors

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

## How It Works

1. **Parse Archive**: ArchiveParser reads and parses your Twitter archive JavaScript file
2. **Filter Retweets**: ArchiveParser automatically excludes retweets (you didn't write them)
3. **Batch Processing**: TweetAuditService processes tweets in batches of 10 (configurable)
4. **Rate Limiting**: TweetAuditService adds 60-second delay between batches to respect API limits
5. **AI Evaluation**: GeminiClient evaluates each tweet against AlignmentCriteria via Gemini API
6. **Error Handling**: GeminiClient retries failed evaluations (exponential backoff), then marks as ERROR
7. **CSV Output**: CSVWriter filters and writes only flagged tweets and errors for review

## Learning Goals

This project focuses on:
- Modern API integration (WebFlux, WebClient)
- Error handling & retry logic (Spring Retry, exponential backoff)
- Concurrency patterns (batched processing with rate limiting)
- Testing strategies (mocking external APIs)
- Architectural decision-making (documented in TRADEOFFS.md)

## License

Personal learning project - feel free to learn from it!
