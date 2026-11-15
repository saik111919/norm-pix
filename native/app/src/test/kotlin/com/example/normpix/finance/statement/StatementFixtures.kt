package com.example.normpix.finance.statement

import kotlin.test.assertNotNull

/**
 * Made-up statements in the style of Indian banks' downloads, for the import's tests: no real names, accounts, references or amounts.
 * Each has the rows it reads to in its comment.
 */
object StatementFixtures {
    /**
     * Date, Narration, Chq./Ref.No., Value Dt, Withdrawal Amt., Deposit Amt., Closing Balance - the account's details above the table and
     * a summary below it. Reads to three transactions (dd/MM/yy dates) and skips four lines.
     */
    val withWithdrawals = """
        Statement of account,,,,,,
        Account holder: A SAMPLE PERSON,,,,,,
        Address: 1 EXAMPLE ROAD,,,,,,
        From 01/10/2026 to 31/10/2026,,,,,,
        Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance
        01/10/26,UPI-SAMPLE CAFE-samplecafe@bank-BANK0000001-428312345678-UPI,0000428312345678,01/10/26,"1,250.00",,"98,750.00"
        05/10/26,NEFT CR-BANK0000001-EXAMPLE EMPLOYER LTD-A SAMPLE PERSON-N123456789012,N123456789012,05/10/26,,"85,000.50","1,83,750.50"
        09/10/26,POS 416021XXXXXX1234 EXAMPLE STORE,000012,09/10/26,499.00,,"1,83,251.50"
        STATEMENT SUMMARY,,,,,,
        Opening Balance,Dr Count,Cr Count,Debits,Credits,Closing Bal,
        "1,00,000.00",2,1,"1,749.00","85,000.50","1,83,251.50",
        This is a computer generated statement.,,,,,,
    """.trimIndent()

    /** Txn Date, Description, Ref No., Debit, Credit, Balance (dd-MMM-yyyy). Reads to two transactions. */
    val withDebitCredit = """
        Txn Date,Description,Ref No.,Debit,Credit,Balance
        15-Nov-2026,CASH DEPOSIT,REF000111,,"5,000.00","5,000.00"
        16-Nov-2026,BILL PAYMENT,REF000112,"1,200.00",,"3,800.00"
    """.trimIndent()

    /** One signed Amount and a Dr/Cr column, with semicolons (dd MMM yyyy). Reads to two transactions. */
    val withAmountAndMarker = """
        Account;Sample savings
        Tran Date;Transaction Details;Amount;Dr/Cr;Balance
        15 Nov 2026;Grocery shop;1250.50;Dr;8749.50
        16 Nov 2026;Salary;50000;Cr;58749.50
    """.trimIndent()

    /** The table of [text] as a [StatementSheet]. */
    fun sheet(text: String): StatementSheet = assertNotNull(StatementLayout.find(StatementCsv.parse(text)))
}
