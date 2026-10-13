package com.supermarketmanager.pantry;

import com.supermarketmanager.core.model.Basket;

import java.util.List;

/**
 * Missing amounts for a profile at a date.
 *
 * @param basket the open lines as a basket, ready for the next pipeline stage
 */
public record DeltaReport(String profileId, long versionId, String effectiveFrom, String at, List<Line> lines, Basket basket) {

    /**
     * @param have        stock of the subject, including everything below it in the category tree
     * @param fromTargetsBelow amount already planned by more specific targets under this one
     * @param need        max(0, target - have - fromTargetsBelow)
     */
    public record Line(Subject subject, String name, String measure, double target, double have,
                       double fromTargetsBelow, double need, Double packSize, Integer suggestedPacks) {
    }
}
