package com.ronitgandhi.motionfuel.domain.algorithm

import java.util.Locale

object FoodPlanning {
    data class Ingredient(val name: String, val servings: Double)
    fun groceryList(required: List<Ingredient>, pantry: List<Ingredient>): List<Ingredient> {
        fun group(items: List<Ingredient>) = items.filter { it.servings.isFinite() && it.servings > 0 && it.name.isNotBlank() }
            .groupBy { it.name.trim().lowercase(Locale.ROOT) }
            .mapValues { (_, entries) -> entries.sumOf { it.servings } }
        val available = group(pantry)
        return group(required).map { (name, amount) -> Ingredient(name, (amount - (available[name] ?: 0.0)).coerceAtLeast(0.0)) }
            .filter { it.servings > 0.001 }.sortedBy { it.name }
    }
    fun allowed(tags: Set<String>, allergens: Set<String>, diet: String, excluded: Set<String>, verified: Boolean): Boolean {
        if (!verified && (diet != "Any" || excluded.isNotEmpty())) return false
        return (diet == "Any" || diet in tags) && allergens.intersect(excluded).isEmpty()
    }
}
