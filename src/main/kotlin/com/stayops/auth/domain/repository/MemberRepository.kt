package com.stayops.auth.domain.repository

import com.stayops.auth.domain.model.Member

interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: String): Member?
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}
