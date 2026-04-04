package com.stayops.property.domain.repository

import com.stayops.property.domain.model.Property

interface PropertyRepository {
    fun save(property: Property): Property
    fun findById(id: String): Property?
    fun findByOwnerId(ownerId: String): List<Property>
    fun findByIds(ids: List<String>): List<Property>
    fun findAll(): List<Property>
}
