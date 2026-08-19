package com.planb.domain.travel.service;

import com.planb.domain.travel.repository.PlanDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanDayService {

    private final PlanDayRepository planDayRepository;
}
