module com.currencyconverter {
    requires javafx.controls;
    requires javafx.fxml;
    requires org.json;
    requires java.net.http;

    opens com.currencyconverter to javafx.fxml;
    exports com.currencyconverter;
}
