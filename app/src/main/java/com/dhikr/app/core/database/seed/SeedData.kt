package com.dhikr.app.core.database.seed

import com.dhikr.app.core.database.entity.RoutineEntity
import com.dhikr.app.core.database.entity.RoutineStepEntity
import com.dhikr.app.core.database.entity.TasbihEntity

object SeedData {
    private const val SEED_TIMESTAMP = 0L

    val builtInTasbih: List<TasbihEntity> = listOf(
        TasbihEntity(
            id = "kursi",
            name = "Ayatul Kursi",
            arabic = "",
            pronunciation = "আল্লাহু লা ইলাহা ইল্লা হুয়াল হাইয়্যুল কইয়্যুমু লা তা খুজুহু সিনাত্যু ওয়ালা নাউম। " +
                "লাহু মা ফিছছামা ওয়াতি ওয়ামা ফিল আরদ। মান যাল্লাযী ইয়াস ফায়ু ইন দাহু ইল্লা বি ইজনিহি ইয়া লামু মা " +
                "বাইনা আইদিহিম ওয়ামা খল ফাহুম ওয়ালা ইউ হিতুনা বিশাই ইম্ মিন ইল্ মিহি ইল্লা বিমা সাআ ওয়াসিয়া " +
                "কুরসিইউ হুস ছামা ওয়াতি ওয়াল আরদ্ ওয়ালা ইয়া উদুহু হিফজুহুমা ওয়াহুয়াল আলিয়্যুল আজিম",
            translation = "",
            lapTarget = 7,
            lapCount = 1,
            isFavorite = true,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        TasbihEntity(
            id = "subhan",
            name = "SubhanAllah",
            arabic = "سُبْحَانَ اللّٰه",
            pronunciation = "সুবহানাল্লাহ",
            translation = "Glory be to Allah",
            lapTarget = 33,
            lapCount = 3,
            isFavorite = true,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        TasbihEntity(
            id = "hamd",
            name = "Alhamdulillah",
            arabic = "الْحَمْدُ لِلّٰه",
            pronunciation = "আলহামদুলিল্লাহ",
            translation = "All praise is due to Allah",
            lapTarget = 33,
            lapCount = 3,
            isFavorite = true,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        TasbihEntity(
            id = "akbar",
            name = "Allahu Akbar",
            arabic = "اللّٰهُ أَكْبَر",
            pronunciation = "আল্লাহু আকবার",
            translation = "Allah is the greatest",
            lapTarget = 34,
            lapCount = 3,
            isFavorite = true,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        TasbihEntity(
            id = "istighfar",
            name = "Astaghfirullah",
            arabic = "أَسْتَغْفِرُ اللّٰه",
            pronunciation = "আস্তাগফিরুল্লাহ",
            translation = "I seek forgiveness from Allah",
            lapTarget = 100,
            lapCount = 1,
            isFavorite = false,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        TasbihEntity(
            id = "bihamdihi",
            name = "Subhanallahi wa bihamdihi",
            arabic = "سُبْحَانَ اللّٰهِ وَبِحَمْدِهِ",
            pronunciation = "সুবহানাল্লাহি ওয়া বিহামদিহি",
            translation = "Glory be to Allah and praise be to Him",
            lapTarget = 100,
            lapCount = 1,
            isFavorite = false,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
        TasbihEntity(
            id = "hawla",
            name = "La hawla wa la quwwata illa billah",
            arabic = "لَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰه",
            pronunciation = "লা হাওলা ওয়ালা কুওয়াতা ইল্লা বিল্লাহ",
            translation = "There is no power nor strength except with Allah",
            lapTarget = 33,
            lapCount = 1,
            isFavorite = false,
            isBuiltIn = true,
            createdAt = SEED_TIMESTAMP,
            updatedAt = SEED_TIMESTAMP,
        ),
    )

    val presetRoutines: List<RoutineEntity> = listOf(
        RoutineEntity(id = "morning", name = "Morning Dhikr", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
        RoutineEntity(id = "evening", name = "Evening Dhikr", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
        RoutineEntity(id = "after_salah", name = "After Salah", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
        RoutineEntity(id = "before_sleep", name = "Before Sleep", isPreset = true, createdAt = SEED_TIMESTAMP, updatedAt = SEED_TIMESTAMP),
    )

    val presetRoutineSteps: List<RoutineStepEntity> = listOf(
        // Morning Dhikr: SubhanAllah x33, Alhamdulillah x33, AllahuAkbar x34 (matches
        // the prototype's ROUTINES array and design README's own example exactly —
        // design/Dhikr Android App.dc.html's `morning`/`salah` routine definitions)
        RoutineStepEntity(routineId = "morning", tasbihId = "subhan", stepOrder = 0, targetCount = 33),
        RoutineStepEntity(routineId = "morning", tasbihId = "hamd", stepOrder = 1, targetCount = 33),
        RoutineStepEntity(routineId = "morning", tasbihId = "akbar", stepOrder = 2, targetCount = 34),
        // Evening Dhikr: same three, evening framing — no separate evening-specific
        // prototype data exists, so this reuses the same structure as Morning per
        // the plan's own "Evening Dhikr" listing (plan.md §22) which gives no
        // distinct counts of its own.
        RoutineStepEntity(routineId = "evening", tasbihId = "subhan", stepOrder = 0, targetCount = 33),
        RoutineStepEntity(routineId = "evening", tasbihId = "hamd", stepOrder = 1, targetCount = 33),
        RoutineStepEntity(routineId = "evening", tasbihId = "akbar", stepOrder = 2, targetCount = 34),
        // After Salah: matches the prototype's `salah` routine exactly (same 3 steps)
        RoutineStepEntity(routineId = "after_salah", tasbihId = "subhan", stepOrder = 0, targetCount = 33),
        RoutineStepEntity(routineId = "after_salah", tasbihId = "hamd", stepOrder = 1, targetCount = 33),
        RoutineStepEntity(routineId = "after_salah", tasbihId = "akbar", stepOrder = 2, targetCount = 34),
        // Before Sleep: matches the prototype's `sleep` routine exactly —
        // Astaghfirullah x100, Subhanallahi wa bihamdihi x100
        RoutineStepEntity(routineId = "before_sleep", tasbihId = "istighfar", stepOrder = 0, targetCount = 100),
        RoutineStepEntity(routineId = "before_sleep", tasbihId = "bihamdihi", stepOrder = 1, targetCount = 100),
    )
}
