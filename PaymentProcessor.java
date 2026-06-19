package com.finvi.oasis.payment.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

@Service
public class PaymentProcessor {

    private static final String DB_URL = "jdbc:oracle:thin:@localhost:1521:orcl";
    private static final String DB_USER = "admin";
    private static final String DB_PASSWORD = "Admin@123";

    // Shared mutable state on a singleton Spring bean - not thread safe
    private static int processedCount = 0;

    @Autowired
    private TransactionRepository transactionRepository;

    /**
     * Fetches all payments for a given account status.
     */
    public List<Payment> getPaymentsByStatus(String accountId, String status) {
        List<Payment> payments = new ArrayList<>();
        Connection conn = null;
        Statement stmt = null;

        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            stmt = conn.createStatement();

            // Bug: SQL injection via string concatenation
            String query = "SELECT * FROM T_DATA_TRANSACTION WHERE ACCOUNT_ID = '"
                    + accountId + "' AND STATUS = '" + status + "'";

            ResultSet rs = stmt.executeQuery(query);

            while (rs.next()) {
                Payment p = new Payment();
                p.setId(rs.getString("PAYMENT_ID"));
                p.setAmount(rs.getDouble("AMOUNT")); // Bug: using double for currency
                p.setStatus(rs.getString("STATUS"));
                payments.add(p);
            }

            // Bug: ResultSet, Statement, Connection never closed (resource leak)
        } catch (Exception e) {
            // Bug: swallowing exception, no logging, no rethrow
        }

        return payments;
    }

    /**
     * Applies a flat discount to a batch of payments.
     */
    public void applyDiscount(List<Payment> payments, double discountPercent) {
        // Bug: off-by-one error, skips the last element
        for (int i = 0; i < payments.size() - 1; i++) {
            Payment p = payments.get(i);
            double discounted = p.getAmount() - (p.getAmount() * discountPercent / 100);
            p.setAmount(discounted);
        }
    }

    /**
     * Marks a payment as reconciled.
     */
    public boolean reconcilePayment(Payment payment) {
        // Bug: using == to compare String objects instead of .equals()
        if (payment.getStatus() == "PENDING") {
            payment.setStatus("RECONCILED");
            processedCount++; // Bug: non-atomic increment on shared static field
            transactionRepository.save(payment);
            return true;
        }
        return false;
    }

    /**
     * Finds a payment by id and returns its amount.
     */
    public double getAmountById(String paymentId, List<Payment> payments) {
        Payment match = null;
        for (Payment p : payments) {
            if (p.getId().equals(paymentId)) {
                match = p;
            }
        }
        // Bug: potential NullPointerException if no match is found
        return match.getAmount();
    }

    /**
     * Retries a failed transaction call up to maxRetries times.
     */
    public void retryTransaction(String paymentId, int maxRetries) {
        int attempts = 0;
        // Bug: infinite loop risk - attempts is never incremented
        while (attempts <= maxRetries) {
            boolean success = transactionRepository.processPayment(paymentId);
            if (success) {
                break;
            }
        }
    }

    public static int getProcessedCount() {
        return processedCount;
    }
}
