package com.ssafy.upcycle.common.jpa.config;

import com.ssafy.upcycle.common.jpa.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "querydsl_test_entity")
class QuerydslTestEntity extends BaseEntity {
    @Id
    @GeneratedValue
    private Long id;

    @Column(nullable = false)
    private String name;

    protected QuerydslTestEntity() {
    }

    QuerydslTestEntity(String name) {
        this.name = name;
    }

    String getName() {
        return name;
    }
}
