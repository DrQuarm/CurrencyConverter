package com.currencyconverter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ConversionRecord {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("MMM d, HH:mm:ss");

    private final String from;
    private final String to;
    private final double amount;
    private final double result;
    private final double rate;
    private final String timestamp;

    public ConversionRecord(String from, String to, double amount, double result, double rate) {
        this.from      = from;
        this.to        = to;
        this.amount    = amount;
        this.result    = result;
        this.rate      = rate;
        this.timestamp = LocalDateTime.now().format(FMT);
    }

    public String getFrom()      { return from; }
    public String getTo()        { return to; }
    public double getAmount()    { return amount; }
    public double getResult()    { return result; }
    public double getRate()      { return rate; }
    public String getTimestamp() { return timestamp; }
}