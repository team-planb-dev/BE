# KorService2 coordinate validation research

Research date: 2026-09-23  
Scope: PlanB issue #76; validation of Korea Tourism Organization `KorService2` coordinates before a place candidate is exposed to the AI

## Decision

Treat `mapx` as **WGS84 longitude** and `mapy` as **WGS84 latitude**. The Korea Tourism Organization's official v4.4 manual defines the fields exactly this way: `mapx` is `GPS X` / WGS84 longitude and `mapy` is `GPS Y` / WGS84 latitude. The same definitions apply to the `locationBasedList2` request fields `mapX` and `mapY`. See the [official Korean usage manual v4.4](https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003603931&fileDetailSn=1) linked from the [official KorService2 catalog entry](https://www.data.go.kr/data/15101578/openapi.do).

Before a TourAPI item is recorded in `PlaceCandidateContext` or returned by the AI tool, Java should require all of the following:

1. `mapx` and `mapy` are non-null and non-blank.
2. Both strings parse as `double` values.
3. Both parsed values pass `Double.isFinite`.
4. The original field order is preserved: `longitude = mapx`, `latitude = mapy`; values are never guessed or swapped.
5. The pair is inside the inclusive operational envelope `124.0 <= longitude <= 132.0` and `33.0 <= latitude <= 39.0`.

This envelope is a coarse fail-fast policy, not a legal boundary or a proof that a point belongs to the stated city. It is intentionally wider than mainland South Korea so that Jeju, Marado, Ulleungdo, Dokdo, and western islands are not excluded.

The candidate with `mapx=117.9925662504` and `mapy=19.6944274800` must therefore be rejected before AI exposure. Read according to the provider contract, it is longitude `117.9925662504` and latitude `19.6944274800`; both are outside the operational envelope. Swapping does not repair the record because `117.9925662504` is outside the valid latitude interval even at the global level. The official manual's Seoul example is near `126.9913616044 E, 37.5633937314 N`, and the [Seoul Metropolitan Government places Seoul at approximately `126.59 E, 37.34 N`](https://english.seoul.go.kr/seoul-views/meaning-of-seoul/2-location/).

## Primary-source findings

### KorService2 field meaning and coordinate reference system

The [Public Data Portal catalog](https://www.data.go.kr/data/15101578/openapi.do) identifies the provider as the Korea Tourism Organization and describes the API as nationwide Korean tourism information delivered as JSON or XML. Its reference-document download is the official v4.4 Korean usage manual.

The [official v4.4 manual](https://www.data.go.kr/cmm/cmm/fileDownload.do?atchFileId=FILE_000000003603931&fileDetailSn=1) says:

| Field | Official meaning | Application meaning |
|---|---|---|
| `mapx` / request `mapX` | GPS X coordinate; WGS84 longitude | longitude |
| `mapy` / request `mapY` | GPS Y coordinate; WGS84 latitude | latitude |

The manual also provides a `locationBasedList2` example centered on Seoul Jung-gu with `mapX=126.98375` and `mapY=37.563446`, and an item at a Seoul address with `mapx=126.9913616044` and `mapy=37.5633937314`. This confirms the order using real values as well as labels.

### Why the operational envelope includes islands

The Ministry of Land, Infrastructure and Transport states that Korea consists of the peninsula and about 3,300 islands. Its GRS80 extreme-point table identifies Marado as the southern extreme at `33°06′43″ N`, Dokdo as the eastern extreme at `131°52′22″ E`, and the broader Korean western extreme at `124°10′51″ E`. The page attributes those values to the National Geographic Information Institute. See the [MOLIT official territory and extreme-points page](https://www.molit.go.kr/kids/USR/WPGE0201/m_36059/DTL.jsp).

The integer-degree longitude limits `124` and `132` therefore include both the western extreme and Dokdo. The latitude floor `33` includes Marado rather than cutting off Jeju and its southern islands. The upper limit `39` is an application buffer for the Republic of Korea's operational tourism data: the official government overview says the Republic of Korea exercises jurisdiction south of the Military Demarcation Line, while an official Korea.net description places the inter-Korean division approximately along the 38th parallel. See the [official overview of Korea](https://www.korea.net/AboutKorea/OverviewofKorea) and [official DMZ background](https://www.korea.net/Events/Overseas/view?articleId=18656).

The envelope is deliberately rounded outward. Do not replace it with a narrow mainland rectangle such as `126-130 E` or `34-38 N`: such a rule would exclude official Korean island extremes even though KorService2 is a nationwide tourism service. If precise containment is later required, use an authoritative administrative-boundary polygon or verify the provider's legal-region codes; do not keep tightening the rectangle.

## Minimal Java policy

The smallest safe filter can remain at the existing tool boundary, immediately before `candidates.record(item)` and before the item list is returned to the model:

```java
private static boolean hasUsableKoreanCoordinates(
        Kor2KeywordSearchResponse.Item item
) {

    if (item == null
            || item.mapx() == null
            || item.mapx().isBlank()
            || item.mapy() == null
            || item.mapy().isBlank()) {
        return false;
    }

    try {
        double longitude = Double.parseDouble(item.mapx());
        double latitude = Double.parseDouble(item.mapy());

        return Double.isFinite(longitude)
                && Double.isFinite(latitude)
                && longitude >= 124.0
                && longitude <= 132.0
                && latitude >= 33.0
                && latitude <= 39.0;
    } catch (NumberFormatException exception) {
        return false;
    }
}
```

Oracle documents that [`Double.parseDouble`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#parseDouble(java.lang.String)) throws `NumberFormatException` for a non-parsable `double`. Parsing alone is insufficient because IEEE 754 values also include NaN and infinities; Oracle documents that [`Double.isFinite`](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Double.html#isFinite(double)) returns false for both.

Invalid items should be omitted from the candidate list and never entered into `PlaceCandidateContext`. If every returned item is invalid, the tool should return an empty candidate list through its existing behavior. Do not silently swap coordinates, clamp them to the envelope, substitute a default coordinate, or ask the model to repair them; all four choices would create a location that the official source did not provide.

## Limits of this policy

Passing the envelope only means that a coordinate is numerically plausible for South Korean tourism operations. A point elsewhere in Korea could still be paired with a Seoul address. City-level consistency requires a separate check against an authoritative city boundary, provider region codes, or a trusted reverse-geocoding result. That stronger check is not necessary to reject the issue #76 sample because the sample already fails the country-level envelope by a large margin.

This research changes no production code, tests, or `docs/perf` files.
