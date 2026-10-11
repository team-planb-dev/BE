import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import java.time.LocalTime;
import java.util.Set;

public class PlaceTypeRepro {

    private static PlanScheduleDetail slot(CourseType type, String id) {

        return new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                type,
                LocalTime.of(10, 45),
                LocalTime.of(11, 15),
                "fixture",
                "강원특별자치도 강릉시",
                "128.9",
                "37.7",
                null,
                null,
                30,
                null,
                Set.of(),
                null,
                null,
                id
        );
    }

    public static void main(String[] args) {

        PlaceCandidateContext candidates = new PlaceCandidateContext();
        candidates.record(new PlaceWithRouteResult(
                true,
                "관측 분류 재현용 호수",
                "강원특별자치도 강릉시",
                "128.9",
                "37.7",
                5,
                "kakao:8654759",
                "AT4",
                "여행 > 관광,명소 > 호수"
        ));
        PlanPlaceResolver resolver = new PlanPlaceResolver();
        PlanScheduleDetail original = slot(CourseType.PARK_WALK, "kakao:8654759");
        var observed = resolver.validate(
                original,
                candidates,
                Set.of(),
                Set.of()
        );
        System.out.println("Original PARK_WALK: " + observed.reason());
        if (args.length > 0 && args[0].equals("expect-success")) {
            if (!observed.valid()) {
                throw new AssertionError("Observed place-type rejection reproduced");
            }
            return;
        }
        if (observed.valid()) {
            throw new AssertionError("Lake must not satisfy the existing park contract");
        }
        var attraction = resolver.validate(
                slot(CourseType.ATTRACTION, "kakao:8654759"),
                candidates,
                Set.of(),
                Set.of()
        );
        if (!attraction.valid()) {
            throw new AssertionError("Same AT4 lake must satisfy the attraction contract");
        }
        var sameLakeChoice = resolver.select(
                original,
                slot(CourseType.ATTRACTION, "kakao:8654759")
        );
        if (sameLakeChoice.courseType() != CourseType.PARK_WALK
                || resolver
                        .validate(
                                sameLakeChoice,
                                candidates,
                                Set.of(),
                                Set.of()
                        )
                        .valid()) {
            throw new AssertionError("Reselection must retain the original type and reject a lake");
        }
        candidates.record(new PlaceWithRouteResult(
                true,
                "검증용 공원",
                "강원특별자치도 강릉시",
                "128.9",
                "37.7",
                5,
                "kakao:park-fixture",
                "AT4",
                "여행 > 관광,명소 > 공원"
        ));
        var parkChoice = resolver.select(
                original,
                slot(CourseType.ATTRACTION, "kakao:park-fixture")
        );
        if (!resolver
                .validate(
                        parkChoice,
                        candidates,
                        Set.of(),
                        Set.of()
                )
                .valid()) {
            throw new AssertionError("Verified park must recover the original PARK_WALK slot");
        }
        System.out.println("Contract probes PASS: lake rejected; lake attraction allowed; type preserved; verified park recovery allowed");
    }
}
