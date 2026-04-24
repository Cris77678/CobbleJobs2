# CobbleJobs — Guía de Compilación

## Requisitos previos

| Herramienta | Versión mínima | Descarga |
|-------------|---------------|---------|
| JDK | 21 | https://adoptium.net/ |
| Git | cualquiera | https://git-scm.com/ |
| IDE (opcional) | — | IntelliJ IDEA recomendado |

---

## Paso 1 — Añadir el `gradle-wrapper.jar` (OBLIGATORIO)

El archivo `gradle/wrapper/gradle-wrapper.jar` no está incluido en el ZIP porque
es un binario (~60 KB) que GitHub no permite distribuir directamente.

**Opción A — con Gradle instalado localmente:**
```bash
cd cobblejobs
gradle wrapper --gradle-version 8.8
```

**Opción B — descargarlo manualmente:**
1. Ve a: https://github.com/gradle/gradle/raw/v8.8.0/gradle/wrapper/gradle-wrapper.jar
2. Guárdalo en `gradle/wrapper/gradle-wrapper.jar`

**Opción C — usando el template oficial de Fabric:**
```bash
git clone https://github.com/FabricMC/fabric-example-mod temp-fabric
cp temp-fabric/gradle/wrapper/gradle-wrapper.jar gradle/wrapper/
rm -rf temp-fabric
```

---

## Paso 2 — Compilar el proyecto

### Linux / macOS
```bash
cd cobblejobs
chmod +x ./gradlew        # solo la primera vez
./gradlew build
```

### Windows (PowerShell)
```powershell
cd cobblejobs
.\gradlew.bat build
```

El primer `build` descarga automáticamente:
- Gradle 8.8
- Yarn mappings (1.21.1+build.3)
- Fabric API
- Cobblemon

Esto puede tardar **5–10 minutos** la primera vez.

---

## Paso 3 — Localizar el JAR compilado

```
cobblejobs/
└── build/
    └── libs/
        ├── cobblejobs-1.0.0.jar         ← este es el mod
        └── cobblejobs-1.0.0-sources.jar ← código fuente (opcional)
```

Copia `cobblejobs-1.0.0.jar` a la carpeta `mods/` de tu servidor/cliente Fabric.

---

## Paso 4 — Configurar IntelliJ IDEA (opcional pero recomendado)

```bash
./gradlew genSources       # genera las fuentes de Minecraft para el IDE
./gradlew idea             # genera los archivos .iml e .ipr
```

Luego abre IntelliJ → **Open** → selecciona la carpeta `cobblejobs`.

---

## Estructura del proyecto

```
cobblejobs/
├── build.gradle                  ← dependencias y configuración de compilación
├── settings.gradle               ← nombre del proyecto y repositorios de plugins
├── gradle.properties             ← versiones (MC, Fabric, Cobblemon, Java)
├── gradlew / gradlew.bat         ← wrappers para Linux/Windows
├── gradle/wrapper/
│   ├── gradle-wrapper.jar        ← DEBES AÑADIRLO (ver Paso 1)
│   └── gradle-wrapper.properties ← URL de Gradle 8.8
└── src/main/
    ├── java/dev/cobblejobs/
    │   ├── CobbleJobs.java           ← entrypoint principal
    │   ├── core/ConfigManager.java
    │   ├── data/
    │   │   ├── PlayerDataManager.java    ← caché + IO asíncrono
    │   │   ├── PlayerDataBundle.java     ← wrapper en memoria
    │   │   ├── PlayerJobState.java       → <UUID>_job.json
    │   │   └── PlayerProgressData.java   → <UUID>_data.json
    │   ├── event/
    │   │   ├── ServerTickHandler.java
    │   │   └── DynamicEventManager.java
    │   ├── job/
    │   │   ├── Job.java (interfaz)
    │   │   ├── JobRegistry.java
    │   │   └── impl/
    │   │       ├── FisherJob.java
    │   │       └── ButcherJob.java
    │   ├── command/JobCommandRegistry.java
    │   └── util/MessageUtil.java
    └── resources/
        └── fabric.mod.json
```

---

## Dependencias declaradas

| Dependencia | Scope | Versión |
|-------------|-------|---------|
| Minecraft | minecraft | 1.21.1 |
| Yarn mappings | mappings | 1.21.1+build.3 |
| Fabric Loader | modImplementation | 0.15.11 |
| Fabric API | modImplementation | 0.102.0+1.21.1 |
| Cobblemon | modCompileOnly | 1.6.1+1.21.1 |
| Lombok | compileOnly + annotationProcessor | 1.18.32 |

> **Impactor Economy API** está comentada en `build.gradle`. Descoméntala cuando
> vayas a implementar el sistema de pagos.

---

## Comandos Gradle útiles

```bash
./gradlew build          # compila y genera el JAR
./gradlew clean build    # limpia antes de compilar (útil si algo falla)
./gradlew runServer      # lanza un servidor de prueba local
./gradlew runClient      # lanza el cliente de Minecraft para pruebas
./gradlew genSources     # genera fuentes de Minecraft (para el IDE)
./gradlew dependencies   # muestra el árbol de dependencias
```

---

## Solución de problemas comunes

**Error: `Could not find gradle-wrapper.jar`**
→ Sigue el Paso 1 de esta guía.

**Error: `Could not resolve com.cobblemon:cobblemon-fabric`**
→ Verifica que el repositorio `https://maven.cobblemon.org/repository/all-public/`
  esté accesible. Si la versión cambió, actualiza `cobblemon_version` en `gradle.properties`.

**Error: `package net.fabricmc.fabric.api... does not exist`**
→ Ejecuta `./gradlew clean build` para forzar la re-descarga de dependencias.

**Error de Lombok: `cannot find symbol` en getters/setters**
→ En IntelliJ: File → Settings → Build Tools → Gradle → Build and run using: **IntelliJ IDEA**.
  Luego habilita el plugin de Lombok: File → Settings → Plugins → busca "Lombok".
