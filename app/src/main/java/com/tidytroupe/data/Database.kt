package com.tidytroupe.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface TidyDao {
    @Query("SELECT * FROM members ORDER BY rowid") fun members(): Flow<List<Member>>
    @Query("SELECT * FROM chores ORDER BY due, title") fun chores(): Flow<List<Chore>>
    @Query("SELECT * FROM completions ORDER BY date DESC") fun completions(): Flow<List<Completion>>
    @Query("SELECT * FROM rewards ORDER BY cost, title") fun rewards(): Flow<List<Reward>>
    @Query("SELECT * FROM redemptions ORDER BY date DESC") fun redemptions(): Flow<List<Redemption>>
    @Query("SELECT * FROM routines ORDER BY title") fun routines(): Flow<List<Routine>>
    @Query("SELECT * FROM members WHERE id = :id") suspend fun member(id: String): Member?
    @Query("SELECT * FROM chores WHERE id = :id") suspend fun chore(id: String): Chore?
    @Query("SELECT * FROM rewards WHERE id = :id") suspend fun reward(id: String): Reward?
    @Query("SELECT * FROM completions WHERE choreId = :id AND date = :date") suspend fun completion(id: String, date: String): Completion?
    @Query("SELECT COALESCE(SUM(points),0) FROM completions WHERE memberId = :id AND status = 'APPROVED'") suspend fun earned(id: String): Long
    @Query("SELECT COALESCE(SUM(cost),0) FROM redemptions WHERE memberId = :id") suspend fun spent(id: String): Long
    @Query("SELECT COUNT(*) FROM members") suspend fun memberCount(): Int
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Member)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Chore)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Completion)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Reward)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Redemption)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun put(value: Routine)
    @Query("DELETE FROM members WHERE id = :id") suspend fun deleteMember(id: String)
    @Query("UPDATE chores SET memberId = NULL WHERE memberId = :id") suspend fun unassign(id: String)
    @Query("DELETE FROM chores WHERE id = :id") suspend fun deleteChore(id: String)
    @Query("DELETE FROM completions WHERE choreId = :id AND date = :date") suspend fun deleteCompletion(id: String, date: String)
    @Query("DELETE FROM completions WHERE memberId = :id") suspend fun deleteMemberCompletions(id: String)
    @Query("DELETE FROM redemptions WHERE memberId = :id") suspend fun deleteMemberRedemptions(id: String)
    @Query("DELETE FROM rewards WHERE id = :id") suspend fun deleteReward(id: String)
    @Query("DELETE FROM routines WHERE id = :id") suspend fun deleteRoutine(id: String)
    @Query("DELETE FROM completions") suspend fun clearCompletions()
    @Query("DELETE FROM redemptions") suspend fun clearRedemptions()
    @Query("DELETE FROM routines") suspend fun clearRoutines()
    @Query("DELETE FROM chores") suspend fun clearChores()
    @Query("DELETE FROM members") suspend fun clearMembers()
    @Query("DELETE FROM rewards") suspend fun clearRewards()
}

@Database(entities = [Member::class, Chore::class, Completion::class, Reward::class, Redemption::class, Routine::class], version = 1, exportSchema = true)
abstract class TidyDatabase : RoomDatabase() { abstract fun dao(): TidyDao }
