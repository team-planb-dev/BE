"""보존된 생성 결과와 로그의 영양 누락 대조"""
import argparse
import collections
import hashlib
import json
import re
from pathlib import Path

FIELDS = ("carbohydrate", "sodium", "fat")
TOOL = re.compile(r"영양정보 평가 호출 - foodName: (.*?), standardFoodName: (.*?), diseaseTypes:")
RESULT = re.compile(r"\[NUTRITION LOOKUP RESULT\] lookupName=(.*?), matchedFoodName=(.*?), referenceAmount=(.*?), carbohydratePresent=(true|false), sodiumPresent=(true|false), fatPresent=(true|false), status=")


def classify(menu, calls, results, warnings):
    called = [standard for name, standard in calls if name == menu]
    matched = [row for row in results if row[0] == menu]
    return {
        "path": "tool_called" if called else "tool_not_observed",
        "standardFoodName": called[-1] if called else None,
        "rootCause": "UNKNOWN",
        "noSuccessfulLookupLogForExactMenu": not matched and not warnings,
        "limits": "Raw provider/cache rows absent: empty response, name filtering, cache hit and provider failure cannot be separated per slot",
    }


def analyse(root):
    totals = collections.Counter()
    cases = []
    details = []
    inputs = []
    for mode in ("generation", "medication"):
        log_file = root / mode / "application.log"
        text = log_file.read_text()
        groups = re.split(r"^.*JwtLoginFilter.*로그인 성공:.*$", text, flags=re.M)[1:]
        files = list((root / mode / "baseline").glob("results-*.jsonl"))
        assert len(files) == 1, "Expected one immutable run per mode"
        rows = [json.loads(line) for line in files[0].read_text().splitlines() if line]
        assert len(groups) == len(rows), "Cannot correlate request logs one-to-one"
        inputs.extend((log_file, files[0]))
        for row, group in zip(rows, groups):
            raw_file = files[0].parent / row["runId"] / row["rawFile"]
            raw = json.loads(raw_file.read_text())
            assert raw["success"] and row["outcome"] == "PASS"
            inputs.append(raw_file)
            calls = TOOL.findall(group)
            results = RESULT.findall(group)
            warnings = [line for line in group.splitlines() if "WARN" in line and "NutritionService" in line]
            failures = re.findall(r"영양성분 조회 실패 - menuName: (.+)", group)
            empty_menus = []
            case = collections.Counter()
            for day in raw["data"]["planDays"]:
                for slot in day["schedules"]:
                    detail = slot.get("restaurantDetail")
                    if detail is None:
                        continue
                    totals["mealSlots"] += 1
                    values = [detail.get(field) for field in FIELDS]
                    if all(value is None for value in values):
                        menu = detail["menuName"]
                        empty_menus.append(menu)
                        evidence = classify(menu, calls, results, warnings)
                        totals["emptySlots"] += 1
                        totals[evidence["path"]] += 1
                        case[evidence["path"]] += 1
                        details.append({"mode": mode, "case": row["caseId"], "repeat": row["repeat"], "day": day["dayNumber"], "slot": slot["scheduleType"], "menu": menu, **evidence})
                    elif all(value is not None for value in values):
                        totals["fullSlots"] += 1
                    else:
                        totals["partialSlots"] += 1
            assert collections.Counter(empty_menus) == collections.Counter(failures), "Output/log missing-menu mismatch"
            cases.append({"case": row["caseId"], "repeat": row["repeat"], "emptySlots": len(empty_menus), "nutritionWarnings": len(warnings), "paths": dict(case), "javaNutritionLookupCount": sum(v["count"] for k, v in row["meterDelta"].get("planb.travel.plan.stage", {}).items() if "stage=nutrition_lookup" in k)})
    assert totals["mealSlots"] == totals["emptySlots"] + totals["fullSlots"] + totals["partialSlots"]
    return {"totals": dict(totals), "cases": cases, "inputs": [{"path": str(p.relative_to(root)), "sha256": hashlib.sha256(p.read_bytes()).hexdigest()} for p in inputs]}, details


def self_test():
    call = TOOL.findall("영양정보 평가 호출 - foodName: 밥 (A, B), standardFoodName: 밥, diseaseTypes: []")
    assert call == [("밥 (A, B)", "밥")]
    assert classify("밥 (A, B)", call, [], [])["path"] == "tool_called"
    assert classify("다른 메뉴", call, [], [])["path"] == "tool_not_observed"
    result = RESULT.findall("[NUTRITION LOOKUP RESULT] lookupName=밥, matchedFoodName=밥, referenceAmount=100g, carbohydratePresent=false, sodiumPresent=false, fatPresent=false, status=NOT_EVALUABLE")
    assert not classify("밥", [], result, [])["noSuccessfulLookupLogForExactMenu"]
    assert not classify("밥", [], [], ["WARN"])["noSuccessfulLookupLogForExactMenu"]
    print("Self-check: 5 assertions passed")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--self-test", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
    else:
        if not args.root or not args.output:
            parser.error("--root and --output required")
        summary, private = analyse(args.root)
        args.output.mkdir(parents=True, exist_ok=True)
        (args.output / "nutrition-summary.json").write_text(json.dumps(summary, ensure_ascii=False, indent=2) + "\n")
        (args.output / "nutrition-slot-details.private.json").write_text(json.dumps(private, ensure_ascii=False, indent=2) + "\n")
        print(json.dumps(summary["totals"], ensure_ascii=False))
