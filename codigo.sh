#!

# Crear/limpiar los archivos de salida
> res-layouts.txt
> res-drawable.txt
> todo-kotlin.txt

# Procesar archivos XML de layout
echo "Procesando layouts..."
find app/src/main/res/layout -name "*.xml" | while read file; do
    echo "===== $file =====" >> res-layouts.txt
    cat "$file" >> res-layouts.txt
    echo -e "\n" >> res-layouts.txt
done

# Procesar archivos XML de drawable
echo "Procesando drawables..."
find app/src/main/res/drawable* -name "*.xml" | while read file; do
    echo "===== $file =====" >> res-drawable.txt
    cat "$file" >> res-drawable.txt
    echo -e "\n" >> res-drawable.txt
done

# Procesar archivos Kotlin (desde java/)
echo "Procesando archivos Kotlin..."
find app/src/main/java -name "*.kt" | while read file; do
    echo "===== $file =====" >> todo-kotlin.txt
    cat "$file" >> todo-kotlin.txt
    echo -e "\n" >> todo-kotlin.txt
done

echo "¡Completado! Archivos generados:"
echo "- res-layouts.txt"
echo "- res-drawable.txt"
echo "- todo-kotlin.txt"