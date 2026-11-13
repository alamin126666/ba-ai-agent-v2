package com.ba.aiagent.data.local

import android.content.Context
import com.ba.aiagent.domain.model.DesignTheme

/** DESIGN.md themes — 31 brand-grade inspirations, curated starter set. */
class DesignStore(private val context: Context) {
    fun themes(): List<DesignTheme> = listOf(
        DesignTheme("linear", "Linear", "#08090A", "#141619", "#5E6AD2", "#F7F8F8", 8),
        DesignTheme("stripe", "Stripe", "#FFFFFF", "#F6F9FC", "#635BFF", "#0A2540", 12),
        DesignTheme("vercel", "Vercel", "#000000", "#111111", "#FFFFFF", "#FAFAFA", 6),
        DesignTheme("notion", "Notion", "#FFFFFF", "#F7F6F3", "#37352F", "#37352F", 6),
        DesignTheme("apple", "Apple", "#000000", "#1C1C1E", "#0A84FF", "#F5F5F7", 14),
        DesignTheme("supabase", "Supabase", "#0E0E0E", "#1C1C1C", "#3ECF8E", "#EDEDED", 8),
        DesignTheme("claude", "Claude", "#262624", "#333330", "#D97757", "#F5F4EE", 10),
        DesignTheme("figma", "Figma", "#1E1E1E", "#2C2C2C", "#A259FF", "#FFFFFF", 10),
        DesignTheme("github", "GitHub", "#0D1117", "#161B22", "#2F81F7", "#E6EDF3", 6),
        DesignTheme("spotify", "Spotify", "#121212", "#1A1A1A", "#1DB954", "#FFFFFF", 16)
    )

    fun byId(id: String): DesignTheme = themes().firstOrNull { it.id == id } ?: themes()[0]
}
