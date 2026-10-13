package com.supermarketmanager.rules;

/**
 * <ul>
 *   <li>DENY / ALLOW: product matchers in {@code values}. Brand or name words (accent/case-insensitive, word-prefix),
 *       or exact {@code gtin:<barcode>} / {@code id:<productId>}. Deny beats allow; every applicable ALLOW rule must match.</li>
 *   <li>NO_PRIVATE_LABEL: drop retailer own-brand products.</li>
 *   <li>UNIT_PRICE: {@code min}/{@code max} EUR per kg, l or piece. PACK_PRICE: per pack. PACK_SIZE: pack size range.</li>
 *   <li>RETAILER_DENY / RETAILER_ALLOW: retailer ids or names in {@code values}.</li>
 *   <li>ONLY_DISCOUNTED: keep only offers flagged as on discount.</li>
 *   <li>MIX_POLICY: how a line may be filled ({@code mix}: SINGLE_PRODUCT, SINGLE_BRAND, ANY). Most specific scope wins.</li>
 * </ul>
 */
public enum RuleType {
    DENY, ALLOW, NO_PRIVATE_LABEL, UNIT_PRICE, PACK_PRICE, PACK_SIZE, RETAILER_DENY, RETAILER_ALLOW, ONLY_DISCOUNTED, MIX_POLICY
}
