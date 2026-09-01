package com.planb.query.travel.service;

import com.planb.query.travel.repository.PlannedPlaceQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlannedPlaceQueryService {

    private final PlannedPlaceQueryRepository plannedPlaceQueryRepository;

}
