# -*- coding: utf-8 -*-
import pandas as pd

files_info = [
    (r'C:\Users\user\Downloads\nuevos.xls', 'nuevos', 'marca', 'tipo'),
    (r'C:\Users\user\Downloads\usados_importados.xls', 'USADOS_IMPORTADOS', 'marca', 'tipo'),
    (r'C:\Users\user\Downloads\usados_nacionales.xls', 'Unnamed: 0', 'marca', 'tipo')
]

marcas = set()
for file_path, linea_col, marca_col, tipo_col in files_info:
    df = pd.read_excel(file_path)
    df = df.dropna(subset=[linea_col, marca_col])
    for index, row in df.iterrows():
        raw_marca = str(row[marca_col]).strip().upper()
        marcas.add(raw_marca)

for m in sorted(list(marcas)):
    print(m)
