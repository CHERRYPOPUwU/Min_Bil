import xml.etree.ElementTree as ET

xml_path = r'C:\Users\user\OneDrive\Escritorio\Proyectos_Universidad\aplicaciones_moviles\Proyecto-Final-main\app\src\main\res\layout\activity_main.xml'
tree = ET.parse(xml_path)
root = tree.getroot()

# Search using full namespace for androidx.cardview.widget.CardView
for elem in root.iter():
    if 'CardView' in elem.tag:
        print(f"Tag: {elem.tag}")
        print(f"Radius: {elem.get('{http://schemas.android.com/apk/res-auto}cardCornerRadius')}")
        print(f"Elevation: {elem.get('{http://schemas.android.com/apk/res-auto}cardElevation')}")
        print("---")
