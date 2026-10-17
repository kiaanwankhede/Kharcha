package app.kharcha.core

/**
 * A well-known merchant: the name to show, its default category, and the words that identify it.
 * [alternatives] lists the other things a multi-business merchant sells (Swiggy: food delivery and
 * Instamart groceries), which a bank SMS usually cannot tell apart, so the user is asked instead.
 */
data class Brand(
    val name: String,
    val category: Category,
    val keywords: List<String>,
    val alternatives: List<Category> = emptyList(),
) {
    /** True when one payment to this merchant could be any of several categories. */
    val isMultiBusiness: Boolean get() = alternatives.isNotEmpty()

    /** Every category this merchant's payments can be, default first. */
    val choices: List<Category> get() = listOf(category) + alternatives
}

object Merchants {

    val BRANDS: List<Brand> = listOf(
        // Groceries & daily needs
        Brand("Swiggy Instamart", Category.QUICK_COMMERCE, listOf("instamart", "swiggyinstamart", "swiggy instamart", "swiggy-instamart")),
        Brand("Amazon Fresh", Category.QUICK_COMMERCE, listOf("amazon fresh", "amazonfresh", "amazon now")),
        Brand("Blinkit", Category.QUICK_COMMERCE, listOf("blinkit", "grofers")),
        Brand("Zepto", Category.QUICK_COMMERCE, listOf("zepto", "kiranakart"), alternatives = listOf(Category.CAFES)),
        Brand("Flipkart Minutes", Category.QUICK_COMMERCE, listOf("flipkart minutes", "flipkartminutes")),
        Brand("Zepto Café", Category.CAFES, listOf("zepto cafe", "zeptocafe")),
        Brand("BigBasket", Category.KIRANA, listOf("bigbasket", "bbnow", "innovative retail")),
        Brand("DMart", Category.KIRANA, listOf("dmart", "avenue supermarts")),
        Brand("JioMart", Category.KIRANA, listOf("jiomart", "reliance retail", "reliance fresh", "smart bazaar"), alternatives = listOf(Category.ONLINE_SHOPPING)),
        Brand("More", Category.KIRANA, listOf("more retail", "more supermarket")),
        Brand("Spencer's", Category.KIRANA, listOf("spencer")),
        Brand("Star Bazaar", Category.KIRANA, listOf("star bazaar", "trent hypermarket")),
        Brand("Country Delight", Category.MILK, listOf("country delight", "countrydelight")),
        Brand("Milkbasket", Category.MILK, listOf("milkbasket")),
        Brand("Amul", Category.MILK, listOf("amul")),
        Brand("Mother Dairy", Category.MILK, listOf("mother dairy", "motherdairy")),
        Brand("Akshayakalpa", Category.MILK, listOf("akshayakalpa")),
        Brand("Licious", Category.MEAT, listOf("licious", "delightful gourmet")),
        Brand("FreshToHome", Category.MEAT, listOf("freshtohome")),
        Brand("Bisleri", Category.WATER_CANS, listOf("bisleri")),
        // Food & drinks
        Brand(
            "Swiggy", Category.FOOD_DELIVERY, listOf("swiggy", "bundl technologies"),
            alternatives = listOf(Category.QUICK_COMMERCE, Category.RESTAURANTS),
        ),
        Brand("Zomato", Category.FOOD_DELIVERY, listOf("zomato", "eternal limited"), alternatives = listOf(Category.RESTAURANTS)),
        Brand("EatSure", Category.FOOD_DELIVERY, listOf("eatsure", "rebel foods")),
        Brand("Domino's", Category.RESTAURANTS, listOf("domino", "jubilant")),
        Brand("McDonald's", Category.RESTAURANTS, listOf("mcdonald", "hardcastle", "connaught plaza")),
        Brand("KFC", Category.RESTAURANTS, listOf("kfc", "devyani", "sapphire foods")),
        Brand("Pizza Hut", Category.RESTAURANTS, listOf("pizza hut", "pizzahut")),
        Brand("Burger King", Category.RESTAURANTS, listOf("burger king", "restaurant brands asia")),
        Brand("Haldiram's", Category.SNACKS, listOf("haldiram")),
        Brand("Starbucks", Category.CAFES, listOf("starbucks")),
        Brand("Chai Point", Category.CAFES, listOf("chai point", "chaipoint", "mountain trail")),
        Brand("Third Wave Coffee", Category.CAFES, listOf("third wave")),
        Brand("Café Coffee Day", Category.CAFES, listOf("coffee day", "cafe coffee")),
        Brand("Chaayos", Category.CAFES, listOf("chaayos", "sunshine teahouse")),
        Brand("Blue Tokai", Category.CAFES, listOf("blue tokai")),
        // Transport
        Brand("Uber", Category.CABS, listOf("uber")),
        Brand("Ola", Category.CABS, listOf("olacabs", "ola cabs", "ani technologies", "ola money")),
        Brand("Rapido", Category.CABS, listOf("rapido", "roppen")),
        Brand("BluSmart", Category.CABS, listOf("blusmart")),
        Brand("Namma Yatri", Category.CABS, listOf("namma yatri", "nammayatri")),
        Brand("Indian Oil", Category.FUEL, listOf("indian oil", "iocl")),
        Brand("Bharat Petroleum", Category.FUEL, listOf("bharat petroleum", "bpcl")),
        Brand("HP Petrol", Category.FUEL, listOf("hindustan petroleum", "hpcl")),
        Brand("Shell", Category.FUEL, listOf("shell")),
        Brand("Nayara", Category.FUEL, listOf("nayara")),
        Brand("Metro", Category.METRO, listOf("metro rail", "dmrc", "bmrcl", "mmrcl", "metro card", "mmmocl", "chennai metro")),
        Brand("FASTag", Category.TOLLS, listOf("fastag", "netc")),
        Brand("Parking", Category.TOLLS, listOf("parking", "park+", "parkplus")),
        // Home
        Brand("NoBroker", Category.RENT, listOf("nobroker", "housing.com")),
        Brand("MyGate", Category.MAINTENANCE, listOf("mygate", "nobrokerhood", "adda", "society maint")),
        Brand("Urban Company", Category.HOME_SERVICES, listOf("urban company", "urbanclap", "urbancompany")),
        Brand("IKEA", Category.FURNITURE, listOf("ikea")),
        Brand("Pepperfry", Category.FURNITURE, listOf("pepperfry")),
        Brand("Home Centre", Category.FURNITURE, listOf("home centre")),
        // Bills & utilities
        Brand("Airtel", Category.MOBILE, listOf("airtel")),
        Brand("Jio", Category.MOBILE, listOf("jio prepaid", "reliance jio", "jio recharge", "myjio")),
        Brand("Vi", Category.MOBILE, listOf("vodafone", "vi recharge")),
        Brand("BSNL", Category.MOBILE, listOf("bsnl")),
        Brand("Tata Play", Category.DTH, listOf("tata play", "tatasky", "tata sky")),
        Brand("Dish TV", Category.DTH, listOf("dish tv", "dishtv", "d2h")),
        Brand("Electricity", Category.ELECTRICITY, listOf("electricity", "bescom", "msedcl", "tneb", "tangedco", "bses", "tata power", "adani elec", "torrent power", "mahavitaran", "uppcl", "kseb", "cesc", "tsspdcl", "apspdcl")),
        Brand("Gas", Category.GAS, listOf("mahanagar gas", "indraprastha gas", "piped gas", "gas bill", "indane", "bharatgas", "hp gas", "gujarat gas", "adani total gas")),
        Brand("Water", Category.WATER, listOf("water bill", "jal board", "bwssb", "water supply")),
        Brand("Broadband", Category.INTERNET, listOf("act fibernet", "hathway", "jiofiber", "airtel xstream", "broadband", "excitel", "you broadband")),
        // Shopping
        Brand(
            "Amazon", Category.ONLINE_SHOPPING, listOf("amazon", "amzn"),
            alternatives = listOf(Category.QUICK_COMMERCE, Category.ELECTRONICS, Category.OTT),
        ),
        Brand("Flipkart", Category.ONLINE_SHOPPING, listOf("flipkart"), alternatives = listOf(Category.QUICK_COMMERCE, Category.ELECTRONICS)),
        Brand("Meesho", Category.ONLINE_SHOPPING, listOf("meesho")),
        Brand("Myntra", Category.CLOTHES, listOf("myntra")),
        Brand("Ajio", Category.CLOTHES, listOf("ajio")),
        Brand("Westside", Category.CLOTHES, listOf("westside", "trent limited")),
        Brand("Zudio", Category.CLOTHES, listOf("zudio")),
        Brand("Max Fashion", Category.CLOTHES, listOf("max fashion", "lifestyle international")),
        Brand("H&M", Category.CLOTHES, listOf("h&m", "hennes")),
        Brand("Bata", Category.CLOTHES, listOf("bata")),
        Brand("Decathlon", Category.CLOTHES, listOf("decathlon")),
        Brand("Nykaa", Category.BEAUTY, listOf("nykaa")),
        Brand("Purplle", Category.BEAUTY, listOf("purplle")),
        Brand("Croma", Category.ELECTRONICS, listOf("croma", "infiniti retail")),
        Brand("Reliance Digital", Category.ELECTRONICS, listOf("reliance digital")),
        Brand("Vijay Sales", Category.ELECTRONICS, listOf("vijay sales")),
        Brand("Apple Store", Category.ELECTRONICS, listOf("apple store", "imagine store")),
        Brand("FNP", Category.GIFTS_GIVEN, listOf("ferns n petals", "fnp", "igp.com")),
        // Health
        Brand("PharmEasy", Category.MEDICINES, listOf("pharmeasy")),
        Brand("Tata 1mg", Category.MEDICINES, listOf("1mg", "tata 1mg")),
        Brand("Netmeds", Category.MEDICINES, listOf("netmeds")),
        Brand("Apollo Pharmacy", Category.MEDICINES, listOf("apollo pharmacy", "apollo 247", "apollo")),
        Brand("MedPlus", Category.MEDICINES, listOf("medplus")),
        Brand("Practo", Category.DOCTOR, listOf("practo")),
        Brand("Thyrocare", Category.LAB_TESTS, listOf("thyrocare", "dr lal", "lal pathlabs", "metropolis", "redcliffe", "healthians")),
        Brand("Cult.fit", Category.FITNESS, listOf("cult.fit", "cultfit", "curefit")),
        // Entertainment
        Brand("Netflix", Category.OTT, listOf("netflix")),
        Brand("Spotify", Category.OTT, listOf("spotify")),
        Brand("YouTube Premium", Category.OTT, listOf("youtube", "google youtube")),
        Brand("JioHotstar", Category.OTT, listOf("hotstar", "jiohotstar", "novi digital", "jiocinema")),
        Brand("Prime Video", Category.OTT, listOf("prime video", "primevideo")),
        Brand("SonyLIV", Category.OTT, listOf("sonyliv", "sony liv")),
        Brand("Zee5", Category.OTT, listOf("zee5")),
        Brand("Apple", Category.OTT, listOf("apple.com", "apple services", "itunes")),
        Brand("Google Play", Category.GAMES, listOf("google play", "googleplay", "play store")),
        Brand("BookMyShow", Category.MOVIES, listOf("bookmyshow", "bigtree")),
        Brand("PVR INOX", Category.MOVIES, listOf("pvr", "inox")),
        Brand("District", Category.MOVIES, listOf("district")),
        // Travel & holidays
        Brand("IndiGo", Category.FLIGHTS, listOf("indigo", "interglobe")),
        Brand("Air India", Category.FLIGHTS, listOf("air india", "airindia")),
        Brand("Akasa Air", Category.FLIGHTS, listOf("akasa")),
        Brand("SpiceJet", Category.FLIGHTS, listOf("spicejet")),
        Brand("MakeMyTrip", Category.FLIGHTS, listOf("makemytrip", "mmt")),
        Brand("Goibibo", Category.FLIGHTS, listOf("goibibo")),
        Brand("Cleartrip", Category.FLIGHTS, listOf("cleartrip")),
        Brand("ixigo", Category.TRAINS, listOf("ixigo")),
        Brand("IRCTC", Category.TRAINS, listOf("irctc")),
        Brand("redBus", Category.BUSES, listOf("redbus")),
        Brand("AbhiBus", Category.BUSES, listOf("abhibus")),
        Brand("OYO", Category.HOTELS, listOf("oyo", "oravel")),
        Brand("Airbnb", Category.HOTELS, listOf("airbnb")),
        Brand("Booking.com", Category.HOTELS, listOf("booking.com")),
        Brand("Agoda", Category.HOTELS, listOf("agoda")),
        // Education
        Brand("Coursera", Category.COURSES, listOf("coursera")),
        Brand("Udemy", Category.COURSES, listOf("udemy")),
        Brand("Kindle", Category.COURSES, listOf("kindle")),
        Brand("Unacademy", Category.TUITION, listOf("unacademy", "sorting hat")),
        Brand("BYJU'S", Category.TUITION, listOf("byju", "think and learn")),
        Brand("Physics Wallah", Category.TUITION, listOf("physicswallah", "physics wallah")),
        Brand("School / college", Category.SCHOOL_FEES, listOf("school", "college", "university", "vidyalaya")),
        // Giving
        Brand("Temple", Category.PUJA, listOf("temple", "mandir", "iskcon", "devasthanam", "tirumala", "ttd", "gurudwara", "puja", "pooja")),
        Brand("Donation", Category.CHARITY, listOf("giveindia", "give india", "ketto", "milaap", "donation", "charity", "pm cares")),
        // Loans, fees & taxes
        Brand("Income tax", Category.TAXES, listOf("income tax", "incometax", "tin-nsdl", "protean", "egov", "challan", "gst")),
        Brand("LIC", Category.INSURANCE, listOf("lic of india", "licindia", "life insurance")),
        Brand("Insurance", Category.INSURANCE, listOf("insurance", "policybazaar", "acko", "hdfc ergo", "icici lombard", "star health", "niva bupa", "care health", "digit insurance")),
        Brand("Loan EMI", Category.EMI, listOf("emi", "loan", "bajaj finance", "bajaj finserv", "home credit", "nach", "tata capital", "hdb financial")),
        // Saving & investing
        Brand("Groww", Category.MUTUAL_FUNDS, listOf("groww", "nextbillion")),
        Brand("Mutual fund SIP", Category.MUTUAL_FUNDS, listOf("bse star", "nse clearing", "mutual fund", "indian clearing corp", "iccl", "sip", "kfintech", "cams", "coin by zerodha", "kuvera", "paytm money")),
        Brand("Zerodha", Category.STOCKS, listOf("zerodha")),
        Brand("Upstox", Category.STOCKS, listOf("upstox", "rksv")),
        Brand("Angel One", Category.STOCKS, listOf("angel one", "angel broking")),
        Brand("Dhan", Category.STOCKS, listOf("moneylicious", "dhan.co")),
        Brand("NPS", Category.RETIREMENT, listOf("nps", "national pension", "ppf", "epfo")),
        Brand("Fixed deposit", Category.DEPOSITS, listOf("fixed deposit", "recurring deposit", "fd booking", "rd installment")),
        Brand("Digital gold", Category.GOLD, listOf("digital gold", "safegold", "mmtc-pamp", "augmont", "jar app", "tanishq", "kalyan jewel", "caratlane")),
        // Money movement
        Brand("Credit card bill", Category.CARD_BILL, listOf("cred club", "credclub", "cred.club", "@cred", "bill desk cc", "credit card payment", "cc bill")),
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

    /** The merchant behind a stored transaction, if it is a known one. */
    fun brandOf(t: Transaction): Brand? = brandFor(listOfNotNull(t.merchant, t.vpa).joinToString(" "))

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
