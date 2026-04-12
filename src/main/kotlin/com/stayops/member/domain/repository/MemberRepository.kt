package com.stayops.member.domain.repository

import com.stayops.member.domain.model.Member

interface MemberRepository {
    fun save(member: Member): Member
    fun findById(id: String): Member?
    fun findByEmail(email: String): Member?
    fun existsByEmail(email: String): Boolean
}
