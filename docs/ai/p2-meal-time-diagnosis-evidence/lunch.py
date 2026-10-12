import json,glob,os,sys
def m(t): h,mi,*_=t.split(':'); return int(h)*60+int(mi)
for f in sorted(glob.glob(sys.argv[1]+'/raw/*.json')):
    j=json.load(open(f))
    if not j.get('success'): continue
    d=j['data']
    for day in d['planDays']:
        s=day['schedules']
        for i,x in enumerate(s):
            if x['scheduleType']!='LUNCH': continue
            st=m(x['startTime']); off=st-720
            prev=s[i-1] if i else None
            flag='OUT' if abs(off)>30 else '  '
            seq=' > '.join(f"{y['scheduleType'][:5]}/{y['courseType'][:5]} {y['startTime'][:5]}-{y['endTime'][:5]} tr{y['travelMinutes']}" for y in s[max(0,i-2):i+2])
            print(f"{flag} {os.path.basename(f)[:-5]:40s} d{day['dayNumber']} {x['startTime'][:5]} off{off:+4d} tags={'MEAL_TIME_APPLIED' in x['tags']} | {seq}")
