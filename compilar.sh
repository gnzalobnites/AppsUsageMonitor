#!

# build.sh - Script para compilar AppsUsageMonitor en Termux
echo "=== COMPILANDO APPS USAGE MONITOR ==="

# Configurar memoria para Termux
export GRADLE_OPTS="-Xmx512m -Dorg.gradle.daemon=false"
export _JAVA_OPTIONS="-Xmx512m"

# Opcional: Limpiar si se pasa el argumento 'clean'
if [ "$1" == "clean" ]; then
    echo "🔧 Limpiando proyecto..."
    ./gradlew clean
fi

# Construir APK
echo "🔨 Construyendo APK debug..."
./gradlew :app:assembleDebug --no-daemon --max-workers=1 --console=plain

# Verificar resultado
if [ $? -eq 0 ]; then
    echo ""
    echo "✅ ✅ ¡COMPILACIÓN EXITOSA! ✅ ✅"
    echo ""
    echo "📦 APK generado:"
    ls -lh app/build/outputs/apk/debug/*.apk
    echo ""
    echo "📋 Para instalar en Android:"
    echo "adb install -r app/build/outputs/apk/debug/app-debug.apk"
    echo ""
    echo "🚀 Para ejecutar en emulador/dispositivo:"
    echo "adb shell am start -n com.gnzalobnites.appsusagemonitor/.MainNavActivity"
else
    echo ""
    echo "❌ ❌ ¡COMPILACIÓN FALLIDA! ❌ ❌"
    exit 1
fi