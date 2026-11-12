from pathlib import Path
import subprocess
P=Path(__file__).parent
marks=[('01','Intervalo','''<path d="M29 43C30 32 40 26 52 28C64 30 72 39 70 49C68 59 57 63 48 60L51 49C55 51 59 49 58 45C56 39 48 37 43 40C40 42 39 45 39 49Z"/><path d="M79 65C78 76 68 82 56 80C44 78 36 69 38 59C40 49 51 45 60 48L57 59C53 57 49 59 50 63C52 69 60 71 65 68C68 66 69 63 69 59Z"/>'''),('02','Cadência','''<path d="M30 66C25 52 31 35 44 29C48 27 53 28 55 32C57 36 55 40 51 43C43 49 40 58 43 67C45 73 42 77 37 77C34 77 32 73 30 66Z"/><path d="M49 67C45 56 49 43 58 37C62 34 67 35 69 39C71 43 68 47 65 50C60 55 59 61 61 67C63 73 60 78 56 78C52 78 50 73 49 67Z"/><path d="M69 68C66 61 68 53 73 49C76 47 80 49 81 52C82 55 79 58 79 62C78 66 81 70 78 73C75 77 71 74 69 68Z"/>'''),('03','Sillage','''<path d="M27 48C38 46 42 39 48 30C51 26 56 26 58 31C62 42 67 46 79 48C84 49 85 54 80 57C70 63 64 68 61 79C60 83 55 84 52 80C44 69 38 65 27 61C20 58 20 50 27 48Z M40 54C48 56 53 61 56 67C58 60 63 55 69 52C60 51 55 47 52 41C49 47 45 51 40 54Z" fill-rule="evenodd"/>''')]
svg='<svg xmlns="http://www.w3.org/2000/svg"'
a=[svg+' width="1080" height="1450"><rect width="1080" height="1450" fill="#F5EBDD"/><text x="60" y="70" font-family="sans-serif" font-size="32" fill="#29231F">FCC / explorações abstratas</text>']
for i,(n,name,body) in enumerate(marks):
 (P/f'{n}-symbol.svg').write_text(svg+' viewBox="0 0 108 108"><g fill="#FFF3DE">'+body+'</g></svg>')
 y=130+i*425
 a.append(f'<text x="60" y="{y+30}" font-family="sans-serif" font-size="25" fill="#29231F">{n} / {name}</text>')
 for x,dy,s,r in [(60,65,300,24),(405,70,192,54),(665,85,96,24),(805,85,48,24),(900,85,24,24)]:
  a.append(f'<svg x="{x}" y="{y+dy}" width="{s}" height="{s}" viewBox="0 0 108 108"><rect width="108" height="108" rx="{r}" fill="#D97757"/><g fill="#FFF3DE">'+body+'</g></svg>')
 a.append(f'<svg x="665" y="{y+215}" width="108" height="108" viewBox="0 0 108 108"><g fill="#29231F">'+body+'</g></svg>')
a.append('</svg>');(P/'comparacao.svg').write_text(''.join(a))
subprocess.run(['rsvg-convert',str(P/'comparacao.svg'),'-o',str(P/'comparacao.png')],check=True)
