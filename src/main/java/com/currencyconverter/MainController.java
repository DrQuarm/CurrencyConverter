package com.currencyconverter;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.text.Text;

import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;

public class MainController implements Initializable {

    // ── FXML nodes ─────────────────────────────────────────────────────────────

    @FXML private ComboBox<String> fromCurrencyBox;
    @FXML private ComboBox<String> toCurrencyBox;
    @FXML private TextField amountField;
    @FXML private Button convertButton;
    @FXML private Button swapButton;
    @FXML private Button refreshButton;

    @FXML private Text resultText;
    @FXML private Text rateText;
    @FXML private Text lastUpdatedText;
    @FXML private Text statusText;
    @FXML private Text offlineBannerText;

    @FXML private ProgressIndicator loadingSpinner;
    @FXML private javafx.scene.layout.HBox offlineBanner;
    @FXML private TableView<ConversionRecord> historyTable;
    @FXML private TableColumn<ConversionRecord, String> colTime;
    @FXML private TableColumn<ConversionRecord, String> colFrom;
    @FXML private TableColumn<ConversionRecord, String> colTo;
    @FXML private TableColumn<ConversionRecord, String> colAmount;
    @FXML private TableColumn<ConversionRecord, String> colResult;
    @FXML private TableColumn<ConversionRecord, String> colRate;
    @FXML private Button clearHistoryButton;

    // ── State ──────────────────────────────────────────────────────────────────

    private final ExchangeRateService service = new ExchangeRateService();
    private Map<String, Double> currentRates = new HashMap<>();
    private String currentBase = "";

    private static final DecimalFormat RESULT_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat RATE_FORMAT   = new DecimalFormat("#,##0.######");

    // Popular currencies shown first
    private final ObservableList<ConversionRecord> history = FXCollections.observableArrayList();
    private static final List<String> POPULAR = List.of(
            "USD", "EUR", "GBP", "JPY", "CAD", "AUD",
            "CHF", "CNY", "INR", "GHS", "NGN", "ZAR",
            "MXN", "BRL", "AED", "SGD", "HKD", "SEK"
    );

    // ── Initialization ─────────────────────────────────────────────────────────

    @Override
    public void initialize(URL url, ResourceBundle resourceBundle) {
        // Populate combo boxes with popular currencies; full list loads after fetch
        ObservableList<String> initial = FXCollections.observableArrayList(POPULAR);
        fromCurrencyBox.setItems(initial);
        toCurrencyBox.setItems(initial);

        fromCurrencyBox.setValue("USD");
        toCurrencyBox.setValue("GHS");   // default to Ghanaian Cedi (user location)

        // Only allow numeric input in amount field
        amountField.textProperty().addListener((obs, oldVal, newVal) -> {
            if (!newVal.matches("\\d*\\.?\\d*")) {
                amountField.setText(oldVal);
            }
        });

        // Auto-convert on currency change
        fromCurrencyBox.valueProperty().addListener((obs, o, n) -> autoRefreshIfNeeded(n));
        toCurrencyBox.valueProperty().addListener((obs, o, n) -> autoConvert());


        // Wire up history table columns
        colTime.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getTimestamp()));
        colFrom.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getFrom()));
        colTo.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getTo()));
        colAmount.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(RESULT_FORMAT.format(d.getValue().getAmount())));
        colResult.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(RESULT_FORMAT.format(d.getValue().getResult())));
        colRate.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(RATE_FORMAT.format(d.getValue().getRate())));

        historyTable.setItems(history);
        historyTable.setPlaceholder(new Label("No conversions yet."));

// Initial fetch
        fetchRatesFor("USD");
    }

    // ── Handlers ───────────────────────────────────────────────────────────────

    @FXML
    private void onConvertClicked() {
        String from = fromCurrencyBox.getValue();
        if (from == null || from.isBlank()) return;

        if (!from.equals(currentBase) || currentRates.isEmpty()) {
            fetchRatesFor(from);
        } else {
            performConversion();
        }
    }

    @FXML
    private void onSwapClicked() {
        String from = fromCurrencyBox.getValue();
        String to   = toCurrencyBox.getValue();
        fromCurrencyBox.setValue(to);
        toCurrencyBox.setValue(from);
    }

    @FXML
    private void onRefreshClicked() {
        String from = fromCurrencyBox.getValue();
        if (from != null) fetchRatesFor(from);
    }

    // ── Logic ──────────────────────────────────────────────────────────────────

    private void autoRefreshIfNeeded(String newBase) {
        if (newBase != null && !newBase.equals(currentBase)) {
            fetchRatesFor(newBase);
        } else {
            autoConvert();
        }
    }

    private void autoConvert() {
        if (!currentRates.isEmpty()) {
            performConversion();
        }
    }

    private void fetchRatesFor(String base) {
        setLoading(true);
        setStatus("Fetching live rates for " + base + "…");

        service.fetchRates(base).thenAccept(result -> Platform.runLater(() -> {
            setLoading(false);

            if (!result.isSuccess()) {
                setStatus("⚠ " + result.errorMessage());
                offlineBanner.setVisible(true);
                offlineBannerText.setText("⚠  No internet connection — please connect to load rates.");
                return;
            }

            currentRates = result.rates();
            currentBase  = base;

            // Show or hide offline banner
            if (result.offline()) {
                offlineBanner.setVisible(true);
                String msg = result.lastUpdated().contains("Built-in")
                        ? "📦  No internet — using built-in rates (Mar 2026). Connect to get live rates."
                        : "📴  Offline — using saved rates from: " + result.lastUpdated();
                offlineBannerText.setText(msg);
                setStatus("⚠ Offline — " + (result.lastUpdated().contains("Built-in")
                        ? "built-in rates active" : "using saved rates"));
            } else {
                offlineBanner.setVisible(false);
                setStatus("✓ Live rates loaded — " + currentRates.size() + " currencies available");
            }

            // Rebuild combo box lists with all available currencies
            List<String> allCurrencies = new ArrayList<>(currentRates.keySet());
            Collections.sort(allCurrencies);

            List<String> sorted = new ArrayList<>(POPULAR);
            allCurrencies.stream()
                    .filter(c -> !POPULAR.contains(c))
                    .forEach(sorted::add);

            ObservableList<String> items = FXCollections.observableArrayList(sorted);
            String currentFrom = fromCurrencyBox.getValue();
            String currentTo   = toCurrencyBox.getValue();

            fromCurrencyBox.setItems(items);
            toCurrencyBox.setItems(items);

            fromCurrencyBox.setValue(currentFrom);
            toCurrencyBox.setValue(currentTo != null ? currentTo : "GHS");

            lastUpdatedText.setText("Last updated: " + result.lastUpdated());
            performConversion();
        }));
    }

    private void performConversion() {
        String to     = toCurrencyBox.getValue();
        String amtStr = amountField.getText();

        if (to == null || amtStr == null || amtStr.isBlank()) {
            resultText.setText("—");
            rateText.setText("");
            return;
        }

        double amount;
        try {
            amount = Double.parseDouble(amtStr);
        } catch (NumberFormatException e) {
            resultText.setText("Invalid amount");
            return;
        }

        if (!currentRates.containsKey(to)) {
            resultText.setText("Rate unavailable");
            return;
        }

        double rate   = currentRates.get(to);
        double result = amount * rate;

        resultText.setText(RESULT_FORMAT.format(result) + " " + to);
        rateText.setText(
                "1 " + currentBase + " = " + RATE_FORMAT.format(rate) + " " + to
        );

        // Log to history (newest first)
        history.add(0, new ConversionRecord(currentBase, to, amount, result, rate));
    }
    @FXML
    private void onClearHistoryClicked() {
        history.clear();
    }

    private void setLoading(boolean loading) {
        loadingSpinner.setVisible(loading);
        convertButton.setDisable(loading);
        refreshButton.setDisable(loading);
    }

    private void setStatus(String msg) {
        statusText.setText(msg);
    }
}
