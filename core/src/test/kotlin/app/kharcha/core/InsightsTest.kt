package app.kharcha.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.ZoneId

class InsightsTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val engine = InsightsEngine(zone)
    private val today = LocalDate.of(2026, 10, 17)

    private fun spend(date: LocalDate, rupees: Long, merchant: String, category: Category) = Transaction(
        amountPaise = rupees * 100,
        direction = Direction.DEBIT,
        timestampMillis = date.atTime(13, 0).atZone(zone).toInstant().toEpochMilli(),
        merchant = merchant,
        merchantKey = Merchants.keyOf(merchant),
        category = category,
        source = Source.SMS,
        rawText = "",
    )

    private val data = buildList {
        // Steady background spend for three months.
        var d = LocalDate.of(2026, 7, 1)
        while (d <= today) {
            add(spend(d, 120, "Chai Point", Category.FOOD_DELIVERY))
            d = d.plusDays(1)
        }
        // Netflix every month on the 21st.
        listOf(7, 8, 9).forEach { add(spend(LocalDate.of(2026, it, 21), 649, "Netflix", Category.OTT)) }
        // October weekend Zomato orders push Food up.
        listOf(3, 4, 10, 11).forEach { add(spend(LocalDate.of(2026, 10, it), 700, "Zomato", Category.FOOD_DELIVERY)) }
        // One big purchase.
        add(spend(LocalDate.of(2026, 10, 12), 8999, "Croma", Category.ELECTRONICS))
        // Own transfer must not count as spend.
        add(spend(LocalDate.of(2026, 10, 5), 18420, "Credit card bill", Category.TRANSFER))
    }

    @Test
    fun `summary compares with the same point last month`() {
        val s = engine.summary(data, today, monthlyLimitPaise = 30_000_00)
        assertEquals((17 * 120 + 4 * 700 + 8999) * 100L, s.spentPaise)
        assertEquals(17 * 120 * 100L, s.previousSamePointPaise)
        assertEquals(Category.ELECTRONICS, s.byCategory.first().category)
        // ₹30,000 limit minus spent, over the 15 days left including today.
        assertEquals((30_000_00 - s.spentPaise) / 15, s.safeToSpendTodayPaise)
    }

    @Test
    fun `finds the monthly subscription`() {
        val r = engine.recurring(data, today)
        assertEquals(listOf("Netflix"), r.map { it.merchant })
        assertEquals(LocalDate.of(2026, 10, 21), r.first().nextExpected)
    }

    @Test
    fun `explains the spike and flags the unusual purchase`() {
        val insights = engine.insights(data, today)
        val spike = insights.firstOrNull { it.kind == InsightKind.SPIKE && it.title.startsWith("Food") }
        assertNotNull(spike)
        assertTrue(spike!!.body.contains("mostly Zomato on weekends"), spike.body)
        val unusual = insights.first { it.kind == InsightKind.UNUSUAL }
        assertTrue(unusual.body.startsWith("Croma on 12 October"), unusual.body)
        assertTrue(insights.any { it.kind == InsightKind.RECURRING })
    }
}

class MonthlyTotalsTest {
    @Test
    fun `six months oldest first, empty months are zero`() {
        val zone = ZoneId.of("Asia/Kolkata")
        val t = Transaction(
            amountPaise = 50000, direction = Direction.DEBIT,
            timestampMillis = LocalDate.of(2026, 9, 3).atTime(10, 0).atZone(zone).toInstant().toEpochMilli(),
            merchant = "A", merchantKey = "a", category = Category.FOOD_DELIVERY, source = Source.SMS, rawText = "",
        )
        val totals = InsightsEngine(zone).monthlyTotals(listOf(t, t.copy(category = Category.TRANSFER)), LocalDate.of(2026, 10, 17))
        assertEquals(6, totals.size)
        assertEquals(java.time.YearMonth.of(2026, 5), totals.first().first)
        assertEquals(50000L, totals[4].second)
        assertEquals(0L, totals[5].second)
    }

    @Test
    fun `custom categories survive a backup round trip`() {
        val mine = Category.custom("Pet care", "🐶", Category.G_PEOPLE)
        val payload = BackupPayload(createdAtMillis = 1, transactions = emptyList(), customCategories = listOf(mine), merchantRules = mapOf("vet" to mine))
        val decoded = BackupCodec.decode(BackupCodec.encode(payload, "passphrase1".toCharArray()), "passphrase1".toCharArray())
        assertEquals(payload, decoded)
        assertEquals(mine.copy(parentId = null), CategoryCatalog(decoded.customCategories).byId(mine.id))
        assertEquals(Category.OTHER, CategoryCatalog().byId("custom_gone"))
    }
}

class CashTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 17)

    private fun txn(day: Int, rupees: Long, category: Category, source: Source = Source.SMS) = Transaction(
        amountPaise = rupees * 100, direction = Direction.DEBIT,
        timestampMillis = LocalDate.of(2026, 10, day).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(),
        merchant = "x", merchantKey = "x", category = category, source = source, rawText = "",
    )

    @Test
    fun `withdrawn cash counts once, whether or not spends are logged`() {
        val engine = InsightsEngine(zone)
        val atm = txn(2, 2000, Category.CASH)
        val upi = txn(3, 500, Category.FOOD_DELIVERY)

        // Nothing logged: the whole withdrawal counts as unlogged cash.
        val none = engine.summary(listOf(atm, upi), today)
        assertEquals(250000L, none.spentPaise)
        assertTrue(none.byCategory.any { it.category == Category.UNLOGGED_CASH && it.spentPaise == 200000L })

        // ₹1,200 logged: logged spends count, the remaining ₹800 stays unlogged. Total unchanged.
        val logged = listOf(atm, upi, txn(4, 700, Category.FOOD_DELIVERY, Source.MANUAL), txn(5, 500, Category.CABS, Source.MANUAL))
        val some = engine.summary(logged, today)
        assertEquals(250000L, some.spentPaise)
        assertEquals(200000L, some.cashWithdrawnPaise)
        assertEquals(120000L, some.cashLoggedPaise)
        assertTrue(some.byCategory.any { it.category == Category.UNLOGGED_CASH && it.spentPaise == 80000L })

        // Logged more than withdrawn (cash from elsewhere): no negative unlogged amount.
        val over = engine.summary(listOf(atm, txn(6, 2500, Category.ELECTRONICS, Source.MANUAL)), today)
        assertEquals(250000L, over.spentPaise)
    }

    @Test
    fun `daily totals cover each day up to today`() {
        val days = InsightsEngine(zone).dailyTotals(listOf(txn(3, 500, Category.FOOD_DELIVERY), txn(3, 100, Category.FOOD_DELIVERY)), java.time.YearMonth.of(2026, 10), today)
        assertEquals(17, days.size)
        assertEquals(60000L, days[2].second)
        assertEquals(0L, days[0].second)
    }
}

class WalletTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val today = LocalDate.of(2026, 10, 17)
    private val t0 = LocalDate.of(2026, 10, 2).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `bank to wallet top-ups are recognised and not counted as spending`() {
        val samples = listOf(
            "Sent Rs.2000.00 From HDFC Bank A/C *1234 To Add Money to Paytm Wallet On 02/10/26 Ref 628941075400",
            "Rs.2000.00 debited from A/c XX1234 to VPA paytm-addmoney@paytm on 02-10-26 Ref 628941075401",
            "Rs 500.00 debited from A/c XX1234 for UPI Lite top-up on 02-10-26. UPI Ref 628941075402",
            "INR 1000.00 debited A/c no. XX1234 02-10-26 UPI/P2M/628941075403/Amazon Pay Balance",
        )
        samples.forEach { body ->
            val t = Classifier().classify(SmsParser.parse("AX-HDFCBK", body, t0)!!)
            assertEquals(Category.WALLET, t.category, body)
            assertTrue(!t.isSpend, body)
        }
        // Paying out of the wallet is a spend, not a top-up.
        val fromWallet = Classifier().classify(SmsParser.parse("VM-PAYTMB", "Rs.450 paid from your Paytm Wallet to Swiggy on 02-10-26. Txn ID 628941075404", t0)!!)
        assertEquals(Category.FOOD_DELIVERY, fromWallet.category)

        // The wallet app's own "money added" notification is skipped; the bank SMS records it.
        assertEquals(null, NotificationParser.parse("net.one97.paytm", "₹2,000 added to Paytm Wallet", null, t0))
    }

    @Test
    fun `wallet money counts once, whether or not wallet payments are captured`() {
        val engine = InsightsEngine(zone)
        val topUp = Classifier().classify(SmsParser.parse("AX-HDFCBK", "Rs.2000.00 debited from A/c XX1234 to VPA paytm-addmoney@paytm on 02-10-26 Ref 628941075401", t0)!!)
            .copy(status = ReviewStatus.REVIEWED)
        val walletSpend = Classifier().classify(NotificationParser.parse("net.one97.paytm", "Paid ₹450 to Swiggy", null, t0 + 3_600_000)!!)
            .copy(status = ReviewStatus.REVIEWED)

        // Only the top-up seen: all ₹2,000 counts as untracked wallet spending.
        val onlyTopUp = engine.summary(listOf(topUp), today)
        assertEquals(200000L, onlyTopUp.spentPaise)

        // Top-up plus a ₹450 wallet payment: still ₹2,000 in total, ₹450 of it as Food. Not ₹2,450.
        val both = engine.summary(listOf(topUp, walletSpend), today)
        assertEquals(200000L, both.spentPaise)
        assertTrue(both.byCategory.any { it.category == Category.FOOD_DELIVERY && it.spentPaise == 45000L })
        assertTrue(both.byCategory.any { it.category == Category.UNTRACKED_WALLET && it.spentPaise == 155000L })
        assertEquals(200000L, both.walletToppedUpPaise)
        assertEquals(45000L, both.walletSpentPaise)
    }
}

class FlatCategoriesTest {
    @Test
    fun `the user's categories are flat, each its own group, and old built-in spending ids become hints`() {
        val petrol = Category.mine("Petrol", "⛽")
        val catalog = CategoryCatalog(listOf(petrol))
        assertEquals(petrol, catalog.groupOf(petrol))
        assertEquals(listOf(petrol) + Category.SYSTEM, catalog.all)
        assertTrue(Category.FOOD_DELIVERY !in catalog.all)
        assertEquals(Category.OTHER, catalog.byId(Category.FOOD_DELIVERY.id))
        assertEquals("Food delivery", catalog.hintFor(Category.FOOD_DELIVERY.id))
        // Old non-spending ids keep their meaning, so a SIP never turns into spending.
        assertEquals(Category.MUTUAL_FUNDS, catalog.byId(Category.MUTUAL_FUNDS.id))
        assertEquals(null, catalog.hintFor(petrol.id))

        val zone = ZoneId.of("Asia/Kolkata")
        val t = Transaction(
            amountPaise = 300000, direction = Direction.DEBIT,
            timestampMillis = LocalDate.of(2026, 10, 3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli(),
            merchant = "HP", merchantKey = "hp", category = catalog.byId(petrol.id), source = Source.SMS, rawText = "",
        )
        val groups = InsightsEngine(zone).summary(listOf(t), LocalDate.of(2026, 10, 17)).byGroup(catalog::groupOf)
        assertEquals(petrol, groups.single().group)
    }

    @Test
    fun `old custom categories under a group come through flat`() {
        val old = Category.custom("School van", "🚐", Category.G_EDUCATION)
        assertEquals(old.copy(parentId = null), CategoryCatalog(listOf(old)).byId(old.id))
    }
}
