cd /storage/internal_new/project/AppsUsageMonitor

# Crear archivo con todos los Kotlin relacionados a temas
echo "===== ARCHIVOS KOTLIN DE TEMAS - $(date) =====" > temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 1. ThemeHelper.kt
echo "===== 1. ThemeHelper.kt =====" >> temas_kotlin.txt
cat app/src/main/java/com/gnzalobnites/appsusagemonitor/ThemeHelper.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ ThemeHelper.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "----------------------------------------" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 2. AppUsageMonitorApp.kt
echo "===== 2. AppUsageMonitorApp.kt =====" >> temas_kotlin.txt
cat app/src/main/java/com/gnzalobnites/appsusagemonitor/AppUsageMonitorApp.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ AppUsageMonitorApp.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "----------------------------------------" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 3. UserPreferences.kt (parte de dark mode)
echo "===== 3. UserPreferences.kt (sección tema) =====" >> temas_kotlin.txt
grep -A 10 -B 10 "isDarkMode\|dark_mode" app/src/main/java/com/gnzalobnites/appsusagemonitor/UserPreferences.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ UserPreferences.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "----------------------------------------" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 4. MainViewModel.kt (método updateDarkMode)
echo "===== 4. MainViewModel.kt (método updateDarkMode) =====" >> temas_kotlin.txt
grep -A 15 -B 5 "updateDarkMode\|dark.*mode" app/src/main/java/com/gnzalobnites/appsusagemonitor/MainViewModel.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ MainViewModel.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "----------------------------------------" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 5. BaseFragment.kt
echo "===== 5. BaseFragment.kt =====" >> temas_kotlin.txt
cat app/src/main/java/com/gnzalobnites/appsusagemonitor/fragments/BaseFragment.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ BaseFragment.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "----------------------------------------" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 6. SettingsFragment.kt (solo partes de tema)
echo "===== 6. SettingsFragment.kt (secciones de tema) =====" >> temas_kotlin.txt
grep -A 50 -B 10 "DarkMode\|darkMode\|tema\|theme\|swDarkMode" app/src/main/java/com/gnzalobnites/appsusagemonitor/fragments/SettingsFragment.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ SettingsFragment.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "----------------------------------------" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt

# 7. MonitoredAppsAdapter.kt (método setThemeColors)
echo "===== 7. MonitoredAppsAdapter.kt (método setThemeColors) =====" >> temas_kotlin.txt
grep -A 20 -B 5 "setThemeColors\|isDarkMode\|applyThemeColors" app/src/main/java/com/gnzalobnites/appsusagemonitor/fragments/MonitoredAppsAdapter.kt >> temas_kotlin.txt 2>/dev/null || echo "⚠️ MonitoredAppsAdapter.kt no encontrado" >> temas_kotlin.txt
echo "" >> temas_kotlin.txt
echo "========================================" >> temas_kotlin.txt

echo "✅ Archivo creado: temas_kotlin.txt"
ls -la temas_kotlin.txt