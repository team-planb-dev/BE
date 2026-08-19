package com.planb.domain.travel.entity;

import com.planb.domain.travel.converter.PlaceTypeConverter;
import com.planb.domain.travel.entity.constant.PlaceType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@Getter
public class PlannedPlace {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "location_name",nullable = false)
    private String locationName;

    @Column(name = "location")
    private String location;

    @Convert(converter = PlaceTypeConverter.class)
    @Column(name = "place_type")
    private PlaceType placeType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "travel_id",nullable = false)
    private Travel travel;
}
