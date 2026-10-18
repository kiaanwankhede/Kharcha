package app.kharcha.core

import kotlinx.serialization.Serializable

/** What a category means for totals. Only SPENDING counts as spending. */
enum class Kind { SPENDING, SAVING, MOVEMENT, INCOME }

/**
 * A category in a two-level tree: groups (no [parentId]) hold subcategories, and every transaction
 * points at a subcategory. Users pick subcategories; groups only organise and total them.
 *
 * Built-in categories are the constants below. Users can add their own subcategories (ids start with
 * "custom_") under any group; they take the group's kind.
 */
@Serializable
data class Category(
    val id: String,
    val label: String,
    val emoji: String,
    val countsAsSpend: Boolean = true,
    val builtIn: Boolean = true,
    val parentId: String? = null,
    val kind: Kind = Kind.SPENDING,
) {
    val isGroup: Boolean get() = parentId == null

    /** One of the user's tags, standing in as a category so totals and budgets work per tag. */
    val isTag: Boolean get() = id.startsWith(TAG_PREFIX)

    /** The tag name for a tag category ("food"), else null. */
    val tagName: String? get() = if (isTag) id.removePrefix(TAG_PREFIX) else null

    companion object {
        private fun group(id: String, label: String, emoji: String, kind: Kind = Kind.SPENDING) =
            Category(id, label, emoji, countsAsSpend = kind == Kind.SPENDING, parentId = null, kind = kind)

        private fun sub(parent: Category, id: String, label: String, emoji: String, kind: Kind = parent.kind) =
            Category(id, label, emoji, countsAsSpend = kind == Kind.SPENDING, parentId = parent.id, kind = kind)

        // Spending
        val G_GROCERIES = group("g.groceries", "Groceries & daily needs", "🛒")
        val G_FOOD = group("g.food", "Food & drinks", "🍔")
        val G_TRANSPORT = group("g.transport", "Transport", "🚕")
        val G_HOME = group("g.home", "Home", "🏠")
        val G_BILLS = group("g.bills", "Bills & utilities", "💡")
        val G_SHOPPING = group("g.shopping", "Shopping", "🛍️")
        val G_HEALTH = group("g.health", "Health", "💊")
        val G_FUN = group("g.fun", "Entertainment", "🎬")
        val G_TRAVEL = group("g.travel", "Travel & holidays", "✈️")
        val G_EDUCATION = group("g.education", "Education", "🎓")
        val G_PEOPLE = group("g.people", "Family & people", "👨‍👩‍👧")
        val G_GIVING = group("g.giving", "Puja, gifts & giving", "🙏")
        val G_LOANS = group("g.loans", "Loans, fees & taxes", "🏦")
        val G_OTHER = group("g.other", "Other", "🏷️")
        // Not spending
        val G_INVEST = group("g.invest", "Saving & investing", "📈", Kind.SAVING)
        val G_TRANSFERS = group("g.transfers", "Money movement", "🔄", Kind.MOVEMENT)
        val G_INCOME = group("g.income", "Money in", "💰", Kind.INCOME)

        val KIRANA = sub(G_GROCERIES, "groceries.kirana", "Kirana & supermarket", "🛒")
        val QUICK_COMMERCE = sub(G_GROCERIES, "groceries.quick", "Quick commerce", "⚡")
        val MILK = sub(G_GROCERIES, "groceries.milk", "Milk & dairy", "🥛")
        val VEGETABLES = sub(G_GROCERIES, "groceries.veg", "Vegetables & fruits", "🥦")
        val MEAT = sub(G_GROCERIES, "groceries.meat", "Meat & fish", "🍗")
        val WATER_CANS = sub(G_GROCERIES, "groceries.water", "Water cans", "💧")

        val FOOD_DELIVERY = sub(G_FOOD, "food.delivery", "Food delivery", "🛵")
        val RESTAURANTS = sub(G_FOOD, "food.restaurants", "Restaurants", "🍽️")
        val CAFES = sub(G_FOOD, "food.cafes", "Cafés & chai", "☕")
        val SNACKS = sub(G_FOOD, "food.snacks", "Snacks & sweets", "🍩")
        val ALCOHOL = sub(G_FOOD, "food.alcohol", "Bars & alcohol", "🍺")

        val CABS = sub(G_TRANSPORT, "transport.cabs", "Cabs & autos", "🚕")
        val FUEL = sub(G_TRANSPORT, "transport.fuel", "Fuel", "⛽")
        val METRO = sub(G_TRANSPORT, "transport.metro", "Metro, bus & local train", "🚇")
        val TOLLS = sub(G_TRANSPORT, "transport.tolls", "Parking & tolls", "🅿️")
        val VEHICLE_SERVICE = sub(G_TRANSPORT, "transport.service", "Vehicle service & repairs", "🔧")
        val VEHICLE_COSTS = sub(G_TRANSPORT, "transport.vehicle", "Vehicle EMI & insurance", "🚗")

        val RENT = sub(G_HOME, "home.rent", "Rent", "🏠")
        val MAINTENANCE = sub(G_HOME, "home.maintenance", "Society maintenance", "🏢")
        val DOMESTIC_HELP = sub(G_HOME, "home.help", "Domestic help", "🧹")
        val HOME_SERVICES = sub(G_HOME, "home.services", "Repairs & services", "🛠️")
        val FURNITURE = sub(G_HOME, "home.furniture", "Furniture & home items", "🛋️")

        val ELECTRICITY = sub(G_BILLS, "bills.electricity", "Electricity", "⚡")
        val MOBILE = sub(G_BILLS, "bills.mobile", "Mobile recharge & postpaid", "📱")
        val INTERNET = sub(G_BILLS, "bills.internet", "Internet & broadband", "🌐")
        val GAS = sub(G_BILLS, "bills.gas", "Gas", "🔥")
        val WATER = sub(G_BILLS, "bills.water", "Water", "🚰")
        val DTH = sub(G_BILLS, "bills.dth", "DTH & cable", "📺")
        val OTHER_BILLS = sub(G_BILLS, "bills.other", "Other bills", "🧾")

        val CLOTHES = sub(G_SHOPPING, "shopping.clothes", "Clothes & shoes", "👕")
        val ELECTRONICS = sub(G_SHOPPING, "shopping.electronics", "Electronics & gadgets", "💻")
        val ONLINE_SHOPPING = sub(G_SHOPPING, "shopping.online", "Online shopping", "📦")
        val BEAUTY = sub(G_SHOPPING, "shopping.beauty", "Beauty & personal care", "💄")
        val GIFTS_GIVEN = sub(G_SHOPPING, "shopping.gifts", "Gifts given", "🎁")

        val MEDICINES = sub(G_HEALTH, "health.medicines", "Medicines", "💊")
        val DOCTOR = sub(G_HEALTH, "health.doctor", "Doctor & consultations", "🩺")
        val LAB_TESTS = sub(G_HEALTH, "health.tests", "Tests & labs", "🧪")
        val HOSPITAL = sub(G_HEALTH, "health.hospital", "Hospital", "🏥")
        val FITNESS = sub(G_HEALTH, "health.fitness", "Gym & fitness", "🏋️")

        val OTT = sub(G_FUN, "fun.ott", "OTT & music subscriptions", "🔁")
        val MOVIES = sub(G_FUN, "fun.movies", "Movies & events", "🎟️")
        val GAMES = sub(G_FUN, "fun.games", "Games & apps", "🎮")
        val OUTINGS = sub(G_FUN, "fun.outings", "Outings & hobbies", "🎳")

        val FLIGHTS = sub(G_TRAVEL, "travel.flights", "Flights", "✈️")
        val TRAINS = sub(G_TRAVEL, "travel.trains", "Trains", "🚆")
        val BUSES = sub(G_TRAVEL, "travel.bus", "Long-distance bus", "🚌")
        val HOTELS = sub(G_TRAVEL, "travel.hotels", "Hotels & stays", "🏨")
        val HOLIDAY = sub(G_TRAVEL, "travel.holiday", "Holiday spends", "🏖️")

        val SCHOOL_FEES = sub(G_EDUCATION, "edu.fees", "School & college fees", "🏫")
        val TUITION = sub(G_EDUCATION, "edu.tuition", "Tuition & coaching", "📚")
        val COURSES = sub(G_EDUCATION, "edu.courses", "Courses & books", "🎓")
        val STATIONERY = sub(G_EDUCATION, "edu.stationery", "Stationery", "✏️")

        val FAMILY = sub(G_PEOPLE, "people.family", "Money to parents & family", "👪")
        val KIDS = sub(G_PEOPLE, "people.kids", "Kids", "🧸")
        val PETS = sub(G_PEOPLE, "people.pets", "Pets", "🐶")
        val PEOPLE = sub(G_PEOPLE, "people.friends", "Sent to friends", "🧑‍🤝‍🧑")
        /** Money lent is expected back, so it is not spending. */
        val LENT = sub(G_PEOPLE, "people.lent", "Lent (to get back)", "🤝", Kind.MOVEMENT)
        /** Paying back money borrowed earlier: settles a debt, not spending. */
        val REPAYING = sub(G_PEOPLE, "people.repay", "Paying back what I borrowed", "↪️", Kind.MOVEMENT)

        val PUJA = sub(G_GIVING, "giving.puja", "Puja & temple", "🪔")
        val FESTIVALS = sub(G_GIVING, "giving.festivals", "Festivals", "🎆")
        val CHARITY = sub(G_GIVING, "giving.charity", "Donations & charity", "💝")
        val WEDDINGS = sub(G_GIVING, "giving.weddings", "Weddings & functions", "💐")

        val EMI = sub(G_LOANS, "loans.emi", "Loan EMI", "🏦")
        val CARD_CHARGES = sub(G_LOANS, "loans.card", "Credit card interest & charges", "💳")
        val BANK_FEES = sub(G_LOANS, "loans.fees", "Bank fees & penalties", "🧾")
        val TAXES = sub(G_LOANS, "loans.tax", "Income tax & govt fees", "🏛️")
        val INSURANCE = sub(G_LOANS, "loans.insurance", "Insurance premiums", "🛡️")

        val OTHER = sub(G_OTHER, "other.uncategorised", "Untagged", "🏷️")
        /** Only appears in summaries, for withdrawn cash that has no logged spends against it. */
        val UNLOGGED_CASH = sub(G_OTHER, "other.cash", "Cash (not logged)", "💵")
        /** Only appears in summaries, for wallet money whose payments were not captured. */
        val UNTRACKED_WALLET = sub(G_OTHER, "other.wallet", "Wallet (spends not tracked)", "👛")

        val MUTUAL_FUNDS = sub(G_INVEST, "invest.mf", "Mutual fund SIPs", "📈")
        val STOCKS = sub(G_INVEST, "invest.stocks", "Stocks", "📊")
        val DEPOSITS = sub(G_INVEST, "invest.deposits", "FD & RD", "🏦")
        val RETIREMENT = sub(G_INVEST, "invest.retirement", "PPF, NPS & EPF", "🧓")
        val GOLD = sub(G_INVEST, "invest.gold", "Gold & silver", "🪙")
        /** One choice for any saving or investing, so it is never counted as spending. */
        val SAVINGS = sub(G_INVEST, "invest.all", "Savings & investments", "📈")

        val TRANSFER = sub(G_TRANSFERS, "move.own", "Own accounts", "🔄")
        val CARD_BILL = sub(G_TRANSFERS, "move.cardbill", "Credit card bill payment", "💳")
        /**
         * Money moved from a bank account into a wallet or UPI Lite. Like an ATM withdrawal it is not
         * spending by itself: payments made from the wallet count instead, and top-ups with no tracked
         * wallet payments against them count as [UNTRACKED_WALLET].
         */
        val WALLET = sub(G_TRANSFERS, "move.wallet", "Wallet / UPI Lite top-up", "👛")
        /**
         * ATM withdrawals are not spending by themselves: the cash is spent later. Cash spends the user
         * logs count instead, and whatever was withdrawn but never logged counts as [UNLOGGED_CASH].
         */
        val CASH = sub(G_TRANSFERS, "move.atm", "ATM cash withdrawal", "🏧")

        val SALARY = sub(G_INCOME, "in.salary", "Salary", "💼")
        val REFUNDS = sub(G_INCOME, "in.refunds", "Refunds & cashback", "↩️")
        val INTEREST = sub(G_INCOME, "in.interest", "Interest & dividends", "💹")
        val RECEIVED = sub(G_INCOME, "in.received", "Received from people", "🤲")
        val BORROWED = sub(G_INCOME, "in.borrowed", "Borrowed (to repay)", "🫴", Kind.MOVEMENT)
        /** Money lent earlier coming back: settles a loan, not income. */
        val PAID_BACK = sub(G_INCOME, "in.paidback", "Paid back to me", "🔁", Kind.MOVEMENT)
        /** The office paying back expenses marked for reimbursement: not income. */
        val REIMBURSED = sub(G_INCOME, "in.reimbursed", "Reimbursement received", "🧾", Kind.MOVEMENT)
        val INCOME = sub(G_INCOME, "in.other", "Other income", "💰")

        val GROUPS: List<Category> = listOf(
            G_GROCERIES, G_FOOD, G_TRANSPORT, G_HOME, G_BILLS, G_SHOPPING, G_HEALTH, G_FUN, G_TRAVEL, G_EDUCATION,
            G_PEOPLE, G_GIVING, G_LOANS, G_OTHER, G_INVEST, G_TRANSFERS, G_INCOME,
        )

        /** Every built-in subcategory, in display order. */
        val BUILT_IN: List<Category> = listOf(
            KIRANA, QUICK_COMMERCE, MILK, VEGETABLES, MEAT, WATER_CANS,
            FOOD_DELIVERY, RESTAURANTS, CAFES, SNACKS, ALCOHOL,
            CABS, FUEL, METRO, TOLLS, VEHICLE_SERVICE, VEHICLE_COSTS,
            RENT, MAINTENANCE, DOMESTIC_HELP, HOME_SERVICES, FURNITURE,
            ELECTRICITY, MOBILE, INTERNET, GAS, WATER, DTH, OTHER_BILLS,
            CLOTHES, ELECTRONICS, ONLINE_SHOPPING, BEAUTY, GIFTS_GIVEN,
            MEDICINES, DOCTOR, LAB_TESTS, HOSPITAL, FITNESS,
            OTT, MOVIES, GAMES, OUTINGS,
            FLIGHTS, TRAINS, BUSES, HOTELS, HOLIDAY,
            SCHOOL_FEES, TUITION, COURSES, STATIONERY,
            FAMILY, KIDS, PETS, PEOPLE, LENT, REPAYING,
            PUJA, FESTIVALS, CHARITY, WEDDINGS,
            EMI, CARD_CHARGES, BANK_FEES, TAXES, INSURANCE,
            OTHER,
            MUTUAL_FUNDS, STOCKS, DEPOSITS, RETIREMENT, GOLD, SAVINGS,
            TRANSFER, CARD_BILL, WALLET, CASH,
            SALARY, REFUNDS, INTEREST, RECEIVED, BORROWED, PAID_BACK, REIMBURSED, INCOME,
        )

        /**
         * The only built-in choices left: money that isn't spending. The app needs these to avoid
         * counting things twice (ATM cash, wallet top-ups, own transfers, card bills) and for lending.
         * Every spending category is the user's own.
         */
        val SYSTEM: List<Category> = listOf(
            TRANSFER, CARD_BILL, CASH, WALLET, SAVINGS,
            LENT, REPAYING, BORROWED, PAID_BACK, REIMBURSED,
            SALARY, REFUNDS, INTEREST, RECEIVED, INCOME,
        )

        /** Summary-only rows; never picked by hand. */
        val SYNTHETIC: List<Category> = listOf(UNLOGGED_CASH, UNTRACKED_WALLET)

        private val groupsById = GROUPS.associateBy { it.id }

        /** The group a category belongs to (a group is its own group). */
        fun groupOf(category: Category): Category =
            if (category.isGroup) category else groupsById[category.parentId] ?: G_OTHER

        /**
         * Ids used by the first version of the app (a flat list), mapped to the closest subcategory.
         * Merchant-specific re-sorting happens in [Classifier.recategorise].
         */
        val LEGACY_IDS: Map<String, Category> = mapOf(
            "FOOD" to RESTAURANTS, "GROCERIES" to KIRANA, "SHOPPING" to ONLINE_SHOPPING, "TRAVEL" to CABS,
            "FUEL" to FUEL, "BILLS" to OTHER_BILLS, "RECHARGE" to MOBILE, "ENTERTAINMENT" to MOVIES,
            "SUBSCRIPTIONS" to OTT, "HEALTH" to MEDICINES, "EDUCATION" to SCHOOL_FEES, "RENT" to RENT,
            "INVESTMENT" to MUTUAL_FUNDS, "EMI" to EMI, "INSURANCE" to INSURANCE, "PEOPLE" to PEOPLE,
            "CASH" to CASH, "WALLET" to WALLET, "TRANSFER" to TRANSFER, "INCOME" to INCOME, "OTHER" to OTHER,
            "UNLOGGED_CASH" to UNLOGGED_CASH, "UNTRACKED_WALLET" to UNTRACKED_WALLET,
        )

        /** A user-made group; its subcategories take its [kind]. */
        fun customGroup(label: String, emoji: String, kind: Kind = Kind.SPENDING): Category = Category(
            id = CUSTOM_GROUP_PREFIX + java.util.UUID.randomUUID().toString().replace("-", "").take(12),
            label = label.trim(),
            emoji = emoji.trim().ifEmpty { "📁" },
            countsAsSpend = kind == Kind.SPENDING,
            builtIn = false,
            parentId = null,
            kind = kind,
        )

        const val CUSTOM_GROUP_PREFIX = "customg_"

        const val TAG_PREFIX = "tag:"

        /**
         * A tag as a category: payments count under their first (main) tag in totals. The id is the
         * cleaned name, so the same name is always the same tag.
         */
        fun forTag(name: String, @Suppress("UNUSED_PARAMETER") emoji: String = ""): Category {
            val clean = Tags.clean(name)
            // Tags are plain words: no icon.
            return Category(TAG_PREFIX + clean, clean, "", countsAsSpend = true, builtIn = false, parentId = null, kind = Kind.SPENDING)
        }

        /** A category the user made while reviewing: flat, no group, counts as spending. */
        fun mine(label: String, emoji: String): Category = Category(
            id = "mine_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12),
            label = label.trim(),
            emoji = emoji.trim().ifEmpty { "🏷️" },
            countsAsSpend = true,
            builtIn = false,
            parentId = null,
            kind = Kind.SPENDING,
        )

        fun custom(label: String, emoji: String, parent: Category): Category {
            val group = if (parent.isGroup) parent else groupOf(parent)
            return Category(
                id = "custom_" + java.util.UUID.randomUUID().toString().replace("-", "").take(12),
                label = label.trim(),
                emoji = emoji.trim().ifEmpty { "🏷️" },
                countsAsSpend = group.kind == Kind.SPENDING,
                builtIn = false,
                parentId = group.id,
                kind = group.kind,
            )
        }
    }
}

/**
 * The categories in use: the user's own (a flat list, no groups) plus the few built-in "not spending"
 * ones in [Category.SYSTEM]. Each category is its own group, so totals and budgets work per category.
 *
 * Payments still filed under one of the old built-in spending categories read as Uncategorised, with
 * the old name kept as a [hintFor] so it can be suggested or turned into a category of the user's own.
 */
class CategoryCatalog(custom: List<Category> = emptyList()) {
    /** The user's own categories, including any made before categories went flat. */
    val mine: List<Category> = custom.map { c -> c.copy(parentId = null, emoji = if (c.isTag) "" else c.emoji) }.sortedBy { it.label.lowercase() }

    /** Every pickable category: the user's, then the built-in non-spending ones. */
    val all: List<Category> = mine + Category.SYSTEM

    /** Kept for screens that list groups; with a flat list every category is a group. */
    val groups: List<Category> get() = all

    // Old non-spending ids (SIPs, FDs...) stay meaningful so old payments keep counting correctly.
    private val known: Map<String, Category> =
        (Category.BUILT_IN.filter { it.kind != Kind.SPENDING } + Category.SYSTEM + Category.SYNTHETIC + Category.OTHER + mine)
            .associateBy { it.id }

    /** The user's tags (with their emoji), most recently made last. */
    val tags: List<Category> get() = mine.filter { it.isTag }

    /** The tag category for [name]: the saved one with its emoji, or a fresh one. */
    fun tag(name: String): Category = known[Category.TAG_PREFIX + Tags.clean(name)] ?: Category.forTag(name)

    fun childrenOf(group: Category): List<Category> = if (known.containsKey(group.id)) listOf(group) else emptyList()

    fun groupOf(category: Category): Category = category

    fun isKnown(id: String): Boolean = known.containsKey(id) || id.startsWith(Category.TAG_PREFIX)

    /** Unknown ids (an old built-in spending category, a deleted category) read as Uncategorised. */
    fun byId(id: String): Category =
        known[id]
            ?: if (id.startsWith(Category.TAG_PREFIX)) Category.forTag(id.removePrefix(Category.TAG_PREFIX))
            else Category.LEGACY_IDS[id]?.let { known[it.id] } ?: Category.OTHER

    /** The old built-in name behind an id this catalog no longer knows, like "Food delivery". */
    fun hintFor(id: String): String? =
        if (isKnown(id)) null else (Category.BUILT_IN.firstOrNull { it.id == id } ?: Category.LEGACY_IDS[id])?.label
}
