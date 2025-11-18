package com.xiaoguang.assistant.data.local.database.dao

import androidx.room.*
import com.xiaoguang.assistant.data.local.database.entity.IdentityEntity

/**
 * 身份标识符 DAO
 */
@Dao
interface IdentityDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(identity: IdentityEntity)

    @Query("SELECT * FROM identities WHERE canonicalId = :canonicalId LIMIT 1")
    suspend fun getById(canonicalId: String): IdentityEntity?

    @Query("SELECT * FROM identities")
    suspend fun getAll(): List<IdentityEntity>

    @Query("SELECT * FROM identities WHERE isMaster = 1 LIMIT 1")
    suspend fun getMaster(): IdentityEntity?

    @Query("DELETE FROM identities WHERE canonicalId = :canonicalId")
    suspend fun deleteById(canonicalId: String)

    @Query("DELETE FROM identities")
    suspend fun deleteAll()
}
