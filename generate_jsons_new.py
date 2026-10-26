# -*- coding: utf-8 -*-
import pandas as pd
import json
import os
import re

files_info = [
    (r'C:\Users\user\Downloads\nuevos.xls', 'nuevos', 'marca', 'tipo'),
    (r'C:\Users\user\Downloads\usados_importados.xls', 'USADOS_IMPORTADOS', 'marca', 'tipo'),
    (r'C:\Users\user\Downloads\usados_nacionales.xls', 'Unnamed: 0', 'marca', 'tipo')
]

out_dir = r'C:\Users\user\OneDrive\Escritorio\Proyectos_Universidad\aplicaciones_moviles\Proyecto-Final-main\app\src\main\res\raw'

tipo_mapping = {
    'MOTO': 'Motocicleta',
    'AUTOMOVILES': 'Automóvil',
    'AUTOMÓVIL': 'Automóvil',
    'TAXI': 'Automóvil',
    'ELÉCTRICO': 'Automóvil',
    'HÍBRIDO': 'Automóvil',
    'CAMIONETA': 'Camioneta / SUV',
    'SUV': 'Camioneta / SUV',
    'UTILITARIO DEPORTIVO 4X4 / 4X2': 'Camioneta / SUV',
    '4X4': 'Camioneta / SUV',
    'CAMPERO': 'Campero',
    'PICK UP 4X2 / 4X4': 'Pick Up',
    'PICK-UP': 'Pick Up',
    'PICKUP': 'Pick Up',
    'UTILITARIO': 'Utilitario / Vans',
    'VANS PASAJEROS': 'Utilitario / Vans'
}

bad_marcas = ['ELÉCTRICO', 'HÍBRIDO', 'ELECTRICO', 'HIBRIDO', 'NAN', 'AUTOMÓVILES', 'AUTOMOVILES', 'CAMIONETAS']

words_to_remove = [
    'HIBRIDO', 'HÍBRIDO', 'ELECTRICO', 'ELÉCTRICO', 'MOTO', 'MOTOS',
    'DEPORTIVO', 'CAMIONETA', 'CAMPERO', 'PICKUP', 'PICK', 'UTILITARIO',
    'VANS', '4X4', 'TAXI'
]

def clean_marca(marca_str):
    m = marca_str.strip().upper()
    for w in words_to_remove:
        # replace the word if it is at the end of the string, or inside
        m = re.sub(rf'\s*{w}\s*', ' ', m).strip()
        # handle glued words like CHEVROLETHIBRIDO
        m = m.replace(w, '')
    
    # special cases manual replacements
    if m == 'AKT': m = 'AKT'
    if m == 'MITSU': m = 'MITSUBISHI'
    if m == 'LANROVER': m = 'LAND ROVER'
    if m == 'LANDROVER': m = 'LAND ROVER'
    if m == 'ALFA ROMEO': m = 'ALFA ROMEO'
    
    m = m.strip()
    return m

data = {}

for file_path, linea_col, marca_col, tipo_col in files_info:
    df = pd.read_excel(file_path)
    df = df.dropna(subset=[linea_col, marca_col])
    
    for index, row in df.iterrows():
        raw_marca = str(row[marca_col]).strip().upper()
        linea = str(row[linea_col]).strip().upper()
        raw_tipo = str(row[tipo_col]).strip().upper() if tipo_col in df.columns and pd.notnull(row[tipo_col]) else 'OTRO'
        
        if raw_marca in bad_marcas or linea == 'NAN':
            continue
            
        marca = clean_marca(raw_marca)
        if not marca or marca == '':
            continue
            
        tipo_final = tipo_mapping.get(raw_tipo, 'Otro')
        
        if tipo_final not in data:
            data[tipo_final] = {}
            
        if marca not in data[tipo_final]:
            data[tipo_final][marca] = set()
            
        data[tipo_final][marca].add(linea)

# Sort
final_data = {}
for t in sorted(data.keys()):
    final_data[t] = {k: sorted(list(v)) for k, v in sorted(data[t].items()) if len(v) > 0}

with open(os.path.join(out_dir, 'tipos_marcas_lineas.json'), 'w', encoding='utf-8') as f:
    json.dump(final_data, f, ensure_ascii=False)

print(f'Generated tipos_marcas_lineas.json with fully cleaned brands.')
