package app.kharcha.core

/** A well-known merchant: the name to show, its default category, and the words that identify it. */
data class Brand(val name: String, val category: Category, val keywords: List<String>)

object Merchants {

    val BRANDS: List<Brand> = listOf(
        Brand("Swiggy", Category.FOOD, listOf("swiggy")),
        Brand("Zomato", Category.FOOD, listOf("zomato")),
        Brand("Domino's", Category.FOOD, listOf("domino", "jubilant")),
        Brand("McDonald's", Category.FOOD, listOf("mcdonald", "hardcastle")),
        Brand("KFC", Category.FOOD, listOf("kfc", "devyani")),
        Brand("Starbucks", Category.FOOD, listOf("starbucks", "tata starbucks")),
        Brand("Blinkit", Category.GROCERIES, listOf("blinkit", "grofers")),
        Brand("Zepto", Category.GROCERIES, listOf("zepto", "kiranakart")),
        Brand("BigBasket", Category.GROCERIES, listOf("bigbasket", "bbnow", "innovative retail")),
        Brand("Swiggy Instamart", Category.GROCERIES, listOf("instamart")),
        Brand("DMart", Category.GROCERIES, listOf("dmart", "avenue supermarts")),
        Brand("JioMart", Category.GROCERIES, listOf("jiomart")),
        Brand("Amazon", Category.SHOPPING, listOf("amazon", "amzn")),
        Brand("Flipkart", Category.SHOPPING, listOf("flipkart")),
        Brand("Myntra", Category.SHOPPING, listOf("myntra")),
        Brand("Ajio", Category.SHOPPING, listOf("ajio")),
        Brand("Meesho", Category.SHOPPING, listOf("meesho")),
        Brand("Nykaa", Category.SHOPPING, listOf("nykaa")),
        Brand("Croma", Category.SHOPPING, listOf("croma", "infiniti retail")),
        Brand("Reliance Digital", Category.SHOPPING, listOf("reliance digital")),
        Brand("Decathlon", Category.SHOPPING, listOf("decathlon")),
        Brand("IKEA", Category.SHOPPING, listOf("ikea")),
        Brand("Uber", Category.TRAVEL, listOf("uber")),
        Brand("Ola", Category.TRAVEL, listOf("olacabs", "ola cabs", "ani technologies", "ola money")),
        Brand("Rapido", Category.TRAVEL, listOf("rapido", "roppen")),
        Brand("IRCTC", Category.TRAVEL, listOf("irctc")),
        Brand("MakeMyTrip", Category.TRAVEL, listOf("makemytrip", "mmt")),
        Brand("Goibibo", Category.TRAVEL, listOf("goibibo")),
        Brand("IndiGo", Category.TRAVEL, listOf("indigo", "interglobe")),
        Brand("Air India", Category.TRAVEL, listOf("air india", "airindia")),
        Brand("redBus", Category.TRAVEL, listOf("redbus")),
        Brand("Metro", Category.TRAVEL, listOf("metro rail", "dmrc", "bmrcl", "mmrcl", "metro card")),
        Brand("FASTag", Category.TRAVEL, listOf("fastag", "netc")),
        Brand("Indian Oil", Category.FUEL, listOf("indian oil", "iocl")),
        Brand("Bharat Petroleum", Category.FUEL, listOf("bharat petroleum", "bpcl")),
        Brand("HP Petrol", Category.FUEL, listOf("hindustan petroleum", "hpcl")),
        Brand("Shell", Category.FUEL, listOf("shell")),
        Brand("Netflix", Category.SUBSCRIPTIONS, listOf("netflix")),
        Brand("Spotify", Category.SUBSCRIPTIONS, listOf("spotify")),
        Brand("YouTube Premium", Category.SUBSCRIPTIONS, listOf("youtube", "google youtube")),
        Brand("JioHotstar", Category.SUBSCRIPTIONS, listOf("hotstar", "jiohotstar", "novi digital")),
        Brand("Prime Video", Category.SUBSCRIPTIONS, listOf("prime video", "primevideo")),
        Brand("Apple", Category.SUBSCRIPTIONS, listOf("apple.com", "apple services", "itunes")),
        Brand("Google Play", Category.SUBSCRIPTIONS, listOf("google play", "googleplay", "play store")),
        Brand("BookMyShow", Category.ENTERTAINMENT, listOf("bookmyshow", "bigtree")),
        Brand("PVR INOX", Category.ENTERTAINMENT, listOf("pvr", "inox")),
        Brand("District", Category.ENTERTAINMENT, listOf("district")),
        Brand("Airtel", Category.RECHARGE, listOf("airtel")),
        Brand("Jio", Category.RECHARGE, listOf("jio prepaid", "reliance jio", "jio recharge", "myjio")),
        Brand("Vi", Category.RECHARGE, listOf("vodafone", "vi recharge")),
        Brand("Tata Play", Category.RECHARGE, listOf("tata play", "tatasky", "tata sky")),
        Brand("Electricity", Category.BILLS, listOf("electricity", "bescom", "msedcl", "tneb", "bses", "tata power", "adani elec", "torrent power", "mahavitaran")),
        Brand("Gas", Category.BILLS, listOf("mahanagar gas", "indraprastha gas", "piped gas", "gas bill", "indane", "bharatgas", "hp gas")),
        Brand("Water", Category.BILLS, listOf("water bill", "jal board", "bwssb")),
        Brand("Broadband", Category.BILLS, listOf("act fibernet", "hathway", "jiofiber", "airtel xstream", "broadband")),
        Brand("Apollo", Category.HEALTH, listOf("apollo")),
        Brand("PharmEasy", Category.HEALTH, listOf("pharmeasy")),
        Brand("1mg", Category.HEALTH, listOf("1mg", "tata 1mg")),
        Brand("Netmeds", Category.HEALTH, listOf("netmeds")),
        Brand("Practo", Category.HEALTH, listOf("practo")),
        Brand("Cult.fit", Category.HEALTH, listOf("cult.fit", "cultfit", "curefit")),
        Brand("Zerodha", Category.INVESTMENT, listOf("zerodha")),
        Brand("Groww", Category.INVESTMENT, listOf("groww", "nextbillion")),
        Brand("Upstox", Category.INVESTMENT, listOf("upstox")),
        Brand("Mutual fund SIP", Category.INVESTMENT, listOf("bse star", "nse clearing", "mutual fund", "indian clearing corp", "iccl", "sip")),
        Brand("LIC", Category.INSURANCE, listOf("lic of india", "licindia", "life insurance")),
        Brand("Insurance", Category.INSURANCE, listOf("insurance", "policybazaar", "acko", "hdfc ergo", "icici lombard", "star health")),
        Brand("Credit card bill", Category.TRANSFER, listOf("cred club", "credclub", "cred.club", "@cred", "bill desk cc", "credit card payment")),
        Brand("Loan EMI", Category.EMI, listOf("emi", "loan", "bajaj finance", "bajaj finserv", "home credit", "nach")),
        Brand("Rent", Category.RENT, listOf("rent", "nobroker", "housing.com")),
        Brand("Education", Category.EDUCATION, listOf("school", "college", "university", "tuition", "byju", "unacademy", "coursera", "udemy")),
    )

    /** Handles used by personal UPI IDs (Google Pay, PhonePe, Paytm) rather than merchants. */
    private val PERSONAL_HANDLES = setOf("okaxis", "okhdfcbank", "oksbi", "okicici", "ybl", "ibl", "axl", "apl", "upi")
    private val MERCHANT_QR = Regex("""^(?:paytmqr|bharatpe|q\d|mab\.|gpay-|pay-|qr|msw|vyapar|razorpay|cf\.|pinelabs)""", RegexOption.IGNORE_CASE)
    private val PHONE_HANDLE = Regex("""^(?:91)?\d{10}(?:-\d)?$""")
    private val COMPANY_SUFFIX = Regex(
        """\s+(?:pvt\.?|private|ltd\.?|limited|llp|india|technologies|technology|tech|services|solutions|retail|online|internet|e-?commerce|co\.?)\b""",
        RegexOption.IGNORE_CASE,
    )

    data class Resolved(val name: String, val key: String, val brand: Brand?, val isPerson: Boolean)

    fun brandFor(text: String): Brand? {
        val haystack = text.lowercase()
        return BRANDS.firstOrNull { brand -> brand.keywords.any { keyword -> containsWord(haystack, keyword) } }
    }

    /** Works out the display name for the other party from whatever the message gave us. */
    fun resolve(counterparty: String?, vpa: String?): Resolved {
        val brand = brandFor(listOfNotNull(counterparty, vpa).joinToString(" "))
        if (brand != null) return Resolved(brand.name, keyOf(brand.name), brand, isPerson = false)

        val handle = vpa?.substringBefore('@')
        val psp = vpa?.substringAfter('@', "")?.lowercase()
        val counterpartyIsVpa = counterparty != null && counterparty.contains('@')
        val niceName = counterparty?.takeUnless { counterpartyIsVpa }?.let(::tidy)?.takeIf { it.isNotEmpty() }

        val looksPersonal = handle != null && (PHONE_HANDLE.matches(handle) || (psp in PERSONAL_HANDLES && !MERCHANT_QR.containsMatchIn(handle)))
        val name = when {
            niceName != null -> niceName
            handle != null && PHONE_HANDLE.matches(handle) -> "UPI ··" + handle.takeLast(4)
            handle != null && MERCHANT_QR.containsMatchIn(handle) -> "UPI merchant"
            handle != null -> tidy(handle.replace(Regex("""[._\-]+"""), " ").replace(Regex("""\d+"""), " "))
                .ifEmpty { "UPI ··" + handle.takeLast(4) }
            else -> "Unknown"
        }
        val key = if (name == "UPI merchant" && vpa != null) keyOf(vpa) else keyOf(name)
        return Resolved(name, key, null, isPerson = looksPersonal)
    }

    fun keyOf(name: String): String = name.lowercase().filter { it.isLetterOrDigit() }

    /** "SWIGGY LIMITED" -> "Swiggy"; "ravi kumar" -> "Ravi Kumar". */
    fun tidy(raw: String): String {
        var name = raw.trim()
        while (true) {
            val stripped = name.replace(COMPANY_SUFFIX, "").trim()
            if (stripped == name || stripped.isEmpty()) break
            name = stripped
        }
        return name.split(Regex("""\s+""")).filter { it.isNotEmpty() }.joinToString(" ") { word ->
            if (word.length <= 3 && word.all { it.isUpperCase() || it.isDigit() } && word.any { it.isLetter() } && word.length > 1) {
                word
            } else {
                word.lowercase().replaceFirstChar { it.titlecase() }
            }
        }
    }

    private fun containsWord(haystack: String, keyword: String): Boolean {
        var index = haystack.indexOf(keyword)
        while (index >= 0) {
            val before = if (index == 0) ' ' else haystack[index - 1]
            val afterIndex = index + keyword.length
            val after = if (afterIndex >= haystack.length) ' ' else haystack[afterIndex]
            if (!before.isLetter() && !after.isLetter()) return true
            index = haystack.indexOf(keyword, index + 1)
        }
        return false
    }
}
