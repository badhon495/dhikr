package com.dhikr.app.core.model

object BuiltInDhikr {
    val all: List<Dhikr> = listOf(
        Dhikr(
            id = "kursi",
            name = "Ayatul Kursi",
            arabic = "",
            transliteration = "আল্লাহু লা ইলাহা ইল্লা হুয়াল হাইয়্যুল কইয়্যুমু লা তা খুজুহু সিনাত্যু ওয়ালা নাউম। " +
                "লাহু মা ফিছছামা ওয়াতি ওয়ামা ফিল আরদ। মান যাল্লাযী ইয়াস ফায়ু ইন দাহু ইল্লা বি ইজনিহি ইয়া লামু মা " +
                "বাইনা আইদিহিম ওয়ামা খল ফাহুম ওয়ালা ইউ হিতুনা বিশাই ইম্ মিন ইল্ মিহি ইল্লা বিমা সাআ ওয়াসিয়া " +
                "কুরসিইউ হুস ছামা ওয়াতি ওয়াল আরদ্ ওয়ালা ইয়া উদুহু হিফজুহুমা ওয়াহুয়াল আলিয়্যুল আজিম",
            translation = "",
            lapTarget = 7,
            lapCount = 1,
            isFavorite = true,
        ),
        Dhikr(
            id = "subhan",
            name = "SubhanAllah",
            arabic = "سُبْحَانَ اللّٰه",
            transliteration = "সুবহানাল্লাহ",
            translation = "Glory be to Allah",
            lapTarget = 33,
            lapCount = 3,
            isFavorite = true,
        ),
        Dhikr(
            id = "hamd",
            name = "Alhamdulillah",
            arabic = "الْحَمْدُ لِلّٰه",
            transliteration = "আলহামদুলিল্লাহ",
            translation = "All praise is due to Allah",
            lapTarget = 33,
            lapCount = 3,
            isFavorite = true,
        ),
        Dhikr(
            id = "akbar",
            name = "Allahu Akbar",
            arabic = "اللّٰهُ أَكْبَر",
            transliteration = "আল্লাহু আকবার",
            translation = "Allah is the greatest",
            lapTarget = 34,
            lapCount = 3,
            isFavorite = true,
        ),
        Dhikr(
            id = "istighfar",
            name = "Astaghfirullah",
            arabic = "أَسْتَغْفِرُ اللّٰه",
            transliteration = "আস্তাগফিরুল্লাহ",
            translation = "I seek forgiveness from Allah",
            lapTarget = 100,
            lapCount = 1,
            isFavorite = false,
        ),
        Dhikr(
            id = "bihamdihi",
            name = "Subhanallahi wa bihamdihi",
            arabic = "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ",
            transliteration = "সুবহানাল্লাহি ওয়া বিহামদিহি",
            translation = "Glory be to Allah and praise be to Him",
            lapTarget = 100,
            lapCount = 1,
            isFavorite = false,
        ),
        Dhikr(
            id = "hawla",
            name = "La hawla wa la quwwata illa billah",
            arabic = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰه",
            transliteration = "লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ",
            translation = "There is no power nor strength except with Allah",
            lapTarget = 33,
            lapCount = 1,
            isFavorite = false,
        ),
    )

    fun byId(id: String): Dhikr = all.find { it.id == id } ?: all.first()
}
