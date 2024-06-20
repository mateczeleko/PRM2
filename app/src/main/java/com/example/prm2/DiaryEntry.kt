package com.example.prm2

import android.content.ContentValues.TAG
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

data class DiaryEntry(
    val title: String = "",
    val content: String = "",
    val imageUrl: String? = null,
    val audioUrl: String? = null,
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val cityName: String? = null
)

fun addEntry(entry: DiaryEntry, db: FirebaseFirestore) {
    db.collection("entries")
        .add(entry)
        .addOnSuccessListener { documentReference ->
            Log.d(TAG, "DocumentSnapshot added with ID: ${documentReference.id}")
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Error adding document", e)
        }
}

fun getEntries(db: FirebaseFirestore, callback: (Map<String, DiaryEntry>) -> Unit) {
    db.collection("entries")
        .get()
        .addOnSuccessListener { result ->
            // Convert the whole Query snapshot to map document id to diaryentry
            val entries = result.map { document -> document.id to document.toObject(DiaryEntry::class.java) }.toMap()
            callback(entries)

        }
        .addOnFailureListener { exception ->
            Log.w(TAG, "Error getting documents.", exception)
        }
}
fun updateEntry(id: String, entry: DiaryEntry, db: FirebaseFirestore) {
    db.collection("entries")
        .document(id)
        .set(entry)
        .addOnSuccessListener {
            Log.d(TAG, "DocumentSnapshot successfully updated!")
        }
        .addOnFailureListener { e ->
            Log.w(TAG, "Error updating document", e)
        }
}
