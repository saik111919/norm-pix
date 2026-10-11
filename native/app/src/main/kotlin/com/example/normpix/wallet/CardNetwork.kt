package com.example.normpix.wallet

/**
 * Card network (scheme) inferred from a card number's IIN (the leading digits
 * banks are assigned by network) - used to render network badges on the card face.
 */
enum class CardNetwork(val wordmark: String) {
    Visa("VISA"),
    Mastercard("mastercard"),
    Rupay("RuPay"),
    Amex("AMEX"),
    Discover("DISCOVER"),
    DinersClub("Diners Club"),
    Unknown(""),
    ;

    companion object {
        fun fromNumber(digits: String): CardNetwork {
            if (digits.isEmpty()) return Unknown

            fun prefix(length: Int): Int =
                (if (digits.length >= length) digits.substring(0, length) else digits).toIntOrNull() ?: -1

            val p1 = prefix(1)
            val p2 = prefix(2)
            val p3 = prefix(3)
            val p4 = prefix(4)
            val p6 = prefix(6)

            if (p1 == 4) return Visa
            if (p2 in 51..55 || p4 in 2221..2720) return Mastercard
            if (p2 == 34 || p2 == 37) return Amex
            // RuPay-exclusive prefixes (508, 6521, 6522, 81, 82) take priority over the broad Discover 65xx space.
            if (p4 == 6521 || p4 == 6522 || p3 == 508 || p2 == 81 || p2 == 82) return Rupay
            // Discover: 6011, 622126-622925, 644-649, and 65xx (non-RuPay).
            if (p4 == 6011 || p6 in 622126..622925 || p3 in 644..649 || p2 == 65) return Discover
            // Other 60xx cards (excluding 6011 Discover) belong to RuPay.
            if (p2 == 60) return Rupay
            if (p3 in 300..305 || p2 == 36 || p2 == 38) return DinersClub
            return Unknown
        }
    }
}
