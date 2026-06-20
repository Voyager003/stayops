package com.stayops.member.infrastructure.persistence

import com.stayops.member.domain.model.Member
import com.stayops.member.domain.repository.MemberRepository
import com.stayops.member.infrastructure.persistence.dao.MemberMongoDao
import org.springframework.data.repository.findByIdOrNull
import org.springframework.stereotype.Repository

@Repository
class MongoMemberRepository(
    private val mongo: MemberMongoDao
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
