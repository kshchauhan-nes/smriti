package com.example.myapplication.speech
object AssameseObjects {
    private val names = mapOf("Tea" to "চাহ", "Rice" to "চাউল", "Basket" to "খৰাহী", "Flower" to "ফুল", "Mango" to "আম", "Bird" to "চৰাই", "Umbrella" to "ছাতি", "Apple" to "আপেল", "Sunflower" to "সূৰ্যমুখী ফুল", "Fish" to "মাছ")
    fun name(value: String) = names[value] ?: value
}
