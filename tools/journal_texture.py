from PIL import Image
import random, sys
P = {
 '.':(0,0,0,0), 'O':(36,20,12,255), 'S':(58,32,18,255), 's':(200,172,118,255),
 'L':(112,64,36,255), 'l':(86,47,26,255), 'h':(146,90,52,255), 'd':(70,38,22,255),
 'P':(232,220,186,255), 'p':(184,166,128,255), 'q':(150,132,98,255),
 'B':(222,180,74,255), 'b':(150,108,38,255), 'R':(176,38,42,255), 'r':(118,22,28,255),
 'E':(236,204,122,255), 'e':(168,128,76,255), 'W':(150,28,30,255), 'w':(200,60,54,255),
}
G = [
"................",
"..OOOOOOOOOOO...",
".OBSOhhhhhhhhO..",
".OSsLhLLLLLLLOP.",
".OSLLLLLeLLLLOP.",
".OSsLLLeEeLLLOP.",
".OSLLLeEEEeLlOP.",
".OSsLLLeEeLLlOP.",
".OSllllldlllbBO.",
".OSsLLLLLLLLlBO.",
".OSLLLLLLLLllOP.",
".OSsLLLLLLLllOP.",
".OSLWLLLLLlllOp.",
".OBSdlllllllOOq.",
"..OOOOROOOOOpq..",
"......Rr........",
]
im = Image.new("RGBA", (16, 16)); px = im.load(); rnd = random.Random(4)
for y, row in enumerate(G):
    assert len(row) == 16, (y, len(row))
    for x, ch in enumerate(row):
        c = P[ch]
        if ch in 'Ll' and rnd.random() < 0.22:  # потёртости кожи
            k = 0.9 if rnd.random() < 0.6 else 1.1
            c = (min(255, int(c[0]*k)), min(255, int(c[1]*k)), min(255, int(c[2]*k)), 255)
        px[x, y] = c
im.save(sys.argv[1])
big = im.resize((256, 256), Image.NEAREST); big.save('/tmp/journal_big.png')
