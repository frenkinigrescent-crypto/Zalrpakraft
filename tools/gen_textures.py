"""Генератор пиксельных текстур мода (16×16). Запуск: python3 tools/gen_textures.py"""
import math, random, os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources")
ITEM = os.path.join(ROOT, "assets", "zalrpakraft", "textures", "item")
BLOCK = os.path.join(ROOT, "assets", "zalrpakraft", "textures", "block")
os.makedirs(ITEM, exist_ok=True); os.makedirs(BLOCK, exist_ok=True)

def hexc(h, a=255):
    h = h.lstrip('#'); return (int(h[0:2],16), int(h[2:4],16), int(h[4:6],16), a)
def mix(c1, c2, t):
    return tuple(int(c1[i] + (c2[i]-c1[i])*t) for i in range(4))
def shade(c, k):
    return (max(0,min(255,int(c[0]*k))), max(0,min(255,int(c[1]*k))), max(0,min(255,int(c[2]*k))), c[3])

OUT = (20, 12, 30, 255)

def eye(name, iris, glow, pupil='slit'):
    im = Image.new("RGBA", (16,16), (0,0,0,0)); px = im.load()
    c = (7.5, 7.5); R = 6.6
    for y in range(16):
        for x in range(16):
            d = math.hypot(x-c[0], y-c[1])
            if d <= R+0.5:
                if d > R-0.7: px[x,y] = OUT; continue
                t = d / R
                col = mix(hexc(glow), hexc(iris), min(1, t*1.15))
                col = shade(col, 1.15 - 0.45*t)
                px[x,y] = col
    # зрачок
    if pupil == 'slit':
        for y in range(4,12):
            px[7,y] = (12,6,18,255); px[8,y] = (12,6,18,255)
    elif pupil == 'round':
        for y in range(6,10):
            for x in range(6,10):
                if math.hypot(x-7.5,y-7.5) < 2.1: px[x,y]=(12,6,18,255)
    elif pupil == 'star':
        for i in range(-2,3):
            px[7+i,7]=(255,255,240,255); px[7,7+i]=(255,255,240,255)
        px[7,7]=(255,255,255,255)
    elif pupil == 'cross':
        for i in range(4,12): px[i,7]=(12,6,18,255); px[7,i]=(12,6,18,255)
    # блик
    px[5,4] = (255,255,255,230); px[4,5] = (255,255,255,170); px[5,5]=(255,255,255,120)
    im.save(os.path.join(ITEM, name + ".png"))

EYES = [
 ("eye_rust", "#8A5A3C", "#E3A15F", "slit"),
 ("eye_frost", "#3E7FB8", "#D8F4FF", "slit"),
 ("eye_sun", "#D98A16", "#FFF2A0", "round"),
 ("eye_sculptor", "#6E6A88", "#E6E0FF", "cross"),
 ("eye_abyss", "#123F5A", "#5FE0D0", "slit"),
 ("eye_flame", "#B8320E", "#FFD24A", "slit"),
 ("eye_forge", "#3B2F3A", "#FF7A2E", "round"),
 ("eye_depths", "#0E3D47", "#4FB8C9", "round"),
 ("eye_sands", "#B58A47", "#FFE9B0", "slit"),
 ("eye_mechanism", "#6B5532", "#F2C55C", "cross"),
 ("eye_undying", "#2F8F4F", "#F7E26B", "round"),
 ("eye_stars", "#2A1659", "#C9B7FF", "star"),
]
for e in EYES: eye(*e)

def orb(px, c, R, col, rim=True):
    for y in range(16):
        for x in range(16):
            d = math.hypot(x-c[0], y-c[1])
            if d <= R:
                px[x,y] = shade(col, 1.25 - 0.55*(d/R)) if not (rim and d > R-0.9) else shade(col, 0.45)

def relic(name, kind, color, accent):
    im = Image.new("RGBA",(16,16),(0,0,0,0)); px = im.load()
    col, acc = hexc(color), hexc(accent)
    rnd = random.Random(name)
    if kind == "orb":
        orb(px,(7.5,7.5),5.8,col)
        for _ in range(5):
            x,y = rnd.randint(5,10), rnd.randint(5,10); px[x,y]=acc
        px[5,5]=(255,255,255,220)
    elif kind == "shard":
        pts = [(7,1),(11,6),(9,14),(5,13),(4,6)]
        for y in range(16):
            for x in range(16):
                inside = True
                for i in range(len(pts)):
                    x1,y1 = pts[i]; x2,y2 = pts[(i+1)%len(pts)]
                    if (x2-x1)*(y-y1) - (y2-y1)*(x-x1) < 0: inside=False; break
                if inside: px[x,y] = shade(col, 0.8 + 0.5*((16-y)/16)) if x < 8 else shade(col, 0.7 + 0.3*((16-y)/16))
        for y in range(3,12): px[7,y] = acc
    elif kind == "feather":
        for i in range(13):
            x = 3+i*0.75; y = 13-i
            for w in range(-2,3):
                xx = int(x+w*0.6); yy=int(y+w*0.6)
                if 0<=xx<16 and 0<=yy<16: px[xx,yy] = shade(col, 1.1 - abs(w)*0.18)
            px[int(x),int(y)] = acc
    elif kind == "gear":
        for y in range(16):
            for x in range(16):
                d = math.hypot(x-7.5,y-7.5); a = math.atan2(y-7.5,x-7.5)
                rr = 6.2 if (int((a+math.pi)/(math.pi/4)+0.5)%2==0) else 4.8
                if 2.0 < d <= rr: px[x,y] = shade(col, 1.2-0.5*d/rr)
        px[7,7]=px[8,8]=acc
    elif kind == "heart":
        for y in range(16):
            for x in range(16):
                inside = math.hypot(x-5,y-5.5) <= 3.3 or math.hypot(x-10,y-5.5) <= 3.3 or (y >= 5.5 and abs(x-7.5) <= (13.5-y)*0.95)
                if inside and y <= 13: px[x,y] = shade(col, 1.25 - 0.05*y)
        px[4,4]=(255,255,255,210); px[5,4]=(255,255,255,140)
    elif kind == "lantern":
        for y in range(4,14):
            for x in range(4,12):
                px[x,y] = shade(col,0.6) if x in (4,11) or y in (4,13) else shade(acc, 1.0 - 0.04*abs(y-9))
        for x in range(6,10): px[x,3]=shade(col,0.5)
        px[7,2]=px[8,2]=shade(col,0.5)
    elif kind == "pile":
        for y in range(8,15):
            w = (y-7)
            for x in range(8-w, 8+w):
                if 0<=x<16: px[x,y] = shade(col, 0.8+rnd.random()*0.4)
        for _ in range(4): px[rnd.randint(5,10), rnd.randint(10,14)] = acc
    elif kind == "scale":
        for y in range(16):
            for x in range(16):
                d = math.hypot((x-7.5)/6.5,(y-6)/7.5)
                if d <= 1 and y >= 1: px[x,y] = shade(col, 1.15-0.5*d)
        for y in range(3,13,3):
            for x in range(3,13): 
                if px[x,y][3]: px[x,y] = shade(acc,0.9)
    elif kind == "chisel":
        for i in range(10):
            px[3+i,12-i]=shade(col,1.0); px[4+i,12-i]=shade(col,0.7)
        for i in range(4): px[12+i if 12+i<16 else 15,3-i if 3-i>=0 else 0]=acc
        for x in range(1,5):
            for y in range(11,15): px[x,y]=hexc("#6B4A2B")
    elif kind == "sigil":
        orb(px,(7.5,7.5),6.5,col)
        for i in range(3,13): px[i,i]=acc; px[15-i,i]=acc
    im.save(os.path.join(ITEM, name+".png"))

RELICS = [
 ("rust_core","orb","#8B4A26","#E8A060"), ("frost_heart","heart","#7CC8F0","#FFFFFF"),
 ("sun_feather","feather","#F4B23A","#FFF3B0"), ("sculptor_chisel","chisel","#B9B4D6","#FFFFFF"),
 ("cornelia_lantern","lantern","#2E4E5C","#7FFFE6"), ("maze_pearl","orb","#2A6E7A","#BFFFF4"),
 ("ignis_ember","shard","#E2501B","#FFE16A"), ("revenant_ash","pile","#4A4448","#FF6A2A"),
 ("forge_heart","heart","#5A3A3A","#FF8A3A"), ("leviathan_scale","scale","#1D5A66","#7FE7F0"),
 ("remnant_sand","pile","#D9B776","#7A5A2A"), ("harbinger_gear","gear","#9A7B45","#FF3A3A"),
 ("guardian_shard","shard","#5A3A8A","#E0C0FF"), ("qliphoth_fragment","shard","#3A2A5A","#B0FFEA"),
 ("azazel_sigil","sigil","#7A1A1A","#FFCF4A"), ("rise_trophy","orb","#6A6A72","#FF4040"),
 ("meteor_shard","shard","#4A3A33","#FF8A40"),
]
for r in RELICS: relic(*r)

def key(name, metal, gem):
    im = Image.new("RGBA",(16,16),(0,0,0,0)); px = im.load()
    m, g = hexc(metal), hexc(gem)
    for y in range(2,8):
        for x in range(2,8):
            d = math.hypot(x-4.5,y-4.5)
            if 1.3 < d <= 3.0: px[x,y] = shade(m, 1.2 - 0.15*d)
    px[4,4]=px[5,5]=px[4,5]=px[5,4]=g
    for i in range(8):
        px[6+i, 6+i] = shade(m,1.0); px[7+i if 7+i<16 else 15, 6+i] = shade(m,0.7)
    for (x,y) in [(11,13),(12,12),(13,14),(10,12)]:
        if x<16 and y<16: px[x,y]=shade(m,0.8)
    im.save(os.path.join(ITEM, name+".png"))
key("nether_key","#3A2A2A","#FF4A1A")
key("paradise_key","#E8C24A","#FFFFFF")

# дневник
im = Image.new("RGBA",(16,16),(0,0,0,0)); px = im.load()
for y in range(2,15):
    for x in range(3,13):
        px[x,y] = (92,58,34,255) if x>3 else (60,36,20,255)
for y in range(3,14): px[12,y]=(230,220,195,255)
for (x,y) in [(8,6),(7,7),(8,7),(9,7),(8,8),(6,7),(10,7),(8,5),(8,9)]: px[x,y]=(255,226,140,255)
for y in range(2,15): px[3,y]=(40,24,14,255)
im.save(os.path.join(ITEM,"stranger_journal.png"))

# звёздный стол
rnd = random.Random(7)
top = Image.new("RGBA",(16,16)); p = top.load()
for y in range(16):
    for x in range(16):
        p[x,y] = (24,18,44,255) if 1<=x<=14 and 1<=y<=14 else (70,48,30,255)
for _ in range(14):
    x,y = rnd.randint(2,13), rnd.randint(2,13); p[x,y] = (230,220,255,255) if rnd.random()<0.6 else (160,140,255,255)
for (a,b) in [((4,4),(8,6)),((8,6),(11,10)),((8,6),(6,11))]:
    for t in range(10):
        x = int(a[0]+(b[0]-a[0])*t/10); y=int(a[1]+(b[1]-a[1])*t/10); p[x,y]=(120,100,200,255)
top.save(os.path.join(BLOCK,"star_table_top.png"))
side = Image.new("RGBA",(16,16)); p = side.load()
for y in range(16):
    for x in range(16):
        if y < 4: p[x,y] = (86,60,36,255) if y%3 else (64,44,26,255)
        elif x in (1,2,3,12,13,14): p[x,y] = (78,54,32,255) if (y+x)%4 else (60,40,24,255)
        else: p[x,y] = (0,0,0,0)
for x in range(16): p[x,2] = (140,110,220,255) if x%3==0 else p[x,2]
side.save(os.path.join(BLOCK,"star_table_side.png"))
bot = Image.new("RGBA",(16,16),(70,48,30,255)); bot.save(os.path.join(BLOCK,"star_table_bottom.png"))

# логотип для списка модов
logo = Image.new("RGBA",(128,128),(10,8,24,255)); p = logo.load()
r = random.Random(3)
for _ in range(260):
    x,y = r.randint(0,127), r.randint(0,127); b = r.randint(90,255); p[x,y]=(b,b,min(255,b+30),255)
for y in range(128):
    for x in range(128):
        d = math.hypot(x-64,y-64)
        if d < 34:
            t = d/34; c = mix(hexc("#F6E8FF"), hexc("#5B3BB0"), t); p[x,y] = shade(c, 1.1-0.4*t)
        elif d < 36: p[x,y] = (30,18,60,255)
for i in range(-50,51):
    a = int(255*(1-abs(i)/51))
    for (x,y) in [(64+i,64),(64,64+i)]:
        if 0<=x<128 and 0<=y<128 and math.hypot(x-64,y-64)>30: p[x,y]=(a,a,255,255)
for y in range(56,72):
    for x in range(61,67): p[x,y]=(20,10,30,255)
logo.save(os.path.join(ROOT,"zalrpakraft_logo.png"))
print("ok")

# ---------- иконка окна: «звёздный глаз» ----------
ICONS = os.path.join(ROOT, "assets", "zalrpakraft", "icons")
os.makedirs(ICONS, exist_ok=True)
def star_eye(size):
    S = size * 4  # суперсэмплинг
    im = Image.new("RGBA", (S, S), (0, 0, 0, 0)); p = im.load()
    c = S / 2; R = S * 0.47
    rr = random.Random(11)
    for y in range(S):
        for x in range(S):
            d = math.hypot(x - c, y - c)
            if d <= R:
                t = d / R
                base = mix(hexc("#2A1659"), hexc("#07050F"), t)
                if d > R - S * 0.035: base = hexc("#B79CFF")
                p[x, y] = base
    for _ in range(int(S * 0.6)):
        x, y = rr.randint(0, S - 1), rr.randint(0, S - 1)
        if math.hypot(x - c, y - c) < R * 0.9:
            b = rr.randint(150, 255)
            for dx in range(max(1, S // 64)):
                for dy in range(max(1, S // 64)):
                    if x+dx < S and y+dy < S: p[x+dx, y+dy] = (b, b, 255, 255)
    # глаз (миндалина)
    ew, eh = R * 0.78, R * 0.42
    for y in range(S):
        for x in range(S):
            X, Y = (x - c) / ew, (y - c) / eh
            if abs(Y) <= (1 - X * X) and abs(X) <= 1:
                d = math.hypot(x - c, y - c)
                if d < R * 0.34:
                    t = d / (R * 0.34)
                    p[x, y] = shade(mix(hexc("#FFE9A0"), hexc("#B07CFF"), t), 1.15 - 0.3 * t)
                    if abs(x - c) < R * 0.055 and abs(y - c) < R * 0.27: p[x, y] = (14, 6, 24, 255)
                else:
                    p[x, y] = (236, 228, 250, 255)
                if abs(Y) > (1 - X * X) - 0.09: p[x, y] = (40, 22, 70, 255)
    # лучи звезды
    for i in range(-int(R), int(R) + 1):
        a = int(255 * (1 - abs(i) / R) ** 2)
        w = max(1, S // 48)
        for k in range(-w, w + 1):
            for (x, y) in ((int(c + i), int(c + k)), (int(c + k), int(c + i))):
                if 0 <= x < S and 0 <= y < S and math.hypot(x - c, y - c) > R * 0.36:
                    q = p[x, y]
                    p[x, y] = mix(q if q[3] else (0,0,0,0), (255, 240, 200, 255), a / 255)
    im = im.resize((size, size), Image.LANCZOS)
    return im
for s in (16, 32, 48, 128, 256):
    star_eye(s).save(os.path.join(ICONS, f"icon_{s}.png"))
star_eye(128).save(os.path.join(ROOT, "zalrpakraft_logo.png"))
print("icons ok")
