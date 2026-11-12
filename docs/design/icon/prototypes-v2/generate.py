from pathlib import Path
import subprocess
P=Path(__file__).parent
cream='#FFF3DE';coral='#D97757'
marks=[('01','Perspectiva','Um olhar atento sobre o projeto.', '''<path d="M24 58C30 37 46 29 62 33C74 36 81 46 84 57C74 73 57 80 42 73C34 69 28 64 24 58Z" fill="none" stroke="#FFF3DE" stroke-width="9" stroke-linejoin="round"/><path d="M56 43C48 43 43 49 43 57C43 65 49 69 56 67C63 65 66 58 64 52L57 56Z" fill="#FFF3DE"/>'''),
('02','Confluência','Duas contribuições formam uma solução.', '''<path d="M61 31C48 22 31 29 27 42C23 55 31 67 44 68L51 58C41 60 35 54 37 46C39 38 49 35 55 41L61 31Z" fill="#FFF3DE"/><path d="M47 77C60 86 77 79 81 66C85 53 77 41 64 40L57 50C67 48 73 54 71 62C69 70 59 73 53 67L47 77Z" fill="#FFF3DE"/>'''),
('03','Desdobrar','Uma ideia ganha forma e continuidade.', '''<path d="M33 31C29 31 27 34 29 38L49 72C52 78 58 80 63 76L80 63C84 60 84 55 79 53L63 45L71 35C74 31 71 27 67 28L52 33L44 31Z M40 39L58 68L71 59L55 51L61 40L50 44Z" fill="#FFF3DE" fill-rule="evenodd"/>''')]
head='<svg xmlns="http://www.w3.org/2000/svg"'
for n,name,desc,body in marks:
 (P/f'{n}-symbol.svg').write_text(head+' viewBox="0 0 108 108">'+body+'</svg>')
 (P/f'{n}-icon.svg').write_text(head+' viewBox="0 0 108 108"><rect width="108" height="108" fill="'+coral+'"/>'+body+'</svg>')
parts=[head+' width="1080" height="1460" viewBox="0 0 1080 1460"><rect width="1080" height="1460" fill="#F5EBDD"/><text x="60" y="68" fill="#29231F" font-family="sans-serif" font-size="32" font-weight="bold">FCC / novos estudos</text><text x="60" y="105" fill="#76675C" font-family="sans-serif" font-size="19">Coral de argila · papel claro · formas de uma cor</text>']
for i,(n,name,desc,body) in enumerate(marks):
 y=150+i*430
 parts.append(f'<text x="60" y="{y+30}" font-family="sans-serif" font-size="27" fill="#29231F">{n} / {name}</text><text x="60" y="{y+63}" font-family="sans-serif" font-size="19" fill="#76675C">{desc}</text>')
 for x,dy,s,r in [(60,90,295,65),(400,95,192,96),(660,105,96,23),(800,105,48,12),(890,105,24,6)]:
  parts.append(f'<svg x="{x}" y="{y+dy}" width="{s}" height="{s}" viewBox="0 0 108 108"><rect width="108" height="108" rx="{r*108/s}" fill="{coral}"/>'+body+'</svg>')
 parts.append(f'<svg x="665" y="{y+230}" width="108" height="108" viewBox="0 0 108 108">'+body.replace(cream,'#29231F')+'</svg>')
parts.append('</svg>');(P/'comparacao.svg').write_text(''.join(parts))
subprocess.run(['rsvg-convert',str(P/'comparacao.svg'),'-o',str(P/'comparacao.png')],check=True)
