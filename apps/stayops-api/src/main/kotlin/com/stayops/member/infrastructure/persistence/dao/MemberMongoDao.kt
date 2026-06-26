package com.stayops.member.infrastructure.persistence.dao

import com.stayops.shared.config.MongoPersistence

import com.stayops.member.infrastructure.persistence.MemberDocument
import org.springframework.data.mongodb.repository.MongoRepository




@MongoPersistence
interface MemberMongoDao : MongoRepository<MemberDocument, String> {
    fun findByEmail(email: String): MemberDocument?
    fun existsByEmail(email: String): Boolean
}
