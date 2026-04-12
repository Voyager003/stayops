package com.stayops.member.infrastructure.persistence

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.repository.MemberRepository
import org.springframework.data.mongodb.repository.MongoRepository
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MongoMemberRepository(
    private val mongo: MemberMongoDataRepository
) : MemberRepository {

    override fun save(member: Member): Member =
        mongo.save(MemberDocument.from(member)).toDomain()

    override fun findById(id: String): Member? =
        mongo.findByIdOrNull(id)?.toDomain()

    override fun findByEmail(email: String): Member? =
        mongo.findByEmail(email)?.toDomain()

    override fun existsByEmail(email: String): Boolean =
        mongo.existsByEmail(email)
}

interface MemberMongoDataRepository : MongoRepository<MemberDocument, String> {
    fun findByEmail(email: String): MemberDocument?
    fun existsByEmail(email: String): Boolean
}
