package com.hisabak.feature.sms.domain

/**
 * Mirrors `config/hisabi.php` → `sms_templates`.
 * Each pattern uses `{placeholder}` markers; the detector converts them to capture groups.
 */
object DefaultSmsTemplates {
    val patterns: List<String> = listOf(
        "Purchase of AED {amount} with {card} at {brand},",
        "Payment of AED {amount} to {brand} with {card}.",
        "{brand} of AED {amount} has been credited into ",
        "AED {amount} has been debited from {account} using {card} at {brand} on {date} {time}.",
        "{brand} of AED {amount} has been credited to your {account} on {date} {time}.",
        "Your {brand} of AED {amount} has been credited to your {account} on {date} {time}.",
        "Outward {brand} of AED {amount} is debited from your {account}. Your {card} as of {date} {time}.",
        "An ATM cash {brand} of AED{amount} has been debited from your {account} on {date} {time}.",
        "{brand} PAYMENT for {card} via MOBAPP of AED {amount} was debited from {date} {time}.",
        "Your Cr.Card {card} was used for AED{amount} on {date} {time} at {brand},{ignore}. {ignore}",
    )
}
