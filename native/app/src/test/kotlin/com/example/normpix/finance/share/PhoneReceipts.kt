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

    // ---------------------------------------------------------------- 2026-10-14
    // Read on the same phone from the pictures the owner shared (some sent through WhatsApp, so smaller than a screenshot),
    // with an avatar's or an icon's letter parted from the text beside it (splitAtGaps).

    /** PhonePe's Share of ₹40 received from a person: their masked phone number below the name, and their banking name. */
    val phonePeReceived = listOf(
        line("Transaction Successful", 191, 37, 651, 70),
        line("01:23 am on 11 Oct 2026", 191, 86, 556, 110),
        line("Received from", 65, 207, 336, 238),
        line("Ravi Kumar", 214, 310, 566, 348),
        // "+91 ••••• •6411", the dots read as letters.
        line("+91 ee 0000", 226, 369, 491, 400),
        line("Banking Name", 65, 492, 280, 530),
        line(": Ravi Kumar", 354, 493, 648, 529),
        line("Transfer Details", 191, 629, 443, 667),
        line("PhonePe Transaction ID", 66, 725, 423, 750),
        line("T2610110123000000000005", 65, 782, 550, 810),
        line("Credited to", 65, 867, 227, 892),
        line("XXXXXXXXXX1234", 212, 943, 600, 973),
        line("UTR: 323456789012", 214, 1031, 588, 1060),
        line("Powered by", 452, 1177, 628, 1209),
        line("UrIDAXIS BANK", 420, 1233, 676, 1265),
        line("₹40", 938, 310, 1014, 342),
        line("₹40", 941, 940, 1013, 972),
    )

    /** PhonePe's Share of a ₹89 UPI AutoPay to Google: the avatar's "G", an icon read as "E", the AUTOPAY badge. */
    val phonePeAutopay = listOf(
        line("Paid to", 65, 205, 189, 238),
        line("Transaction Successful", 191, 37, 651, 70),
        line("02:14 pm on 05 Oct 2026", 191, 86, 572, 115),
        line("G", 103, 326, 127, 356),
        line("Google", 211, 316, 348, 363),
        line("E", 72, 506, 118, 550),
        line("Payment Details", 172, 506, 450, 550),
        line("Message", 66, 608, 200, 638),
        line("MandateExecute", 66, 663, 354, 691),
        line("PhonePe Transaction ID", 66, 748, 423, 773),
        line("OLEX2610051414000000000006", 65, 805, 628, 833),
        line("Debited from", 66, 890, 259, 915),
        line("XXXXXXXXXX1234", 212, 966, 599, 996),
        line("UTR: 423456789012", 213, 1054, 587, 1083),
        line("Powered by", 453, 1225, 628, 1256),
        line("UFIAUTOPAYAXIS BANK", 331, 1272, 750, 1314),
        line("₹89", 940, 322, 1016, 354),
        line("A) AUTOPAY", 908, 384, 1004, 403),
        line("₹89", 944, 965, 1013, 996),
    )

    /** CRED's screen of a ₹43 payment to a person: the name at the top with no label, "SUCCESSFUL" in mixed case. */
    val cred = listOf(
        line("X", 78, 59, 95, 77),
        // Read "743", the 7 scored 0.07.
        line("₹43", 96, 390, 157, 418),
        line("Imran Ali", 200, 238, 413, 257),
        line("02:00pm, 23rd sep'26", 96, 447, 347, 470),
        line("Paid via CRED", 115, 523, 269, 539),
        line("HDFC BANK LTD", 200, 642, 374, 659),
        line("XXXXXXXXXX1234 LIFI»", 200, 685, 500, 704),
        line("Check balance", 250, 761, 403, 776),
        line("payment initiated from your HDFC BANK LTD", 155, 870, 645, 890),
        line("account", 154, 905, 244, 920),
        line("bank details of imran ali have been", 155, 952, 637, 969),
        line("authenticated by NPCI", 154, 988, 405, 1008),
        line("amount deducted from Asha's bank", 154, 1037, 529, 1053),
        line("amount credited to recipient's bank", 154, 1086, 550, 1105),
        line("paying them again?", 59, 1234, 379, 1264),
        line("send money to Imran Ali instantly", 58, 1287, 553, 1310),
        line("Pay again", 123, 1381, 245, 1406),
        line("SUcCEsSFUL", 585, 398, 692, 409),
        line("CRED transaction ID", 58, 1551, 300, 1569),
    )

    /** PayZapp's receipt of a ₹5,679.30 credit card bill paid by UPI: the card's badge read as an "O", the id run on to a second line. */
    val payZapp = listOf(
        line("Paid to HDFC Bank Credit Card", 267, 476, 813, 505),
        line("PAID TO", 244, 803, 342, 822),
        line("₹5679.3", 374, 366, 705, 429),
        line("30 SEPT 2026 , 02:31 PM", 317, 542, 763, 576),
        line("HDFC Bank Credit CardO", 244, 851, 692, 881),
        line("Transaction Details", 64, 1072, 454, 1106),
        line("Paid using", 70, 1481, 245, 1519),
        line("HDFC Bank Credit Card Number", 71, 1203, 631, 1232),
        line("Transaction ID", 70, 1342, 318, 1370),
        // The PayZapp logo.
        line("EPAY", 149, 1617, 294, 1697),
        line("APP", 251, 1616, 405, 1695),
        line("XXXX-5511", 811, 1204, 1010, 1231),
        line("txn_1a2b3c4d-1111-4222-a333-4", 420, 1332, 1011, 1363),
        line("44455566677", 764, 1378, 1009, 1405),
        line("HDFC BANK LTD UPI", 650, 1483, 1022, 1517),
        line("POWERED BY", 726, 1627, 893, 1646),
        line("UPID", 781, 1660, 879, 1706),
        line("UNIFIED PAYMENTS INTERFACE", 745, 1705, 865, 1712),
    )

    /** SBI's transaction details of a ₹25 UPI payment: a table, then the narration over two lines. */
    val sbi = listOf(
        // The logo: its keyhole read as an "O".
        line("OSBI", 114, 25, 224, 86),
        line("Account Number:", 60, 250, 244, 273),
        line("Amount:", 60, 311, 148, 330),
        line("Mode of Transfer:", 62, 364, 250, 383),
        line("Transaction Date:", 61, 417, 250, 435),
        line("Narration", 57, 510, 153, 531),
        line("TRANSACTION DETAILS", 499, 176, 733, 191),
        line("XXXXXXX4321", 490, 255, 683, 275),
        // "₹ 25.00", the ₹ read as a "z".
        line("z 25.00", 490, 309, 583, 337),
        line("UPI", 491, 366, 534, 386),
        line("14/10/2026", 491, 412, 630, 433),
        // "UPI/DR/" read as "UP/DRI".
        line("TRANSFER TO XXXXXXXXX9876 UP/DRIXXXXXXXX0001/Kiran", 59, 561, 926, 582),
        line("/SBIN/kirankumar/Payme", 58, 590, 371, 621),
    )

    /** Groww's order details of a ₹500 SIP instalment: a full screenshot, its status bar too. */
    val mutualFundSip = listOf(
        line("10:35 S 6 mins", 93, 46, 390, 80),
        // Read "F500.00", the F scored 0.15.
        line("₹500.00", 45, 446, 278, 493),
        line("New SIP • Completed", 44, 531, 357, 560),
        line("Order details", 179, 175, 420, 218),
        line("Example Small Cap Fund Direct Growth >", 43, 618, 725, 648),
        line("Completed on", 44, 794, 231, 823),
        line("14 Oct '26 O", 46, 850, 282, 884),
        line("Status", 42, 1037, 173, 1075),
        line("Order approved by exchange", 150, 1140, 601, 1174),
        line("08 Oct '26, 10:37 AM", 150, 1198, 431, 1225),
        line("Auto-payment confirmed", 149, 1306, 549, 1339),
        line("12 Oct '26, 12:00 AM", 151, 1364, 433, 1392),
        line("Units allocated", 150, 1472, 387, 1498),
        line("14 Oct '26, 1:28 PM", 154, 1527, 413, 1556),
        line("Details", 45, 1740, 187, 1775),
        line("Placed On", 44, 1839, 200, 1868),
        line("Paid via", 45, 1932, 163, 1958),
        line("Folio no", 61, 2016, 162, 2053),
        line("HDFC BANK XX", 329, 1932, 571, 1958),
        line("NAV date", 608, 795, 730, 817),
        line("08 Oct '26 10:37 AM", 328, 1841, 645, 1867),
        line("One-time", 197, 2216, 363, 2246),
        line("3 !!! 67", 784, 44, 973, 81),
        line("12 Oct '26", 622, 850, 795, 885),
        line("Start SIP", 720, 2216, 880, 2246),
    )

    /**
     * PhonePe's Share of a ₹100 payment to a shop (1080 x 1458), the ₹ put back: the name runs over three lines, the avatar's
     * initials sit beside the middle one and the ₹100 is at the right of the first; the account is a RuPay credit card on UPI,
     * shown by its first six and last two digits. The shop and what is printed of it are as they were; the handle and ids are
     * stand-ins of the same shape.
     */
    val phonePeShop = listOf(
        line("Powered by", 441, 1297, 640, 1336),
        line("Transaction Successful", 218, 40, 742, 84),
        line("10:33 pm on 15 Oct 2026", 216, 96, 640, 132),
        line("Paid to", 73, 236, 219, 276),
        line("SRI LAKSHMI NARAYANA", 241, 308, 817, 349),
        line("₹100", 897, 308, 1007, 349),
        line("SS", 105, 372, 160, 408),
        line("MEDICAL AND GENERAL", 241, 366, 806, 407),
        line("STORES", 241, 424, 427, 465),
        line("paytmqr0test01@ptys", 241, 492, 645, 532),
        line("Transfer Details", 193, 650, 504, 690),
        line("PhonePe Transaction ID", 73, 757, 479, 791),
        line("T2610152233000000000003", 73, 822, 647, 859),
        line("Debited from", 73, 918, 293, 953),
        line("356188XXXXXXXX56", 241, 1000, 727, 1043),
        line("₹100", 902, 1000, 1007, 1043),
        line("UTR: 324900000001", 241, 1100, 673, 1142),
        line("UPI AXIS BANK", 480, 1362, 692, 1392),
    )

    /**
     * Paytm's Share of a ₹74 payment (912 x 1716): the green tick beside the amount is not text, the payer's message "Auto" has
     * a speech bubble before it, the amount is spelled out under the figure, and the user's own UPI id is partly hidden above
     * their bank and its last four digits. The people, the handles and the ids are stand-ins of the same shape.
     */
    val paytmAuto = listOf(
        line("UPI Ref No: 611500000001", 106, 1383, 568, 1420),
        line("Paid Successfully", 243, 316, 671, 360),
        line("₹74", 335, 420, 480, 485),
        line("Rupees Seventy Four Only", 246, 538, 668, 572),
        line("Auto", 453, 633, 532, 672),
        line("To: Mohan Yadav", 106, 920, 453, 962),
        line("UPI ID: paytm.s0a1bcd@pty", 106, 990, 584, 1028),
        line("From: Account Holder", 106, 1156, 570, 1198),
        line("UPI ID: ******0000@ptyes", 106, 1220, 521, 1258),
        line("HDFC Bank - 1234", 106, 1272, 403, 1306),
        line("02:25 PM, 04 Aug 2026", 106, 1445, 483, 1480),
        line("paytm", 253, 1643, 328, 1668),
        line("Powered by UPI", 340, 1645, 535, 1668),
        line("YES BANK", 546, 1645, 656, 1668),
    )

    /**
     * Kredit.Pe's Share of a ₹150 payment (1072 x 1600): white on black, the amount alone at the top, the date with no year, the
     * payee in capitals, the user only by a partly hidden UPI id, and an advertisement at the foot. The handles and ids are
     * stand-ins of the same shape.
     */
    val kreditPeBells = listOf(
        line("Earn 3% cashback on UPI payments", 197, 1432, 875, 1470),
        line("₹150", 120, 188, 270, 240),
        line("Paid successfully", 120, 280, 488, 325),
        line("Oct 07, 1:25 PM", 120, 355, 404, 392),
        line("To: BELLS AND PEPPERS", 120, 515, 712, 560),
        line("UPI ID: paytmqr0test02@paytm", 120, 582, 789, 625),
        line("From", 120, 742, 214, 778),
        line("UPI ID: xxxxxx7777-1@kphdfc", 120, 800, 697, 840),
        line("UPI Transaction ID", 120, 958, 469, 998),
        line("628000000001", 120, 1022, 447, 1064),
        line("Paid with", 459, 1304, 612, 1338),
        line("Kredit.Pe", 470, 1362, 672, 1408),
    )

    /** [lines] with [text] read [asRead] instead: how a line came out before the ₹ was put back. */
    fun raw(lines: List<PictureLine>, text: String, asRead: String) = lines.map { if (it.text == text) it.copy(text = asRead) else it }

    /** [lines] with only the [nth] one (from 0) whose text is [text] read [asRead]: one of two figures printed alike. */
    fun rawNth(lines: List<PictureLine>, text: String, nth: Int, asRead: String): List<PictureLine> {
        var seen = -1
        return lines.map { if (it.text == text && ++seen == nth) it.copy(text = asRead) else it }
    }
}
