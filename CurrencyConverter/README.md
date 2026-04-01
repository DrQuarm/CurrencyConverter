# Currency Converter — JavaFX Desktop App

A real-time currency converter built with **JavaFX 21** and **Java 17+**, using the
[ExchangeRate-API](https://open.er-api.com) open-access endpoint — **no API key required**.

---

## Features
- Live exchange rates fetched on launch and on demand
- 160+ currencies supported
- 1-hour client-side cache (avoids hammering the API)
- Swap currencies with one click
- Clean dark UI with instant conversion

---

## Prerequisites

| Tool | Minimum Version |
|------|-----------------|
| Java JDK | 17 |
| Maven | 3.8+ |
| IntelliJ IDEA | Any recent version (Community or Ultimate) |

---

## Setup in IntelliJ IDEA

### 1. Open the project
1. Launch IntelliJ IDEA
2. **File → Open** → select the `CurrencyConverter` folder
3. IntelliJ will detect the `pom.xml` automatically — click **Load Maven Project** if prompted

### 2. Set the SDK
1. **File → Project Structure → Project**
2. Set **SDK** to Java 17 or higher
3. Set **Language level** to 17

### 3. Run the application

**Option A — Maven (recommended):**
1. Open the **Maven** panel (right sidebar or View → Tool Windows → Maven)
2. Navigate to: `Plugins → javafx → javafx:run`
3. Double-click `javafx:run`

**Option B — Run configuration:**
1. Go to **Run → Edit Configurations → + → Application**
2. Set **Main class**: `com.currencyconverter.MainApp`
3. Add VM options:
   ```
   --module-path /path/to/javafx-sdk/lib --add-modules javafx.controls,javafx.fxml
   ```
   *(Skip this if using Maven — the plugin handles the module path automatically)*

---

## Project Structure

```
CurrencyConverter/
├── pom.xml                          ← Maven build file
└── src/main/
    ├── java/
    │   ├── module-info.java
    │   └── com/currencyconverter/
    │       ├── MainApp.java          ← Application entry point
    │       ├── MainController.java   ← UI logic / event handlers
    │       └── ExchangeRateService.java ← API calls & caching
    └── resources/com/currencyconverter/
        ├── main-view.fxml           ← UI layout
        └── styles.css               ← Dark theme stylesheet
```

---

## API Details

- **Endpoint:** `https://open.er-api.com/v6/latest/{BASE}`
- **No API key required**
- Rates are cached for **1 hour** per base currency to stay within fair-use limits
- Click **↻ Refresh Rates** to force a fresh fetch

---

## Troubleshooting

| Issue | Fix |
|-------|-----|
| `No JavaFX runtime components found` | Run via Maven `javafx:run` instead of the green play button |
| Network error on startup | Check your internet connection; the API needs outbound HTTPS |
| Currency not showing | Click **↻ Refresh Rates** — the full list loads after first fetch |
