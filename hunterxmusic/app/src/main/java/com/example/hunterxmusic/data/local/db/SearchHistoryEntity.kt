package com.example.hunterxmusic.data.local.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "search_history",
    indices = [Index(value = ["query"], unique = true)]
)
data class SearchHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val query: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface SearchHistoryDao {
    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentQueriesFlow(limit: Int = 25): Flow<List<String>>

    @Query("SELECT query FROM search_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentQueries(limit: Int = 25): List<String>

    @Query("SELECT query FROM search_history WHERE query LIKE :prefix || '%' ORDER BY timestamp DESC LIMIT 10")
    suspend fun getSuggestions(prefix: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAll()
}
