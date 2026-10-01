"""Generate block-native station parts with flush trim and continuous playing-surface UVs.
Poker and exchange resources are intentionally not regenerated.
"""
import json
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / "src/main/resources"
ASSETS = ROOT / "assets/shiinacasino"
SIDES = ("north", "east", "south", "west", "up", "down")

def write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(value, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

def element(lo, hi, texture="wood", directions=SIDES):
    return {"from": lo, "to": hi, "faces": {d: {"texture": "#"+texture, "uv": [0,0,16,16]} for d in directions}}

def model(elements, game="blackjack"):
    return {"parent":"minecraft:block/block", "textures":{"particle":"shiinacasino:block/wood", "wood":"shiinacasino:block/wood", "trim":"shiinacasino:block/trim", "top":"shiinacasino:block/"+game}, "elements":elements}

def table_cell(kind,x,z):
    min_z = -1 if kind=="roulette" else 0
    outer = {(cx,cz) for cx in (0,15.25) for cz in (0,15.25)
             if (cx==0 and x==-1 or cx==15.25 and x==1) and (cz==0 and z==min_z or cz==15.25 and z==1)}
    elements=[element([0,11,0],[16,14.75,16],directions=("north","east","south","west","down"))]
    for f in elements[0]["faces"].values():
        if f is not elements[0]["faces"]["down"]: f["uv"]=[0,1,16,16]
    cuts=[0,.75,15.25,16]
    for ix in range(3):
        for iz in range(3):
            x0,x1,z0,z1=cuts[ix],cuts[ix+1],cuts[iz],cuts[iz+1]
            trim=(x0,z0) in outer
            dirs=["up"] + (["north"] if z0==0 else []) + (["south"] if z1==16 else []) + (["west"] if x0==0 else []) + (["east"] if x1==16 else [])
            e=element([x0,14.75,z0],[x1,15,z1],"trim" if trim else "wood",dirs)
            for d,f in e["faces"].items():
                if trim: f["uv"]=[0,0,.75,.75 if d=="up" else .25]
                elif d=="up": f["uv"]=[x0,z0,x1,z1]
                elif d=="north": f["uv"]=[16-x1,0,16-x0,1]
                elif d=="south": f["uv"]=[x0,0,x1,1]
                elif d=="west": f["uv"]=[z0,0,z1,1]
                else: f["uv"]=[16-z1,0,16-z0,1]
            elements.append(e)
    lo=[1 if x==-1 else 0,15,1 if z==min_z else 0]
    hi=[15 if x==1 else 16,15.1,15 if z==1 else 16]
    surface=element(lo,hi,directions=("up",))
    surface["faces"]["up"]["texture"]="#top"
    if kind=="roulette":
        # Green felt only; the renderer supplies the visible wheel and numbered betting grid.
        surface["faces"]["up"]["uv"]=[1,1,1.5,1.5]
    else:
        width=46;depth=30
        surface["faces"]["up"]["uv"]=[(16*(x+1)+lo[0]-1)/width*16,(16*z+lo[2]-1)/depth*16,(16*(x+1)+hi[0]-1)/width*16,(16*z+hi[2]-1)/depth*16]
    elements.append(surface)
    if abs(x)==1 and z in (min_z,1):
        px=1 if x<0 else 12;pz=1 if z==min_z else 12
        elements.append(element([px,0,pz],[px+3,11,pz+3],directions=("north","east","south","west","down")))
    result=model(elements,kind)
    if kind=="roulette": result["textures"]["top"]="shiinacasino:block/felt"
    return result

def layouts(kind):
    if kind=="wheel": return [(x,y,0,-1) for y in range(3) for x in range(-1,2) if x or y]
    minz=-1 if kind=="roulette" else 0
    return [(x,0,z,-1) for z in range(minz,2) for x in range(-1,2) if x or z] + [(i-1,0,minz-1,i) for i in range(3)] + [(i-1,0,2,i+3) for i in range(3)]

def stand(x,y):
    elements=[]
    if y==0: elements.append(element([0,0,3],[16,4,13]))
    if x==0: elements.append(element([6,4 if y==0 else 0,6],[10,16,10]))
    return model(elements,"wheel")

chair=[element([3,8,3],[13,10,13]),element([3,10,2],[13,21,4])]
for x in (3,11):
    for z in (3,11): chair.append(element([x,0,z],[x+2,8,z+2],directions=("north","east","south","west","down")))
chair.append(element([3,10,4.01],[13,10.1,13],"top",("up",)))
chair_model=model(chair);chair_model["textures"]["top"]="shiinacasino:block/felt"
write(ASSETS/"models/block/casino_chair.json",chair_model)
write(ASSETS/"models/block/station_empty.json",model([]))
for kind,block_name in [("blackjack","blackjack_table"),("roulette","roulette_table"),("wheel","fortune_wheel")]:
    variants={};part_variants={};parts=layouts(kind)
    for expanded in (False,True):
        for facing,rotation in [("north",0),("east",90),("south",180),("west",270)]:
            variants[f"expanded={str(expanded).lower()},facing={facing}"]={"model":"shiinacasino:block/"+(block_name+"_center" if expanded else block_name),"y":rotation}
    write(ASSETS/("blockstates/"+block_name+".json"),{"variants":variants})
    write(ASSETS/("models/block/"+block_name+"_center.json"),stand(0,0) if kind=="wheel" else table_cell(kind,0,0))
    for index in range(14):
        chair_rotation=0
        if index>=len(parts): name="station_empty"
        else:
            x,y,z,seat=parts[index]
            if seat>=0: name="casino_chair";chair_rotation=180 if seat>=3 else 0
            else:
                name=block_name+"_part_"+str(index)
                write(ASSETS/("models/block/"+name+".json"),stand(x,y) if kind=="wheel" else table_cell(kind,x,z))
        for facing,rotation in [("north",0),("east",90),("south",180),("west",270)]:
            part_variants[f"facing={facing},part={index}"]={"model":"shiinacasino:block/"+name,"y":(rotation+chair_rotation)%360}
    write(ASSETS/("blockstates/"+block_name+"_part.json"),{"variants":part_variants})
    write(ROOT/("data/shiinacasino/loot_table/blocks/"+block_name+"_part.json"),{"type":"minecraft:block","pools":[]})
# Legacy wheel gains an upright stand too, but keeps its one-block placement footprint.
write(ASSETS/"models/block/fortune_wheel.json",model([element([3,0,3],[13,4,13]),element([6,4,6],[10,16,10])],"wheel"))
item=model([element([3,0,3],[13,4,13]),element([6,4,6],[10,16,10]),element([1,10,7],[15,24,9],"top")],"wheel")
write(ASSETS/"models/block/fortune_wheel_item.json",item)
write(ASSETS/"models/item/fortune_wheel.json",{"parent":"shiinacasino:block/fortune_wheel_item"})
p=ROOT/"data/minecraft/tags/block/mineable/axe.json";tag=json.loads(p.read_text())
for name in ("blackjack_table_part","roulette_table_part","fortune_wheel_part"):
    if "shiinacasino:"+name not in tag["values"]: tag["values"].append("shiinacasino:"+name)
write(p,tag)
for language,values in {
    "de_de":{"casino.error.seat":"Dieser Sitz ist belegt oder f?r die laufende Hand reserviert.","casino.error.sit":"Setze dich zuerst auf einen Stuhl an diesem Tisch.","casino.wheel.world":"Das Rad dreht sich drau?en in der Welt.","casino.wheel.payouts":"6? 0 | 3? 1 | 2? 2 | 1? 4", "casino.seat.hint":"Stuhl: setzen ? Shift: aufstehen"},
    "en_us":{"casino.error.seat":"This seat is occupied or reserved for the current hand.","casino.error.sit":"Sit on a chair at this table first.","casino.wheel.world":"Watch the wheel spin in the world.","casino.wheel.payouts":"6? 0 | 3? 1 | 2? 2 | 1? 4", "casino.seat.hint":"Chair: sit ? Shift: stand up"}
}.items():
    p=ASSETS/("lang/"+language+".json");data=json.loads(p.read_text(encoding="utf-8"));data.update(values)
    for part in ("blackjack_table_part","roulette_table_part","fortune_wheel_part"):
        data["block.shiinacasino."+part]="Stationsteil" if language=="de_de" else "Station part"
    write(p,data)
print("Generated station models, blockstates and part loot; retained existing textures.")
