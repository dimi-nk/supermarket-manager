package com.supermarketmanager.pantry;

/**
 * "Keep {@code amount} of {@code subject} in the house". Amount is in the subject's measure.
 * {@code packCount}/{@code packSize} record how the user expressed it ("2 x 0.5 kg") and become pack hints.
 */
public record Target(Subject subject, double amount, Integer packCount, Double packSize, String note) {

    /** Fills {@code amount} from packCount x packSize when only those are given. */
    public Target normalised() {
        if (amount <= 0 && packCount != null && packSize != null) return new Target(subject, packCount * packSize, packCount, packSize, note);
        return this;
    }
}
