#!/usr/bin/env python3
"""Generate explicit English screenshot/test fixtures. Never seeds a user's app automatically."""
import argparse
import datetime as dt
import json
from pathlib import Path
import uuid

def build(today):
    def identifier(name): return str(uuid.uuid5(uuid.NAMESPACE_URL, "tidytroupe-demo/" + name))
    members = [{"id":identifier(name),"nickname":name,"child":name!="Parent","avatar":i} for i,name in enumerate(("Parent","Alex","Rowan"))]
    tasks=[]
    for i,(title,member,category,points,repeat) in enumerate([
        ("Make the bed",1,"Bedroom",5,"DAILY"),("Water the plants",2,"Garden",3,"DAILY"),
        ("Feed the cat",1,"Pets",5,"DAILY"),("Set the table",2,"Kitchen",4,"DAILY"),
        ("Fold the towels",0,"Laundry",5,"WEEKLY"),("Pack the school bag",1,"School Preparation",3,"WEEKDAYS"),
        ("Tidy the living room",0,"Living Room",5,"WEEKLY")]):
        tasks.append({"id":identifier(title),"title":title,"memberId":members[member]["id"],"category":category,"notes":"A little help makes a difference.","points":points,"due":str(today-dt.timedelta(days=7)),"repeat":repeat,"paused":False,"archived":False,"approval":True,"repeatInterval":1,"weekdays":"","effort":1,"reminderMinutes":None})
    completions=[]
    for offset in range(7):
        day=today-dt.timedelta(days=offset)
        for task in tasks[:2]:
            completions.append({"choreId":task["id"],"date":str(day),"memberId":task["memberId"],"points":task["points"],"status":"APPROVED"})
    completions.append({"choreId":tasks[2]["id"],"date":str(today),"memberId":tasks[2]["memberId"],"points":5,"status":"PENDING"})
    return {"format":"tidytroupe","version":1,"household":"Willow House · Demo","members":members,"chores":tasks,"completions":completions,
            "rewards":[{"id":identifier("movie"),"title":"Choose the family film","cost":30},{"id":identifier("picnic"),"title":"A picnic together","cost":50}],
            "redemptions":[],"routines":[{"id":identifier("morning"),"title":"Morning preparation","choreIdsJson":json.dumps([tasks[0]["id"],tasks[2]["id"],tasks[5]["id"]])}]}

if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--date",required=True,type=dt.date.fromisoformat)
    parser.add_argument("--output",type=Path,default=Path("demo/household.en-GB.json"))
    args=parser.parse_args()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    args.output.write_text(json.dumps(build(args.date),ensure_ascii=False,indent=2)+"\n",encoding="utf-8")
    print(args.output)
