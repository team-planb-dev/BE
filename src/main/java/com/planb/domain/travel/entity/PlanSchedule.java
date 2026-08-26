package com.planb.domain.travel.entity;

import com.planb.domain.travel.converter.CourseTypeConverter;
import com.planb.domain.travel.converter.RecommendationTagConverter;
import com.planb.domain.travel.converter.ScheduleTypeConverter;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

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

    // 해당 날짜
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "plan_day_id",
            nullable = false
    )
    private PlanDay planDay;

    // 스케쥴 타입
    @Convert(converter = ScheduleTypeConverter.class)
    @Column(
            name = "schedule_type",
            nullable = false
    )
    private ScheduleType scheduleType;

    // 시작시간
    @Column(name = "start_time")
    private LocalTime startTime;

    // 끝나는 시간
    @Column(name = "end_time")
    private LocalTime endTime;

    // 장소 이름
    @Column(name = "location_name")
    private String locationName;

    // 장소 이미지
    @Column(name = "image_url")
    private String imageUrl;

    // 썸네일 이미채ㅜ
    @Column(name = "thumb_nail_image_url")
    private String thumbNailImageUrl;

    // 장소 위치
    @Column(name = "location")
    private String location;

    // 여행 시간
    @Column(name = "stay_minutes")
    private Integer stayMinutes;

    // 다음 장소 까지의 이동 시간
    @Column(name = "travel_minutes")
    private Integer travelMinutes;


    // 코스 종류
    @Column(
            name = "course_type",
            nullable = false
    )
    @Convert(converter = CourseTypeConverter.class)
    private CourseType courseType;

    // 코스에 대한 태그
    @ElementCollection
    @CollectionTable(
            name = "plan_schedule_tag",
            joinColumns = @JoinColumn(
                    name = "plan_schedule_id"
            )
    )
    @Convert(converter = RecommendationTagConverter.class)
    @Column(name = "tag")
    @Builder.Default
    private Set<RecommendationTag> tags = new HashSet<>();

    // 약 복용 간격
    @Column(name = "medication_interval_minutes")
    private Integer medicationIntervalMinutes;

    // 약 설명
    @Column(name = "medication_description")
    private String medicationDescription;
}