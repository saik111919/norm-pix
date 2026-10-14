package com.example.normpix.finance.share

/**
 * What the owner's phone (a motorola, ML Kit's bundled Latin recognizer, 2026-10-13) read off real PhonePe and Google Pay
 * receipts: every line where it was on the picture, in the order the recognizer returned them - which is not reading
 * order. Names of people, the owner's own account digits, UPI handles and the payments' ids are swapped for stand-ins of
 * the same shape; everything else - the places, the misreads - is as read.
 *
 * Where the phone read a ₹ as "7" ("730" for ₹30) it scored that character 0.08-0.12 against 0.6-0.95 for the digits, and
 * [rupeeRestored] puts the ₹ back before anything reads the text; these lines are as they come out of it. [raw] gives a
 * line as the recognizer returned it, before that.
 */
object PhoneReceipts {
    fun line(text: String, left: Int, top: Int, right: Int, bottom: Int) = PictureLine(text, left, top, right, bottom)

    /** [lines] as the phone's reader hands them on: in reading order, with the figure printed largest. */
    fun picture(lines: List<PictureLine>, name: String? = null) =
        PictureText(ReceiptLayout(ReceiptLayout.rowsOf(lines)).readingText, SharedTransactionParser.headline(lines), lines, name)

    /** PhonePe's Share of a ₹30 payment to Uber: the name runs on to a second line, beside an avatar's initials. */
    val phonePeUber = listOf(
        line("Paid to", 65, 205, 189, 238),
        line("UL", 94, 326, 138, 356),
        line("Transaction Successful", 191, 37, 651, 70),
        line("Uber", 65, 695, 148, 725),
        line("11:29 am on 12 Oct 2026", 191, 86, 556, 110),
        line("Message", 66, 641, 200, 672),
        line("UBER INDIA SYSTEMS PRIVATE", 214, 293, 834, 332),
        line("LIMITED", 214, 349, 373, 379),
        line("amzn-abcdefgh@apl", 212, 406, 625, 444),
        line("Transfer Details", 170, 547, 444, 576),
        line("PhonePe Transaction ID", 66, 782, 423, 807),
        line("T2610121129280000000001", 65, 839, 553, 867),
        line("Debited from", 66, 924, 259, 949),
        line("XXXXXXXXXX1234", 211, 1000, 600, 1030),
        line("UTR: 812345678901", 213, 1088, 590, 1117),
        line("Powered by", 452, 1235, 627, 1268),
        line("UPI AXIS BANK", 402, 1287, 672, 1322),
        line("UNYNO AMINIS INJE", 401, 1318, 471, 1324),
        // Both read "730", the 7 scored 0.12 and 0.09.
        line("₹30", 940, 297, 1015, 328),
        line("₹30", 942, 1000, 1014, 1030),
    )

    /** PhonePe's Share of a ₹10 payment to a person. */
    val phonePeTen = listOf(
        line("Transaction Successful", 191, 37, 651, 70),
        line("11:34 am on 05 Oct 2026", 190, 86, 563, 110),
        line("Paid to", 65, 205, 189, 238),
        line("Asha Verma", 213, 311, 510, 342),
        line("Q500000001@ybl", 212, 368, 524, 405),
        line("Payment Details", 171, 504, 447, 546),
        line("PhonePe Transaction ID", 66, 602, 423, 627),
        line("T2610051134000000000002", 65, 659, 560, 687),
        line("Debited from", 66, 744, 259, 769),
        // The bank's logo, read as a letter.
        line("H", 64, 811, 219, 859),
        line("XXXXXXXXXX1234", 212, 820, 600, 850),
        line("UTR: 223456789012", 213, 908, 577, 937),
        line("Powered by", 452, 1054, 628, 1089),
        line("UPIPAXIS BANK", 423, 1105, 676, 1142),
        // Read "Z10", the Z scored 0.07.
        line("₹10", 948, 309, 1014, 340),
        // The ₹ swallowed into the "1" (scored 0.59): left as read.
        line("10", 952, 820, 1014, 850),
    )

    /** Google Pay's Share of a ₹30 payment to Uber, at full size (1080 x 2612). */
    val googlePayUber = listOf(
        line("To UBER INDIA SYSTEMS PRIVATE LIMITED", 129, 433, 950, 466),
        line("U", 559, 290, 578, 355),
        // The big "₹30", its ₹ dropped.
        line("30", 424, 542, 650, 665),
        line("12 Oct 2026, 8:55 pm", 362, 861, 716, 900),
        line("HDFC Bank 1234", 277, 1056, 601, 1087),
        line("UPI transaction ID", 134, 1556, 422, 1592),
        line("131100000001", 106, 1624, 331, 1651),
        line("Completed", 473, 747, 666, 782),
        // Read "Payment of 730 completed", the 7 scored 0.08.
        line("Payment of ₹30 completed", 213, 1215, 747, 1256),
        line("Receiver's bank has confirmed deposit of", 213, 1298, 928, 1333),
        line("money to UBER INDIA SYSTEMS PRIVATE", 213, 1342, 904, 1385),
        line("LIMITED's bank account", 213, 1404, 623, 1431),
        line("To: UBER INDIA SYSTEMS PRIVATE LIMITED", 106, 1718, 855, 1747),
        line("1abc@rapl on Amazon Pay", 170, 1779, 618, 1817),
        line("From: ACCOUNT HOLDER (HDFC Bank)", 108, 1871, 769, 1907),
        line("***00-0@okhdfcbank on Google Pay", 108, 1939, 735, 1974),
        line("Google transaction ID", 107, 2029, 492, 2068),
        line("CICAgLtestId01", 107, 2096, 437, 2132),
        line("POWERED BY", 489, 2240, 586, 2252),
        line("UPI»", 477, 2261, 604, 2295),
        line("UNIFIED PAYMENTS INTERFACE", 476, 2292, 595, 2311),
        line("G Pay", 513, 2507, 612, 2573),
    )

    /** The file name Google Pay's Share gave that picture. */
    const val GOOGLE_PAY_UBER_FILE = "1791818718 - 30.00 To UBER INDIA SYSTEMS PRIVATE LIMITED on Google Pay.png"

    /** Google Pay's Share of a ₹360 payment to Swiggy with a RuPay credit card on UPI. */
    val googlePaySwiggy = listOf(
        line("HDFC Bank", 276, 1024, 496, 1055),
        line("360", 385, 550, 691, 661),
        line("131100000003", 106, 1571, 346, 1600),
        line("To SWIGGY", 429, 429, 647, 467),
        line("608001XXXXXXXX42", 275, 1086, 688, 1119),
        line("UPI transaction ID", 127, 1503, 421, 1540),
        line("To: SWIGGY", 106, 1661, 314, 1695),
        line("S", 522, 295, 558, 350),
        line("11 Oct 2026, 4:39 pm", 371, 860, 713, 898),
        // Read "Payment of 7360 completed", the 7 scored 0.11.
        line("Payment of ₹360 completed", 213, 1215, 772, 1256),
        line("•iggy@icici", 108, 1728, 334, 1764),
        line("Completed", 473, 747, 666, 782),
        line("Receiver's bank has confirmed deposit of", 213, 1298, 928, 1333),
        line("money to SWIGGY's bank account", 213, 1345, 801, 1384),
        line("CICAgLtestId03", 107, 2043, 407, 2079),
        line("From: ACCOUNT HOLDER (HDFC Bank)", 108, 1820, 769, 1856),
        line("o00-0@okhdfcbank on Google Pay", 108, 1887, 737, 1922),
        line("Google transaction ID", 107, 1975, 493, 2016),
        line("RuPay", 476, 2191, 598, 2224),
        line("G Pay", 508, 2475, 613, 2536),
    )

    /** Google Pay's Share of a ₹10 payment to a person: the bell icon in front of "Payment of" came out as a letter. */
    val googlePayTen = listOf(
        line("To Mr RAVI", 411, 434, 669, 465),
        line("131000000002", 106, 1782, 348, 1809),
        line("M", 514, 296, 566, 349),
        line("UPI transaction ID", 135, 1714, 422, 1750),
        line("To: Mr RAVI", 106, 1876, 348, 1904),
        line("10", 440, 550, 637, 661),
        line("HDFC Bank", 287, 1230, 496, 1270),
        line("608001XXXXXXXX42", 275, 1296, 688, 1329),
        line("Pay again", 457, 801, 622, 840),
        line("10 Oct 2026, 10:07 pm", 350, 1072, 727, 1108),
        line("n Payment of 10 completed", 110, 1422, 741, 1468),
        line("Completed", 472, 954, 665, 991),
        line("Receiver's bank has confirmed deposit of", 213, 1508, 927, 1544),
        line("money to Mr RAVI's bank account", 213, 1561, 833, 1594),
        line("CICAgLtestId02", 107, 2247, 400, 2290),
        line("•0000@ybl on PhonePe", 108, 1939, 548, 1974),
        line("From: ACCOUNT HOLDER (HDFC Bank)", 108, 2030, 769, 2067),
        line("**00-0@okhdfcbank on Google Pay", 107, 2090, 736, 2132),
        line("Google transaction ID", 107, 2185, 489, 2227),
        line("RuPay", 476, 2403, 601, 2433),
        line("UPI", 500, 2449, 569, 2470),
        line("G Pay", 514, 2684, 606, 2744),
    )

    /** [lines] with [text] read [asRead] instead: how a line came out before the ₹ was put back. */
    fun raw(lines: List<PictureLine>, text: String, asRead: String) = lines.map { if (it.text == text) it.copy(text = asRead) else it }
}
