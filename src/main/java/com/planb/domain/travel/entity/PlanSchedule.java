package com.planb.domain.travel.entity;

import com.planb.domain.travel.entity.constant.PlaceType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Getter
@Builder
@Table(name = "plan_schedule")
public class PlanSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "plan_schedule_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "plan_day_id",
            nullable = false
    )
    private PlanDay planDay;

    @Enumerated(EnumType.STRING)
    @Column(
            name = "schedule_type",
            nullable = false
    )
    private ScheduleType scheduleType;

    @Column(name = "start_time")
    private LocalTime startTime;

    @Column(name = "end_time")
    private LocalTime endTime;

    @Column(name = "location_name")
    private String locationName;

    @Column(name = "location")
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(name = "place_type")
    private PlaceType placeType;

    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    @Column(name = "travel_minutes")
    private Integer travelMinutes;

    @ElementCollection
    @CollectionTable(
            name = "plan_schedule_caution",
            joinColumns = @JoinColumn(name = "plan_schedule_id")
    )
    @Column(name = "caution")
    @Builder.Default
    private List<String> cautions = new ArrayList<>();

    @Column(name = "medication_interval_minutes")
    private Integer medicationIntervalMinutes;

    @Column(name = "medication_description")
    private String medicationDescription;
}