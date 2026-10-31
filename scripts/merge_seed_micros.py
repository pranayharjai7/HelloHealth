#!/usr/bin/env python3
"""Merge micronutrient values (keyed by slug) into app/src/main/assets/common_foods.json.

Reads a JSON file of the form {"foods":[{"slug":..., "sodiumMgPer":..., ...}, ...]} and injects the
8 micronutrient fields into each matching entry, preserving entry order and existing fields. Run with
the workflow output path as argv[1]. Idempotent: re-running overwrites the same fields.
"""
import json
import sys

ASSET = "app/src/main/assets/common_foods.json"
FIELDS = [
    "sodiumMgPer", "saturatedFatGPer", "sugarGPer", "calciumMgPer",
    "ironMgPer", "potassiumMgPer", "vitaminDMcgPer", "vitaminCMgPer",
]

def main():
    micros_path = sys.argv[1]
    with open(micros_path) as f:
        micros = {e["slug"]: e for e in json.load(f)["foods"]}
    with open(ASSET) as f:
        foods = json.load(f)

    missing = []
    for food in foods:
        m = micros.get(food["slug"])
        if not m:
            missing.append(food["slug"])
            continue
        for fld in FIELDS:
            food[fld] = m[fld]

    with open(ASSET, "w") as f:
        json.dump(foods, f, indent=2)
        f.write("\n")

    print(f"merged {len(foods) - len(missing)}/{len(foods)} foods")
    if missing:
        print("MISSING micros for:", ", ".join(missing))

if __name__ == "__main__":
    main()
