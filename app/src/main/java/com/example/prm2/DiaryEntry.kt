package com.example.prm2

import android.content.ContentValues.TAG
import android.util.Log

//data class DiaryEntry(
//    val title: String = "",
//    val content: String = "",
//    val imageUrl: String? = null,
//    val audioUrl: String? = null,
//    val location: String? = null,
//    val timestamp: Long = System.currentTimeMillis()
//)
//
//fun addEntry(entry: DiaryEntry) {
//    db.collection("entries")
//        .add(entry)
//        .addOnSuccessListener { documentReference ->
//            Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
//        }
//        .addOnFailureListener { e ->
//            Log.w(TAG, "Error adding document", e)
//        }
//}
//
//fun getEntries(callback: (List<DiaryEntry>) -> Unit) {
//    db.collection("entries")
//        .get()
//        .addOnSuccessListener { result ->
//            val entries = result.map { document -> document.toObject(DiaryEntry::class.java) }
//            callback(entries)
//        }
//        .addOnFailureListener { exception ->
//            Log.w(TAG, "Error getting documents.", exception)
//        }
//}
