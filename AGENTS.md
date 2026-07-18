# Dynamic Sound Culling — Multi-Version Port Matrix

## Proje Yapısı

| Dizin | MC | Loader | Java | Gradle | Mappings | Build | Run |
|-------|----|--------|------|--------|----------|-------|-----|
| `fabric-1201/` | 1.20.1 | Fabric | 17 | 8.8 | Yarn | ✅ | — |
| `forge-1201/` | 1.20.1→.4 | Forge FG6 | 17 | 8.8 | Mojang | ✅ | — |
| `forge-1206/` | 1.20.6 | Forge FG7 | 21 | 9.4 | Mojang | ✅ | ✅ |
| `fabric-1211/` | 1.21.1 | Fabric | 21 | 8.13 | Yarn | ✅ | — |
| `forge-1211/` | 1.21→.5 | Forge FG7 | 21 | 9.4 | Mojang | ✅ | — |
| `neoforge-1211/` | 1.21→.5 | NeoForge MDG | 21 | 9.4 | Mojang | ✅ | — |
| `fabric-2612/` | 26.1.1→.2 | Fabric | 25 | 9.4 | Mojang (none) | ✅ | — |
| `forge-2612/` | 26.1.2 | Forge FG7 | 25 | 9.4 | Mojang (none) | ✅ | — |
| `neoforge-2612/` | 26.1→.2 | NeoForge MDG | 25 | 9.4 | Mojang (none) | ✅ | — |
| `fabric-262/` | 26.2 | Fabric | 25 | 9.5 | Mojang (none) | ✅ | ✅ |
| `forge-262/` | 26.2 | Forge FG7 | 25 | 9.4 | Mojang (none) | ✅ | ✅ |
| `neoforge-262/` | 26.2 | NeoForge MDG | 25 | 9.4 | Mojang (none) | ✅ | ✅ |

## Build Komutları

### 26.2 (Java 25)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
cd fabric-262    && .\gradlew.bat build
cd forge-262     && .\gradlew.bat build
cd neoforge-262  && .\gradlew.bat build
```

### 26.1.x (Java 25)
```powershell
$env:JAVA_HOME = "C:\Program Files\Java\jdk-25"
cd fabric-2612   && .\gradlew.bat build
cd forge-2612    && .\gradlew.bat build
cd neoforge-2612 && .\gradlew.bat build
```

### 1.21.x (Java 21)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
cd fabric-1211   && .\gradlew.bat build
cd forge-1211    && .\gradlew.bat build
cd neoforge-1211 && .\gradlew.bat build
```

### 1.20.6 (Java 21)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot"
cd forge-1206    && .\gradlew.bat build
```

### 1.20.1 (Java 17)
```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot"
cd fabric-1201   && .\gradlew.bat build
cd forge-1201    && .\gradlew.bat build
```

---

## Forge Sürümleri — Hangi MC'de Hangi Forge

| MC | Forge Versiyon | FG | Java | Not |
|----|---------------|----|------|-----|
| 1.20.1 | `47.2.0` | FG6 | 17 | forge-1201 |
| 1.20.2 | maven'da var | FG6 | 17 | forge-1201 kodu çalışır |
| 1.20.3 | maven'da var | FG6 | 17 | forge-1201 kodu çalışır |
| 1.20.4 | maven'da var | FG6 | 17 | forge-1201 kodu çalışır |
| 1.20.5 | ❌ userdev YOK | — | — | Forge bu sürümü atlamış |
| 1.20.6 | `50.2.1` | FG7 | **21** | forge-1206 (yeni port) |
| 1.21.0 | ❌ yok | — | — | Forge 1.21.0'ı atlamış |
| 1.21.1 | `52.1.2` | FG7 | 21 | forge-1211 |
| 1.21.2 | maven'da var | FG7 | 21 | forge-1211 kodu çalışır |
| 1.21.3 | maven'da var | FG7 | 21 | forge-1211 kodu çalışır |
| 1.21.4 | `54.1.6` | FG7 | 21 | forge-1211 |
| 1.21.5 | `55.0.24` | FG7 | 21 | test edildi ✅ |
| 1.21.6→.11 | ❌ Forge YOK | — | — | Sadece Fabric/NeoForge |
| 26.1 | — | FG7 | 25 | |
| 26.1.2 | `64.0.8` | FG7 | 25 | forge-2612 |
| 26.2 | `65.0.3` | FG7 | 25 | forge-262 |

---

## 26.2 Özel Notlar

### API Değişiklikleri
```diff
- Minecraft.getInstance().setScreen(this.parent);
+ Minecraft.getInstance().gui.setScreen(this.parent);
```
`setScreen()` ve `getScreen()` metodları `Minecraft` sınıfından `Gui` sınıfına taşındı.

### Fabric 26.2 Build Değişiklikleri
- Loom: `1.17.13` (1.15-SNAPSHOT çalışmaz)
- Loader: `0.19.3`
- Fabric API: `0.152.1+26.2`
- Gradle: `9.5.1` (9.4.1 çalışmaz)

### Mod Menu Uyumsuzluğu
Mod Menu `18.0.0-alpha.8` 26.2'de crash veriyor (`I18n.exists()` kaldırılmış). 26.2 için Mod Menu kullanma:
```gradle
// compileOnly "com.terraformersmc:modmenu:latest.release"  // 26.2 uyumsuz!
```
ve `fabric.mod.json`'dan `modmenu` entrypoint'ini kaldır.

### NeoForge 26.2
- `neo_version=26.2.0.1-beta`
- `neoforge.mods.toml` dosya adına dikkat (`mods.toml` değil!)
- versionRange: `[26.2,26.3)`

---

## 1.20.6 vs 1.21.x API Farkları

### Config Screen Render
```diff
// 1.21.x:
- graphics.drawStringWithBackdrop(this.font, text, x, y, color, 0x80000000);

// 1.20.6:
+ graphics.drawString(this.font, text, x, y, color);
```
`drawStringWithBackdrop` 1.21+ sürümlerde var, 1.20.6'da yok.

### Forge API
- `ModLoadingContext.get()` deprecation warning (her iki sürümde de)
- `TickEvent.Phase.END` deprecation warning (1.20.6'da)

---

## Fabric + Mojang Mappings (1.21.x) — ÇALIŞMIYOR

Loom 1.9 + Fabric API 1.21.x + `officialMojangMappings()` access widener uyumsuzluğu veriyor:
```
AccessWidenerFormatException: Invalid access widener file header
```
Sebep: Fabric API'nin access widener dosyaları intermediary namespace'te, Mojang mappings ile uyuşmuyor.

**Çözüm:** Fabric için Yarn kullanmaya devam et. Her MC sürümü için ayrı build al.
Forge/NeoForge zaten Mojang kullandığı için cross-version çalışıyor.

---

## API Farkları Özeti

| | 26.x Mojang | 1.21.x/1.20.x Yarn | 1.21.x/1.20.x Mojang |
|---|---|---|---|
| Client class | `Minecraft` | `MinecraftClient` | `Minecraft` |
| Text | `Component` | `Text` | `Component` |
| Font | `Font` | `TextRenderer` | `Font` |
| GUI render | `GuiGraphics` | `DrawContext` | `GuiGraphics` |
| Render method | `extractRenderState` | `render` | `render` |
| Sound engine | `SoundEngine` | `SoundSystem` | `SoundEngine` |
| Sound ID | `getIdentifier()` | `getId()` | `getLocation()` |
| ID type | `Identifier` | `Identifier` | `ResourceLocation` |
| Category | `SoundSource` | `SoundCategory` | `SoundSource` |
| play() return | `PlayResult` | `void` | `void` |
| Event tick | `ClientTickEvent.Post` | `END_CLIENT_TICK` | `TickEvent.ClientTickEvent` + `.phase` |
| setScreen | `gui.setScreen()` (26.2) | `setScreen()` | `setScreen()` |

## JDK Konumları
```
Java 25: C:\Program Files\Java\jdk-25
Java 21: C:\Program Files\Eclipse Adoptium\jdk-21.0.9.10-hotspot
Java 17: C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot
```
