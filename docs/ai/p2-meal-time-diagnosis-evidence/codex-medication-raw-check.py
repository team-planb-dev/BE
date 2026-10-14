from pathlib import Path
import json
roots={'before':Path('/private/tmp/planb-selection-contract-177/build/llm-quality-baseline/raw/2026-10-11T03-03-33.798400Z'),'after':Path('/private/tmp/planb-stage2-179/docs/ai/evidence/p2-selection-recovery-stage2-20261011T114549Z/raw')}
def mins(t):
 h,m,s=map(int,t.split(':'));return h*60+m+s/60
out={}
for label,root in roots.items():
 rows=[];interval_count=0;tail_count=0;med_count=0;late_count=0
 for p in sorted(root.glob('*.json')):
  raw=json.loads(p.read_text())
  if not raw.get('success'):continue
  for day in raw['data']['planDays']:
   ss=day['schedules'];lunch=[s for s in ss if s['scheduleType']=='LUNCH'];meds=[s for s in ss if s['courseType']=='MEDICATION']
   assert len(lunch)==1,(p.name,day['dayNumber'])
   le=mins(lunch[0]['endTime']);late_count+=abs(mins(lunch[0]['startTime'])-720)>30
   places=[s for s in ss if s['courseType']!='MEDICATION' and s.get('locationName') and s.get('startTime') and s.get('endTime')]
   for med in meds:
    med_count+=1;ms=mins(med['startTime']);me=mins(med['endTime']);delay=ms-le
    assert med['medication']['intervalMinutes']==30,(p.name,med['medication'])
    interval_bad=delay!=30;interval_count+=interval_bad
    tails=[s for s in places if ms<mins(s['endTime']) and me>mins(s['startTime'])]
    tail_count+=bool(tails)
    if interval_bad or tails:rows.append({'case':p.stem,'day':day['dayNumber'],'mealEnd':lunch[0]['endTime'],'medicationStart':med['startTime'],'medicationEnd':med['endTime'],'delayMinutes':delay,'intervalViolation':interval_bad,'overlappingPlaceCount':len(tails)})
 out[label]={'mealLateCount':late_count,'medicationSlots':med_count,'intervalViolationSlots':interval_count,'overlapSlots':tail_count,'cases':rows}
assert [(out[k]['medicationSlots'],out[k]['intervalViolationSlots'],out[k]['overlapSlots']) for k in ['before','after']]==[(22,10,1),(24,9,1)],out
Path('/private/tmp/p179-meal-handoff-audit.json').write_text(json.dumps(out,ensure_ascii=False,indent=2))
print(json.dumps({k:{a:b for a,b in v.items() if a!='cases'} for k,v in out.items()},ensure_ascii=False))
