package com.planb.query.travel.service;

import com.planb.query.travel.repository.TravelQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class TravelQueryService {

    private final TravelQueryRepository travelQueryRepository;
}
