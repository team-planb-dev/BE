package com.planb.query.travel.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TravelQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
}
