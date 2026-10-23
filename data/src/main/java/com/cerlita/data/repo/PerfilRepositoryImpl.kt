package com.cerlita.data.repo

import com.cerlita.core.common.DispatcherProvider
import com.cerlita.data.db.PerfilDao
import com.cerlita.data.mapper.toDomain
import com.cerlita.data.mapper.toEntity
import com.cerlita.domain.model.Perfil
import com.cerlita.domain.repository.PerfilRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class PerfilRepositoryImpl(
    private val dao: PerfilDao,
    private val dispatchers: DispatcherProvider,
) : PerfilRepository {

    override val perfil: Flow<Perfil> =
        dao.observe().map { it?.toDomain() ?: Perfil() }

    override suspend fun guardarPerfil(perfil: Perfil) = withContext(dispatchers.io) {
        dao.upsert(perfil.toEntity(System.currentTimeMillis()))
    }
}
