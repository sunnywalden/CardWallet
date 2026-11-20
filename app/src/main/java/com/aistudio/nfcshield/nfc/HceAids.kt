package com.aistudio.nfcshield.nfc

/**
 * Single source of truth for the AIDs this app can route and answer.
 *
 * The static list in res/xml/apduservice.xml must stay in sync with [BUILTIN]; a unit
 * test asserts equality. Removing an AID here without updating the XML (or vice versa)
 * creates routing/acceptance divergence where a reader sees the service but gets 6A82.
 */
object HceAids {
    val DEFAULT: String = NfcTagParser.DEFAULT_HCE_AID

    val BUILTIN: Set<String> = linkedSetOf(
        DEFAULT,              // custom access demo AID (F04E464353484945)
        "F0010203040506",
        "A0000002471001",     // PC/SC "Get Token" demo
        "D2760000850101"      // NDEF standard AID (kept for reader compatibility)
    )

    fun isAcceptedAid(requestedAid: String, configuredAid: String): Boolean {
        val configured = NfcTagParser.normalizeHex(configuredAid).ifEmpty { DEFAULT }
        return requestedAid == configured ||
            (configured == DEFAULT && requestedAid in BUILTIN)
    }

    /** AIDs that must be registered for routing: the builtin set plus the card AID. */
    fun registeredAidsFor(cardAidHex: String?): List<String> = buildList {
        addAll(BUILTIN)
        val clean = cardAidHex?.let { NfcTagParser.normalizeHex(it) }.orEmpty()
        if (NfcTagParser.isValidAidHex(clean) && clean !in BUILTIN) {
            add(clean)
        }
    }
}