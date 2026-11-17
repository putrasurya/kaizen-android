package com.putrasurya.kaizen.milestones.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import com.putrasurya.kaizen.milestones.domain.Milestone
import com.putrasurya.kaizen.milestones.domain.MilestoneMath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

@Entity(tableName = "milestones")
data class MilestoneEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val note: String,
    val achievedOn: LocalDate?,
    val year: Int,
    val kept: Boolean = false,
) {
    fun toDomain() = Milestone(id, title, note, achievedOn, year, kept)

    companion object {
        fun from(m: Milestone) = MilestoneEntity(m.id, m.title, m.note, m.achievedOn, m.year, m.kept)
    }
}

@Dao
interface MilestoneDao {
    @Query("SELECT * FROM milestones")
    fun observeAll(): Flow<List<MilestoneEntity>>

    @Query("SELECT * FROM milestones")
    suspend fun getAll(): List<MilestoneEntity>

    @Query("SELECT * FROM milestones WHERE id = :id")
    suspend fun get(id: Long): MilestoneEntity?

    @Insert
    suspend fun insert(milestone: MilestoneEntity): Long

    @Update
    suspend fun update(milestone: MilestoneEntity)

    @Query("DELETE FROM milestones WHERE id = :id")
    suspend fun delete(id: Long)
}

class MilestoneRepository(private val dao: MilestoneDao) {

    fun observeAll(): Flow<List<Milestone>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Reached on [achievedOn], or (null) a goal for [year]. */
    suspend fun add(title: String, note: String, achievedOn: LocalDate?, year: Int) {
        val cleanTitle = title.trim().take(MilestoneMath.TITLE_MAX)
        if (cleanTitle.isEmpty()) return
        dao.insert(
            MilestoneEntity(
                title = cleanTitle,
                note = note.trim().take(MilestoneMath.NOTE_MAX),
                achievedOn = achievedOn,
                year = achievedOn?.year ?: year,
            )
        )
    }

    /** Title, note and date; a null date makes it a goal again (in its current year). */
    suspend fun update(id: Long, title: String, note: String, achievedOn: LocalDate?) {
        val current = dao.get(id)?.toDomain() ?: return
        val cleanTitle = title.trim().take(MilestoneMath.TITLE_MAX).ifEmpty { current.title }
        val edited = current.copy(title = cleanTitle, note = note.trim().take(MilestoneMath.NOTE_MAX))
        val next = if (achievedOn != null) MilestoneMath.achieve(edited, achievedOn) else edited.copy(achievedOn = null)
        dao.update(MilestoneEntity.from(next))
    }

    suspend fun markAchieved(id: Long, date: LocalDate) {
        val current = dao.get(id)?.toDomain() ?: return
        dao.update(MilestoneEntity.from(MilestoneMath.achieve(current, date)))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    /** Unreached goals from before [toYear]: carried into it, or left as "not reached". */
    suspend fun settleOldGoals(toYear: Int, carry: Boolean) {
        for (entity in dao.getAll()) {
            val milestone = entity.toDomain()
            val settled = MilestoneMath.settle(milestone, toYear, carry)
            if (settled != milestone) dao.update(MilestoneEntity.from(settled))
        }
    }
}
