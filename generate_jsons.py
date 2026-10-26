import pandas as pd
import json
import os

xls_path = r'C:\Users\user\Downloads\Marcas_lineas_colores\TABLA MARCAS LINEAS COLORES.xls'
out_dir = r'C:\Users\user\OneDrive\Escritorio\Proyectos_Universidad\aplicaciones_moviles\Proyecto-Final-main\app\src\main\res\raw'

if not os.path.exists(out_dir):
    os.makedirs(out_dir)

xls = pd.ExcelFile(xls_path)
marcas = pd.read_excel(xls, sheet_name='MARCAS')
lineas = pd.read_excel(xls, sheet_name='LINEAS')
colores = pd.read_excel(xls, sheet_name='COLORES')

# Clean data
marcas['DESCRIPCION'] = marcas['DESCRIPCION'].astype(str).str.strip().str.upper()
lineas['DESCRIPCION'] = lineas['DESCRIPCION'].astype(str).str.strip().str.upper()
colores['DESCRIPCION'] = colores['DESCRIPCION'].astype(str).str.strip().str.upper()

# Drop NaNs or invalid
marcas = marcas.dropna(subset=['CODIGO', 'DESCRIPCION'])
lineas = lineas.dropna(subset=['CODIGO MARCA', 'DESCRIPCION'])
colores = colores.dropna(subset=['DESCRIPCION'])

# Create a mapping of CODIGO MARCA to MARCA NAME
marca_dict = dict(zip(marcas['CODIGO'], marcas['DESCRIPCION']))

# Group lineas by MARCA NAME
marcas_lineas = {}
for index, row in lineas.iterrows():
    cod_marca = row['CODIGO MARCA']
    linea_desc = row['DESCRIPCION']
    if cod_marca in marca_dict:
        marca_desc = marca_dict[cod_marca]
        if marca_desc not in marcas_lineas:
            marcas_lineas[marca_desc] = []
        if linea_desc not in marcas_lineas[marca_desc]:
            marcas_lineas[marca_desc].append(linea_desc)

# Sort 
marcas_lineas = {k: sorted(v) for k, v in sorted(marcas_lineas.items())}
colores_list = sorted(list(set(colores['DESCRIPCION'].tolist())))

# Save JSON
with open(os.path.join(out_dir, 'marcas_lineas.json'), 'w', encoding='utf-8') as f:
    json.dump(marcas_lineas, f, ensure_ascii=False)

with open(os.path.join(out_dir, 'colores.json'), 'w', encoding='utf-8') as f:
    json.dump(colores_list, f, ensure_ascii=False)

print('JSONs generated successfully.')
