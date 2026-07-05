import com.ritesh.parser.core.TransactionType
import com.ritesh.parser.core.bank.CashfreeParser
import com.ritesh.parser.core.bank.KeralaBankParser
import com.ritesh.parser.core.bank.NSDLPaymentsBankParser
import com.ritesh.parser.core.bank.NaviMutualFundParser
import com.ritesh.parser.core.bank.PunjabSindBankParser
import com.ritesh.parser.core.test.ExpectedTransaction
import com.ritesh.parser.core.test.ParserTestCase
import com.ritesh.parser.core.test.ParserTestUtils
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory
import java.math.BigDecimal

class IndianBanksParsersTest {

    @TestFactory
    fun `nsdl payments bank parser`(): List<DynamicTest> {
        val parser = NSDLPaymentsBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "NSDL debit transaction",
                message = "INR 500.00 debited from your NSDL Payments Bank account. Avl Bal INR 10000.00",
                sender = "NSDLPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("500.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "NSDL credit transaction",
                message = "INR 1500.00 credited to your NSDL Payments Bank account. Avl Bal INR 11500.00",
                sender = "NSDLPAY",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1500.00"),
                    currency = "INR",
                    type = TransactionType.INCOME
                )
            )
        )

        val handleCases = listOf(
            "NSDLPAY" to true,
            "NSDLBANK" to true,
            "HDFCBK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "NSDL Payments Bank Parser"
        )
    }

    @TestFactory
    fun `punjab and sind bank parser`(): List<DynamicTest> {
        val parser = PunjabSindBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "PSB debit transaction",
                message = "INR 1200.00 debited from your PSB account. Avl Bal INR 8800.00",
                sender = "PSBALRT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1200.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "PSB credit transaction",
                message = "INR 3000.00 credited to your PSB account. Avl Bal INR 11800.00",
                sender = "PSBALRT",
                expected = ExpectedTransaction(
                    amount = BigDecimal("3000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME
                )
            )
        )

        val handleCases = listOf(
            "PSBALRT" to true,
            "PUNJAB SIND BANK" to true,
            "HDFCBK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Punjab & Sind Bank Parser"
        )
    }

    @TestFactory
    fun `kerala bank parser`(): List<DynamicTest> {
        val parser = KeralaBankParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Kerala Bank debit transaction",
                message = "INR 750.00 debited from your Kerala Bank account. Avl Bal INR 9250.00",
                sender = "KERALABK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("750.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Kerala Bank credit transaction",
                message = "INR 2000.00 credited to your Kerala Bank account. Avl Bal INR 11250.00",
                sender = "KERALABK",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME
                )
            )
        )

        val handleCases = listOf(
            "KERALABK" to true,
            "KERALA BANK" to true,
            "HDFCBK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Kerala Bank Parser"
        )
    }

    @TestFactory
    fun `cashfree parser`(): List<DynamicTest> {
        val parser = CashfreeParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Cashfree debit transaction",
                message = "INR 999.00 debited from your account via Cashfree. Ref: CF123456",
                sender = "CASHFREE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("999.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Cashfree credit transaction",
                message = "INR 5000.00 credited to your account via Cashfree. Ref: CF789012",
                sender = "CASHFREE",
                expected = ExpectedTransaction(
                    amount = BigDecimal("5000.00"),
                    currency = "INR",
                    type = TransactionType.INCOME
                )
            )
        )

        val handleCases = listOf(
            "CASHFREE" to true,
            "CASHFREE-PAY" to true,
            "HDFCBK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Cashfree Parser"
        )
    }

    @TestFactory
    fun `navi mutual fund parser`(): List<DynamicTest> {
        val parser = NaviMutualFundParser()

        val testCases = listOf(
            ParserTestCase(
                name = "Navi Mutual Fund debit transaction",
                message = "INR 1000.00 debited towards Navi Mutual Fund SIP. Ref: NAVI123456",
                sender = "NAVIMF",
                expected = ExpectedTransaction(
                    amount = BigDecimal("1000.00"),
                    currency = "INR",
                    type = TransactionType.EXPENSE
                )
            ),
            ParserTestCase(
                name = "Navi Mutual Fund credit transaction",
                message = "INR 2500.00 credited from Navi Mutual Fund redemption. Ref: NAVI789012",
                sender = "NAVIMF",
                expected = ExpectedTransaction(
                    amount = BigDecimal("2500.00"),
                    currency = "INR",
                    type = TransactionType.INCOME
                )
            )
        )

        val handleCases = listOf(
            "NAVIMF" to true,
            "NAVI" to true,
            "HDFCBK" to false
        )

        return ParserTestUtils.runTestSuite(
            parser = parser,
            testCases = testCases,
            handleCases = handleCases,
            suiteName = "Navi Mutual Fund Parser"
        )
    }
}
