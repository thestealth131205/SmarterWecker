package de.letheapp.nocturn.holidays

/** Die 16 deutschen Bundesländer mit ihrem ISO-3166-2-Subdivision-Code, wie ihn die OpenHolidaysAPI erwartet. */
enum class GermanState(val subdivisionCode: String, val displayName: String) {
    BADEN_WUERTTEMBERG("DE-BW", "Baden-Württemberg"),
    BAYERN("DE-BY", "Bayern"),
    BERLIN("DE-BE", "Berlin"),
    BRANDENBURG("DE-BB", "Brandenburg"),
    BREMEN("DE-HB", "Bremen"),
    HAMBURG("DE-HH", "Hamburg"),
    HESSEN("DE-HE", "Hessen"),
    MECKLENBURG_VORPOMMERN("DE-MV", "Mecklenburg-Vorpommern"),
    NIEDERSACHSEN("DE-NI", "Niedersachsen"),
    NORDRHEIN_WESTFALEN("DE-NW", "Nordrhein-Westfalen"),
    RHEINLAND_PFALZ("DE-RP", "Rheinland-Pfalz"),
    SAARLAND("DE-SL", "Saarland"),
    SACHSEN("DE-SN", "Sachsen"),
    SACHSEN_ANHALT("DE-ST", "Sachsen-Anhalt"),
    SCHLESWIG_HOLSTEIN("DE-SH", "Schleswig-Holstein"),
    THUERINGEN("DE-TH", "Thüringen");

    companion object {
        fun fromCode(code: String): GermanState? = entries.firstOrNull { it.subdivisionCode == code }
    }
}
