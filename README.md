# Now Bar Color 🎨

<p align="center">
  <b>Personaliza la cápsula Now Bar en la pantalla de bloqueo de Samsung One UI 7</b><br>
  <i>LSPosed Module for Samsung Galaxy Devices (Android 15 / One UI 7)</i>
</p>

<p align="center">
  <img src="res/drawable/ic_launcher.png" width="128" height="128" alt="Now Bar Color Icon" />
</p>

---

## 📖 Descripción / Overview

**Now Bar Color** es un módulo LSPosed diseñado específicamente para **Samsung One UI 7** que permite personalizar de forma integral el color de fondo, opacidad, estilo visual (Color Sólido o Color Blur) y degradados de las cápsulas contraídas de la **Now Bar** en la pantalla de bloqueo, manteniendo intactos los textos, iconos, portadas de álbumes y animaciones nativas del sistema.

Incluye una aplicación de configuración con estética nativa One UI, soporte para extracción automática de colores del fondo de pantalla (Monet, Zona Inferior exacta y Fondo), controles individuales por categoría y un motor de inyección de hooks de alto rendimiento a nivel de `SystemUI` y `FaceWidget/AOD`.

---

## ✨ Características Principales / Features

- 🎨 **Acabado Visual Flexible**:
  - **Color Sólido Puro**: Fondos limpios con degradados suaves de alta fidelidad.
  - **Color Blur**: Efecto de desenfoque translúcido con mezcla cromática adaptativa.
- 🖼️ **Extracción Inteligente del Fondo de Pantalla (Wallpaper Palette)**:
  - **Zona Inferior**: Detecta y aplica el color exacto del wallpaper en la posición donde reposa la Now Bar.
  - **Acento Monet**: Paleta dinámica de Material You del sistema.
  - **Color Dominante**: Color de fondo principal de la pantalla de bloqueo.
- 🎛️ **Control Granular e Independiente por Categoría**:
  Cada servicio de la Now Bar cuenta con su propio interruptor y ajuste de opacidad individual:
  - 🎵 **Música y Reproducción Multimedia** (`NowBarMediaCardView`)
  - 🤖 **Now Brief / Sugerencias IA** (`NowBarBriefCardView`)
  - 🔋 **Batería y Carga en Vivo** (`NowBarBatteryCardView`)
  - ⏱️ **Reloj, Temporizador y Cronómetro**
  - 🔦 **Linterna**
  - 🎙️ **Grabación de Voz y Notas de Audio**
  - 📞 **Llamadas Telefónicas**
  - 🛡️ **Modos y Rutinas**
- ⚡ **Inyección en Caliente sin Retardo (Zero-Lag Hot Creation)**:
  - Intercepción profunda en `NowBarCardStackView` (`addView` y `q`).
  - Supresión automática del degradado nativo de notificación de Samsung (`NowBarCardGradientView`), garantizando que cualquier cápsula que aparezca repentinamente con la pantalla encendida (ej. linterna o temporizador) se pinte con tu paleta personalizada desde el frame 0 (sin parpadeos ni colores por defecto).
- 🎚️ **Vista Previa en Vivo (Live Preview)**:
  - Visualizador en tiempo real dentro de la app con ondas de audio dinámicas, controles de reproducción y fondo sincronizado.

---

## 📱 Requisitos / Requirements

- Dispositivo **Samsung Galaxy** con **One UI 7.0 / 7.1** (Android 15).
- Acceso Root (**Magisk v26+**, **KernelSU** o **APatch**).
- **LSPosed Framework** instalado y funcionando (Zygisk release / JingMatrix fork).

---

## 🚀 Instalación / Installation

1. Descarga el APK más reciente desde la sección de **[Releases](https://github.com/tavataan/NowBarColor/releases)**.
2. Instala el APK en tu dispositivo.
3. Abre el administrador de **LSPosed**:
   - Activa el módulo **Now Bar Color**.
   - Verifica que el alcance (Scope) recomendado incluya:
     - `Interfaz del sistema` (`com.android.systemui`)
     - `Always On Display` / `FaceWidget` (`com.samsung.android.app.aodservice`)
4. Reinicia la interfaz del sistema (`su -c killall com.android.systemui`) o reinicia el dispositivo.
5. Abre la aplicación **Now Bar Color** desde el cajón de aplicaciones para personalizar los colores y ajustes a tu gusto.

---

## 🛠️ Compilación desde el Código Fuente / Building from Source

El proyecto utiliza un pipeline ligero y autónomo en Python (`build.py`) compatible tanto con entornos Linux/Android nativos (Termux/AGY) como con sistemas de escritorio con Android SDK:

```bash
# Clonar el repositorio
git clone https://github.com/tavataan/NowBarColor.git
cd NowBarColor

# Compilar y generar el APK firmado
python3 build.py
```

El APK resultante se generará en:
```
build/NowBarColor.apk
```

---

## 📂 Estructura del Proyecto / Project Structure

```
├── AndroidManifest.xml          # Definición del paquete y configuración de LSPosed
├── assets/                      # Metadatos del módulo (xposed_init) y recursos gráficos
├── meta_xposed/                 # Metadatos para el repositorio de LSPosed (module.prop, scope.list)
├── res/                         # Recursos de la UI (layouts, strings, iconos, estilos One UI)
├── src/com/tavataan/nowbarcolor/
│   ├── MainActivity.java        # UI de configuración nativa One UI con Live Preview y HSV Pickers
│   ├── NowBarHook.java          # Inyección principal LSPosed en SystemUI y FaceWidget/AOD
│   ├── ConfigManager.java       # Persistencia y gestión de configuración por categorías
│   ├── WallpaperColorHelper.java# Extracción de colores del fondo de bloqueo (Monet, Zona Inferior)
│   ├── OneUIMediaCapsuleView.java# Componente visual de vista previa con controles y ondas de audio
│   ├── OneUISwitch.java         # Interruptor personalizado estilo Samsung One UI
│   └── HsvColorPickerView.java  # Selector cromático HSV interactivo
└── build.py                     # Script de compilación y empaquetado autónomo
```

---

## 📄 Licencia / License

Distribuido bajo la Licencia **MIT**. Consulta el archivo [LICENSE](LICENSE) para más detalles.

---

## 👤 Autor

Creado por **tavataan**.
